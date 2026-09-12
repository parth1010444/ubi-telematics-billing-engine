# UBI Telematics Billing Engine

This is dev branch, all new phases changes will be done here

A Spring Boot backend for a Usage-Based Insurance (UBI) telematics billing workflow.

The service accepts simulated vehicle telemetry, stores driving events in MongoDB, calculates a usage-based risk charge, reports billable usage to Stripe Meter Events, and lets the customer pay the accrued unpaid premium through Stripe Checkout.

## What This Project Does

1. Bootstrap a Stripe Customer + Billing Meter (`scripts/setup-stripe.sh`).
2. Create an insurance policy linked to that Stripe customer.
3. Ingest / simulate vehicle telemetry for the policy.
4. Store the telemetry event in MongoDB.
5. Calculate a usage/risk charge and accrue it onto the monthly premium.
6. Persist a billing audit/outbox record and report usage to Stripe meters.
7. Retry failed Stripe meter reports automatically.
8. Trigger monthly billing → create a Stripe Checkout Session for the unpaid balance.
9. After the customer pays on Stripe-hosted Checkout, confirm the session so paid amounts are recorded on the policy.

CORS is enabled for the companion dashboard on localhost, private LAN addresses, and common HTTPS tunnels (ngrok / Cloudflare). After Checkout, Stripe returns to the dashboard origin that triggered billing — not a hardcoded localhost URL.

## Technology Stack

- Java 21
- Spring Boot 3.x
- Spring Web / Spring Data MongoDB / Validation
- Stripe Java SDK (`stripe-java` 33.x) — Meter Events + Checkout Sessions
- MongoDB

## Current Features

- Policy create / list / get / delete / status update
- Human-readable policy names (`Policy_1`, `Policy_2`, …)
- Accrued / paid / unpaid premium tracking on each policy
- Telemetry ingestion with idempotent `eventId` handling
- Telemetry history with per-event surcharge + risk level
- Basic UBI risk calculation
- Billing audit/outbox records + scheduled Stripe meter retries
- Monthly billing via Stripe Checkout (unpaid accrued premium)
- Checkout confirmation to record successful payments
- Checkout return URLs follow the live dashboard origin (localhost, LAN, or HTTPS tunnel)
- CORS for local Vite dashboard, LAN, and common tunnels
- Local Stripe secrets via gitignored `application-local.yml`
- Stripe bootstrap script (`scripts/setup-stripe.sh`)

## Project Layout

```text
com.example.ubi
├── api
│   ├── PolicyController
│   ├── TelemetryController
│   └── BillingController
├── application
│   ├── PolicyService
│   ├── TelemetryIngestionService
│   ├── RiskCalculationService
│   ├── BillingOutboxService
│   ├── BillingOutboxRetryScheduler
│   ├── BillingCycleService
│   └── StripeBillingService
├── config
│   ├── StripeProperties
│   ├── BillingProperties
│   └── CorsConfig
├── domain
│   ├── model       Policy, TelemetryEvent, BillingUsageRecord, …
│   └── repository  Spring Data repositories
├── dto             API request/response DTOs
└── exception       API error handling (+ BillingPrerequisiteException)

scripts/
└── setup-stripe.sh

src/main/resources/
├── application.yml
└── application-local.yml.example
```

## Domain Model

### Policy

| Field | Purpose |
| --- | --- |
| `policyId` | MongoDB id |
| `policyNumber` / `displayName()` | Human name like `Policy_1` |
| `userId` | External user id |
| `stripeCustomerId` | Stripe Customer (`cus_…`) |
| `basePremium` | Base monthly premium (dollars) |
| `status` | `ACTIVE` / `SUSPENDED` / `CANCELLED` |
| `paidAmountCents` | Cumulative amount paid via Checkout |
| `lastCheckoutSessionId` / `lastPaidAt` | Last successful Checkout |

Only **ACTIVE** policies can receive billable telemetry or trigger monthly billing.

**Accrual:**

```text
accruedPremium = basePremium + sum(usageCharge per telemetry event)
unpaidAmountCents = max(0, accruedCents - paidAmountCents)
```

### TelemetryEvent

- `eventId` (idempotency key)
- `policyId`, `timestamp`
- `speedKmh`, `isHardBraking`, `distanceTraveledKm`

Duplicate `eventId` values are accepted but ignored so the driver is not double billed.

### BillingUsageRecord

Outbox / audit row created from each telemetry event:

- `billingUsageId`, `telemetryEventId`, `policyId`, `stripeCustomerId`
- `usageCharge`, `billableUsageUnits`
- `status`: `PENDING` | `REPORTED` | `FAILED` | `ABANDONED`
- `stripeMeterEventIdentifier`, `attemptCount`, `lastError`, `nextRetryAt`

