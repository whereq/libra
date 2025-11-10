# Where Is The Driver?

**Understanding Driver Location in Different Spark Deployments**

---

## The Key Insight

When you see a Spark deployment with only **Master** and **Workers**, the **driver is NOT part of the cluster infrastructure**. The driver is part of your **APPLICATION**, not the cluster.

```
Cluster Infrastructure (Long-lived):
├─ Master (resource manager)
└─ Workers (compute nodes)

Your Application (Per-job):
├─ Driver (coordinator) ← WHERE IS THIS?
└─ Executors (run on workers)
```

**Answer:** The driver location depends on the **deploy mode**:
- **Client Mode**: Driver runs on the **submit machine** (outside the cluster)
- **Cluster Mode**: Driver runs **inside the cluster** (on a worker node)

---

## Visual Explanation

### Scenario 1: Client Mode (Driver Outside Cluster)

```
┌─────────────────────────────────────────────────────────────────┐
│                     Spark Standalone Cluster                    │
│                     (Master + Workers Only)                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────┐                                           │
│  │  Spark Master    │  spark://master:7077                      │
│  │  (Resource Mgr)  │  - Allocates resources                    │
│  └────────┬─────────┘  - NO application logic                   │
│           │                                                     │
│           │ (Manages workers, allocates executors)              │
│           │                                                     │
│  ┌────────┴─────────────────────────────────────┐               │
│  │                                              │               │
│  ↓                        ↓                     ↓               │
│  Worker 1                 Worker 2              Worker 3        │
│  192.168.1.101           192.168.1.102          192.168.1.103   │
│  ┌──────────────┐        ┌──────────────┐      ┌──────────────┐ │
│  │ Executor 1   │        │ Executor 2   │      │ Executor 3   │ │
│  │ (App-123)    │        │ (App-123)    │      │ (App-123)    │ │
│  └──────────────┘        └──────────────┘      └──────────────┘ │
│         ↑                       ↑                      ↑        │
│         │                       │                      │        │
│         └───────────────────────┼──────────────────────┘        │
│                                 │                               │
└─────────────────────────────────┼───────────────────────────────┘
                                  │
                                  │ (Driver communicates with executors)
                                  │
                    ┌─────────────┴──────────────┐
                    │  CLIENT MACHINE            │
                    │  (YOUR LAPTOP / GATEWAY)   │
                    │                            │
                    │  ┌──────────────────────┐  │
                    │  │ DRIVER (App-123)     │  │ ← DRIVER IS HERE!
                    │  │ SparkContext         │  │
                    │  │ - Schedules tasks    │  │
                    │  │ - Collects results   │  │
                    │  └──────────────────────┘  │
                    │                            │
                    │  $ spark-submit \          │
                    │      --master spark://...\ │
                    │      --deploy-mode client  │
                    └────────────────────────────┘
```

**Key Points:**
- Cluster only has **Master + Workers**
- **Driver runs on your laptop** or submit machine
- If you disconnect, the application **fails**
- Driver **must stay connected** to the cluster

**When to use:**
- Interactive development (spark-shell, pyspark)
- Debugging (see logs locally)
- Small applications
- **WhereQ Libra SHARED mode** (driver embedded in Libra)

---

### Scenario 2: Cluster Mode (Driver Inside Cluster)

```
┌─────────────────────────────────────────────────────────────────┐
│                     Spark Standalone Cluster                    │
│                     (Master + Workers Only)                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────┐                                           │
│  │  Spark Master    │  spark://master:7077                      │
│  │  (Resource Mgr)  │  - Allocates resources                    │
│  └────────┬─────────┘  - Launches driver on a worker            │
│           │                                                     │
│           │ (Allocates driver + executors to workers)           │
│           │                                                     │
│  ┌────────┴─────────────────────────────────────┐               │
│  │                                              │               │
│  ↓                        ↓                     ↓               │
│  Worker 1                 Worker 2              Worker 3        │
│  192.168.1.101           192.168.1.102          192.168.1.103   │
│  ┌──────────────┐        ┌──────────────┐      ┌──────────────┐ │
│  │ DRIVER       │        │ Executor 1   │      │ Executor 2   │ │
│  │ (App-123)    │← HERE! │ (App-123)    │      │ (App-123)    │ │
│  │              │        │              │      │              │ │
│  │ SparkContext │────────→ Tasks        │      │ Tasks        │ │
│  └──────────────┘        └──────────────┘      └──────────────┘ │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
         ↑
         │
         │ (Master launched driver here)
         │

┌────────────────────────────┐
│  CLIENT MACHINE            │
│  (YOUR LAPTOP)             │
│                            │
│  $ spark-submit \          │
│      --master spark://...\ │
│      --deploy-mode cluster │  ← Just submits and can disconnect!
│                            │
│  ✓ Can close laptop        │
│  ✓ App keeps running       │
└────────────────────────────┘
```

