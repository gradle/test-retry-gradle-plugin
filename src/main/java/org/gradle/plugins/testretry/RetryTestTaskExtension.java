package org.gradle.plugins.testretry;

public class RetryTestTaskExtension {

    private int maxRetries;

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

}
