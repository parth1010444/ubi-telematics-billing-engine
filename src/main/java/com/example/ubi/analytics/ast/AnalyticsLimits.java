package com.example.ubi.analytics.ast;

public final class AnalyticsLimits {

    public static final int MAX_FILTERS = 8;
    public static final int MAX_GROUP_BY = 3;
    public static final int MAX_METRICS = 5;
    public static final int MAX_SORT = 2;
    public static final int MAX_HAVING = 8;
    public static final int MIN_LIMIT = 1;
    public static final int MAX_LIMIT = 100;
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_IN_VALUES = 50;

    private AnalyticsLimits() {
    }
}
