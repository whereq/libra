# WhereQ Libra - Resource Allocation Guide

## Overview

This guide explains how Spark resource allocation works in WhereQ Libra and how to configure driver/executor memory and cores for your jobs.

## Table of Contents

1. [Understanding Spark Resource Allocation](#understanding-spark-resource-allocation)
2. [Global vs Per-Job Configuration](#global-vs-per-job-configuration)
3. [Resource Allocation by Job Type](#resource-allocation-by-job-type)
4. [Configuration Examples](#configuration-examples)
5. [Best Practices](#best-practices)

---

## Understanding Spark Resource Allocation

### Spark Components

```
┌─────────────────────────────────────────────────────────────┐
│                         Spark Application                   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────┐                                           │
│  │   Driver     │  ← Controls execution, schedules tasks    │
│  │              │  ← spark.driver.memory, spark.driver.cores│
│  └──────────────┘                                           │
│         ↓                                                   │
│  ┌──────────────────────────────────────────┐               │
│  │          Cluster Manager                 │               │
│  │   (local[*], YARN, K8s, Standalone)      │               │
│  └──────────────────────────────────────────┘               │
│         ↓                                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │  Executor 1  │  │  Executor 2  │  │  Executor N  │       │
│  │  - Memory    │  │  - Memory    │  │  - Memory    │       │
│  │  - Cores     │  │  - Cores     │  │  - Cores     │       │
│  └──────────────┘  └──────────────┘  └──────────────┘       │
│         ↑                  ↑                  ↑             │
│         │                  │                  │             │
│    spark.executor.memory   │                  │             │
│    spark.executor.cores    │                  │             │
│    spark.executor.instances (# of executors)  │             │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Key Resource Parameters

| Parameter | Description | Example | When to Increase |
|-----------|-------------|---------|------------------|
| **spark.driver.memory** | Memory for driver process | `2g`, `4g`, `8g` | Large result sets, collect(), broadcast variables |
| **spark.driver.cores** | CPU cores for driver | `1`, `2`, `4` | Complex driver computations |
| **spark.executor.memory** | Memory per executor | `2g`, `4g`, `16g` | Large datasets, caching, shuffles |
| **spark.executor.cores** | CPU cores per executor | `2`, `4`, `8` | CPU-intensive transformations |
| **spark.executor.instances** | Number of executors | `2`, `10`, `50` | Parallelism, data volume |
| **spark.dynamicAllocation.enabled** | Auto-scale executors | `true`, `false` | Variable workloads |

---

## Global vs Per-Job Configuration

### Global Configuration (application.yml)

**Location:** `src/main/resources/application.yml`

```yaml
spark:
  app-name: whereq-libra
  master: local[*]  # All available cores
  config:
    spark.driver.memory: 2g
    spark.executor.memory: 2g
    spark.sql.warehouse.dir: /tmp/spark-warehouse
    spark.scheduler.mode: FAIR
```

**When to Use:**
- ✅ Default settings for all jobs
- ✅ Development/testing environments
- ✅ Homogeneous workload patterns

### Per-Job Configuration (API Request)

**When to Use:**
- ✅ Resource-intensive specific jobs
- ✅ Different job requirements
- ✅ Production workloads with varied characteristics

**Supported via `sparkConfig` field in API request**

---

## Resource Allocation by Job Type

### 1. `jar-class` Mode (Intelligent Auto-Switching) 🎯

**How It Works:**
```
Libra automatically chooses execution mode based on sparkConfig:

WITHOUT resource config:          WITH resource config:
┌──────────────────────┐         ┌──────────────────────┐
│  In-JVM Execution    │         │  Auto-switch to      │
│  (Shared Session)    │         │  spark-submit mode   │
│                      │         │  (Dedicated Resources│
│  Fast & Efficient    │         │  │                   │
└──────────────────────┘         └──────────────────────┘
```

**Resource Behavior:**
- ✅ **Smart mode switching** based on sparkConfig
- ✅ **NO resource config** → Uses shared SparkSession (fast)
- ✅ **WITH resource config** → Auto-switches to spark-submit (custom resources)
- ✅ Transparent to the caller
- ℹ️  Logs warning when auto-switching occurs

**When to Use:**
- Want simplicity - Libra decides the best execution method
- Small jobs without sparkConfig → Fast in-JVM
- Large jobs with sparkConfig → Dedicated resources

**Example 1: Fast In-JVM (No Resources Specified)**
```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "jar-class",
    "filePath": "my-app.jar",
    "mainClass": "com.example.MyApp"
  }'
# → Executes in-JVM, uses shared session (2g default)
```

**Example 2: Auto-Switch to spark-submit (Resources Specified)**
```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "jar-class",
    "filePath": "my-app.jar",
    "mainClass": "com.example.MyApp",
    "sparkConfig": {
      "spark.driver.memory": "4g",
      "spark.executor.memory": "8g"
    }
  }'
# → Auto-switches to spark-submit with 4g driver, 8g executors
# → Logs: "Automatically switching to 'jar' mode..."
```

---

### 2. `jar` Mode (spark-submit) ✅

**How It Works:**
```
Libra Process                    Separate JVM Process
├── Receives request             ┌──────────────────────┐
├── Calls spark-submit ────────→ │ Your Spark App       │
└── Monitors execution           │ ├── SparkSession     │
                                 │ ├── Driver           │
                                 │ └── Executors        │
                                 └──────────────────────┘
                                 (NEW resources allocated)
```

**Resource Behavior:**
- ✅ **FULL control over resources per-job**
- ✅ Creates separate JVM with specified resources
- ✅ Complete isolation from other jobs
- ✅ `sparkConfig` fully supported
- ⚠️  Slower startup (JVM + SparkContext initialization)

**When to Use:**
- Large jobs requiring significant resources
- Jobs with different resource profiles
- Complete isolation required
- Production deployments with SLAs

**Example:**
```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "jar",
    "filePath": "my-big-job.jar",
    "mainClass": "com.example.BigDataJob",
    "sparkConfig": {
      "spark.driver.memory": "4g",
      "spark.driver.cores": "2",
      "spark.executor.memory": "8g",
      "spark.executor.cores": "4",
      "spark.executor.instances": "10",
      "spark.dynamicAllocation.enabled": "true"
    }
  }'
```

---

### 3. `python` and `python-file` Modes ✅

**How It Works:**
```
Python jobs execute via spark-submit:
┌─────────────────────────────────┐
│  spark-submit script.py         │
│  - Separate process             │
│  - Full resource control        │
│  - Per-job configuration        │
└─────────────────────────────────┘
```

**Resource Behavior:**
- ✅ **FULL control over resources per-job**
- ✅ Executes via spark-submit (separate process)
- ✅ Supports all resource configuration options
- ✅ Per-job driver/executor memory and cores

**When to Use:**
- Python-based data processing
- ML pipelines with PySpark
- ETL jobs in Python

**Example: Python with Custom Resources**
```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "python-file",
    "filePath": "/path/to/etl_job.py",
    "args": ["2025-11-03", "/data/input"],
    "sparkConfig": {
      "spark.driver.memory": "4g",
      "spark.executor.memory": "8g",
      "spark.executor.cores": "4",
      "spark.executor.instances": "10"
    }
  }'
```

---

### 4. `sql` Mode (In-JVM) ⚠️

**Resource Behavior:**
- ❌ **CANNOT change driver/executor resources**
- ✅ Uses shared SparkSession
- ✅ Can set job-level SQL configs

**Supported Per-Job Configs:**
```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "sql",
    "code": "SELECT * FROM large_table",
    "sparkConfig": {
      "spark.sql.shuffle.partitions": "400",       # ✅ Works
      "spark.sql.adaptive.enabled": "true",        # ✅ Works
      "spark.sql.adaptive.coalescePartitions.enabled": "true",  # ✅ Works
      "spark.executor.memory": "8g"                # ❌ Ignored (shared session)
    }
  }'
```

---

## Configuration Examples

### Example 1: Small ETL Job (jar-class)

**Scenario:** Quick data transformation, small dataset

```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "jar-class",
    "filePath": "etl-small.jar",
    "mainClass": "com.company.etl.SmallETL",
    "args": ["2025-11-03", "/data/input", "/data/output"],
    "pool": "interactive"
  }'
