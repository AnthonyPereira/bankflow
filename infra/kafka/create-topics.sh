#!/bin/bash
# Crée les topics BankFlow de façon déclarative (BF-003-03).
# Le script est idempotent : il peut être relancé à chaque démarrage.
# La configuration de chaque topic est expliquée dans docs/kafka/topics.md.
set -euo pipefail

BOOTSTRAP_SERVER="${BOOTSTRAP_SERVER:-kafka:29092}"
KAFKA_BIN=/opt/kafka/bin
DAY_MS=86400000

# Usage : create_topic <nom> <partitions> <rétention en jours>
create_topic() {
    "$KAFKA_BIN/kafka-topics.sh" --bootstrap-server "$BOOTSTRAP_SERVER" --create --if-not-exists \
        --topic "$1" \
        --partitions "$2" \
        --replication-factor 1 \
        --config cleanup.policy=delete \
        --config min.insync.replicas=1 \
        --config retention.ms=$(($3 * DAY_MS))
}

create_topic bank.users 3 30
create_topic bank.customers 3 30
create_topic bank.accounts 6 30
create_topic bank.transfers 6 30
create_topic bank.transactions 6 30
create_topic bank.fraud-alerts 3 90

"$KAFKA_BIN/kafka-topics.sh" --bootstrap-server "$BOOTSTRAP_SERVER" --list
