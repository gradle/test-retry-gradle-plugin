package org.gradle.testretry.internal.executer;

import javax.annotation.Nullable;

public class LastResultHolder {
    @Nullable
    private RoundResult lastResult;

    @Nullable
    public RoundResult get() {
        return lastResult;
    }

    public void set(RoundResult lastResult) {
        this.lastResult = lastResult;
    }
}