```

**Resource Allocation:**
- Uses global config (2g driver, 2g executor)
- Fast execution (no JVM startup)

---

### Example 2: Large ML Training Job (jar)

**Scenario:** Machine learning model training, 100GB dataset

```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "jar",
    "filePath": "ml-training.jar",
    "mainClass": "com.company.ml.TrainModel",
    "args": ["/data/features", "/models/output"],
    "pool": "high-priority",
    "sparkConfig": {
      "spark.driver.memory": "8g",
      "spark.driver.cores": "4",
      "spark.executor.memory": "16g",
      "spark.executor.cores": "8",
      "spark.executor.instances": "20",
      "spark.sql.adaptive.enabled": "true",
      "spark.sql.adaptive.coalescePartitions.enabled": "true"
    }
  }'
```

**Resource Allocation:**
- Driver: 8GB RAM, 4 CPU cores
- Executors: 20 executors × 16GB × 8 cores = 320GB total
- Adaptive execution enabled for optimization

---

### Example 3: Variable Workload (jar with dynamic allocation)

**Scenario:** Unpredictable data volume, need auto-scaling

```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "jar",
    "filePath": "batch-processor.jar",
    "mainClass": "com.company.batch.Processor",
    "sparkConfig": {
      "spark.driver.memory": "4g",
      "spark.executor.memory": "8g",
      "spark.executor.cores": "4",
      "spark.dynamicAllocation.enabled": "true",
      "spark.dynamicAllocation.minExecutors": "2",
      "spark.dynamicAllocation.maxExecutors": "50",
      "spark.dynamicAllocation.initialExecutors": "5"
    }
  }'