## Risk Calculation

```text
speedMultiplier = 1.50 if speedKmh >= 100
                = 1.20 if speedKmh >= 50
                = 1.00 otherwise
distanceCost    = (distanceTraveledKm * 0.5) * speedMultiplier
hardBrakingPenalty = 5  (if isHardBraking)
usageCharge     = distanceCost + hardBrakingPenalty
billableUsageUnits = ceil(usageCharge)
```

Example: `12.4 km` at `110 km/h` with a hard brake → `(12.4 * 0.5) * 1.20 + 5 = 12.44` → **13** meter units.

## Stripe Billing Flow

### Meter Events (per telemetry)

Usage is reported with Stripe Billing Meter Events:

- `event_name`: `stripe.telematics-meter-event-name` (default `ubi_telematics_usage`)
- `payload[stripe_customer_id]`: policy Stripe customer
- `payload[value]`: billable usage units
- `identifier`: deterministic id derived from the telemetry event (safe for retries)

### Monthly Checkout (unpaid accrued premium)

`POST /api/v1/billing/trigger-cycle/{policyId}`:

1. Computes accrued vs paid balance.
2. `already_paid` — unpaid is 0 and something was already paid (unless `?force=true`).
3. `zero_balance` — nothing to charge.
4. Otherwise creates a Checkout Session (`mode=payment`) and returns `checkout_pending` with the hosted Checkout URL in `hostedInvoiceUrl`.

Send the dashboard origin in the JSON body so Stripe returns there after payment:

```json
{ "returnOrigin": "https://YOUR-TUNNEL.ngrok-free.app" }
```

If `returnOrigin` is omitted, the API uses the request `Origin` header, then falls back to `billing.checkout-success-url` (`http://localhost:5173`). Allowed origins: localhost, private LAN IPs, HTTPS tunnels (ngrok / Cloudflare / localtunnel), hosted frontends (`*.vercel.app` / `*.netlify.app` / `*.onrender.com`), plus any extras in `BILLING_ALLOWED_RETURN_ORIGINS`.

After payment, call `POST /api/v1/billing/confirm-checkout` with `{ "policyId", "sessionId" }`. The API verifies the session in Stripe and increments `paidAmountCents` (idempotent per session).

## Prerequisites

- Java 21+
- Maven
- MongoDB

```bash
java -version
mvn -version
mongod --version
```

Homebrew Java 21:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

## MongoDB Setup

```bash
brew services start mongodb-community
brew services list | grep mongodb
```

If feature-compatibility errors appear (old data dir):

```bash
brew services stop mongodb-community
mv /opt/homebrew/var/mongodb /opt/homebrew/var/mongodb-backup-$(date +%Y%m%d-%H%M%S)
mkdir -p /opt/homebrew/var/mongodb
brew services start mongodb-community
```

## Configuration

### Local profile (recommended)

```bash
cp src/main/resources/application-local.yml.example \
   src/main/resources/application-local.yml
```

Edit `application-local.yml` with real Stripe keys, then:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

`application-local.yml` is gitignored.

### Bootstrap Stripe Customer + Meter

```bash
./scripts/setup-stripe.sh
```

Creates a Stripe Customer and Billing Meter (`ubi_telematics_usage`), then prints `cus_…` for the dashboard env (`VITE_STRIPE_CUSTOMER_ID`).

### Environment variables

| Variable | Purpose |
| --- | --- |
| `STRIPE_SECRET_KEY` | Stripe secret key (`sk_test_…`) |
| `STRIPE_PUBLISHABLE_KEY` | Stripe publishable key (`pk_test_…`) |
| `STRIPE_TELEMATICS_METER_EVENT_NAME` | Meter event name (default `ubi_telematics_usage`) |
| `MONGODB_URI` | Mongo connection (default `mongodb://localhost:27017/ubi_billing`) |
| `BILLING_RETRY_FIXED_DELAY_MS` | Outbox retry interval (default `60000`) |
| `BILLING_CHECKOUT_SUCCESS_URL` | Fallback Checkout success redirect if the dashboard does not send `returnOrigin` |
| `BILLING_CHECKOUT_CANCEL_URL` | Fallback Checkout cancel redirect |
| `BILLING_ALLOWED_RETURN_ORIGINS` | Extra Checkout return origins, comma-separated (e.g. `https://ubi-telematics-dashboard.vercel.app`) |

Placeholder secret keys allow the app to start; meter reporting and Checkout will fail until a real key is set.

### `application.yml`

