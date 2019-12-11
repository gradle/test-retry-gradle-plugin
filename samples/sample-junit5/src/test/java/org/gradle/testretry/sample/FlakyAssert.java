package org.gradle.testretry.sample;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FlakyAssert {
    public static void flakyAssert() {
        try {
            Path marker = Paths.get("marker.file");
            if (!Files.exists(marker)) {
                Files.write(marker, "mark".getBytes());
                throw new RuntimeException("fail me!");
            }
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}