**Key Points:**
- Cluster has **Master + Workers**
- **Driver runs on a worker node** (inside cluster)
- Master decides which worker runs the driver
- You can **disconnect** after submission
- Driver runs as a **separate process** on the worker

**When to use:**
- Production applications
- Long-running jobs
- When submit machine is unstable
- **WhereQ Libra with spark-submit in cluster mode**

---

## WhereQ Libra: Where Is The Driver?

### Case 1: SHARED Mode (In-JVM Execution)

```
┌───────────────────────────────────────────────────────────────┐
│  Libra Container (Docker/K8s Pod)                             │
│  IP: 192.168.1.50                                             │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  Spring Boot Application (JVM)                          │  │
│  │                                                         │  │
│  │  ┌────────────────────────────────────────────────┐     │  │
│  │  │  REST API (:8080)                              │     │  │
│  │  └────────────────────────────────────────────────┘     │  │
│  │                    ↓                                    │  │
│  │  ┌────────────────────────────────────────────────┐     │  │
│  │  │  SparkSessionService                           │     │  │
│  │  │                                                │     │  │
│  │  │  SparkSession (DRIVER IS HERE!) ←──────────────┼─────┼──┐
│  │  │  ├─ SparkContext                               │     │  │
│  │  │  ├─ Task Scheduler                             │     │  │
│  │  │  ├─ DAG Scheduler                              │     │  │
│  │  │  └─ Executor Coordinator                       │     │  │
│  │  └────────────────────────────────────────────────┘     │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                               │
│  Driver Location: INSIDE LIBRA CONTAINER                      │
│  Deploy Mode: Equivalent to "client mode"                     │
└───────────────────────────┬───────────────────────────────────┘
                            │
                            │ (Connects to cluster)
                            ↓
        ┌────────────────────────────────────────┐
        │  Spark Cluster (Master + Workers)      │
        │  ┌──────────────┐  ┌──────────────┐    │
        │  │ Executor 1   │  │ Executor 2   │    │
        │  └──────────────┘  └──────────────┘    │
        └────────────────────────────────────────┘
```

**API Request Example:**
```bash
curl -X POST http://libra:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "sql",
    "code": "SELECT * FROM table"
  }'
```

**What happens:**
1. REST request arrives at Libra
2. **Driver (inside Libra)** receives the request
3. Driver analyzes SQL and creates execution plan
4. Driver sends tasks to executors in the cluster
5. Executors run tasks and return results
6. **Driver (inside Libra)** collects results
7. Libra returns results via REST API

**Driver Location:** Libra container (192.168.1.50)

---

### Case 2: ISOLATED Mode - Client Deploy Mode

```
┌───────────────────────────────────────────────────────────────┐
│  Libra Container (Docker/K8s Pod)                             │
│  IP: 192.168.1.50                                             │
│                                                               │
│  ┌──────────────────────────────────────────────────────┐     │
│  │  Libra JVM (Main Process)                            │     │
│  │  - REST API                                          │     │
│  │  - Launches spark-submit                             │     │
│  └──────────────────────────────────────────────────────┘     │
│                                                               │
│  ┌──────────────────────────────────────────────────────┐     │
│  │  spark-submit Process (Separate JVM)                 │     │
│  │                                                      │     │
│  │  ┌────────────────────────────────────────────┐      │     │
│  │  │  DRIVER IS HERE!                           │      │  ←──┐
│  │  │  - Reads JAR/Python file                   │      │     │
│  │  │  - Creates SparkContext                    │      │     │
│  │  │  - Schedules tasks                         │      │     │
│  │  │  - Collects results                        │      │     │
│  │  └────────────────────────────────────────────┘      │     │
│  └──────────────────────────────────────────────────────┘     │
│                                                               │
│  Driver Location: INSIDE LIBRA CONTAINER (separate JVM)       │
│  Deploy Mode: client (explicit)                               │
└───────────────────────────┬───────────────────────────────────┘
                            │
                            │ (Connects to cluster)
                            ↓
        ┌────────────────────────────────────────┐
        │  Spark Cluster (Master + Workers)      │
        │  ┌──────────────┐  ┌──────────────┐    │
        │  │ Executor 1   │  │ Executor 2   │    │
        │  │ (16GB, 8c)   │  │ (16GB, 8c)   │    │
        │  └──────────────┘  └──────────────┘    │
        └────────────────────────────────────────┘
```

