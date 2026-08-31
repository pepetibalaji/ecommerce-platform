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
payment-success:3
payment-failed:3
payment-refund-completed:3
order-cancelled:3
order-shipped:3
order-delivered:3
low-inventory:3
seller-order-paid:3
user-contact-updated:3
notification-dlq:3
product-created:3
product-created-dlt:3
order-dlq:3
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
