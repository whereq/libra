# WhereQ Libra - Quick Start Guide

## Prerequisites

```bash
# Install PySpark
pip install pyspark

# Verify spark-submit
spark-submit --version
```

## Start Libra

```bash
# Build
mvn clean package -DskipTests

# Run
java -jar target/libra-1.0.0-SNAPSHOT.jar

# Or with Docker
docker-compose -f docker-compose.local.yml up
```

## API Endpoints

- **Base URL**: `http://localhost:8080`
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **Spark UI**: `http://localhost:4040`
- **Health Check**: `http://localhost:8080/api/v1/health`

## Execute Jobs

### 1. SQL Query

```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "sql",
    "code": "SELECT 1 AS result",
    "pool": "default"
  }'
```

### 2. Python File

```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "python-file",
    "filePath": "scripts/hello_spark.py",
    "args": ["arg1", "arg2"],
    "pool": "default"
  }'
```

### 3. Inline Python

```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "python",
    "code": "from pyspark.sql import SparkSession\nspark = SparkSession.builder.getOrCreate()\nprint(spark.version)\nspark.stop()",
    "pool": "interactive"
  }'
```

## Resource Pools

- `default`: Standard jobs
- `high-priority`: Critical jobs (weight: 3, minShare: 4)
- `low-priority`: Batch jobs (weight: 1, minShare: 1)
- `interactive`: Ad-hoc queries (weight: 2, minShare: 2)

## Configuration

### SHARED Mode (Default)

```yaml
libra:
  session:
    mode: SHARED  # Single SparkSession, better performance
```

### ISOLATED Mode

```yaml
libra:
  session:
    mode: ISOLATED  # Multiple SparkSessions, better isolation
    max-sessions: 10
    timeout-minutes: 30
```

## Test Scripts

```bash
# Test parallel execution
./test-parallel-execution.sh

# Test Python execution
./test-python-execution.sh
```

## Sample Python Scripts

Located in `scripts/` directory:
- `hello_spark.py` - Basic PySpark demo
- `word_count.py` - Word count example

## Documentation

- **Parallel Execution**: See `PARALLEL_EXECUTION.md`
- **Python Execution**: See `PYTHON_EXECUTION.md`
- **Deployment**: See `DEPLOYMENT.md`

## Health Check

```bash
curl http://localhost:8080/api/v1/health | jq .
```

Expected response:
```json
{
  "status": "UP",
  "service": "whereq-libra",
  "spark": {
    "version": "4.0.1",
    "applicationId": "local-...",
    "master": "local[*]",
    "status": "CONNECTED",
    "activeSessions": "1"
  }
}
```

## Common Issues

### spark-submit not found

```bash
export SPARK_HOME=/path/to/spark
export PATH=$PATH:$SPARK_HOME/bin
```

### Python script not found

Use absolute path:
```json
"filePath": "/full/path/to/script.py"
```

## Next Steps

1. Read `PARALLEL_EXECUTION.md` for parallel execution details
2. Read `PYTHON_EXECUTION.md` for Python job execution
3. Check `DEPLOYMENT.md` for production deployment
4. Explore Swagger UI for API documentation
