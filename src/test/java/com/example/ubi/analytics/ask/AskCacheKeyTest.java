package com.example.ubi.analytics.ask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class AskCacheKeyTest {

    @Test
    void normalizesCaseWhitespaceAndTrims() {
        assertEquals(
                "hard brakes by policy last week",
                AskCacheKey.normalizeQuestion("  Hard   BRAKES by policy\nlast week  ")
        );
    }

    @Test
    void sameNormalizedQuestionAndCatalogShareHash() {
        String left = AskCacheKey.hash("Hard  brakes", "2026-09-12.1", null);
        String right = AskCacheKey.hash("hard brakes", "2026-09-12.1", "  ");
        assertEquals(left, right);
        assertEquals(64, left.length());
    }

    @Test
    void catalogVersionAndLocaleChangeTheKey() {
        String base = AskCacheKey.hash("hard brakes", "2026-09-12.1", null);
        assertNotEquals(base, AskCacheKey.hash("hard brakes", "2026-09-12.2", null));
        assertNotEquals(base, AskCacheKey.hash("hard brakes", "2026-09-12.1", "es"));
        assertEquals(
                AskCacheKey.hash("hard brakes", "2026-09-12.1", "ES"),
                AskCacheKey.hash("HARD   brakes", "2026-09-12.1", "es")
        );
    }

    @Test
    void materialPinsCatalogThenLocaleThenQuestion() {
        assertEquals(
                "2026-09-12.1|en|avg premium by status",
                AskCacheKey.material("  Avg  PREMIUM by status ", "2026-09-12.1", "EN")
        );
    }
}
