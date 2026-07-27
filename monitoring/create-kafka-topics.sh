#!/bin/sh

BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVER:-kafka:9092}"

echo "Waiting for Kafka broker (${BOOTSTRAP}) to become available..."

until /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server "${BOOTSTRAP}" >/dev/null 2>&1; do
  echo "Kafka is not ready yet. Retrying in 2 seconds..."
  sleep 2
done

echo "Kafka broker is online. Creating topics..."

topics="
order-created:3
inventory-reserved:3
inventory-released:3
payment-success:3
payment-failed:3
order-completed:3
order-cancelled:3
shipment-created:3
notification-requested:3
order-dlq:1
inventory-dlq:1
payment-dlq:1
notification-dlq:1
"

printf "%s\n" "$topics" | while IFS=: read -r topic partitions; do
  [ -n "$topic" ] || continue

  echo "Ensuring topic exists: ${topic} with partitions=${partitions}"

  /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "${BOOTSTRAP}" \
    --create \
    --if-not-exists \
    --topic "${topic}" \
    --partitions "${partitions}" \
    --replication-factor 1
done

echo "Current Kafka topics:"
/opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server "${BOOTSTRAP}" \
  --list

echo "Kafka topic initialization complete."
