package org.gradle.testretry.internal.filter

import spock.lang.Specification

class RetryFilterTest extends Specification {

    List<String> includeClasses = []
    List<String> excludeClasses = []
    List<String> includeAnnotations = []
    List<String> excludeAnnotations = []

    Map<String, List<String>> annotations = [:]

    def "empty filter allows all"() {
        expect:
        with(filter()) {
            canRetry("foo.bar")
            canRetry("foo.baz")
        }
    }

    def "must match include pattern"() {
        when:
        includeClasses << "1.*" << "2.*"

        then:
        with(filter()) {
            canRetry("1.Test")
            canRetry("2.Test")
            !canRetry("3.Test")
        }
    }

    def "must not match exclude pattern"() {
        when:
        includeClasses << "1.*" << "2.*"
        excludeClasses << "2.*" << "3.*" << "z"

        then:
        with(filter()) {
            canRetry("1.Test")
            !canRetry("2.Test")
            !canRetry("3.Test")
            !canRetry("2.z")
        }
    }

    def "must have include annotation"() {
        when:
        includeClasses << "*include*"
        excludeClasses << "*exclude*"
        includeAnnotations << "*include*"
        annotations["include1"] = ["a"]
        annotations["include2"] = ["a", "include"]

        then:
        with(filter()) {
            !canRetry("include1")
            canRetry("include2")
            !canRetry("include3")
        }
    }

    def "must not have exclude annotation"() {
        when:
        includeClasses << "*include*"
        excludeClasses << "*exclude*"
        includeAnnotations << "*include*"
        excludeAnnotations << "*exclude*"
        annotations["include1"] = ["a"]
        annotations["include2"] = ["a", "include", "exclude"]
        annotations["include3"] = ["a", "include"]

        then:
        with(filter()) {
            !canRetry("include1")
            !canRetry("include2")
            canRetry("include3")
            !canRetry("include4")
        }
    }

    RetryFilter filter() {
        new RetryFilter(
            { annotations.getOrDefault(it, []).toSet() },
            includeClasses,
            includeAnnotations,
            excludeClasses,
            excludeAnnotations
        )
    }
}