**API Request Example:**
```bash
curl -X POST http://libra:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "jar",
    "filePath": "/apps/myapp.jar",
    "mainClass": "com.example.MyApp",
    "sparkConfig": {
      "spark.executor.memory": "16g",
      "spark.executor.cores": "8"
    }
  }'
```

**What happens:**
1. Libra receives REST request
2. Libra launches: `spark-submit --deploy-mode client --master spark://master:7077 ...`
3. **New JVM starts inside Libra container** (driver)
4. Driver connects to Spark master
5. Master allocates executors on workers
6. Driver sends tasks to executors
7. **Driver (in Libra container)** waits for completion
8. Libra returns results

**Driver Location:** Libra container (192.168.1.50), separate JVM

---

### Case 3: ISOLATED Mode - Cluster Deploy Mode

```
┌───────────────────────────────────────────────────────────────┐
│  Libra Container (Docker/K8s Pod)                             │
│  IP: 192.168.1.50                                             │
│                                                               │
│  ┌──────────────────────────────────────────────────────┐     │
│  │  Libra JVM                                           │     │
│  │  - REST API                                          │     │
│  │  - Launches spark-submit                             │     │
│  │  - Can disconnect after submission ✓                 │     │
│  └──────────────────────────────────────────────────────┘     │
│                                                               │
└───────────────────────────┬───────────────────────────────────┘
                            │
                            │ (Submits job to master)
                            ↓
┌───────────────────────────────────────────────────────────────┐
│  Spark Cluster (Master + Workers)                             │
│                                                               │
│  ┌──────────────────┐                                         │
│  │  Spark Master    │  Receives submission                    │
│  └────────┬─────────┘  Decides: "Launch driver on Worker 1"   │
│           │                                                   │
│  ┌────────┴─────────────────────────────────────┐             │
│  │                                              │             │
│  ↓                        ↓                     ↓             │
│  Worker 1                 Worker 2              Worker 3      │
│  192.168.1.101           192.168.1.102          192.168.1.103 │
│  ┌──────────────┐        ┌──────────────┐      ┌──────────┐   │
│  │ DRIVER       │        │ Executor 1   │      │Executor 2│   │
│  │ (App-123)    │←HERE!  │ (App-123)    │      │(App-123) │   │
│  │              │        │ (16GB, 8c)   │      │(16GB, 8c)│   │
│  │ SparkContext │────────→ Tasks        │      │ Tasks    │   │
│  └──────────────┘        └──────────────┘      └──────────┘   │
│                                                               │
│  Driver Location: WORKER 1 (INSIDE CLUSTER)                   │
│  Deploy Mode: cluster (explicit)                              │
└───────────────────────────────────────────────────────────────┘
```

**Configuration:**
```yaml
# application.yml
spark:
  submit:
    deploy-mode: cluster  # Driver runs in cluster
```

**What happens:**
1. Libra receives REST request
2. Libra launches: `spark-submit --deploy-mode cluster --master spark://master:7077 ...`
3. Master receives submission
4. **Master chooses a worker** to run the driver (e.g., Worker 1)
5. **Driver starts on Worker 1** (inside cluster)
6. Driver requests executors from Master
7. Master allocates executors on Worker 2, Worker 3
8. Driver runs application, collects results
9. Libra can **disconnect** or monitor via Spark REST API

**Driver Location:** Worker node (e.g., 192.168.1.101) - INSIDE CLUSTER

---

## Summary Table

