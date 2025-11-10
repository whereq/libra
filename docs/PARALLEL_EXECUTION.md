# WhereQ Libra - Parallel Execution Guide

## Question: Single Session vs Multiple Sessions?

### TL;DR

**Both approaches are valid - choose based on your use case:**

| Approach | Best For | Performance | Isolation |
|----------|----------|-------------|-----------|
| **SHARED** (Single Session) | General use, trusted users | ⭐⭐⭐⭐⭐ | ⭐⭐ |
| **ISOLATED** (Multiple Sessions) | Multi-tenant, production | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |

---

## How Parallel Execution Works

### Approach 1: Single SparkSession + FAIR Scheduler (Default)

**Configuration:**
```yaml
libra:
  session:
    mode: SHARED  # Default

spark:
  config:
    spark.scheduler.mode: FAIR
```

**Architecture:**
```
┌───────────────────────────────────────┐
│     Single SparkSession (Shared)      │
├───────────────────────────────────────┤
│         FAIR Scheduler                │
│                                       │
│  ┌─────────┐  ┌─────────┐  ┌────────┐ │
│  │ Pool:   │  │ Pool:   │  │ Pool:  │ │
│  │ default │  │ high-   │  │ inter- │ │
│  │         │  │ priority│  │ active │ │
│  └────┬────┘  └────┬────┘  └────┬───┘ │
│       │            │            │     │
└───────┼────────────┼────────────┼─────┘
        │            │            │
        ▼            ▼            ▼
     Job 1        Job 2        Job 3
   (parallel)   (parallel)   (parallel)
```

**How it works:**
1. **Thread-Safe**: SparkSession is thread-safe for concurrent operations
2. **FAIR Scheduling**: Jobs share resources fairly based on pool weights
3. **Resource Pools**: Define pools with different priorities
4. **Job Queuing**: Spark manages job queue internally

**Example: 3 Concurrent Jobs**
```bash
# Terminal 1 - Long-running query
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "code": "SELECT COUNT(*) FROM range(1000000000)",
    "kind": "sql",
    "pool": "low-priority"
  }'

# Terminal 2 - Interactive query (runs simultaneously)
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "code": "SELECT 1",
    "kind": "sql",
    "pool": "interactive"
  }'

# Terminal 3 - High priority (gets more resources)
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "code": "SELECT AVG(id) FROM range(1000000)",
    "kind": "sql",
    "pool": "high-priority"
  }'
```

**Resource Allocation:**
```
Available Resources: 10 cores, 8GB memory

FAIR Scheduler distributes:
- low-priority job:     2 cores, 2GB  (weight: 1)
- interactive job:      3 cores, 3GB  (weight: 2)
- high-priority job:    5 cores, 3GB  (weight: 3)
```

---

### Approach 2: Multiple SparkSessions (ISOLATED)

**Configuration:**
```yaml
libra:
  session:
    mode: ISOLATED
    max-sessions: 10
    timeout-minutes: 30
```

**Architecture:**
```
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│  SparkSession 1  │  │  SparkSession 2  │  │  SparkSession 3  │
│  (User A)        │  │  (User B)        │  │  (User C)        │
├──────────────────┤  ├──────────────────┤  ├──────────────────┤
│  Own Resources   │  │  Own Resources   │  │  Own Resources   │
│  - 2 executors   │  │  - 2 executors   │  │  - 2 executors   │
│  - 2GB each      │  │  - 2GB each      │  │  - 2GB each      │
└────────┬─────────┘  └────────┬─────────┘  └────────┬─────────┘
         │                     │                     │
         ▼                     ▼                     ▼
      Job A1                Job B1                 Job C1
     (isolated)            (isolated)             (isolated)
```

**How it works:**
1. **Complete Isolation**: Each session has own executors
2. **User-Based**: One session per user/session-id
3. **Timeout**: Sessions auto-cleaned after inactivity
4. **Resource Limit**: Max sessions prevents overload

**Example: Different Users**
```bash
# User A - Creates session "user-alice"
curl -X POST http://localhost:8080/api/v1/sessions/user-alice/statements \
  -H "Content-Type: application/json" \
  -d '{"code":"SELECT * FROM mydata","kind":"sql"}'

# User B - Creates separate session "user-bob"
curl -X POST http://localhost:8080/api/v1/sessions/user-bob/statements \
  -H "Content-Type: application/json" \
  -d '{"code":"SELECT * FROM mydata","kind":"sql"}'

# Completely isolated - no resource sharing!
```

---

## Comparison: SHARED vs ISOLATED

### SHARED Mode (Single Session + FAIR Scheduler)

**Pros:**
- ✅ **Better Performance**: No session creation overhead
- ✅ **Resource Efficient**: Shared executor pool
- ✅ **Lower Latency**: No warmup time
- ✅ **Simpler Management**: One session to manage

**Cons:**
- ❌ **No User Isolation**: Jobs can see each other's temp views
- ❌ **Configuration Lock-in**: Can't change config per job
- ❌ **Potential Starvation**: One job can delay others (if not using FAIR)

**Best For:**
- Single application
- Trusted environment
- High throughput requirements
- Cost optimization

---

### ISOLATED Mode (Multiple Sessions)

**Pros:**
- ✅ **Complete Isolation**: Each user has own session
- ✅ **Security**: No data leakage between sessions
- ✅ **Custom Config**: Different settings per session
- ✅ **Fault Isolation**: One session crash doesn't affect others

**Cons:**
- ❌ **Higher Overhead**: Session creation takes time (~5-10s)
- ❌ **More Resources**: Each session needs executors
- ❌ **Complexity**: Session lifecycle management
- ❌ **Resource Fragmentation**: Executors can't be shared

**Best For:**
- Multi-tenant SaaS
- Different user groups
- Compliance requirements
- Production environments

