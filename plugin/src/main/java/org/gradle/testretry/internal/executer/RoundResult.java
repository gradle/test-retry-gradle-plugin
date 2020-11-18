package org.gradle.testretry.internal.executer;

final class RoundResult {

    final TestNames failedTests;
    final TestNames nonRetriedTests;
    final boolean lastRound;

    RoundResult(TestNames failedTests, TestNames nonRetriedTests, boolean lastRound) {
        this.failedTests = failedTests;
        this.nonRetriedTests = nonRetriedTests;
        this.lastRound = lastRound;
    }
}
