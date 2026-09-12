package com.example.ubi.analytics.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SchemaCatalogTest {

    private final SchemaCatalog catalog = SchemaCatalog.builtin();

    @Test
    void pinsCatalogVersionAndWhitelistedCollections() {
        assertEquals("2026-09-12.1", catalog.version());
        assertEquals(
                java.util.Set.of("policies", "telemetry_events", "billing_usage_records"),
                catalog.collectionNames()
        );
    }

    @Test
    void hidesSensitiveFieldsFromNlExposure() {
        CollectionSchema policies = catalog.requireCollection("policies");
        assertFalse(policies.requireField("stripeCustomerId").nlExposed());
        assertFalse(policies.requireField("lastCheckoutSessionId").nlExposed());
        assertTrue(policies.requireField("policyId").nlExposed());
        assertEquals("_id", policies.requireField("policyId").mongoName());

        CollectionSchema billing = catalog.requireCollection("billing_usage_records");
        assertFalse(billing.requireField("stripeCustomerId").nlExposed());
        assertTrue(billing.requireField("usageCharge").nlExposed());
    }

    @Test
    void documentsRelationshipsWithoutJoining() {
        CollectionSchema telemetry = catalog.requireCollection("telemetry_events");
        assertEquals(1, telemetry.relationships().size());
        assertEquals("policies", telemetry.relationships().getFirst().targetCollection());
        assertTrue(telemetry.requireField("isHardBraking").matches("hardBraking"));
    }
}
