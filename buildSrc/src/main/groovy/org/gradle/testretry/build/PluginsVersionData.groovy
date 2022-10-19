package org.gradle.testretry.build

import groovy.transform.CompileStatic
import groovy.xml.XmlSlurper

@CompileStatic
class PluginsVersionData {

    static String latestVersion(String group, String name) {
        def url = "https://plugins.gradle.org/m2/${group.replace('.', '/')}/$name/maven-metadata.xml"
        def result = new XmlSlurper().parse(url)

        result["version"]
    }
}
