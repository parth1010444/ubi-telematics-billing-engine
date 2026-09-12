# Analytics Phase A — schema catalog, AST, and safe Mongo execution

Phase A is the **deterministic, no-LLM** foundation for the UBI NL analytics agent.
Natural-language `/ask` (Gemini + Redis) is Phase B — see [analytics-phase-b.md](analytics-phase-b.md).

Pipeline:

```text
AST JSON  →  AstValidator  →  MongoPipelineCompiler  →  PipelineAllowlist  →  AnalyticsExecutor
```

The compiler emits only `$match`, `$group`, `$sort`, `$limit`, `$project`, plus a minimal `$cond` inside `$group` for conditional sums. Multi-collection queries (`$lookup`) are out of scope for v1.

## Catalog

Hand-maintained in `SchemaCatalog` (`com.example.ubi.analytics.catalog`), version **`2026-09-12.1`**.

| Collection | Domain class | Notes |
| --- | --- | --- |
| `policies` | `Policy` | Id field `policyId` maps to Mongo `_id` |
| `telemetry_events` | `TelemetryEvent` | Id field `eventId` maps to Mongo `_id` |
| `billing_usage_records` | `BillingUsageRecord` | Id field `billingUsageId` maps to Mongo `_id` |

Relationships (`telemetry_events.policyId → policies`, billing FKs) are documented for a future planner. **Phase A never joins.**

NL-hidden (rejected by the validator even if present on the document):

- `stripeCustomerId` (policies and billing usage records)
- `lastCheckoutSessionId` (policies)

Inspect the live catalog:

```bash
curl http://localhost:8080/api/v1/analytics/catalog
```

## AST JSON

Jackson-friendly records under `com.example.ubi.analytics.ast`.

```json
{
  "collection": "telemetry_events",
  "filters": [
    { "field": "speedKmh", "op": "gte", "value": 100 }
  ],
  "groupBy": ["policyId"],
  "metrics": [
    { "alias": "events", "fn": "count" },
    { "alias": "totalDistance", "fn": "sum", "field": "distanceTraveledKm" },
    {
      "alias": "hardBrakes",
      "fn": "sum",
      "field": "isHardBraking",
      "filterField": "isHardBraking",
      "filterEquals": true
    }
  ],
  "having": [{ "field": "events", "op": "gt", "value": 5 }],
  "sort": [{ "field": "hardBrakes", "direction": "desc" }],
  "limit": 20,
  "chartHint": "bar"
}
```

| Slot | Allowed values |
| --- | --- |
| `filters[].op` | `eq`, `neq`, `gt`, `gte`, `lt`, `lte`, `in`, `exists` |
| `metrics[].fn` | `count`, `sum`, `avg`, `min`, `max` |
| `chartHint` | `table` (default), `bar`, `line`, `pie` |
| `sort[].direction` | `asc` (default), `desc` |

`filterField` / `filterEquals` compile to `$sum: { $cond: [ { $eq: ["$field", value] }, input, 0 ] }`.

Omit `metrics` and `groupBy` for a listing query (NL-exposed fields only, still capped by `limit`).

## Validator limits (deny-by-default)

- Collection must be in the catalog
- Fields must exist on that collection and be NL-exposed
- Identifiers cannot contain `$` or `.`
- Filter / having values cannot embed Mongo operator objects
- Max **8** filters, **3** groupBy, **5** metrics, **2** sort keys
- `limit` required or defaulted (`analytics.default-limit`, default 50), range **1–100**
- `groupBy` requires at least one metric; `having` / aggregating `sort` must reference metric aliases or groupBy fields

## Execute (internal / test)

`POST /api/v1/analytics/execute` accepts a **pre-built AST**, not natural language. This is for manual testing and later wiring. It is unauthenticated, like the rest of the current API.

```bash
curl -X POST http://localhost:8080/api/v1/analytics/execute \
  -H "Content-Type: application/json" \
  -d @src/test/resources/analytics/telemetry-hard-brakes.json
```

The executor always allowlists the compiled pipeline, forces a `$limit`, and sets `maxTimeMS` (`analytics.max-time-ms`, default 5000).

## Mongo connection

By default analytics uses the existing `spring.data.mongodb.uri`.

Optional read-only override:

```yaml
analytics:
  mongodb:
    uri: ${ANALYTICS_MONGODB_URI:}
```

If `ANALYTICS_MONGODB_URI` is empty, the primary URI is used.

## Out of scope (later phases)

- Phase B (landed separately): [analytics-phase-b.md](analytics-phase-b.md) — Gemini planner, `/ask`, Redis cache
- Eval / golden NL suite beyond the unit fixtures in `src/test/resources/analytics/`
- Dashboard charts
- `$lookup` / multi-collection queries

## Tests

```bash
mvn test
```

Unit coverage: catalog, JSON AST round-trip, validator accept/reject (including write-like encodings), compiler stage assertions on three fixtures, allowlist rejection of handcrafted `$lookup` / `$out` / `$merge` / `$where` / `$function` pipelines, executor with a recording Mongo runner (no Testcontainers).
