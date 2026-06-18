# =========================================================
# ECOMMERCE PLATFORM - KAFKA TOPIC SETUP
# =========================================================
# This script creates Kafka topics required for local development.
#
# Docker Kafka container: ecommerce-kafka
# Internal Kafka bootstrap: kafka:9092
#
# Use kafka:9092 here because this command runs inside the Kafka container.
# For Spring Boot services running locally on the host, use localhost:29092.
# =========================================================

$KafkaContainer = "ecommerce-kafka"
$BootstrapServer = "kafka:9092"
$Partitions = 3
$ReplicationFactor = 1

$Topics = @(
    "order-created",
    "inventory-reserved",
    "inventory-released",
    "payment-success",
    "payment-failed",
    "order-completed",
    "order-cancelled",
    "shipment-created",
    "notification-requested"
)

Write-Host ""
Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host " Creating Kafka topics for Ecommerce Platform" -ForegroundColor Cyan
Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host ""

# Check Docker container exists
$containerExists = docker ps -a --format "{{.Names}}" | Select-String -Pattern "^$KafkaContainer$"

if (-not $containerExists) {
    Write-Host "Kafka container '$KafkaContainer' was not found." -ForegroundColor Red
    Write-Host "Start infrastructure first:" -ForegroundColor Yellow
    Write-Host "docker compose up -d" -ForegroundColor Yellow
    exit 1
}

# Check Docker container is running
$containerRunning = docker ps --format "{{.Names}}" | Select-String -Pattern "^$KafkaContainer$"

if (-not $containerRunning) {
    Write-Host "Kafka container '$KafkaContainer' exists but is not running." -ForegroundColor Red
    Write-Host "Start it with:" -ForegroundColor Yellow
    Write-Host "docker compose up -d kafka" -ForegroundColor Yellow
    exit 1
}

Write-Host "Kafka container found: $KafkaContainer" -ForegroundColor Green
Write-Host "Bootstrap server: $BootstrapServer" -ForegroundColor Green
Write-Host ""

foreach ($topic in $Topics) {
    Write-Host "Creating topic: $topic" -ForegroundColor Cyan

    docker exec $KafkaContainer kafka-topics `
        --bootstrap-server $BootstrapServer `
        --create `
        --if-not-exists `
        --topic $topic `
        --partitions $Partitions `
        --replication-factor $ReplicationFactor

    if ($LASTEXITCODE -ne 0) {
        Write-Host "Failed to create topic: $topic" -ForegroundColor Red
        exit 1
    }

    Write-Host "Topic ready: $topic" -ForegroundColor Green
    Write-Host ""
}

Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host " Kafka topics currently available" -ForegroundColor Cyan
Write-Host "=========================================================" -ForegroundColor Cyan

docker exec $KafkaContainer kafka-topics `
    --bootstrap-server $BootstrapServer `
    --list

Write-Host ""
Write-Host "Kafka topic setup completed successfully." -ForegroundColor Green
Write-Host ""
Write-Host "To consume order-created events:" -ForegroundColor Yellow
Write-Host "docker exec -it ecommerce-kafka kafka-console-consumer --bootstrap-server kafka:9092 --topic order-created --from-beginning" -ForegroundColor Yellow