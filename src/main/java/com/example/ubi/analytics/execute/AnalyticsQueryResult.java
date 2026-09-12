package com.example.ubi.analytics.execute;

import com.example.ubi.analytics.ast.ChartHint;
import java.util.List;
import org.bson.Document;

public record AnalyticsQueryResult(
        String catalogVersion,
        String collection,
        ChartHint chartHint,
        List<Document> rows
) {
}