| Scenario | Deploy Mode | Driver Location | Can Disconnect? | Use Case |
|----------|-------------|-----------------|-----------------|----------|
| **SHARED mode (sql, python code, jar-class no config)** | N/A (embedded) | Libra container | No (Libra must stay up) | Interactive queries, small jobs |
| **spark-submit (client mode)** | `--deploy-mode client` | Libra container (separate JVM) | No (Libra must stay up) | Jobs requiring custom resources |
| **spark-submit (cluster mode)** | `--deploy-mode cluster` | Worker node (inside cluster) | Yes (Libra can disconnect) | Production, long-running jobs |

---

## Common Deployment Patterns

### Pattern 1: Local Development (No Cluster)

```
┌─────────────────────────────────────────┐
│  Developer Laptop                       │
│                                         │
│  ┌───────────────────────────────────┐  │
│  │  Libra (local mode)               │  │
│  │                                   │  │
│  │  DRIVER + EXECUTORS (same JVM)    │  │ ← Everything runs here!
│  │                                   │  │
│  │  --master local[4]                │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

**Driver Location:** Same JVM as Libra (local mode)

---

### Pattern 2: Docker Compose (Standalone Cluster)

```
┌─────────────────────────────────────────────────────────────┐
│  Docker Compose Network                                     │
│                                                             │
│  ┌──────────────────┐                                       │
│  │  libra container │                                       │
│  │  DRIVER HERE ─┐  │                                       │
│  └───────────────┼──┘                                       │
│                  │                                          │
│                  ↓ connects to                              │
│  ┌───────────────────────────────────────────────┐          │
│  │  spark-master container                       │          │
│  │  (Resource manager only, no driver)           │          │
│  └─────────────┬─────────────────────────────────┘          │
│                │                                            │
│  ┌─────────────┴──────────────────────┐                     │
│  │                                    │                     │
│  ↓                                    ↓                     │
│  spark-worker-1                       spark-worker-2        │
│  ┌──────────────┐                     ┌──────────────┐      │
│  │ Executor 1   │                     │ Executor 2   │      │
│  └──────────────┘                     └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

**Driver Location:** libra container (SHARED mode) or libra container via spark-submit (ISOLATED client mode)

---

### Pattern 3: Kubernetes (Native Mode)

```
┌─────────────────────────────────────────────────────────────┐
│  Kubernetes Cluster                                         │
│                                                             │
│  ┌──────────────────┐                                       │
│  │  libra pod       │                                       │
│  │  (Submits job)   │                                       │
│  └────────┬─────────┘                                       │
│           │                                                 │
│           │ spark-submit --master k8s://...                 │
│           │ --deploy-mode cluster                           │
│           ↓                                                 │
│  ┌─────────────────────────────────────┐                    │
│  │  K8s creates pods dynamically:      │                    │
│  │                                     │                    │
│  │  ┌─────────────────┐                │                    │
│  │  │ spark-driver    │  ← DRIVER HERE!│                    │
│  │  │ pod             │                │                    │
│  │  └─────────────────┘                │                    │
│  │                                     │                    │
│  │  ┌───────────┐  ┌───────────┐       │                    │
│  │  │spark-exec │  │spark-exec │       │                    │
│  │  │pod-1      │  │pod-2      │       │                    │
│  │  └───────────┘  └───────────┘       │                    │
│  └─────────────────────────────────────┘                    │
└─────────────────────────────────────────────────────────────┘
```

**Driver Location:** Dedicated `spark-driver` pod (created dynamically by K8s)

---

## How To Find The Driver

### Method 1: Spark UI

The Spark Web UI shows driver information:

```
# Access Spark UI
http://driver-host:4040

# In SHARED mode (Libra):
http://libra:4040

# In cluster mode:
http://worker-node:4040  (driver running on worker)
```

**Driver Information:**
- Application Name
- Driver Host & Port
- Driver Memory
- Executor Summary

---

### Method 2: Spark Master UI

```
# Access Master UI
http://spark-master:8080

# Shows:
- Running Applications
  ├─ Application ID: app-20251103-001
  ├─ Name: MySparkApp
  ├─ User: spark
  ├─ Cores: 16
  ├─ Memory: 32GB
  └─ Submission ID: driver-20251103-001
```

Click on the application to see:
```
Driver:
  Host: 192.168.1.50  ← This is where driver is running!
  Port: 7077
  State: RUNNING

Executors:
  Executor 1: 192.168.1.101:7078
  Executor 2: 192.168.1.102:7078
```

---

### Method 3: Command Line