```

**Resource Allocation:**
- Starts with 5 executors
- Scales down to minimum 2 when idle
- Scales up to maximum 50 under load

---

### Example 4: SQL Query with Optimizations

**Scenario:** Complex SQL on large table

```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "sql",
    "code": "SELECT dept, COUNT(*), AVG(salary) FROM employees GROUP BY dept",
    "pool": "default",
    "sparkConfig": {
      "spark.sql.shuffle.partitions": "800",
      "spark.sql.adaptive.enabled": "true",
      "spark.sql.adaptive.coalescePartitions.enabled": "true",
      "spark.sql.adaptive.skewJoin.enabled": "true"
    }
  }'
```

---

## Best Practices

### 1. **Right-Size Your Resources**

**Driver Memory:**
- Start with 2-4GB
- Increase if: collect(), large broadcast variables, OOM errors
- Don't over-allocate (wastes resources)

**Executor Memory:**
- Rule of thumb: `executor_memory = (dataset_size / num_executors) × 1.5`
- Leave 10-20% overhead for Spark internals
- Monitor GC time (should be < 10% of task time)

**Executor Cores:**
- Recommended: 4-8 cores per executor
- More cores → better parallelism
- Too many cores → memory pressure per core

### 2. **Choose the Right Mode**

```
┌─────────────────────┬─────────────┬────────────┬──────────────┬───────────┐
│ Job Characteristic  │ jar-class*  │    jar     │   python     │ Recommend │
├─────────────────────┼─────────────┼────────────┼──────────────┼───────────┤
│ Small dataset       │     Y       │     Y      │     Y        │ jar-class │
│ Large dataset       │     Y**     │     Y      │     Y        │ jar/python│
│ Fast turnaround     │     Y       │     X      │     X        │ jar-class │
│ Resource isolation  │     Y**     │     Y      │     Y        │ jar/python│
│ Custom resources    │     Y**     │     Y      │     Y        │ Any       │
│ Frequent jobs       │     Y       │     X      │     X        │ jar-class │
│ Production SLA      │     Y**     │     Y      │     Y        │ jar/python│
│ Python-based        │     N/A     │     N/A    │     Y        │ python    │
└─────────────────────┴─────────────┴────────────┴──────────── ─┴───────────┘

*  jar-class intelligently switches to spark-submit when resources specified
** jar-class with sparkConfig auto-switches to isolated spark-submit mode
```

### 3. **Monitor and Tune**

**Key Metrics to Watch:**
- Task execution time (should be 100ms - 1s)
- GC time (should be < 10% of task time)
- Shuffle read/write size
- Memory usage per executor
- Number of tasks vs parallelism

**Access Spark UI:**
```bash
# View active job metrics
http://localhost:4040

# Job history (if configured)
http://localhost:18080
```

### 4. **Local[*] Mode Considerations**

In `local[*]` mode (default in Libra):
- Driver and executors run in same JVM
- `spark.executor.memory` controls executor threads
- `spark.driver.memory` controls driver process
- Limited by host machine resources

**Example:**
```yaml
spark:
  master: local[*]  # Uses all CPU cores
  config:
    spark.driver.memory: 4g
    spark.executor.memory: 4g
