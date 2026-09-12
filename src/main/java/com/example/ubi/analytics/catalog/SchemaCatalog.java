package com.example.ubi.analytics.catalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Hand-maintained catalog of collections the analytics DSL may query.
 * Versioned so later LLM prompts can pin a schema snapshot.
 */
public final class SchemaCatalog {

    public static final String VERSION = "2026-09-12.1";

    public static final String POLICIES = "policies";
    public static final String TELEMETRY_EVENTS = "telemetry_events";
    public static final String BILLING_USAGE_RECORDS = "billing_usage_records";

    private final Map<String, CollectionSchema> collections;

    public SchemaCatalog(List<CollectionSchema> collections) {
        Map<String, CollectionSchema> map = new LinkedHashMap<>();
        for (CollectionSchema collection : collections) {
            map.put(collection.name(), collection);
        }
        this.collections = Map.copyOf(map);
    }

    public static SchemaCatalog builtin() {
        return new SchemaCatalog(List.of(
                policies(),
                telemetryEvents(),
                billingUsageRecords()
        ));
    }

    public String version() {
        return VERSION;
    }

    public Set<String> collectionNames() {
        return collections.keySet();
    }

    public List<CollectionSchema> collections() {
        return List.copyOf(collections.values());
    }

    public Optional<CollectionSchema> collection(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(collections.get(name));
    }

    public CollectionSchema requireCollection(String name) {
        return collection(name).orElseThrow(() ->
                new IllegalArgumentException("Unknown collection: " + name));
    }

    private static CollectionSchema policies() {
        return new CollectionSchema(
                POLICIES,
                "com.example.ubi.domain.model.Policy",
                "Insurance policies. One row per policy. Id is stored as Mongo _id (logical name policyId).",
                List.of(
                        field("policyId", "_id", FieldType.STRING, true,
                                "Policy document id (Mongo _id)."),
                        field("userId", "userId", FieldType.STRING, true,
                                "External user id that owns the policy."),
                        hidden("stripeCustomerId", "stripeCustomerId", FieldType.STRING,
                                "Stripe Customer id (cus_…). Sensitive — not NL-exposed."),
                        field("basePremium", "basePremium", FieldType.DECIMAL, true,
                                "Base monthly premium in dollars."),
                        enumField("status", "status", true,
                                "Policy lifecycle status.",
                                List.of("ACTIVE", "SUSPENDED", "CANCELLED")),
                        field("policyNumber", "policyNumber", FieldType.INTEGER, true,
                                "Human-facing sequence used to build names like Policy_1."),
                        field("paidAmountCents", "paidAmountCents", FieldType.LONG, true,
                                "Cumulative amount successfully paid via Stripe Checkout, in cents."),
                        hidden("lastCheckoutSessionId", "lastCheckoutSessionId", FieldType.STRING,
                                "Last Stripe Checkout session id. Sensitive — not NL-exposed."),
                        field("lastPaidAt", "lastPaidAt", FieldType.INSTANT, true,
                                "Timestamp of the last successful Checkout payment.")
                ),
                List.of()
        );
    }

    private static CollectionSchema telemetryEvents() {
        return new CollectionSchema(
                TELEMETRY_EVENTS,
                "com.example.ubi.domain.model.TelemetryEvent",
                "Vehicle telemetry events ingested for a policy. Id is stored as Mongo _id (logical name eventId).",
                List.of(
                        field("eventId", "_id", FieldType.STRING, true,
                                "Telemetry event id / idempotency key (Mongo _id)."),
                        field("policyId", "policyId", FieldType.STRING, true,
                                "Owning policy id. Documented FK to policies.policyId — do not $lookup in v1."),
                        field("timestamp", "timestamp", FieldType.INSTANT, true,
                                "Event time (ISO-8601). Stored as BSON Date."),
                        field("speedKmh", "speedKmh", FieldType.INTEGER, true,
                                "Vehicle speed in km/h."),
                        field("isHardBraking", "isHardBraking", FieldType.BOOLEAN, true,
                                "Whether the event includes a hard-brake. Java bean name may also appear as hardBraking.",
                                List.of(),
                                List.of("hardBraking")),
                        field("distanceTraveledKm", "distanceTraveledKm", FieldType.DECIMAL, true,
                                "Distance traveled during the event, in km.")
                ),
                List.of(
                        new RelationshipDoc(
                                "policyId",
                                POLICIES,
                                "policyId",
                                "many-to-one",
                                "Each telemetry event belongs to one policy. v1 queries a single collection only."
                        )
                )
        );
    }

