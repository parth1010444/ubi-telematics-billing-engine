package com.example.ubi.analytics.execute;

import com.example.ubi.config.AnalyticsProperties;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Runs an allowlisted aggregation. Uses {@code analytics.mongodb.uri} when set,
 * otherwise the primary Spring Data MongoDB {@link MongoTemplate}.
 */
@Component
public class MongoTemplateAggregationRunner implements MongoAggregationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoTemplateAggregationRunner.class);

    private final MongoTemplate mongoTemplate;
    private final MongoClient ownedClient;

    public MongoTemplateAggregationRunner(MongoTemplate mongoTemplate, AnalyticsProperties properties) {
        String overrideUri = properties.mongodbUri();
        if (overrideUri.isEmpty()) {
            this.mongoTemplate = mongoTemplate;
            this.ownedClient = null;
            LOGGER.info("Analytics Mongo runner using primary spring.data.mongodb.uri");
        } else {
            ConnectionString connectionString = new ConnectionString(overrideUri);
            this.ownedClient = MongoClients.create(connectionString);
            String database = connectionString.getDatabase();
            if (database == null || database.isBlank()) {
                database = mongoTemplate.getDb().getName();
            }
            this.mongoTemplate = new MongoTemplate(ownedClient, database);
            LOGGER.info("Analytics Mongo runner using analytics.mongodb.uri database={}", database);
        }
    }

    @Override
    public List<Document> aggregate(String collection, List<Document> pipeline, long maxTimeMs) {
        return mongoTemplate.getCollection(collection)
                .aggregate(pipeline)
                .allowDiskUse(false)
                .maxTime(maxTimeMs, TimeUnit.MILLISECONDS)
                .into(new ArrayList<>());
    }

    @PreDestroy
    public void closeOwnedClient() {
        if (ownedClient != null) {
            ownedClient.close();
        }
    }
}
