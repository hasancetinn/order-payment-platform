#!/usr/bin/env bash
# End-to-end demo of the saga through the API gateway. Requires: curl, jq, and the stack running
# (docker compose --profile apps up -d --build).
set -euo pipefail

GW="${GATEWAY_URL:-http://localhost:18080}"
EMAIL="demo-$(date +%s)@example.com"
PASSWORD="demo-password-1"

step() { printf '\n\033[1;34m== %s\033[0m\n' "$*"; }

wait_for_status() { # order id -> prints final order JSON
  local id="$1" order status
  for _ in $(seq 1 30); do
    order=$(curl -sf "$GW/api/orders/$id" -H "Authorization: Bearer $TOKEN")
    status=$(jq -r .status <<<"$order")
    if [ "$status" != "PENDING" ]; then echo "$order" | jq '{id, status, totalAmount, cancellationReason}'; return; fi
    sleep 1
  done
  echo "order $id still PENDING after 30s" >&2; return 1
}

place_order() { # description, JSON body
  step "$1"
  local response id
  response=$(curl -sf -X POST "$GW/api/orders" -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' -H "Idempotency-Key: $(uuidgen)" -d "$2")
  id=$(jq -r .id <<<"$response")
  echo "accepted: $id (status $(jq -r .status <<<"$response"))"
  wait_for_status "$id"
}

step "Register and log in as $EMAIL"
curl -sf -X POST "$GW/api/auth/register" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | jq .
TOKEN=$(curl -sf -X POST "$GW/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | jq -r .accessToken)

step "Catalogue"
curl -sf "$GW/api/products" -H "Authorization: Bearer $TOKEN" | jq -c '.[]'

place_order "1) Happy path: 1 laptop + 2 headphones -> CONFIRMED" \
  '{"currency":"EUR","items":[{"sku":"SKU-LAPTOP-001","quantity":1,"unitPrice":1499.00},{"sku":"SKU-HEADPHONE-001","quantity":2,"unitPrice":199.00}]}'

place_order "2) Not enough stock: 3 limited watches (only 2 exist) -> CANCELLED by inventory" \
  '{"currency":"EUR","items":[{"sku":"SKU-WATCH-LTD","quantity":3,"unitPrice":999.00}]}'

place_order "3) Payment declined: 8 laptops = 11 992 EUR (> 10 000 limit) -> stock released, CANCELLED" \
  '{"currency":"EUR","items":[{"sku":"SKU-LAPTOP-001","quantity":8,"unitPrice":1499.00}]}'

step "Stock afterwards (each run sells 1 laptop; the 8 reserved by the declined order are released again)"
curl -sf "$GW/api/products" -H "Authorization: Bearer $TOKEN" | jq -c '.[] | select(.sku=="SKU-LAPTOP-001" or .sku=="SKU-WATCH-LTD")'

step "Idempotency: the same Idempotency-Key twice returns the same order"
KEY=$(uuidgen)
BODY='{"currency":"EUR","items":[{"sku":"SKU-KEYBOARD-001","quantity":1,"unitPrice":129.00}]}'
A=$(curl -sf -X POST "$GW/api/orders" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -H "Idempotency-Key: $KEY" -d "$BODY" | jq -r .id)
B=$(curl -sf -X POST "$GW/api/orders" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -H "Idempotency-Key: $KEY" -d "$BODY" | jq -r .id)
echo "first=$A second=$B"; [ "$A" = "$B" ] && echo "same order, no duplicate" || { echo "MISMATCH" >&2; exit 1; }

step "Notifications received (event-driven, from notification-service)"
sleep 2
curl -sf "$GW/api/notifications" -H "Authorization: Bearer $TOKEN" | jq -c '.[] | {subject, body}'

step "Without a token the gateway answers 401"
curl -s -o /dev/null -w 'HTTP %{http_code}\n' "$GW/api/orders"
