package com.example.ubi.analytics.execute;

import java.util.List;
import org.bson.Document;

/**
 * Thin seam over Mongo so {@link AnalyticsExecutor} can be unit-tested without a database.
 */
public interface MongoAggregationRunner {

    List<Document> aggregate(String collection, List<Document> pipeline, long maxTimeMs);
}