**Client Mode (Driver on Submit Machine):**
```bash
$ spark-submit --deploy-mode client myapp.jar

# Driver logs appear in your terminal
25/11/03 10:00:00 INFO SparkContext: Running Spark version 4.0.1
25/11/03 10:00:01 INFO SparkContext: Driver is running on: 192.168.1.50
                                                              ^^^^^^^^^^^^
                                                              Your machine!
```

**Cluster Mode (Driver on Worker):**
```bash
$ spark-submit --deploy-mode cluster myapp.jar

# Driver logs are on the worker node
# Check worker logs:
$ ssh worker-node
$ cat /opt/spark/work/app-20251103-001/stdout

25/11/03 10:00:00 INFO SparkContext: Running Spark version 4.0.1
25/11/03 10:00:01 INFO SparkContext: Driver is running on: 192.168.1.101
                                                              ^^^^^^^^^^^^
                                                              Worker node!
```

---

### Method 4: ps Command

**On Libra container (client mode):**
```bash
$ docker exec -it libra ps aux | grep spark

# You'll see:
spark    1234  ... java -Xmx4g ... org.apache.spark.deploy.SparkSubmit
                                     ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                                     Driver process running here!
```

**On Worker node (cluster mode):**
```bash
$ ssh worker-node
$ ps aux | grep DriverWrapper

# You'll see:
spark    5678  ... java -Xmx8g ... org.apache.spark.deploy.worker.DriverWrapper
                                     ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                                     Driver running on this worker!
```

---

## Key Takeaways

### 1. Master ≠ Driver
```
❌ WRONG: Master runs the driver
✓ CORRECT: Master allocates resources; driver is your application

Master: Infrastructure component (cluster manager)
Driver: Application component (your code coordinator)
```

### 2. "Master + Workers" is Just Infrastructure
```
Cluster Infrastructure (Always running):
├─ Master (manages resources)
└─ Workers (provide compute)

Your Application (Temporary):
├─ Driver (runs somewhere - client machine OR worker)
└─ Executors (run on workers)
```

### 3. Driver Location = Deploy Mode
```
--deploy-mode client  → Driver on submit machine
--deploy-mode cluster → Driver on a worker node
```

### 4. WhereQ Libra Driver Locations
```
SHARED mode:
  Driver = Inside Libra container (embedded SparkSession)

ISOLATED mode (client):
  Driver = Inside Libra container (separate JVM via spark-submit)

ISOLATED mode (cluster):
  Driver = On a worker node (Libra can disconnect)
```

---

## Debugging: "Where Is My Driver?"

**Checklist:**

1. **Check deploy mode:**
   ```bash
   # In your spark-submit command or config
   --deploy-mode client   # Driver where you submitted
   --deploy-mode cluster  # Driver on a worker
   ```

2. **Check Spark Master UI:**
   - http://spark-master:8080
   - Find your application
   - Look at "Driver" section for host/port

3. **Check Libra logs:**
   ```bash
   docker logs libra | grep "SparkContext"
   # If you see SparkContext starting, driver is in Libra
   ```

4. **Check worker logs:**
   ```bash
   docker logs spark-worker-1 | grep "Driver"
   # If cluster mode, you'll see driver starting on a worker
   ```

5. **Check network connections:**
   ```bash
   # On Libra container
   netstat -an | grep 7077
   # If connected to :7077, driver is communicating with master
   ```

---

## Conclusion

**When you see "Master + Workers" in Spark deployments:**

- **Master** = Resource manager (part of cluster infrastructure)
- **Workers** = Compute nodes (part of cluster infrastructure)
- **Driver** = Your application coordinator (NOT shown in cluster diagram)

**Where is the driver?**
- **Client mode:** Submit machine (e.g., Libra container)
- **Cluster mode:** One of the worker nodes
- **Local mode:** Same JVM as your application

**In WhereQ Libra:**
- **SHARED mode:** Driver embedded in Libra (always client mode)
- **ISOLATED mode:** Driver in Libra (client) or worker (cluster), depending on config

---

**Related Documentation:**
- [Spark Architecture Deep Dive](SPARK_ARCHITECTURE_DEEP_DIVE.md)
- [Driver Performance Impact](DRIVER_PERFORMANCE_IMPACT.md)
- [Resource Allocation Guide](RESOURCE_ALLOCATION.md)
- [README](../README.md)
