package com.opentable.metrics.http;

import java.util.Comparator;

enum CheckState {
    HEALTHY(200), WARNING(400), CRITICAL(500);

    static final Comparator<CheckState> SEVERITY = (c1, c2) -> c1.ordinal() - c2.ordinal();

    private final int httpStatus;

    CheckState(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
