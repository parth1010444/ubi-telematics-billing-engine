package com.example.ubi.analytics.allowlist;

import com.example.ubi.exception.PipelineRejectedException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.Document;
import org.springframework.stereotype.Component;

/**
 * Second-line defense: walk a compiled pipeline and reject any stage or operator
 * that is not explicitly allowlisted. Blocks {@code $out}, {@code $merge},
 * {@code $lookup}, {@code $where}, {@code $function}, and similar.
 */
@Component
public class PipelineAllowlist {

    public static final Set<String> ALLOWED_STAGES = Set.of(
            "$match",
            "$group",
            "$sort",
            "$limit",
            "$project"
    );

    public static final Set<String> ALLOWED_OPERATORS = Set.of(
            "$eq",
            "$ne",
            "$gt",
            "$gte",
            "$lt",
            "$lte",
            "$in",
            "$exists",
            "$sum",
            "$avg",
            "$min",
            "$max",
            "$cond"
    );

    public void verify(List<Document> pipeline) {
        if (pipeline == null || pipeline.isEmpty()) {
            throw new PipelineRejectedException("Pipeline must not be empty");
        }
        boolean hasLimit = false;
        for (int i = 0; i < pipeline.size(); i++) {
            Document stage = pipeline.get(i);
            if (stage == null || stage.size() != 1) {
                throw new PipelineRejectedException("Stage " + i + " must contain exactly one operator");
            }
            String stageName = stage.keySet().iterator().next();
            if (!ALLOWED_STAGES.contains(stageName)) {
                throw new PipelineRejectedException("Stage not allowlisted: " + stageName);
            }
            if ("$limit".equals(stageName)) {
                hasLimit = true;
                Object limit = stage.get("$limit");
                if (!(limit instanceof Number number) || number.longValue() < 1) {
                    throw new PipelineRejectedException("$limit must be a positive number");
                }
            }
            walk(stage.get(stageName), "stage[" + i + "]." + stageName);
        }
        if (!hasLimit) {
            throw new PipelineRejectedException("Pipeline must include a $limit stage");
        }
    }

    private void walk(Object node, String path) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (key.startsWith("$") && !ALLOWED_OPERATORS.contains(key) && !ALLOWED_STAGES.contains(key)) {
                    throw new PipelineRejectedException("Operator not allowlisted at " + path + ": " + key);
                }
                walk(entry.getValue(), path + "." + key);
            }
            return;
        }
        if (node instanceof Iterable<?> items) {
            int index = 0;
            for (Object item : items) {
                walk(item, path + "[" + index + "]");
                index++;
            }
        }
    }
}