    private static CollectionSchema billingUsageRecords() {
        return new CollectionSchema(
                BILLING_USAGE_RECORDS,
                "com.example.ubi.domain.model.BillingUsageRecord",
                "Billing outbox / audit rows created from each telemetry event.",
                List.of(
                        field("billingUsageId", "_id", FieldType.STRING, true,
                                "Billing usage record id (Mongo _id)."),
                        field("telemetryEventId", "telemetryEventId", FieldType.STRING, true,
                                "Source telemetry event id. Documented FK to telemetry_events.eventId."),
                        field("policyId", "policyId", FieldType.STRING, true,
                                "Owning policy id. Documented FK to policies.policyId."),
                        hidden("stripeCustomerId", "stripeCustomerId", FieldType.STRING,
                                "Stripe Customer id copied from the policy. Sensitive — not NL-exposed."),
                        field("usageCharge", "usageCharge", FieldType.DECIMAL, true,
                                "Usage / risk charge in dollars for the source event."),
                        field("billableUsageUnits", "billableUsageUnits", FieldType.INTEGER, true,
                                "Integer units reported to the Stripe billing meter."),
                        enumField("status", "status", true,
                                "Outbox status.",
                                List.of("PENDING", "REPORTED", "FAILED", "ABANDONED")),
                        field("stripeMeterEventIdentifier", "stripeMeterEventIdentifier", FieldType.STRING, true,
                                "Deterministic Stripe meter event identifier used for retries."),
                        field("attemptCount", "attemptCount", FieldType.INTEGER, true,
                                "Number of Stripe meter report attempts."),
                        field("lastError", "lastError", FieldType.STRING, true,
                                "Last Stripe / reporting error message, if any."),
                        field("nextRetryAt", "nextRetryAt", FieldType.INSTANT, true,
                                "When the outbox scheduler should retry a FAILED row."),
                        field("createdAt", "createdAt", FieldType.INSTANT, true,
                                "Row creation time."),
                        field("updatedAt", "updatedAt", FieldType.INSTANT, true,
                                "Last update time.")
                ),
                List.of(
                        new RelationshipDoc(
                                "policyId",
                                POLICIES,
                                "policyId",
                                "many-to-one",
                                "Each billing row belongs to one policy. v1 does not join."
                        ),
                        new RelationshipDoc(
                                "telemetryEventId",
                                TELEMETRY_EVENTS,
                                "eventId",
                                "one-to-one",
                                "Each billing row is created from one telemetry event. v1 does not join."
                        )
                )
        );
    }

    private static FieldSchema field(
            String name,
            String mongoName,
            FieldType type,
            boolean nlExposed,
            String description
    ) {
        return field(name, mongoName, type, nlExposed, description, List.of(), List.of());
    }

    private static FieldSchema field(
            String name,
            String mongoName,
            FieldType type,
            boolean nlExposed,
            String description,
            List<String> enumValues,
            List<String> aliases
    ) {
        return new FieldSchema(name, mongoName, type, nlExposed, description, enumValues, aliases);
    }

    private static FieldSchema enumField(
            String name,
            String mongoName,
            boolean nlExposed,
            String description,
            List<String> enumValues
    ) {
        return new FieldSchema(name, mongoName, FieldType.ENUM, nlExposed, description, enumValues, List.of());
    }

    private static FieldSchema hidden(String name, String mongoName, FieldType type, String description) {
        return new FieldSchema(name, mongoName, type, false, description, List.of(), List.of());
    }
}
