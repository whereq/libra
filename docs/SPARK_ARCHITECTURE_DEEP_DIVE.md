# Spark Architecture Deep Dive

**Understanding Driver, Master, Workers, and Executors**

---

## Table of Contents

1. [Overview](#overview)
2. [Core Components](#core-components)
3. [Deployment Modes](#deployment-modes)
4. [Component Interactions](#component-interactions)
5. [WhereQ Libra's Role](#whereq-libras-role)
6. [Real-World Examples](#real-world-examples)
7. [Common Misconceptions](#common-misconceptions)

---

## Overview

Apache Spark has a **master-worker architecture** with distinct roles for different components. Understanding these components is crucial for deploying and optimizing Spark applications.

### The Big Picture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Spark Application Lifecycle                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  User Code (SparkSession)                                       │
│         ↓                                                       │
│  ┌──────────────┐                                               │
│  │    DRIVER    │  ← The brain (your application JVM)           │
│  │   Process    │    - Analyzes code                            │
│  └──────┬───────┘    - Creates execution plan                   │
│         │            - Schedules tasks                          │
│         │            - Coordinates executors                    │
│         ↓                                                       │
│  ┌──────────────┐                                               │
│  │ CLUSTER      │  ← Resource manager (Spark Standalone,        │
│  │ MANAGER      │    YARN, Kubernetes, Mesos)                   │
│  │ (Master)     │    - Allocates resources                      │
│  └──────┬───────┘    - Monitors cluster health                  │
│         │                                                       │
│         ↓                                                       │
│  ┌─────────────────────────────────────┐                        │
│  │         WORKER NODES                │                        │
│  │  (Physical/Virtual Machines)        │                        │
│  │                                     │                        │
│  │  ┌─────────────┐  ┌─────────────┐  │                         │
│  │  │  EXECUTOR 1 │  │  EXECUTOR 2 │  │  ← JVM processes        │
│  │  │             │  │             │  │    - Run tasks          │
│  │  │  Task Task  │  │  Task Task  │  │    - Store data         │
│  │  │  Task Task  │  │  Task Task  │  │    - Return results     │
│  │  └─────────────┘  └─────────────┘  │                         │
│  └─────────────────────────────────────┘                        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Core Components

### 1. Driver (The Brain) 🧠

**What is it?**
- The **JVM process** that runs your `main()` method
- Contains the **SparkContext** (or **SparkSession** in Spark 2.0+)
- The **application coordinator** and **task scheduler**

**Key Responsibilities:**
1. **Analyzes your code** and creates a Directed Acyclic Graph (DAG) of stages
2. **Converts transformations** into physical execution plans
3. **Schedules tasks** and sends them to executors
4. **Monitors task execution** and handles failures
5. **Aggregates results** from executors
6. **Manages metadata** (RDD lineage, DataFrame schemas)

**Important Characteristics:**
- **Single Point of Failure**: If driver crashes, the entire application fails
- **Memory-intensive**: Holds execution plan, metadata, and collects results
- **CPU-intensive**: Creates execution plans, schedules tasks
- **Network-intensive**: Communicates with all executors

**Example Code:**
```java
// When you create a SparkSession, you're creating the Driver
SparkSession spark = SparkSession.builder()
    .appName("MyApp")
    .master("spark://master-node:7077")
    .getOrCreate();

// The Driver analyzes this code
Dataset<Row> df = spark.read().parquet("/data/large.parquet");
df.filter(col("age").gt(30))
  .groupBy("city")
  .count()
  .show();  // Driver collects results here

// Driver runs in THIS JVM process
```

**Driver Location Examples:**

| Deployment Mode | Driver Location | Example |
|-----------------|-----------------|---------|
| **client mode** | Your laptop/submit machine | `spark-submit --deploy-mode client` |
| **cluster mode** | Inside the cluster | `spark-submit --deploy-mode cluster` |
| **Libra (embedded)** | Inside Libra's JVM | WhereQ Libra IS the driver |

---

### 2. Master (Cluster Manager) 🎛️

**What is it?**
- The **resource manager** for the cluster
- Responsible for **allocating resources** to applications
- **NOT the same as the Driver** (common misconception!)

**Types of Cluster Managers:**

#### A. Spark Standalone Master
```
┌─────────────────────────────────┐
│  Spark Standalone Master        │
│  (spark://master:7077)          │
│                                 │
│  Responsibilities:              │
│  ✓ Register Worker nodes        │
│  ✓ Track available resources    │
│  ✓ Allocate cores/memory        │
│  ✓ Monitor Worker health        │
│  ✓ Reschedule on failures       │
└─────────────────────────────────┘
```

**Configuration:**
```bash
# Start Master
./sbin/start-master.sh

# Master UI: http://master-node:8080
# Master URL: spark://master-node:7077
```

#### B. YARN ResourceManager
```
┌─────────────────────────────────┐
│  YARN ResourceManager           │
│                                 │
│  Components:                    │
│  ├─ ResourceManager (RM)        │
│  ├─ NodeManagers (NM)           │
│  └─ ApplicationMaster (AM)      │
│                                 │
│  Spark Integration:             │
│  ├─ AM manages Spark executors  │
│  └─ NM runs executor containers │
└─────────────────────────────────┘
```

#### C. Kubernetes Master
```
┌─────────────────────────────────┐
│  Kubernetes API Server          │
│                                 │
│  Spark on K8s:                  │
│  ├─ Driver Pod                  │
│  └─ Executor Pods (dynamic)     │
│                                 │
│  Resource Management:           │
│  ├─ CPU requests/limits         │
│  ├─ Memory requests/limits      │
│  └─ Pod scheduling              │
└─────────────────────────────────┘
```

#### D. Apache Mesos
```
┌─────────────────────────────────┐
│  Mesos Master                   │
│                                 │
│  Two-level scheduling:          │
│  ├─ Mesos offers resources      │
│  └─ Spark accepts/rejects       │
└─────────────────────────────────┘
```

**Key Point:** The Master **allocates resources** but does **NOT execute your code**. The Driver executes code logic.

---

### 3. Worker Nodes (Physical Resources) 🖥️

**What is it?**
- **Physical or virtual machines** in the cluster
- Provide **CPU cores and memory** for executors
- Managed by the **cluster manager**

**Worker Responsibilities:**
1. **Register with Master** on startup
2. **Report available resources** (cores, memory)
3. **Launch executor JVMs** when requested
4. **Monitor executor health** and report to Master
5. **Clean up resources** when executors terminate

**Example Worker Node:**
```
┌─────────────────────────────────────────────────┐
│  Worker Node (Physical Machine)                 │
│  IP: 192.168.1.101                              │
│  Total Resources: 32 cores, 128GB RAM           │
├─────────────────────────────────────────────────┤
│                                                 │
│  ┌──────────────────────────────────┐           │
│  │  Executor 1 (App-1)              │           │
│  │  Allocated: 8 cores, 32GB        │           │
│  │  ┌────────┐ ┌────────┐           │           │
│  │  │ Task 1 │ │ Task 2 │           │           │
│  │  │ Task 3 │ │ Task 4 │           │           │
│  │  └────────┘ └────────┘           │           │
│  └──────────────────────────────────┘           │
│                                                 │
│  ┌──────────────────────────────────┐           │
│  │  Executor 2 (App-2)              │           │
│  │  Allocated: 4 cores, 16GB        │           │
│  │  ┌────────┐ ┌────────┐           │           │
│  │  │ Task 1 │ │ Task 2 │           │           │
│  │  └────────┘ └────────┘           │           │
│  └──────────────────────────────────┘           │
│                                                 │
│  Available: 20 cores, 80GB                      │
│  (Can launch more executors)                    │
└─────────────────────────────────────────────────┘
```

**Worker Configuration:**
```bash
# Start Worker (Spark Standalone)
./sbin/start-worker.sh spark://master:7077

# Worker UI: http://worker-node:8081
# Registers with Master at spark://master:7077
```

**Key Point:** Workers are **containers for executors**. They don't execute tasks directly.

---

### 4. Executors (The Workers) ⚙️

**What is it?**
- **JVM processes** running on worker nodes
- **Execute tasks** sent by the Driver
- **Store data** for the application (RDD partitions, cached DataFrames)

**Executor Lifecycle:**
```
1. Driver requests executors from Master
2. Master allocates resources on Workers
3. Workers launch executor JVMs
4. Executors register with Driver
5. Driver sends tasks to executors
6. Executors run tasks and return results
7. Executors cache data if requested
8. When app finishes, executors terminate
```

**Executor Anatomy:**
```
┌─────────────────────────────────────────────────┐
│  Executor JVM Process                           │
│  (e.g., executor-1 on Worker Node 1)            │
├─────────────────────────────────────────────────┤
│                                                 │
│  ┌──────────────────────────────────┐           │
│  │  Thread Pool                     │           │
│  │  (executor.cores threads)        │           │
│  │                                  │           │
│  │  Thread 1: Running Task 1        │           │
│  │  Thread 2: Running Task 2        │           │
│  │  Thread 3: Running Task 3        │           │
│  │  Thread 4: Running Task 4        │           │
│  └──────────────────────────────────┘           │
│                                                 │
│  ┌──────────────────────────────────┐           │
│  │  Block Manager                   │           │
│  │  (Storage for cached data)       │           │
│  │                                  │           │
│  │  RDD Partition 1: 500MB          │           │
│  │  RDD Partition 5: 300MB          │           │
│  │  Broadcast Var: 100MB            │           │
│  └──────────────────────────────────┘           │
│                                                 │
│  ┌──────────────────────────────────┐           │
│  │  Shuffle Service                 │           │
│  │  (Write/Read shuffle data)       │           │
│  └──────────────────────────────────┘           │
│                                                 │
│  Memory: executor.memory (e.g., 8GB)            │
│  Cores: executor.cores (e.g., 4)                │
└─────────────────────────────────────────────────┘
```

**Executor Configuration:**
```bash
# When submitting an application
spark-submit \
  --executor-memory 8g \        # Memory per executor
  --executor-cores 4 \           # CPU cores per executor
  --num-executors 10 \           # Total number of executors
  --driver-memory 4g \           # Driver memory
  myapp.jar
```

**Key Point:** Executors are **long-lived** (run for entire application) and **multi-threaded** (run multiple tasks concurrently).

---

## Deployment Modes

### 1. Standalone Cluster Mode

```
┌─────────────────────────────────────────────────────────────────┐
│                    Standalone Cluster                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────┐                                           │
│  │  Spark Master    │  spark://master:7077                      │
│  │  (Resource Mgr)  │  UI: http://master:8080                   │
│  └────────┬─────────┘                                           │
│           │                                                     │
│           │ (Manages Workers)                                   │
│           │                                                     │
│  ┌────────┴─────────────────────────────────────┐               │
│  │                                              │               │
│  ↓                        ↓                     ↓               │
│  Worker 1                 Worker 2              Worker 3        │
│  192.168.1.101           192.168.1.102          192.168.1.103   │
│  ┌──────────────┐        ┌──────────────┐      ┌──────────────┐ │
│  │ Executor 1   │        │ Executor 2   │      │ Executor 3   │ │
│  │ 8GB, 4 cores │        │ 8GB, 4 cores │      │ 8GB, 4 cores │ │
│  └──────────────┘        └──────────────┘      └──────────────┘ │
│                                                                 │
│  Total Capacity: 24GB, 12 cores                                 │
└─────────────────────────────────────────────────────────────────┘

┌──────────────────┐
│  Driver          │  (Runs on client machine OR in cluster)
│  (Your App)      │
└────────┬─────────┘
         │
         │ (Communicates with executors)
         │
         └─────────────────────────────────────────→ All Executors
```

**Client Mode:**
```bash
spark-submit \
  --master spark://master:7077 \
  --deploy-mode client \          # Driver runs on submit machine
  --executor-memory 8g \
  --num-executors 3 \
  myapp.jar

# Driver runs on YOUR laptop
# Executors run in the cluster
# If your laptop disconnects, app fails
```

**Cluster Mode:**
```bash
spark-submit \
  --master spark://master:7077 \
  --deploy-mode cluster \         # Driver runs in cluster
  --executor-memory 8g \
  --num-executors 3 \
  myapp.jar

# Driver runs on one of the Worker nodes
# Executors run on Worker nodes
# Submit machine can disconnect
```

---

### 2. YARN Cluster Mode

```
┌─────────────────────────────────────────────────────────────────┐
│                        YARN Cluster                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────┐                                           │
│  │ ResourceManager  │  (YARN Master)                            │
│  │    (RM)          │  - Allocates containers                   │
│  └────────┬─────────┘  - Schedules applications                 │
│           │                                                     │
│           ↓                                                     │
│  ┌─────────────────────────────────────────────────┐            │
│  │  NodeManager 1    NodeManager 2    NodeManager 3│            │
│  │  (Worker Nodes)                                 │            │
│  ├─────────────────────────────────────────────────┤            │
│  │                                                 │            │
│  │  Container 1           Container 2              │            │
│  │  ┌──────────────┐     ┌──────────────┐          │            │
│  │  │ Application  │     │ Spark        │          │            │
│  │  │ Master (AM)  │     │ Executor 1   │          │            │
│  │  │ + Driver     │     │              │          │            │
│  │  └──────────────┘     └──────────────┘          │            │
│  │                                                 │            │
│  │  Container 3           Container 4              │            │
│  │  ┌──────────────┐     ┌──────────────┐          │            │
│  │  │ Spark        │     │ Spark        │          │            │
│  │  │ Executor 2   │     │ Executor 3   │          │            │
│  │  └──────────────┘     └──────────────┘          │            │
│  └─────────────────────────────────────────────────┘            │
└─────────────────────────────────────────────────────────────────┘
```

**Key Differences:**
- **ApplicationMaster (AM)** manages Spark executors
- In **cluster mode**, Driver runs inside AM container
- In **client mode**, Driver runs on submit machine, AM just manages executors

---

### 3. Kubernetes Native Mode

```
┌─────────────────────────────────────────────────────────────────┐
│                    Kubernetes Cluster                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────┐                                           │
│  │  K8s API Server  │  (Master)                                 │
│  │  (Scheduler)     │  - Schedules pods                         │
│  └────────┬─────────┘  - Manages resources                      │
│           │                                                     │
│           ↓                                                     │
│  ┌─────────────────────────────────────────────────┐            │
│  │            Kubernetes Nodes (Workers)           │            │
│  ├─────────────────────────────────────────────────┤            │
│  │                                                 │            │
│  │  ┌───────────────────────────────────┐          │            │
│  │  │  Driver Pod                       │          │            │
│  │  │  spark-driver-xyz                 │          │            │
│  │  │  ┌─────────────────────────────┐  │          │            │
│  │  │  │  Spark Driver Container     │  │          │            │
│  │  │  │  (SparkContext)             │  │          │            │
│  │  │  └─────────────────────────────┘  │          │            │
│  │  └───────────────────────────────────┘          │            │
│  │                                                 │            │
│  │  ┌─────────────────┐  ┌─────────────────┐       │            │
│  │  │ Executor Pod 1  │  │ Executor Pod 2  │       │            │
│  │  │ spark-exec-1    │  │ spark-exec-2    │       │            │
│  │  │ ┌─────────────┐ │  │ ┌─────────────┐ │       │            │
│  │  │ │ Executor    │ │  │ │ Executor    │ │       │            │
│  │  │ │ Container   │ │  │ │ Container   │ │       │            │
│  │  │ └─────────────┘ │  │ └─────────────┘ │       │            │
│  │  └─────────────────┘  └─────────────────┘       │            │
│  │                                                 │            │
│  │  ┌─────────────────┐                            │            │
│  │  │ Executor Pod 3  │  (Dynamic scaling)         │            │
│  │  │ spark-exec-3    │                            │            │
│  │  └─────────────────┘                            │            │
│  └─────────────────────────────────────────────────┘            │
└─────────────────────────────────────────────────────────────────┘
```

**Configuration:**
```bash
spark-submit \
  --master k8s://https://k8s-api-server:6443 \
  --deploy-mode cluster \
  --name spark-pi \
  --conf spark.kubernetes.container.image=my-spark:4.0.1 \
  --conf spark.kubernetes.namespace=spark-apps \
  --conf spark.executor.instances=3 \
  --conf spark.executor.memory=8g \
  --conf spark.executor.cores=4 \
  local:///opt/spark/examples/jars/spark-examples.jar
```

---

## Component Interactions

### Example: Word Count Application

Let's trace a simple word count application through all components:

```java
// User submits this code
SparkSession spark = SparkSession.builder().appName("WordCount").getOrCreate();
Dataset<String> lines = spark.read().textFile("/data/large.txt");
Dataset<Row> words = lines.flatMap(line -> Arrays.asList(line.split(" ")).iterator(), Encoders.STRING());
Dataset<Row> counts = words.groupBy("value").count();
counts.show();
```

#### Step-by-Step Execution:

```
Step 1: Application Submission
┌──────────────┐
│ User submits │  spark-submit --master spark://master:7077 wordcount.jar
│ application  │
└──────┬───────┘
       │
       ↓
Step 2: Driver Starts
┌──────────────────────────────────────────────────┐
│ Driver Process (JVM)                             │
│ - Creates SparkContext                           │
│ - Contacts Master to request resources           │
│ - Requests: 3 executors, 8GB each, 4 cores each  │
└──────┬───────────────────────────────────────────┘
       │
       ↓
Step 3: Master Allocates Resources
┌──────────────────────────────────────────────────┐
│ Spark Master                                     │
│ - Finds available Workers                        │
│ - Allocates: Worker1(1 exec), Worker2(1 exec),   │
│              Worker3(1 exec)                     │
│ - Tells Workers to launch executors              │
└──────┬───────────────────────────────────────────┘
       │
       ↓
Step 4: Workers Launch Executors
┌─────────────────────────────────────────────────────────────┐
│ Worker 1          Worker 2          Worker 3                │
│ └─ Executor 1     └─ Executor 2     └─ Executor 3           │
│    (8GB, 4 cores)    (8GB, 4 cores)    (8GB, 4 cores)       │
│                                                             │
│ Each executor registers with Driver                         │
└──────┬──────────────────────────────────────────────────────┘
       │
       ↓
Step 5: Driver Creates Execution Plan
┌──────────────────────────────────────────────────┐
│ Driver analyzes code and creates DAG:            │
│                                                  │
│ Stage 1: Read file (textFile)                    │
│   └─ Task 1: Read partition 1 → Executor 1       │
│   └─ Task 2: Read partition 2 → Executor 2       │
│   └─ Task 3: Read partition 3 → Executor 3       │
│                                                  │
│ Stage 2: FlatMap + GroupBy (shuffle)             │
│   └─ Task 4: Map partition 1 → Executor 1        │
│   └─ Task 5: Map partition 2 → Executor 2        │
│   └─ Task 6: Map partition 3 → Executor 3        │
│                                                  │
│ Stage 3: Count and collect                       │
│   └─ Task 7: Reduce → Executor 1                 │
│   └─ Task 8: Reduce → Executor 2                 │
└──────┬───────────────────────────────────────────┘
       │
       ↓
Step 6: Driver Sends Tasks to Executors
┌─────────────────────────────────────────────────────────────┐
│ Driver → Executor 1: Run Task 1 (read partition 1)          │
│ Driver → Executor 2: Run Task 2 (read partition 2)          │
│ Driver → Executor 3: Run Task 3 (read partition 3)          │
└──────┬──────────────────────────────────────────────────────┘
       │
       ↓
Step 7: Executors Execute Tasks
┌─────────────────────────────────────────────────────────────┐
│ Executor 1:                                                 │
│   - Reads /data/large.txt partition 1 from HDFS             │
│   - Splits into words                                       │
│   - Stores intermediate results in memory                   │
│                                                             │
│ Executor 2:                                                 │
│   - Reads /data/large.txt partition 2 from HDFS             │
│   - Splits into words                                       │
│   - Stores intermediate results in memory                   │
│                                                             │
│ Executor 3:                                                 │
│   - Reads /data/large.txt partition 3 from HDFS             │
│   - Splits into words                                       │
│   - Stores intermediate results in memory                   │
└──────┬──────────────────────────────────────────────────────┘
       │
       ↓
Step 8: Shuffle Phase
┌─────────────────────────────────────────────────────────────┐
│ Executors write shuffle data to disk:                       │
│                                                             │
│ Executor 1 writes:                                          │
│   - Words starting with A-J → /tmp/shuffle/partition-0      │
│   - Words starting with K-R → /tmp/shuffle/partition-1      │
│   - Words starting with S-Z → /tmp/shuffle/partition-2      │
│                                                             │
│ Executors read shuffle data:                                │
│   - Executor 1 reads all partition-0 files                  │
│   - Executor 2 reads all partition-1 files                  │
│   - Executor 3 reads all partition-2 files                  │
└──────┬──────────────────────────────────────────────────────┘
       │
       ↓
Step 9: Aggregation
┌─────────────────────────────────────────────────────────────┐
│ Executor 1: Counts words A-J                                │
│   - apple: 150                                              │
│   - banana: 75                                              │
│   - ...                                                     │
│                                                             │
│ Executor 2: Counts words K-R                                │
│   - kiwi: 30                                                │
│   - mango: 90                                               │
│   - ...                                                     │
│                                                             │
│ Executor 3: Counts words S-Z                                │
│   - strawberry: 120                                         │
│   - watermelon: 60                                          │
│   - ...                                                     │
└──────┬──────────────────────────────────────────────────────┘
       │
       ↓
Step 10: Collect Results
┌─────────────────────────────────────────────────────────────┐
│ Driver calls counts.show():                                 │
│ - Requests results from all executors                       │
│ - Executor 1 sends results to Driver                        │
│ - Executor 2 sends results to Driver                        │
│ - Executor 3 sends results to Driver                        │
│                                                             │
│ Driver combines and displays:                               │
│ +------------+-----+                                        │
│ |       value|count|                                        │
│ +------------+-----+                                        │
│ |       apple|  150|                                        │
│ |      banana|   75|                                        │
│ |        kiwi|   30|                                        │
│ |       mango|   90|                                        │
│ |  strawberry|  120|                                        │
│ |  watermelon|   60|                                        │
│ +------------+-----+                                        │
└─────────────────────────────────────────────────────────────┘
```

---

## WhereQ Libra's Role

### Libra as the Spark Driver

**Key Understanding:** WhereQ Libra **IS** the Spark Driver, not a proxy or middleware.

```
Traditional Spark Application:
┌──────────────────────────────────────────────────────────┐
│  Your Application JAR                                    │
│  ┌────────────────────────────────────────────┐          │
│  │  main() {                                  │          │
│  │    SparkSession spark = ...                │          │
│  │    Dataset<Row> df = spark.read()...       │  ← Driver│
│  │    df.show()                               │          │
│  │  }                                         │          │
│  └────────────────────────────────────────────┘          │
└──────────────────────────────────────────────────────────┘
                     ↓ communicates with
              ┌──────────────┐
              │  Executors   │
              └──────────────┘


WhereQ Libra Application:
┌──────────────────────────────────────────────────────────┐
│  Libra Spring Boot Application                           │
│  ┌────────────────────────────────────────────┐          │
│  │  SparkSessionService {                     │          │
│  │    SparkSession spark = ...  ← Embedded!   │  ← Driver│
│  │                                            │          │
│  │    executeJob(request) {                   │          │
│  │      spark.read()...                       │          │
│  │      df.show()                             │          │
│  │    }                                       │          │
│  │  }                                         │          │
│  └────────────────────────────────────────────┘          │
│                                                          │
│  ┌────────────────────────────────────────────┐          │
│  │  REST API (Port 8080)                      │          │
│  │  POST /api/v1/sessions/default/statements  │          │
│  └────────────────────────────────────────────┘          │
└──────────────────────────────────────────────────────────┘
                     ↓ communicates with
              ┌──────────────┐
              │  Executors   │
              └──────────────┘
```

### Libra's Architecture with Spark Components

```
┌─────────────────────────────────────────────────────────────────┐
│                        Full Picture                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────────────────────────────────────────┐       │
│  │  WhereQ Libra Container (Driver Process)             │       │
│  │  IP: 192.168.1.100                                   │       │
│  │                                                      │       │
│  │  ┌─────────────────────────────────────────────┐     │       │
│  │  │  Spring Boot Application                    │     │       │
│  │  │                                             │     │       │
│  │  │  REST API (:8080)                           │     │       │
│  │  │    ↓                                        │     │       │
│  │  │  SparkSessionService                        │     │       │
│  │  │    ↓                                        │     │       │
│  │  │  SparkSession (Driver)                      │     │       │
│  │  │    - Task Scheduler                         │     │       │
│  │  │    - DAG Scheduler                          │     │       │
│  │  │    - BlockManager                           │     │       │
│  │  │    - Executor Coordinator                   │     │       │
│  │  └─────────────────────────────────────────────┘     │       │
│  │                                                      │       │
│  │  Memory: 4GB (driver.memory)                         │       │
│  │  Port: 8080 (REST), 4040 (Spark UI)                  │       │
│  └──────────────────┬───────────────────────────────────┘       │
│                     │                                           │
│                     │ (Connects to Spark Cluster)               │
│                     ↓                                           │
│  ┌──────────────────────────────────────────────────────┐       │
│  │  Spark Standalone Master                             │       │
│  │  spark://spark-master:7077                           │       │
│  └──────────────────┬───────────────────────────────────┘       │
│                     │                                           │
│                     │ (Manages Workers)                         │
│                     ↓                                           │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                    Worker Nodes                         │    │
│  │                                                         │    │
│  │  Worker 1          Worker 2          Worker 3           │    │
│  │  ┌──────────┐      ┌──────────┐      ┌──────────┐       │    │
│  │  │Executor 1│      │Executor 2│      │Executor 3│       │    │
│  │  │8GB,4cores│      │8GB,4cores│      │8GB,4cores│       │    │
│  │  └──────────┘      └──────────┘      └──────────┘       │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Libra's Two Execution Modes

#### Mode 1: SHARED (Single Driver, Multiple Jobs)
```
┌─────────────────────────────────────────────────────────────────┐
│  Libra Process (Single Driver)                                  │
│                                                                 │
│  SparkSession (shared)                                          │
│    └─ FAIR Scheduler                                            │
│       ├─ Pool: default                                          │
│       │  └─ Job 1 (SQL query) → Tasks 1-10                      │
│       │                                                         │
│       ├─ Pool: high-priority                                    │
│       │  └─ Job 2 (ETL) → Tasks 11-20                           │
│       │                                                         │
│       └─ Pool: low-priority                                     │
│          └─ Job 3 (ML training) → Tasks 21-50                   │
│                                                                 │
│  All jobs share same SparkSession and executors                 │
│  Resources: 2GB driver, 2GB × 3 executors (global config)       │
└─────────────────────────────────────────────────────────────────┘
        ↓ All jobs share these executors
┌─────────────────────────────────────────────────────────────────┐
│  Executor 1       Executor 2       Executor 3                   │
│  Task 1,11,21    Task 2,12,22     Task 3,13,23                  │
│  (interleaved)   (interleaved)    (interleaved)                 │
└─────────────────────────────────────────────────────────────────┘
```

#### Mode 2: ISOLATED (Multiple Drivers via spark-submit)
```
┌─────────────────────────────────────────────────────────────────┐
│  Libra Process (REST API)                                       │
│                                                                 │
│  Request 1: jar-class with sparkConfig                          │
│    └─ Launches: spark-submit (separate JVM)                     │
│       └─ Driver 1 (dedicated 8GB)                               │
│          └─ Executors: 16GB × 10                                │
│             └─ Job 1 tasks                                      │
│                                                                 │
│  Request 2: python-file with sparkConfig                        │
│    └─ Launches: spark-submit (separate JVM)                     │
│       └─ Driver 2 (dedicated 4GB)                               │
│          └─ Executors: 8GB × 5                                  │
│             └─ Job 2 tasks                                      │
│                                                                 │
│  Each job has isolated resources                                │
└─────────────────────────────────────────────────────────────────┘
```

### How Libra Differs from Apache Livy

```
Apache Livy (Proxy Model):
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│  Client      │  HTTP │  Livy Server │       │  Spark       │
│  (curl/SDK)  │──────→│  (Proxy)     │──────→│  Driver      │
└──────────────┘       └──────────────┘       └──────┬───────┘
                                                     ↓
                                               ┌──────────────┐
                                               │  Executors   │
                                               └──────────────┘

WhereQ Libra (Embedded Driver Model):
┌──────────────┐       ┌────────────────────────────┐
│  Client      │  HTTP │  Libra (Driver Embedded)   │
│  (curl/SDK)  │──────→│  Spring Boot + SparkSession│
└──────────────┘       └──────────┬─────────────────┘
                                  ↓
                           ┌──────────────┐
                           │  Executors   │
                           └──────────────┘

Key Difference: Libra eliminates the proxy layer - it IS the driver!
```

---

## Real-World Examples

### Example 1: E-Commerce Analytics Platform

**Scenario:** Real-time product analytics with 1000 concurrent users

```
┌─────────────────────────────────────────────────────────────────┐
│  Infrastructure Setup                                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Master Node (Spark Master):                                    │
│  - EC2 m5.xlarge (4 vCPU, 16GB RAM)                             │
│  - Coordinates cluster resources                                │
│                                                                 │
│  Driver Node (WhereQ Libra):                                    │
│  - EC2 m5.2xlarge (8 vCPU, 32GB RAM)                            │
│  - Runs Libra Spring Boot app                                   │
│  - Driver memory: 16GB                                          │
│  - Handles 1000 concurrent REST API requests                    │
│                                                                 │
│  Worker Nodes (10 nodes):                                       │
│  - EC2 r5.4xlarge (16 vCPU, 128GB RAM) × 10                     │
│  - Each runs 4 executors (4GB each)                             │
│  - Total: 40 executors, 160GB executor memory                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

API Request Flow:
┌──────────────┐
│ User submits │ POST /api/v1/sessions/default/statements
│ SQL query    │ {
└──────┬───────┘   "kind": "sql",
       │           "code": "SELECT product, SUM(sales) FROM orders
       │                     WHERE date = '2025-11-03'
       ↓                     GROUP BY product",
┌──────────────────────────┐  "pool": "interactive"
│ Libra REST Controller    │}
└──────┬───────────────────┘
       │
       ↓
┌────────────────────────────────────────────────────────────┐
│ SparkSessionService (Driver)                               │
│                                                            │
│ 1. Receives SQL query                                      │
│ 2. Creates execution plan (DAG):                           │
│    - Stage 1: Scan Parquet files (200 partitions)          │
│    - Stage 2: Filter by date                               │
│    - Stage 3: Group by product (shuffle)                   │
│    - Stage 4: Aggregate sums                               │
│                                                            │
│ 3. Schedules 200 tasks across 40 executors                 │
│    - Each executor runs 5 tasks concurrently               │
└────────┬───────────────────────────────────────────────────┘
         │
         ↓
┌─────────────────────────────────────────────────────────────┐
│ Executors (40 total across 10 workers)                      │
│                                                             │
│ Executor 1-4 (Worker 1):  Read partitions 1-20              │
│ Executor 5-8 (Worker 2):  Read partitions 21-40             │
│ Executor 9-12 (Worker 3): Read partitions 41-60             │
│ ...                                                         │
│ Executor 37-40 (Worker 10): Read partitions 181-200         │
│                                                             │
│ Shuffle: Re-partition by product                            │
│                                                             │
│ Final Aggregation:                                          │
│ Executor 1: Products A-D → {iPhone: 1500, iPad: 800}        │
│ Executor 2: Products E-M → {MacBook: 450, AirPods: 2000}    │
│ Executor 3: Products N-Z → {Watch: 1200}                    │
└────────┬────────────────────────────────────────────────────┘
         │
         ↓
┌────────────────────────────────────────────────────────────┐
│ Driver collects results                                    │
│ Returns to user via REST API:                              │
│ {                                                          │
│   "results": [                                             │
│     {"product": "iPhone", "total_sales": 1500},            │
│     {"product": "iPad", "total_sales": 800},               │
│     {"product": "MacBook", "total_sales": 450},            │
│     ...                                                    │
│   ]                                                        │
│ }                                                          │
└────────────────────────────────────────────────────────────┘

Performance:
- Query execution: 8 seconds
- 200 partitions processed in parallel
- 40 executors × 5 tasks = 200 concurrent tasks
- Total data scanned: 500GB
- Throughput: 62.5 GB/second
```

### Example 2: Machine Learning Pipeline

**Scenario:** Train recommendation model on 10TB dataset

```
API Request:
POST /api/v1/sessions/default/statements
{
  "kind": "python-file",
  "filePath": "/apps/ml/train_recommender.py",
  "args": ["/data/user_behavior_10tb.parquet", "/models/output"],
  "sparkConfig": {
    "spark.driver.memory": "32g",
    "spark.driver.cores": "16",
    "spark.executor.memory": "64g",
    "spark.executor.cores": "16",
    "spark.executor.instances": "100",
    "spark.dynamicAllocation.enabled": "false"
  }
}

Libra's Execution:
┌────────────────────────────────────────────────────────────────┐
│ Libra detects sparkConfig and launches spark-submit            │
│                                                                │
│ spark-submit \                                                 │
│   --master spark://master:7077 \                               │
│   --deploy-mode cluster \                                      │
│   --driver-memory 32g \                                        │
│   --driver-cores 16 \                                          │
│   --executor-memory 64g \                                      │
│   --executor-cores 16 \                                        │
│   --num-executors 100 \                                        │
│   /apps/ml/train_recommender.py \                              │
│   /data/user_behavior_10tb.parquet /models/output              │
└────────────────────────────────────────────────────────────────┘

Cluster Allocation:
┌────────────────────────────────────────────────────────────────┐
│ Master receives request:                                       │
│ - Needs: 100 executors × 64GB = 6.4TB executor memory          │
│ - Needs: 100 executors × 16 cores = 1600 cores                 │
│                                                                │
│ Master allocates across 50 worker nodes:                       │
│ - Each worker: 2 executors (128GB, 32 cores per worker)        │
│                                                                │
│ Total Resources:                                               │
│ - Driver: 32GB, 16 cores                                       │
│ - Executors: 6.4TB, 1600 cores                                 │
│ - Total: 6.432TB, 1616 cores                                   │
└────────────────────────────────────────────────────────────────┘

Execution Flow:
┌────────────────────────────────────────────────────────────────┐
│ Python Script: train_recommender.py                            │
│                                                                │
│ # Stage 1: Load Data (10TB Parquet)                            │
│ df = spark.read.parquet("/data/user_behavior_10tb.parquet")    │
│   → 10,000 partitions (1GB each)                               │
│   → 100 executors × 100 partitions each                        │
│   → Each executor loads 100GB                                  │
│                                                                │
│ # Stage 2: Feature Engineering                                 │
│ features = df.groupBy("user_id").agg(...)                      │
│   → Shuffle (group by user_id)                                 │
│   → 100 billion user events → 500 million users                │
│   → Each executor processes 5 million users                    │
│                                                                │
│ # Stage 3: Train Model (ALS)                                   │
│ model = ALS(rank=50, maxIter=10).fit(features)                 │
│   → Iterative algorithm (10 iterations)                        │
│   → Each iteration: 100 executors × 16 cores = 1600 parallel   │
│   → Total training time: 4 hours                               │
│                                                                │
│ # Stage 4: Save Model                                          │
│ model.save("/models/output")                                   │
│   → Model size: 50GB                                           │
│   → Saved to distributed storage (HDFS/S3)                     │
└────────────────────────────────────────────────────────────────┘

Cost Analysis (AWS):
- 50 workers: r5.8xlarge ($2.016/hour × 50) = $100.80/hour
- Training time: 4 hours
- Total cost: $403.20 per training run
```

### Example 3: Real-Time Stream Processing

**Scenario:** Process 1 million events/second from Kafka

```
Infrastructure:
┌────────────────────────────────────────────────────────────────┐
│ Kafka Cluster (Event Source)                                   │
│ - Topic: user_events                                           │
│ - Partitions: 100                                              │
│ - Throughput: 1M events/second                                 │
│ - Event size: 1KB average                                      │
│ - Total: 1GB/second                                            │
└────────┬───────────────────────────────────────────────────────┘
         │
         ↓
┌────────────────────────────────────────────────────────────────┐
│ WhereQ Libra (Driver)                                          │
│                                                                │
│ Long-running Spark Streaming application:                      │
│                                                                │
│ val stream = spark                                             │
│   .readStream                                                  │
│   .format("kafka")                                             │
│   .option("kafka.bootstrap.servers", "kafka:9092")             │
│   .option("subscribe", "user_events")                          │
│   .load()                                                      │
│                                                                │
│ stream                                                         │
│   .selectExpr("CAST(value AS STRING)")                         │
│   .groupBy(window($"timestamp", "1 minute"), $"event_type")    │
│   .count()                                                     │
│   .writeStream                                                 │
│   .outputMode("update")                                        │
│   .format("console")                                           │
│   .start()                                                     │
│   .awaitTermination()                                          │
└────────┬───────────────────────────────────────────────────────┘
         │
         ↓
┌────────────────────────────────────────────────────────────────┐
│ 20 Executors (Continuous Processing)                           │
│                                                                │
│ Each executor:                                                 │
│ - Reads from 5 Kafka partitions                                │
│ - Processes ~50,000 events/second                              │
│ - Memory: 16GB (for windowed aggregations)                     │
│ - Cores: 8 (parallel task processing)                          │
│                                                                │
│ Micro-batch every 1 second:                                    │
│ - Executor 1: Partitions 0-4   → 250K events                   │
│ - Executor 2: Partitions 5-9   → 250K events                   │
│ - Executor 3: Partitions 10-14 → 250K events                   │
│ - ...                                                          │
│ - Executor 20: Partitions 95-99 → 250K events                  │
│                                                                │
│ Total: 1M events/second processed                              │
└────────────────────────────────────────────────────────────────┘

Driver's Role:
┌────────────────────────────────────────────────────────────────┐
│ Every micro-batch (1 second):                                  │
│                                                                │
│ 1. Driver receives Kafka offsets                               │
│    - Partition 0: offset 1000-1050                             │
│    - Partition 1: offset 2000-2050                             │
│    - ... (100 partitions)                                      │
│                                                                │
│ 2. Driver creates 100 tasks (1 per Kafka partition)            │
│    - Task 1: Read partition 0, offsets 1000-1050               │
│    - Task 2: Read partition 1, offsets 2000-2050               │
│    - ... assign to 20 executors                                │
│                                                                │
│ 3. Executors process in parallel:                              │
│    - Parse JSON events                                         │
│    - Apply windowed aggregation (1-minute tumbling windows)    │
│    - Update state store                                        │
│                                                                │
│ 4. Driver collects results and outputs:                        │
│    - Window [10:00:00 - 10:01:00]:                             │
│      - click: 30,000                                           │
│      - view: 50,000                                            │
│      - purchase: 2,000                                         │
│                                                                │
│ 5. Driver commits Kafka offsets                                │
│    - Ensures exactly-once processing                           │
│                                                                │
│ Repeat every second...                                         │
└────────────────────────────────────────────────────────────────┘
```

---

## Common Misconceptions

### ❌ Misconception 1: "Master executes tasks"

**Wrong:** The Master (cluster manager) executes Spark tasks.

**Correct:** The Master only **allocates resources**. Executors execute tasks.

```
❌ WRONG:
Master → Runs your map/reduce logic

✓ CORRECT:
Master → Allocates executors
Executors → Run your map/reduce logic
```

---

### ❌ Misconception 2: "Driver is the Master"

**Wrong:** Driver and Master are the same thing.

**Correct:** Driver and Master are **separate components** with different roles.

```
Master (Cluster Manager):
- Manages cluster resources
- Allocates executors to applications
- Monitors worker health
- One per cluster

Driver (Application Coordinator):
- Runs your application logic
- Schedules tasks
- Collects results
- One per application
```

---

### ❌ Misconception 3: "Workers execute tasks"

**Wrong:** Worker nodes directly execute Spark tasks.

**Correct:** Workers **launch executors**, and executors execute tasks.

```
Worker Node (Physical Machine):
├─ Executor 1 (JVM process)
│  └─ Task 1, Task 2, Task 3  ← These execute your code
├─ Executor 2 (JVM process)
│  └─ Task 4, Task 5, Task 6  ← These execute your code
└─ OS, Network, Storage
```

---

### ❌ Misconception 4: "More executors = better performance"

**Wrong:** Always max out number of executors.

**Correct:** Balance executors with cores and memory.

```
Bad Configuration:
--num-executors 1000
--executor-cores 1
--executor-memory 1g

Result:
- 1000 tiny executors
- High overhead (1000 JVMs)
- Poor data locality
- Excessive network shuffling

Good Configuration:
--num-executors 50
--executor-cores 8
--executor-memory 16g

Result:
- 50 well-sized executors (400 cores total)
- Lower overhead
- Better data locality
- Efficient shuffling
```

**Rule of Thumb:**
- **Executor cores:** 4-8 cores per executor (sweet spot: 5)
- **Executor memory:** 8-64GB per executor
- **Total executors:** Depends on data size and cluster capacity

---

### ❌ Misconception 5: "Libra is a proxy like Livy"

**Wrong:** Libra forwards requests to a separate Spark driver.

**Correct:** Libra **IS** the Spark driver (embedded model).

```
Apache Livy (Proxy):
Client → Livy Server → Spark Driver → Executors
         (Proxy)       (Separate JVM)

WhereQ Libra (Embedded):
Client → Libra (Driver + API) → Executors
         (Single JVM)

Benefits of Embedded Model:
✓ Lower latency (no proxy overhead)
✓ Simplified architecture
✓ Direct SparkSession access
✓ Better resource utilization
```

---

## Summary Table

| Component | Type | Runs Where | Key Responsibility | Lifespan |
|-----------|------|------------|-------------------|----------|
| **Driver** | JVM Process | Client machine OR cluster node | Analyzes code, schedules tasks, collects results | Per application |
| **Master** | Service | Cluster master node | Allocates resources, monitors cluster | Cluster lifetime |
| **Worker** | Service | Cluster worker nodes | Launches executors, reports resources | Cluster lifetime |
| **Executor** | JVM Process | Worker nodes | Executes tasks, stores data | Per application |

### Key Relationships:

```
┌─────────────────────────────────────────────────────────────┐
│                    Hierarchical View                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Cluster (Infrastructure)                                   │
│  ├─ Master (Resource Manager)                               │
│  │  └─ Manages workers                                      │
│  │                                                          │
│  └─ Workers (Physical Machines)                             │
│     └─ Launch executors                                     │
│                                                             │
│  Application (Your Code)                                    │
│  ├─ Driver (Coordinator)                                    │
│  │  └─ Creates tasks, collects results                      │
│  │                                                          │
│  └─ Executors (Compute)                                     │
│     └─ Run tasks, store data                                │
│                                                             │
│  Relationship:                                              │
│  Driver requests resources from Master                      │
│  Master allocates executors on Workers                      │
│  Driver sends tasks to Executors                            │
│  Executors execute tasks and return results to Driver       │
└─────────────────────────────────────────────────────────────┘
```

---

## WhereQ Libra Quick Reference

### When Libra is the Driver (SHARED Mode):

```
┌───────────────────────────────────┐
│ Libra Container                   │
│ ┌───────────────────────────────┐ │
│ │ SparkSession (Driver)         │ │
│ │ ├─ Job 1 (sql)                │ │
│ │ ├─ Job 2 (python code)        │ │
│ │ └─ Job 3 (jar-class, no cfg)  │ │
│ └───────────────────────────────┘ │
│                                   │
│ All jobs share:                   │
│ - Same SparkSession               │
│ - Same executors                  │
│ - Global resource config          │
└───────────────────────────────────┘
```

### When Libra Launches Separate Drivers (ISOLATED Mode):

```
┌───────────────────────────────────┐
│ Libra Container (REST API)        │
│                                   │
│ Launches spark-submit:            │
│ ├─ Driver 1 (jar with cfg)        │
│ │  └─ Dedicated executors         │
│ │                                 │
│ ├─ Driver 2 (python-file)         │
│ │  └─ Dedicated executors         │
│ │                                 │
│ └─ Driver 3 (jar-class + cfg)     │
│    └─ Dedicated executors         │
│                                   │
│ Each job isolated with custom     │
│ resource configurations           │
└───────────────────────────────────┘
```

---

**End of Document**

For more information:
- [Apache Spark Architecture Documentation](https://spark.apache.org/docs/latest/cluster-overview.html)
- [WhereQ Libra README](../README.md)
- [Where Is The Driver?](WHERE_IS_THE_DRIVER.md)
- [Driver Performance Impact](DRIVER_PERFORMANCE_IMPACT.md)
- [Resource Allocation Guide](RESOURCE_ALLOCATION.md)
