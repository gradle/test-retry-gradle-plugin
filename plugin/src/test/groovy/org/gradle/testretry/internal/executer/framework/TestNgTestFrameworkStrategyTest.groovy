/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.testretry.internal.executer.framework

import spock.lang.Specification
import spock.lang.Subject

class TestNgTestFrameworkStrategyTest extends Specification {

    @Subject
    def strategy = new TestNgTestFrameworkStrategy()

    def "normalizes '#testName' to '#expected'"() {
        expect:
        strategy.normalizeTestName(testName) == expected

        where:
        testName                           | expected
        'test'                             | 'test'
        'test[0](1)'                       | 'test'
        'test[12](a, b)'                   | 'test'
        'test[0]()'                        | 'test'
        'test[0](null)'                    | 'test'
        'test[0](Wrapper(value=a))'        | 'test'
        'test[1](a (b) c)'                 | 'test'
        'test[0](a))'                      | 'test'
        'test[0](a[1](b))'                 | 'test'
        'test[0](acme.Value@1b6d3586)'     | 'test'
        'test[0](first line\nsecond line)' | 'test'
        'custom[name]'                     | 'custom[name]'
        'custom[name][0](a)'               | 'custom[name][0](a)'
        'test[x](a)'                       | 'test[x](a)'
        'test[0]'                          | 'test[0]'
    }
}