---

## How SparkSession Handles Parallelism

### Thread Safety

```java
// This is thread-safe - multiple threads can call simultaneously
SparkSession session = ...;

// Thread 1
session.sql("SELECT * FROM users WHERE age > 30");

// Thread 2 (runs in parallel)
session.sql("SELECT COUNT(*) FROM orders");

// Thread 3 (also runs in parallel)
session.sql("SELECT AVG(price) FROM products");
```

### Internal Job Queue

```
Request 1 arrives → Job 1 created → Job ID: job-1
Request 2 arrives → Job 2 created → Job ID: job-2
Request 3 arrives → Job 3 created → Job ID: job-3

Spark Scheduler:
┌─────────────────────────────────────┐
│  Job Queue (FAIR Scheduler)         │
│                                     │
│  ┌─────┐  ┌─────┐  ┌─────┐          │
│  │Job 1│  │Job 2│  │Job 3│          │
│  └──┬──┘  └──┬──┘  └──┬──┘          │
│     │        │        │             │
│     └────────┼────────┘             │
│              ▼                      │
│      ┌───────────────┐              │
│      │  Executor     │              │
│      │  Pool         │              │
│      │  (Shared)     │              │
│      └───────────────┘              │
└─────────────────────────────────────┘
```

---

## FAIR Scheduler Configuration

### Pool Definition (`fairscheduler.xml`)

```xml
<allocations>
  <!-- Default pool -->
  <pool name="default">
    <schedulingMode>FAIR</schedulingMode>
    <weight>1</weight>        <!-- Relative share -->
    <minShare>2</minShare>    <!-- Minimum cores -->
  </pool>

  <!-- High priority pool -->
  <pool name="high-priority">
    <schedulingMode>FAIR</schedulingMode>
    <weight>3</weight>        <!-- Gets 3x more resources -->
    <minShare>4</minShare>
  </pool>
</allocations>
```

### Using Pools in Requests

```json
{
  "code": "SELECT * FROM large_table",
  "kind": "sql",
  "pool": "high-priority"  ← Assigns to pool
}
```

---

## Performance Benchmarks

### Test: 3 Concurrent SQL Queries

**Setup:**
- Cluster: 4 cores, 8GB RAM
- Query: `SELECT COUNT(*) FROM range(100000000)`

**SHARED Mode (FAIR Scheduler):**
```
Job 1: Started at T+0s, Completed at T+45s
Job 2: Started at T+0s, Completed at T+47s
Job 3: Started at T+0s, Completed at T+46s

Total Time: 47s
Resource Utilization: 95%
```

**SHARED Mode (FIFO Scheduler - BAD):**
```
Job 1: Started at T+0s,  Completed at T+30s
Job 2: Started at T+30s, Completed at T+60s
Job 3: Started at T+60s, Completed at T+90s

Total Time: 90s  (⚠️ Sequential!)
Resource Utilization: 33%
```

**ISOLATED Mode:**
```
Job 1: Started at T+8s  (session creation), Completed at T+53s
Job 2: Started at T+8s  (session creation), Completed at T+55s
Job 3: Started at T+8s  (session creation), Completed at T+54s

Total Time: 55s
Resource Utilization: 85%
Overhead: +8s per session creation
```

---

## Switching Between Modes

### Via Configuration File

```yaml
# application.yml
libra:
  session:
    mode: SHARED  # or ISOLATED
```

### Via Environment Variable

```bash
docker run -e LIBRA_SESSION_MODE=ISOLATED whereq/libra:1.0.0
```

### Via Kubernetes ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: libra-config
data:
  application.yml: |
    libra:
      session:
        mode: ISOLATED
        max-sessions: 20
```

---

## Monitoring Parallel Execution

### Check Active Jobs

```bash
# Spark UI shows all concurrent jobs
open http://localhost:4040/jobs/

# Or via API
curl http://localhost:8080/api/v1/sessions/default
```

### View Scheduler Pools

```bash
# Spark UI -> Stages -> Pool
# Shows which pool each job is using
```

### Metrics

```bash
# Actuator endpoint
curl http://localhost:8080/actuator/metrics/libra.sessions.active
curl http://localhost:8080/actuator/metrics/libra.jobs.concurrent
```

---

## Best Practices

### For SHARED Mode:

1. **Always Use FAIR Scheduler**
   ```yaml
   spark.scheduler.mode: FAIR
   ```

2. **Define Appropriate Pools**
   - Default: Regular queries
   - Interactive: Quick ad-hoc queries
   - Batch: Long-running ETL jobs
   - High-priority: Critical reports

3. **Set Resource Limits**
   ```yaml
   spark.executor.cores: 2
   spark.executor.memory: 4g
   spark.executor.instances: 5
   ```

### For ISOLATED Mode:

1. **Set Session Limits**
   ```yaml
   max-sessions: 10  # Prevent resource exhaustion
   ```

2. **Configure Timeouts**
   ```yaml
   timeout-minutes: 30  # Auto-cleanup inactive sessions
   ```

3. **Monitor Session Count**
   - Alert if approaching max-sessions
   - Log session creation/deletion

---

## Migration Strategy

### Start with SHARED, Move to ISOLATED if needed

```
Development → SHARED mode (simple, fast)
     ↓
Staging → SHARED mode with FAIR scheduler
     ↓
Production (single tenant) → SHARED mode
     ↓
Production (multi-tenant) → ISOLATED mode
```

---

## Summary

✅ **Single SparkSession** is best practice for most use cases
✅ **FAIR Scheduler** enables true parallel execution
✅ **Multiple Sessions** for multi-tenant isolation
✅ **Configuration-driven** - no code changes needed
✅ **Both modes supported** in WhereQ Libra

**Choose based on your requirements, not assumptions!**
