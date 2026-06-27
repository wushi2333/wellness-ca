#!/usr/bin/env bash
# End-to-end smoke test for the agentic AI feature.
#
# Spins up: register a test user (idempotent), log in, seed 7 days of
# wellness data, trigger /agent/recommend, then list history.
#
# Usage:
#   bash tests/smoke_test.sh
#
# Requires:
#   - uvicorn running locally on :8000
#   - jq installed (brew install jq)
#
# Author: Cai Peilin

set -euo pipefail

BASE="http://localhost:8000"
GATEWAY="team-wellness-2025"
USER="agent_test_user"
PASS="test_password_123"

H_GATEWAY="X-API-Token: ${GATEWAY}"
H_JSON="Content-Type: application/json"

echo "=== 1. Register (ignore 400 if exists) ==="
curl -s -X POST "${BASE}/register" \
  -H "${H_GATEWAY}" -H "${H_JSON}" \
  -d "{\"username\":\"${USER}\",\"password\":\"${PASS}\"}" || true
echo

echo "=== 2. Login ==="
TOKEN=$(curl -s -X POST "${BASE}/login" \
  -H "${H_GATEWAY}" -H "${H_JSON}" \
  -d "{\"username\":\"${USER}\",\"password\":\"${PASS}\"}" | jq -r .access_token)
echo "Token acquired: ${TOKEN:0:20}..."
H_AUTH="Authorization: Bearer ${TOKEN}"

echo
echo "=== 3. Seed 7 days of wellness data ==="
# Declining sleep + spotty exercise to give the agent something to analyze.
SLEEP_HOURS=(7.5 7.0 6.5 6.0 5.5 5.0 4.5)
ACTIVITIES=("Running" "" "Walking" "" "Running" "" "Walking")
DURATIONS=(30 0 20 0 25 0 15)

for i in 0 1 2 3 4 5 6; do
  # Date: today minus (6 - i) days, so index 0 is the oldest.
  DAYS_AGO=$((6 - i))
  if [[ "$(uname)" == "Darwin" ]]; then
    RECORD_DATE=$(date -v-${DAYS_AGO}d +%Y-%m-%d)
  else
    RECORD_DATE=$(date -d "${DAYS_AGO} days ago" +%Y-%m-%d)
  fi
  RESPONSE=$(curl -s -X POST "${BASE}/records" \
    -H "${H_GATEWAY}" -H "${H_AUTH}" -H "${H_JSON}" \
    -d "{
      \"sleep_hours\": ${SLEEP_HOURS[$i]},
      \"exercise_activity\": \"${ACTIVITIES[$i]}\",
      \"exercise_duration\": ${DURATIONS[$i]},
      \"record_date\": \"${RECORD_DATE}\",
      \"notes\": \"smoke test seed\"
    }")
  echo "  Day ${RECORD_DATE} (sleep=${SLEEP_HOURS[$i]}h): ${RESPONSE}"
done

echo
echo "=== 4. Trigger agentic recommendation ==="
echo "  (this takes 5-15 seconds, multiple LLM round-trips...)"
START=$(date +%s)
RESULT=$(curl -s -X POST "${BASE}/agent/recommend" \
  -H "${H_GATEWAY}" -H "${H_AUTH}")
END=$(date +%s)
echo "  elapsed: $((END - START))s"
echo
echo "$RESULT" | jq .

echo
echo "=== 5. Fetch history ==="
curl -s "${BASE}/agent/recommend/history?limit=3" \
  -H "${H_GATEWAY}" -H "${H_AUTH}" | jq '.[] | {id, iterations, created_at, content: (.content | .[0:80] + "...")}'

echo
echo "=== Done ==="