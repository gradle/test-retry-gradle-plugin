package org.gradle.testretry.build

import groovy.xml.XmlSlurper

class PluginsVersionData {

    static String latestVersion(String group, String name) {
        String url = "https://plugins.gradle.org/m2/${group.replace('.', '/')}/$name/maven-metadata.xml"
        return new XmlSlurper().parse(url).version
    }
}
