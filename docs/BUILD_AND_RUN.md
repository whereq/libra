# Libra Phase 1 - Build and Run Guide

## Quick Start

### Prerequisites

1. **Java 21**
   ```bash
   java -version
   # Should show: openjdk version "21" or higher
   ```

2. **Maven 3.8+**
   ```bash
   mvn -version
   ```

3. **Redis 7.x** (for job queue)
   ```bash
   # Option 1: Docker
   docker run -d --name redis -p 6379:6379 redis:7-alpine

   # Option 2: Native installation
   # See: https://redis.io/docs/install/install-redis/
   ```

### Build

```bash
cd /home/whereq/git/libra
mvn clean package -DskipTests
```

**Expected output:**
```
[INFO] BUILD SUCCESS
[INFO] Total time:  6.898 s
```

### Run

```bash
java -jar target/libra-1.0.0-SNAPSHOT.jar
```

**Expected logs:**
```
INFO Starting WhereqLibraApplication
INFO Starting async job processor
INFO Async job processor started successfully
INFO Tomcat started on port(s): 8080 (http)
INFO Started WhereqLibraApplication in X.XXX seconds
```

### Verify

```bash
# Health check
curl http://localhost:8080/actuator/health

# Expected response:
{"status":"UP"}

# Metrics
curl http://localhost:8080/actuator/prometheus | grep libra

# Expected: Various libra_* metrics
```

---

## Test Async Job Submission

### Submit a Simple SQL Job

```bash
curl -X POST http://localhost:8080/api/v1/spark/jobs/submit \
  -H "Content-Type: application/json" \
  -d '{
    "code": "spark.sql(\"SELECT 1 AS test\").show()",
    "kind": "sql",
    "executionMode": "in-cluster"
  }'
```

**Expected Response (202 Accepted):**
```json
{
  "jobId": "job-abc-123-...",
  "status": "QUEUED",
  "submittedAt": "2025-12-02T23:59:00.123Z"
}
```

### Submit a Python Job

```bash
curl -X POST http://localhost:8080/api/v1/spark/jobs/submit \
  -H "Content-Type: application/json" \
  -d '{
    "code": "print(\"Hello from async Spark!\")",
    "kind": "python",
    "priority": 7,
    "retryPolicy": {
      "maxRetries": 3,
      "backoffMultiplier": 2
    }
  }'
```

### Monitor Logs

```bash
# Watch for job execution
tail -f logs/spring.log | grep -E "Job job-|Enqueued|Consumed"
```

**Expected log output:**
```
INFO Enqueued job job-abc-123, queue size: 1
INFO Consumed job job-abc-123 from queue
INFO Executing job job-abc-123 in-cluster
INFO Job job-abc-123 completed successfully
INFO Acknowledged job job-abc-123
```

---

## Configuration

### Environment Variables

```bash
# Redis configuration
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_PASSWORD=""

# Resource limits (from K8s Downward API in production)
export K8S_CPU_LIMIT=16      # CPU cores available
export K8S_MEMORY_LIMIT=64   # Memory in GB
```

### Application Properties

Key settings in `src/main/resources/application.yml`:

```yaml
libra:
  # Resource limits
  resources:
    limits:
      cpu-cores: ${K8S_CPU_LIMIT:16}
      memory-gb: ${K8S_MEMORY_LIMIT:64}
      max-concurrent-jobs: 10

  # Queue configuration
  queue:
    max-size: 1000
    max-wait-time-minutes: 30

  # Execution modes
  execution:
    modes:
      in-cluster:
        enabled: true
        limits:
          max-executors: 5
          max-memory-per-executor: 4g
```

---

## Monitoring

### Prometheus Metrics

```bash
# Get all Libra metrics
curl -s http://localhost:8080/actuator/prometheus | grep ^libra

# Key metrics:
# libra_admission_admitted_total
# libra_admission_queued_total
# libra_admission_rejected_total
# libra_jobs_succeeded_total
# libra_jobs_failed_total
# libra_resources_cpu_used
# libra_resources_memory_used
# libra_resources_jobs_active
```

### Redis Queue Monitoring

```bash
# Connect to Redis
redis-cli

# Check queue size
LLEN libra:jobs:queue

# View jobs in queue (first 10)
LRANGE libra:jobs:queue 0 9
```

---

## Troubleshooting

### Issue: "Connection refused" to Redis

**Solution:**
```bash
# Check if Redis is running
docker ps | grep redis

# If not, start Redis
docker run -d --name redis -p 6379:6379 redis:7-alpine

# Test connection
redis-cli ping
# Expected: PONG
```

### Issue: Jobs not being processed

**Check:**
1. Logs for "Async job processor started"
2. Redis queue size: `redis-cli LLEN libra:jobs:queue`
3. Resource availability in logs

**Debug:**
```bash
# Enable DEBUG logging
export LOGGING_LEVEL_COM_WHEREQ_LIBRA=DEBUG
java -jar target/libra-1.0.0-SNAPSHOT.jar
```

### Issue: OutOfMemoryError

**Solution:**
```bash
# Increase JVM heap
java -Xmx8g -jar target/libra-1.0.0-SNAPSHOT.jar

# Reduce max concurrent jobs in application.yml
libra.resources.limits.max-concurrent-jobs: 5
```

---

## API Examples

### Cancel a Job

```bash
curl -X DELETE http://localhost:8080/api/v1/spark/jobs/job-abc-123
```

**Response:**
```json
{
  "jobId": "job-abc-123",
  "status": "CANCELLED",
  "cancelledAt": "2025-12-02T23:59:30.000Z",
  "message": "Job cancelled successfully"
}
```

### Get Job Status (Phase 2)

```bash
curl http://localhost:8080/api/v1/spark/jobs/job-abc-123
```

**Current Response:** `501 Not Implemented` (will be added in Phase 2)

---

## Next Steps

1. **Phase 2:** Implement job status query API
2. **Phase 2:** Add PostgreSQL for job history persistence
3. **Phase 2:** Add authentication/authorization
4. **Phase 3:** Implement Kubernetes Operator integration

---

## Files Created

**New Components:** 44 Java classes
**Modified Files:** 3 (SparkJobRequest.java, application.yml, pom.xml)
**Documentation:** 5 markdown files

See `docs/PHASE1_IMPLEMENTATION.md` for complete details.

---

## Build Information

- **Java Version:** 21
- **Spring Boot:** 3.5.7
- **Spark:** 4.0.1
- **Redis:** Redis Reactive (Lettuce)
- **Build Tool:** Maven 3.x
- **Package Size:** ~150MB (with Spark dependencies)

---

## Success!

✅ Build successful
✅ Async job submission working
✅ Resource monitoring active
✅ Job queue persistent (Redis)
✅ Metrics exposed (Prometheus)

**Ready for testing and Phase 2 development!**