```

Host with 8 cores, 16GB RAM:
- Total Spark memory: ~8GB (4g driver + 4g executors)
- Parallelism: 8 concurrent tasks

### 5. **Cluster Mode Considerations**

When deploying to YARN, Kubernetes, or Standalone:

```yaml
# application-k8s.yml
spark:
  master: k8s://https://kubernetes.default.svc:443
  config:
    spark.driver.memory: 4g
    spark.executor.memory: 8g
    spark.executor.instances: 10
    spark.kubernetes.namespace: spark-apps
    spark.kubernetes.container.image: my-spark:latest
```

**Per-job override example:**
```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "jar",
    "filePath": "hdfs://namenode:9000/apps/my-job.jar",
    "mainClass": "com.example.MyJob",
    "sparkConfig": {
      "spark.executor.memory": "16g",
      "spark.executor.instances": "50",
      "spark.kubernetes.driver.request.cores": "4",
      "spark.kubernetes.executor.request.cores": "8"
    }
  }'
```

---

## How Spark Allocates Resources

### Allocation Flow

1. **Request Submitted**
   ```
   User → Libra API → SparkSessionService → JarJobExecutor
   ```

2. **spark-submit Command Built**
   ```bash
   spark-submit \
     --master local[*] \
     --driver-memory 4g \
     --driver-cores 2 \
     --executor-memory 8g \
     --executor-cores 4 \
     --num-executors 10 \
     --conf spark.dynamicAllocation.enabled=true \
     --class com.example.MyApp \
     my-app.jar
   ```

3. **Spark Allocates Resources**
   ```
   ┌─────────────────────────────────────────────┐
   │ Cluster Manager (local, YARN, K8s)          │
   ├─────────────────────────────────────────────┤
   │                                             │
   │  1. Reserve driver resources (4g, 2 cores)  │
   │  2. Request executor containers             │
   │     - 10 executors × 8g × 4 cores           │
   │  3. Monitor resource availability           │
   │  4. Scale executors (if dynamic enabled)    │
   │                                             │
   └─────────────────────────────────────────────┘
   ```

4. **Job Executes**
   - Driver schedules tasks based on DAG
   - Executors execute tasks in parallel
   - Shuffle data between executors as needed
   - Resources released when job completes

### Resource Contention

**In SHARED mode (jar-class, sql, python):**
```
Single SparkSession
├── Job 1 (pool: default)     → 40% resources
├── Job 2 (pool: high-priority) → 50% resources
└── Job 3 (pool: low-priority)  → 10% resources
     ↑
     Controlled by fairscheduler.xml
```

**In jar mode:**
```
Job 1: Separate JVM (4g driver, 10×8g executors)
Job 2: Separate JVM (8g driver, 20×16g executors)
Job 3: Separate JVM (2g driver, 5×4g executors)
     ↑
     Each has dedicated resources, no contention
```

---

## Summary

### Quick Reference

| Need | Mode | Configuration Method |
|------|------|---------------------|
| Default resources for all jobs | Any | Global config (application.yml) |
| Per-job custom resources (Java) | `jar` or `jar-class`* | Per-job `sparkConfig` field |
| Per-job custom resources (Python) | `python` or `python-file` | Per-job `sparkConfig` field |
| SQL-specific tuning | `sql` | Per-job `sparkConfig` (limited) |
| Fast execution, shared resources | `jar-class` (no sparkConfig) | Global config only |
| Large isolated jobs | `jar` | Per-job `sparkConfig` |
| Intelligent mode switching | `jar-class` (with sparkConfig) | Auto-switches to spark-submit |

*`jar-class` with `sparkConfig` automatically switches to spark-submit mode

### Key Takeaways

1. ✅ **jar mode** = Full per-job resource control (always spark-submit)
2. ✅ **jar-class mode** = Smart auto-switching (in-JVM or spark-submit)
3. ✅ **python/python-file modes** = Full per-job resource control
4. ⚠️  **sql mode** = Shared resources, limited configs
5. 📊 Monitor Spark UI (port 4040) for tuning insights
6. 🎯 Start conservative, scale up based on metrics
7. 🔄 Use dynamic allocation for variable workloads

---

**Author:** WhereQ Inc.
**Updated:** 2025-11-03
