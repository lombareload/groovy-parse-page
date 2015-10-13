import groovy.transform.ToString
import groovy.util.slurpersupport.GPathResult
import groovy.xml.MarkupBuilder

import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

File htmlFile = new File('../resources/kohls-press-realease.html')

XmlSlurper slurper = new XmlSlurper(false, false, true)
slurper.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)

GPathResult root = slurper.parse(htmlFile)

GPathResult links = root.'body'.div.div.div.div

ExecutorService pool = Executors.newFixedThreadPool(20)

@ToString
class PressRelease {
    int id
    String dateStr
    String relativePDFPath
    String text
}

Map years = [:]
List<PressRelease> currentYear
int counter = 1
links.forEach { GPathResult parentDiv ->
    GPathResult children = parentDiv.children()
    children.forEach { GPathResult child ->
        switch (child.name()) {
            case 'h2':
                String yearText = child.text()
                currentYear = []
                years.put(yearText, currentYear)
                break
            case 'div':
                child.div.p.forEach { GPathResult p ->
                    int[] date = p.span.toString().trim().split('/').collect {Integer.parseInt(it)}
                    String dateStr = String.format('20%02d-%02d-%02d', date[2], date[0], date[1])
                    Path path = Paths.get('www.kohlscorporation.com/PressRoom/', p.a.'@href'.toString())
                    Path normalizedPath = path.normalize()
                    String normalizedStr = 'http://' + normalizedPath.toString()
                    String relativePDFPath = normalizedStr[41..-1]
                    currentYear.add(new PressRelease(dateStr: dateStr, relativePDFPath: relativePDFPath, text: p.a.text().trim(), id: counter++))
                    pool.execute({ ->
                        InputStream urlInput = normalizedStr.toURL().openStream()
                        Files.copy(urlInput, Paths.get(relativePDFPath), StandardCopyOption.REPLACE_EXISTING)
                    })
                }
                break
        }
    }
}

years.forEach { String key, List<PressRelease> value ->
    File yearFile = new File("../resources/press-release/${key}.xml")
    yearFile.parentFile.exists() ? true : yearFile.parentFile.mkdir()
    BufferedWriter writer = Files.newBufferedWriter(yearFile.toPath(),
            StandardCharsets.UTF_8,
            StandardOpenOption.WRITE, StandardOpenOption.CREATE)
    MarkupBuilder builder = new MarkupBuilder(writer)
    builder.pressReleases(xmlns: "kohls-press-release") {
        value.forEach { PressRelease pr ->
            pressRelease {
                id {
                    mkp.yield(counter - pr.id)
                }
                link {
                    mkp.yield(pr.relativePDFPath)
                }
                date {
                    mkp.yield(pr.dateStr)
                }
                text {
                    mkp.yield(pr.text)
                }
            }
        }
    }
    writer.flush()
}

pool.shutdown()