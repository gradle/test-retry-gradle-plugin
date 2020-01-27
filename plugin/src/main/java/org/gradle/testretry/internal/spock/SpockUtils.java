/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.testretry.internal.spock;

import org.gradle.testretry.internal.TestName;
import org.gradle.testretry.internal.visitors.SpockParameterClassVisitor;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SpockUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpockUtils.class);

    private static final String SPOCK_SETUPSPEC_METHOD = "setupSpec";

    public static boolean isSpockSetupSpockFailure(Throwable throwable) {
        return Arrays.asList(throwable.getStackTrace()).stream().anyMatch(it -> it.getMethodName().equals(SPOCK_SETUPSPEC_METHOD));
    }

    @NotNull
    public static List<TestName> withSpockParametersRemoved(TestName failedTest, File testClass, boolean setupSpecFailure) {
        try (FileInputStream testClassIs = new FileInputStream(testClass)) {
            ClassReader classReader = new ClassReader(testClassIs);
            SpockParameterClassVisitor visitor = new SpockParameterClassVisitor(failedTest.getName(), setupSpecFailure);
            classReader.accept(visitor, 0);
            return visitor.getAllTestMethods().stream().map(m -> new TestName(failedTest.getClassName(), m)).collect(Collectors.toList());
        } catch (Throwable t) {
            LOGGER.warn("Unable to determine if class " + failedTest.getClassName() + " contains Spock @Unroll parameterizations", t);
            return Collections.singletonList(failedTest);
        }
    }

}
