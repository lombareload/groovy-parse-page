
XmlSlurper slurper = new XmlSlurper(false, false, true)
slurper.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
slurper.parse(new File('../resources/kohls-press-realease.html'))
println(root.xmlns)