package org.gradle.testretry.internal.filter

import spock.lang.Specification

class GlobPatternTest extends Specification {

    def "glob pattern matching"() {
        expect:
        with(GlobPattern.from("*")) {
            matches "a"
            matches "b"
            matches "*"
            matches " "
        }
        with(GlobPattern.from("**")) {
            matches "a"
            matches "b"
            matches "*"
            matches " "
        }
        with(GlobPattern.from(".")) {
            matches "."
            !matches("b")
        }
        with(GlobPattern.from("a*")) {
            matches "a"
            matches "ab"
            !matches("ba")
            !matches("b")
        }
        with(GlobPattern.from("a*a")) {
            matches "aba"
            matches "aa"
            !matches("ba")
            !matches("abc")
        }
        with(GlobPattern.from("**")) {
            matches "aba"
            matches "aa"
            matches ""
            matches "a"
        }
    }
}
