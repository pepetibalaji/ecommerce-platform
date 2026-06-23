#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP="kafka:9092"

topics=(
  order-created:3
  inventory-reserved:3
  payment-success:3
  payment-failed:3
  order-dlq:1
  payment-dlq:1
)

for item in "${topics[@]}"; do
  topic="${item%%:*}"
  partitions="${item##*:}"

  /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "$BOOTSTRAP" \
    --create \
    --if-not-exists \
    --topic "$topic" \
    --partitions "$partitions" \
    --replication-factor 1
done

/opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --list