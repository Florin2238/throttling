#!/usr/bin/env bash
# Demo: 2 clients x 2 endpoints.
# Start the app first (store type memory or postgres), then run ./demo.sh
#
# Always start from a clean state: restart the app (memory) or truncate the table (postgres),
# otherwise earlier requests are still counted.

BASE_URL="${BASE_URL:-http://localhost:8080}"

call() {   # call <endpoint> <client>  -> prints only the HTTP status code
  curl -s -o /dev/null -w "%{http_code}" "$BASE_URL$1" -H "Authorization: Bearer $2"
}

run() {    # run <client> <endpoint> <number of requests>
  local client=$1 endpoint=$2 n=$3 codes=""
  for ((i = 1; i <= n; i++)); do
    codes+="$(call "$endpoint" "$client") "
  done
  printf "%-9s %-5s -> %s\n" "$client" "$endpoint" "$codes"
}

echo "Base URL: $BASE_URL"
echo "Limits (application.yaml): client-1 = 5, client-2 = 3 requests / 10 seconds"
echo "/foo = Token Bucket, /bar = Sliding Window"
echo

# send limit + 2 requests so that the 429 responses show up too
run client-1 /foo 7
run client-1 /bar 7
run client-2 /foo 5
run client-2 /bar 5