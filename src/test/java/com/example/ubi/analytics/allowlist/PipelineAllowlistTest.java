package com.example.ubi.analytics.allowlist;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ubi.exception.PipelineRejectedException;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;

class PipelineAllowlistTest {

    private final PipelineAllowlist allowlist = new PipelineAllowlist();

    @Test
    void acceptsAllowlistedReadPipeline() {
        List<Document> pipeline = List.of(
                new Document("$match", new Document("status", new Document("$eq", "ACTIVE"))),
                new Document("$group", new Document("_id", "$status").append("n", new Document("$sum", 1))),
                new Document("$sort", new Document("n", -1)),
                new Document("$limit", 10)
        );
        assertDoesNotThrow(() -> allowlist.verify(pipeline));
    }

    @Test
    void rejectsLookupMergeOutAndWhere() {
        assertRejected(List.of(
                new Document("$lookup", new Document("from", "policies")
                        .append("localField", "policyId")
                        .append("foreignField", "_id")
                        .append("as", "policy")),
                new Document("$limit", 10)
        ), "$lookup");

        assertRejected(List.of(
                new Document("$match", new Document("status", "ACTIVE")),
                new Document("$out", "stolen_policies")
        ), "$out");

        assertRejected(List.of(
                new Document("$merge", new Document("into", "other")),
                new Document("$limit", 1)
        ), "$merge");

        assertRejected(List.of(
                new Document("$match", new Document("$where", "this.speedKmh > 100")),
                new Document("$limit", 5)
        ), "$where");
    }

    @Test
    void rejectsNestedFunctionOperator() {
        Document project = new Document("$project", new Document("evil", new Document("$function",
                new Document("body", "return 1").append("args", List.of()).append("lang", "js"))));
        PipelineRejectedException exception = assertThrows(
                PipelineRejectedException.class,
                () -> allowlist.verify(List.of(project, new Document("$limit", 1)))
        );
        assertTrue(exception.getMessage().contains("$function"));
    }

    @Test
    void rejectsMissingLimit() {
        PipelineRejectedException exception = assertThrows(
                PipelineRejectedException.class,
                () -> allowlist.verify(List.of(new Document("$match", new Document("status", "ACTIVE"))))
        );
        assertTrue(exception.getMessage().contains("$limit"));
    }

    private void assertRejected(List<Document> pipeline, String operator) {
        PipelineRejectedException exception = assertThrows(
                PipelineRejectedException.class,
                () -> allowlist.verify(pipeline)
        );
        assertTrue(exception.getMessage().contains(operator), exception.getMessage());
    }
}
