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
package org.gradle.testretry.internal;

import javax.annotation.Nullable;
import java.util.Objects;

public final class TestName {

    private final String className;

    @Nullable
    private final String name;

    public TestName(String className, @Nullable String name) {
        this.className = className;
        this.name = name;
    }

    public String getClassName() {
        return className;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TestName that = (TestName) o;
        return Objects.equals(className, that.className) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, name);
    }
}
