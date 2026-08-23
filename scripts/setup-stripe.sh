#!/usr/bin/env bash
# One-time Stripe bootstrap: create Customer + Billing Meter for UBI telematics.
# Usage:
#   1. Put your real sk_test_... in src/main/resources/application-local.yml
#   2. ./scripts/setup-stripe.sh
#   3. Copy the printed cus_... into the dashboard .env as VITE_STRIPE_CUSTOMER_ID

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOCAL_YML="$ROOT/src/main/resources/application-local.yml"

if [[ -n "${STRIPE_SECRET_KEY:-}" ]]; then
  SECRET_KEY="$STRIPE_SECRET_KEY"
elif [[ -f "$LOCAL_YML" ]]; then
  SECRET_KEY="$(
    python3 - <<PY
from pathlib import Path
for line in Path(r"$LOCAL_YML").read_text().splitlines():
    if line.strip().startswith("secret-key:"):
        print(line.split(":", 1)[1].strip().strip('"').strip("'"))
        break
PY
  )"
else
  echo "No Stripe secret key found. Set STRIPE_SECRET_KEY or edit application-local.yml." >&2
  exit 1
fi

if [[ -z "$SECRET_KEY" || "$SECRET_KEY" == *replace* || "$SECRET_KEY" == *placeholder* || ${#SECRET_KEY} -lt 20 ]]; then
  echo "Replace sk_test_replace_me in application-local.yml with your real Stripe test secret key first." >&2
  echo "Get it from: https://dashboard.stripe.com/test/apikeys" >&2
  exit 1
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "Creating Stripe Customer..."
curl -sS https://api.stripe.com/v1/customers \
  -u "${SECRET_KEY}:" \
  -d name="UBI demo customer" \
  -d description="Demo customer for UBI telematics dashboard" \
  >"$TMP_DIR/customer.json"

CUSTOMER_ID="$(
  python3 - <<'PY' "$TMP_DIR/customer.json"
import json, sys
data = json.load(open(sys.argv[1]))
if data.get("error"):
    raise SystemExit(data["error"].get("message", "customer create failed"))
print(data["id"])
PY
)"
echo "Customer ID: $CUSTOMER_ID"

echo "Creating Billing Meter (ubi_telematics_usage)..."
HTTP_CODE="$(
  curl -sS -o "$TMP_DIR/meter.json" -w "%{http_code}" https://api.stripe.com/v1/billing/meters \
    -u "${SECRET_KEY}:" \
    -d display_name="UBI Telematics Usage" \
    -d event_name="ubi_telematics_usage" \
    -d "default_aggregation[formula]"=sum \
    -d "customer_mapping[type]"=by_id \
    -d "customer_mapping[event_payload_key]"=stripe_customer_id \
    -d "value_settings[event_payload_key]"=value
)"

python3 - <<'PY' "$TMP_DIR/meter.json" "$HTTP_CODE"
import json, sys
data = json.load(open(sys.argv[1]))
code = sys.argv[2]
if data.get("error"):
    msg = data["error"].get("message", "meter create failed")
    # Idempotent-ish: meter with same event_name may already exist.
    print(f"Meter note ({code}): {msg}", file=sys.stderr)
    sys.exit(0)
print(f"Meter ID: {data['id']}")
PY

echo
echo "Next steps:"
echo "  1. In ubi-telematics-dashboard/.env add:"
echo "       VITE_STRIPE_CUSTOMER_ID=$CUSTOMER_ID"
echo "  2. Restart Vite (npm run dev)."
echo "  3. Restart API with: mvn spring-boot:run -Dspring-boot.run.profiles=local"
echo "  4. Click Create demo policy, then simulate a drive."
