#!/bin/bash

# Test parallel execution with different resource pools

echo "Testing Parallel Execution with FAIR Scheduler"
echo "=============================================="
echo ""

# Job 1: Long-running query with low priority
echo "Submitting Job 1 (low-priority pool)..."
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "code": "SELECT COUNT(*) FROM range(10000000)",
    "kind": "sql",
    "pool": "low-priority"
  }' &

sleep 1

# Job 2: Interactive query with high priority
echo "Submitting Job 2 (high-priority pool)..."
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "code": "SELECT 1 AS result",
    "kind": "sql",
    "pool": "high-priority"
  }' &

sleep 1

# Job 3: Default pool
echo "Submitting Job 3 (default pool)..."
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "code": "SELECT AVG(id) FROM range(1000000)",
    "kind": "sql",
    "pool": "default"
  }' &

echo ""
echo "All jobs submitted! Waiting for completion..."
echo ""

# Wait for all background jobs to complete
wait

echo ""
echo "All jobs completed!"
echo ""
echo "Check Spark UI at: http://localhost:4040"
