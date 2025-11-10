#!/bin/bash

# Test Python script execution in WhereQ Libra

echo "Testing Python Script Execution"
echo "================================"
echo ""

BASE_URL="http://localhost:8080/api/v1/sessions/default/statements"

# Test 1: Execute Python file
echo "Test 1: Execute Python file (hello_spark.py)"
echo "---------------------------------------------"
curl -X POST $BASE_URL \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "python-file",
    "filePath": "scripts/hello_spark.py",
    "args": ["arg1", "arg2", "arg3"],
    "pool": "default"
  }' | jq .

echo ""
echo ""

# Test 2: Execute Python file (word_count.py)
echo "Test 2: Execute Python file (word_count.py)"
echo "--------------------------------------------"
curl -X POST $BASE_URL \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "python-file",
    "filePath": "scripts/word_count.py",
    "pool": "high-priority"
  }' | jq .

echo ""
echo ""

# Test 3: Execute inline Python code
echo "Test 3: Execute inline Python code"
echo "-----------------------------------"
curl -X POST $BASE_URL \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "python",
    "code": "from pyspark.sql import SparkSession\nspark = SparkSession.builder.appName(\"Inline Python\").getOrCreate()\nprint(\"Hello from inline Python!\")\nprint(f\"Spark version: {spark.version}\")\ndf = spark.range(10)\nprint(f\"Count: {df.count()}\")\nspark.stop()",
    "pool": "interactive"
  }' | jq .

echo ""
echo ""
echo "All tests completed!"
echo "===================="
