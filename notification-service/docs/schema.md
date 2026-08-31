# Data Model

PostgreSQL entities are `notifications`, `notification_recipients`, `notification_preferences`,
`notification_deliveries`, and `processed_events`. Processed event IDs prevent duplicate Kafka
delivery from creating duplicate notifications. Deliveries retain provider outcome and retry state.
