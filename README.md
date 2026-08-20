# UBI Telematics Billing Engine

A Spring Boot backend for a Usage-Based Insurance (UBI) telematics billing workflow.

The service accepts simulated vehicle telemetry, stores the driving event in MongoDB, calculates a simple usage-based risk charge, and reports billable usage to Stripe Billing through Stripe Meter Events.

## What This Project Does

This is the backend engine for pay-as-you-drive insurance.

At a high level:

1. Create an insurance policy for a user.
2. Send vehicle telemetry for that policy.
3. Store the telemetry event in MongoDB.
4. Calculate a usage/risk charge.
5. Save a billing audit record.
6. Report usage to Stripe metered billing.
7. Retry failed Stripe reports automatically.

There is no frontend UI yet. The application is used through REST APIs.

## Technology Stack

- Java 21
- Spring Boot 3.x
- Spring Web
- Spring Data MongoDB
- Jakarta Validation
- Stripe Java SDK
- MongoDB

## Current Features

- Policy creation and lookup
- Policy status updates
- Telemetry ingestion
- Idempotent telemetry processing using `eventId`
- Basic UBI risk calculation
- Billing audit/outbox records
- Stripe Meter Event reporting
- Scheduled retry for failed Stripe billing reports
- Basic API validation and error responses

## Architecture

The code follows a clean layered structure:

```text
com.example.ubi
├── api             REST controllers
├── application     Business services
├── config          Application configuration
├── domain
│   ├── model       MongoDB domain entities
│   └── repository  Spring Data repositories
├── dto             API request/response DTOs
└── exception       API error handling
```

## Domain Model

### Policy

Represents an insurance policy.

Important fields:

- `policyId`
- `userId`
- `stripeCustomerId`
- `basePremium`
- `status`

Valid statuses:

- `ACTIVE`
- `SUSPENDED`
- `CANCELLED`

Only `ACTIVE` policies can receive billable telemetry.

### TelemetryEvent

Represents one driving event.

Important fields:

- `eventId`
- `policyId`
- `timestamp`
- `speedKmh`
- `isHardBraking`
- `distanceTraveledKm`

`eventId` is used for idempotency. If the same event is sent twice, the second request is accepted but ignored so the driver is not double billed.

### BillingUsageRecord

Represents the billing audit/outbox record created from a telemetry event.

Important fields:

- `telemetryEventId`
- `policyId`
- `stripeCustomerId`
- `usageCharge`
- `billableUsageUnits`
- `status`
- `stripeMeterEventIdentifier`
- `attemptCount`
- `lastError`
- `nextRetryAt`

Statuses:

- `PENDING`
- `REPORTED`
- `FAILED`

## Risk Calculation

The current risk logic is intentionally simple:

```text
distanceCost = distanceTraveledKm * 0.5
hardBrakingPenalty = 5 units if hard braking happened
usageCharge = distanceCost + hardBrakingPenalty
```

Example:

```text
distanceTraveledKm = 12.4
isHardBraking = true

distanceCost = 12.4 * 0.5 = 6.2
hardBrakingPenalty = 5
usageCharge = 11.2
billableUsageUnits = 12
```

Stripe meter values are reported as whole-number usage units, so the decimal charge is rounded up before being sent to Stripe.

## Stripe Billing Flow

Stripe receives usage through:

```java
com.stripe.model.billing.MeterEvent
```

The service sends:

- `event_name`: configured by `STRIPE_TELEMATICS_METER_EVENT_NAME`
- `payload[stripe_customer_id]`: the policy's Stripe customer ID
- `payload[value]`: the calculated usage units
- `identifier`: deterministic value based on the telemetry event ID

The deterministic identifier helps prevent duplicate Stripe meter entries during retries.

## Prerequisites

Install:

- Java 21
- Maven
- MongoDB

Check your versions:

```bash
java -version
mvn -version
mongod --version
```

If Java 21 is installed through Homebrew, activate it:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

## MongoDB Setup

Start MongoDB with Homebrew:

```bash
brew services start mongodb-community
```

Check status:

```bash
brew services list | grep mongodb
```

If you see an error about feature compatibility version, your local MongoDB data directory may be from an older MongoDB version. For a fresh local development database:

```bash
brew services stop mongodb-community
mv /opt/homebrew/var/mongodb /opt/homebrew/var/mongodb-backup-$(date +%Y%m%d-%H%M%S)
mkdir -p /opt/homebrew/var/mongodb
brew services start mongodb-community
```

## Environment Variables

Set these before running the app:

```bash
export STRIPE_SECRET_KEY=sk_test_your_key_here
export STRIPE_TELEMATICS_METER_EVENT_NAME=ubi_telematics_usage
export MONGODB_URI=mongodb://localhost:27017/ubi_billing
```

For local startup without real Stripe billing, `STRIPE_SECRET_KEY` can be any non-empty placeholder:

```bash
export STRIPE_SECRET_KEY=sk_test_placeholder
```

Telemetry ingestion will still work, but Stripe reporting will fail and the billing outbox record will be marked for retry.

## Run The Application

From the project directory:

```bash
cd "/Users/tnluser/Documents/New project/ubi-telematics-billing-engine"
mvn spring-boot:run
```

The API starts at:

```text
http://localhost:8080
```

Opening that URL directly in a browser may show `404`, because this is an API backend and no frontend page has been built yet.

## API Usage

### Create A Policy

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
  "userId": "user_123",
  "stripeCustomerId": "cus_test_customer",
  "basePremium": 100.00,
  "status": "ACTIVE"
}
```

### Get A Policy

```bash
curl http://localhost:8080/api/v1/policies/PASTE_POLICY_ID_HERE
```

### Update Policy Status

```bash
curl -X PATCH http://localhost:8080/api/v1/policies/PASTE_POLICY_ID_HERE/status \
  -H "Content-Type: application/json" \
  -d '{
    "status": "SUSPENDED"
  }'
```

### Ingest Telemetry

```bash
curl -X POST http://localhost:8080/api/v1/telemetry/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt_trip_001",
    "policyId": "PASTE_POLICY_ID_HERE",
    "timestamp": "2026-08-20T10:00:00Z",
    "speedKmh": 72,
    "isHardBraking": true,
    "distanceTraveledKm": 12.4
  }'
```

Expected response:

```text
HTTP 202 Accepted
```

Sending the same `eventId` again will not create another charge.

## Configuration

Configuration lives in:

```text
src/main/resources/application.yml
```

Current settings:

```yaml
spring:
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://localhost:27017/ubi_billing}
      auto-index-creation: true

stripe:
  secret-key: ${STRIPE_SECRET_KEY:}
  telematics-meter-event-name: ${STRIPE_TELEMATICS_METER_EVENT_NAME:ubi_telematics_usage}

billing:
  retry:
    fixed-delay-ms: ${BILLING_RETRY_FIXED_DELAY_MS:60000}
```

## Current Limitations

- No frontend UI
- No authentication or authorization
- No unit/integration tests yet
- Risk scoring is intentionally simple
- Stripe must be configured separately in the Stripe dashboard
- No production deployment files yet

## Suggested Next Phase

Good next targets:

- Add unit and integration tests
- Add authentication for telemetry ingestion
- Add richer driving risk factors, such as speeding, night driving, and harsh acceleration
- Add billing reconciliation APIs
- Add an admin dashboard or simple frontend
- Add Docker Compose for local MongoDB and app startup
