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
package org.gradle.testretry.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Exposes the plugin version, which is written to {@code plugin-version.properties} at build time.
 */
public final class PluginVersion {

    private static final String RESOURCE = "/plugin-version.properties";
    private static final String UNKNOWN = "unknown";
    private static final String VERSION = readVersion();

    private PluginVersion() {
    }

    public static String get() {
        return VERSION;
    }

    private static String readVersion() {
        try (InputStream stream = PluginVersion.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                return UNKNOWN;
            }
            Properties properties = new Properties();
            properties.load(stream);
            String version = properties.getProperty("version");
            return version == null || version.isEmpty() ? UNKNOWN : version;
        } catch (IOException e) {
            return UNKNOWN;
        }
    }
}