```yaml
server:
  port: ${PORT:8080}

spring:
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://localhost:27017/ubi_billing}
      database: ${MONGODB_DATABASE:ubi_billing}
      auto-index-creation: true

stripe:
  secret-key: ${STRIPE_SECRET_KEY:}
  telematics-meter-event-name: ${STRIPE_TELEMATICS_METER_EVENT_NAME:ubi_telematics_usage}
  publishable-key: ${STRIPE_PUBLISHABLE_KEY:}

billing:
  retry:
    fixed-delay-ms: ${BILLING_RETRY_FIXED_DELAY_MS:60000}
  checkout-success-url: ${BILLING_CHECKOUT_SUCCESS_URL:http://localhost:5173/?billing=success}
  checkout-cancel-url: ${BILLING_CHECKOUT_CANCEL_URL:http://localhost:5173/?billing=cancelled}
  allowed-return-origins: ${BILLING_ALLOWED_RETURN_ORIGINS:}
```
## Run

```bash
cd ~/Desktop/ubi-telematics-billing-engine
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

API base: `http://localhost:8080`

## API Usage

### List policies

```bash
curl http://localhost:8080/api/v1/policies
```

### Create policy

```bash
curl -X POST http://localhost:8080/api/v1/policies \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user_123",
    "stripeCustomerId": "cus_test_customer",
    "basePremium": 100.00,
    "status": "ACTIVE"
  }'
```

Example response:

```json
{
  "policyId": "66c123abc456",
  "name": "Policy_1",
  "policyNumber": 1,
  "userId": "user_123",
  "stripeCustomerId": "cus_test_customer",
  "basePremium": 100.00,
  "status": "ACTIVE",
  "accruedPremium": 100.00,
  "paidAmountCents": 0,
  "unpaidAmountCents": 10000,
  "premiumPaymentStatus": "NOT_PAID_YET",
  "lastPaidAt": null
}
```

### Get / update status / delete

```bash
curl http://localhost:8080/api/v1/policies/PASTE_POLICY_ID

curl -X PATCH http://localhost:8080/api/v1/policies/PASTE_POLICY_ID/status \
  -H "Content-Type: application/json" \
  -d '{"status":"SUSPENDED"}'

curl -X DELETE http://localhost:8080/api/v1/policies/PASTE_POLICY_ID
```

Delete also removes that policy’s telemetry events and billing outbox rows.

### Ingest telemetry

```bash
curl -X POST http://localhost:8080/api/v1/telemetry/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt_trip_001",
    "policyId": "PASTE_POLICY_ID",
    "timestamp": "2026-08-20T10:00:00Z",
    "speedKmh": 72,
    "isHardBraking": true,
    "distanceTraveledKm": 12.4
  }'
```

Expected: `HTTP 202 Accepted`. Replaying the same `eventId` does not double-charge.

### Telemetry history

```bash
curl http://localhost:8080/api/v1/telemetry/history/PASTE_POLICY_ID
```

Returns newest-first items including `surchargeAdded` and `riskLevel`.

### Trigger monthly billing (Checkout)

```bash
curl -X POST "http://localhost:8080/api/v1/billing/trigger-cycle/PASTE_POLICY_ID" \
  -H "Content-Type: application/json" \
  -d '{"returnOrigin":"http://localhost:5173"}'
```

Optional: `?force=true` to create a new Checkout Session even when unpaid is already covered.

Example response:

```json
{
  "invoiceId": "cs_test_...",
  "status": "checkout_pending",
  "hostedInvoiceUrl": "https://checkout.stripe.com/c/pay/cs_test_...",
  "invoicePdf": null,
  "amountCents": 11120,
  "accruedAmountCents": 11120,
  "paidAmountCents": 0,
  "unpaidAmountCents": 11120
}
```

Open `hostedInvoiceUrl` and pay with test card `4242 4242 4242 4242`.

### Confirm Checkout

```bash
curl -X POST http://localhost:8080/api/v1/billing/confirm-checkout \
  -H "Content-Type: application/json" \
  -d '{
    "policyId": "PASTE_POLICY_ID",
    "sessionId": "cs_test_PASTE_SESSION_ID"
  }'
```

Returns the updated policy with incremented `paidAmountCents` and `premiumPaymentStatus`.

## Current Limitations

- No authentication / authorization
- No automated tests yet
- Risk scoring is intentionally simple
- Stripe Customer / Meter must be bootstrapped once (`scripts/setup-stripe.sh`)
- Monthly collection is Checkout-based (customer pays), not server-side auto-charge
- No production deployment packaging yet

## Suggested Next Phase

- Unit / integration tests
- Auth for telemetry ingestion
- Richer risk factors (speeding, night driving, harsh acceleration)
- Stripe webhooks in addition to client-side checkout confirm
- Billing reconciliation APIs
- Docker Compose for MongoDB + app
