# Analytics Phase B — Gemini planner, `/ask`, and Redis cache

Phase B adds natural-language analytics on top of the Phase A AST pipeline.
Phase A `GET /catalog` and `POST /execute` are unchanged.

Pipeline:

```text
question
  → Redis cache? (normalized question + catalogVersion [+ locale])
  → QueryPlanner (Gemini structured AnalyticsAst)
  → AstValidator
  → (max 1 repair: feed validator errors back to Gemini)
  → MongoPipelineCompiler → PipelineAllowlist → AnalyticsExecutor
  → ResultSummarizer (1–2 sentences)
  → cache store
```

`POST /api/v1/analytics/ask` is **fully open in v1** (no API key on the HTTP call).
Gemini still needs a **server-side** Google AI Studio key.

v1 still cannot `$lookup` or query multiple collections. Questions that need a join,
or a full accrued / unpaid premium across collections, return HTTP **422**
`needs_clarification` with suggestions.

## Gemini free-tier key (required for `/ask`)

1. Open [Google AI Studio](https://aistudio.google.com/apikey) and create an API key.
   A Google account is enough for the free Gemini Developer API — no Vertex project.
2. Export the key. **Never commit it.**

```bash
export GEMINI_API_KEY="your-ai-studio-key"
```

Spring AI also accepts the standard property / env name:

```bash
export SPRING_AI_GOOGLE_GENAI_API_KEY="your-ai-studio-key"
# or in application-local.yml (gitignored):
# spring.ai.google.genai.api-key: ${GEMINI_API_KEY}
```

When `GEMINI_API_KEY` (or `SPRING_AI_GOOGLE_GENAI_API_KEY`) is set, the app enables
`spring.ai.model.chat=google-genai` automatically. Override with
`SPRING_AI_MODEL_CHAT=none` if you want `/execute` only.

Do **not** set `spring.ai.google.genai.project-id` or `location` for the free tier —
those switch the client to Vertex AI and the AI Studio key will be rejected.

Default model: `gemini-2.5-flash` (`GEMINI_MODEL` to override).

Without a key, the process still boots. `POST /execute` works; `POST /ask` returns
**503** explaining how to set the key.

### Local example

```bash
cp src/main/resources/application-local.yml.example \
   src/main/resources/application-local.yml
# application-local.yml is gitignored — put no real secrets in git.

export GEMINI_API_KEY="your-ai-studio-key"
# optional Redis (see below)
export REDIS_HOST=localhost
export REDIS_PORT=6379

mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Commented Gemini / Redis blocks live in `application-local.yml.example`.

## Redis cache (optional)

Successful `/ask` answers are cached under:

```text
analytics:ask:{sha256(catalogVersion + "|" + locale + "|" + normalizedQuestion)}
```

Normalization: trim, lowercase, collapse whitespace. Locale is omitted when blank
so `en` and a missing locale do **not** share a key, but `"EN"` and `"en"` do.

| Variable | Purpose | Default |
| --- | --- | --- |
| `REDIS_HOST` / `REDIS_PORT` | Redis endpoint | `localhost` / `6379` |
| `ANALYTICS_CACHE_ENABLED` | Disable cache entirely | `true` |
| `ANALYTICS_CACHE_TTL_SECONDS` | Entry TTL | `3600` |
| `ANALYTICS_CACHE_KEY_PREFIX` | Key prefix | `analytics:ask:` |

If Redis is down or unreachable, `/ask` **degrades**: cache get/put failures are
logged and ignored. The planner still runs.

`skipCache: true` on the request skips the read and still writes a fresh entry.

## `POST /api/v1/analytics/ask`

Unauthenticated. Body:

```json
{ "question": "average base premium by policy status", "skipCache": false, "locale": "en" }
```

`skipCache` and `locale` are optional.

### 200 OK

```json
{
  "status": "ok",
  "summary": "Active policies average $120 base premium.",
  "chartHint": "bar",
  "columns": ["status", "avgPremium"],
  "rows": [{ "status": "ACTIVE", "avgPremium": 120 }],
  "ast": { "collection": "policies", "metrics": [], "chartHint": "bar" },
  "meta": {
    "cached": false,
    "latencyMs": 842,
    "llmRepairUsed": false,
    "collection": "policies",
    "catalogVersion": "2026-09-12.1",
    "rowCount": 1
  }
}
```

### 422 Unprocessable Entity — needs clarification

```json
{
  "status": "needs_clarification",
  "message": "Accrued unpaid premium spans policies and billing rows.",
  "suggestions": [
    "Sum usageCharge from billing_usage_records by policyId",
    "List policies.basePremium and policies.paidAmountCents"
  ]
}
```

Also used after one failed AST repair (clear failure + suggestions).

### curl

```bash
curl -sS -X POST http://localhost:8080/api/v1/analytics/ask \
  -H "Content-Type: application/json" \
  -d '{"question":"hard brakes by policy this year","locale":"en"}'
```

Skip cache:

```bash
curl -sS -X POST http://localhost:8080/api/v1/analytics/ask \
  -H "Content-Type: application/json" \
  -d '{"question":"average base premium by status","skipCache":true}'
```

Phase A execute (no LLM) still works:

```bash
curl -sS -X POST http://localhost:8080/api/v1/analytics/execute \
  -H "Content-Type: application/json" \
  -d @src/test/resources/analytics/telemetry-hard-brakes.json
```

## Repair loop

1. Gemini returns an `AnalyticsAst` (or `needs_clarification`).
2. `AstValidator` runs.
3. On failure, Gemini is called **once** more with the validator error text.
4. A second failure becomes 422 with a short message and collection-scoped suggestions.
   There is no second retry.

## Tests

```bash
mvn test
```

Unit tests mock `ChatClient` / `AnalyticsLlmClient`. They do **not** need a live
Gemini key or a running Redis.

## Config reference

| Variable | Purpose |
| --- | --- |
| `GEMINI_API_KEY` | Google AI Studio key (preferred) |
| `SPRING_AI_GOOGLE_GENAI_API_KEY` | Spring AI standard alias |
| `SPRING_AI_MODEL_CHAT` | `google-genai` or `none` |
| `GEMINI_MODEL` | Chat model (default `gemini-2.5-flash`) |
| `ANALYTICS_SUMMARY_MAX_ROWS` | Row cap sent to the summarizer (default 8) |
| `ANALYTICS_SUMMARY_MAX_VALUE_CHARS` | Per-value truncation (default 80) |
