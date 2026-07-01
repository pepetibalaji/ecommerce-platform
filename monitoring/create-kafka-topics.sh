#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP="kafka:9092"

# OPTIMIZATION: Wait for Kafka broker to be fully ready before creating topics
echo "Waiting for Kafka broker ($BOOTSTRAP) to become available..."
until /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server "$BOOTSTRAP" &>/dev/null; do
  echo "Kafka is not ready yet. Retrying in 2 seconds..."
  sleep 2
done
echo "Kafka broker is online! Proceeding with topic creation..."

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

echo "Current Kafka Topics:"
/opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --list
