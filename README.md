```
____    __    ____  __    __   _______ .______       _______   ______
\   \  /  \  /   / |  |  |  | |   ____||   _  \     |   ____| /  __  \
 \   \/    \/   /  |  |__|  | |  |__   |  |_)  |    |  |__   |  |  |  |
  \            /   |   __   | |   __|  |      /     |   __|  |  |  |  |
   \    /\    /    |  |  |  | |  |____ |  |\  \----.|  |____ |  `--'  '--.
    \__/  \__/     |__|  |__| |_______|| _| `._____||_______| \_____\_____\
```

```
 __       __  .______   .______          ___
|  |     |  | |   _  \  |   _  \        /   \
|  |     |  | |  |_)  | |  |_)  |      /  ^  \
|  |     |  | |   _  <  |      /      /  /_\  \
|  `----.|  | |  |_)  | |  |\  \----./  _____  \
|_______||__| |______/  | _| `._____/__/     \__\

```
```
        __
   ___.'  '.___   
   ____________
```

# WhereQ Libra

**Modern REST Interface for Apache Spark - Enterprise Ready**

Libra is an open-source project initialized and developed by **WhereQ**, designed as a better and more advanced alternative to Apache Livy. Acting as a Spark Facade, this service provides easy interaction with Spark through a RESTful API, enabling remote job submission, parallel execution, and intelligent resource management.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://openjdk.org/projects/jdk/21/)
[![Spark](https://img.shields.io/badge/Spark-4.0.1-orange.svg)](https://spark.apache.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.7-brightgreen.svg)](https://spring.io/projects/spring-boot)

---

## 🚀 Why Libra?

Apache Livy's latest update was two years ago, and it only supports up to Scala 2.12. However, **Spark 4 and above will only work with Scala 2.13**, creating a significant compatibility gap. WhereQ built this open source project to make big data platform developers' lives easier by providing a modern, actively maintained solution that keeps pace with the latest Spark ecosystem.

**WhereQ is committed to continuously evolving Libra to follow the latest tech stack**, ensuring compatibility with current and future versions of Apache Spark while providing enhanced features and better developer experience.

---

## ✨ Key Features

### 🎯 Core Capabilities

- **Future-Proof Compatibility**: Full support for Apache Spark 4.x+ with Scala 2.13+
- **RESTful API**: Complete REST interface for Spark job submission and management
- **Multiple Execution Modes**: Java JAR, Python, SQL - all via unified API
- **Enterprise Ready**: Built on Red Hat UBI9, OpenShift compatible
- **Modern Stack**: Java 21, Spring Boot 3.5.7, Maven 3.9+

### ⚡ Advanced Features

#### 1. **Parallel Job Execution with FAIR Scheduler**

True concurrent job execution with intelligent resource allocation:

- **SHARED Mode**: Single SparkSession with resource pools for efficient multi-tenancy
- **ISOLATED Mode**: Multiple SparkSessions for complete job isolation
- **FAIR Scheduler**: Configurable resource pools (default, high-priority, low-priority, interactive)
- **Dynamic Resource Allocation**: Per-job memory and CPU configuration

```yaml
libra:
  session:
    mode: SHARED  # or ISOLATED
    max-sessions: 10
    timeout-minutes: 30
```

#### 2. **Per-Job Resource Configuration**

Specify driver/executor resources for each job independently:

```json
{
  "kind": "jar",
  "filePath": "my-app.jar",
  "mainClass": "com.example.MyApp",
  "sparkConfig": {
    "spark.driver.memory": "8g",
    "spark.driver.cores": "4",
    "spark.executor.memory": "16g",
    "spark.executor.cores": "8",
    "spark.executor.instances": "20"
  }
}
```

**Benefits:**
- Small jobs don't waste resources
- Large jobs get what they need
- No restarts required for different resource profiles
- Optimal cluster utilization

#### 3. **Multiple Execution Modes**

| Mode | Execution Method | Resource Control | Use Case |
|------|-----------------|------------------|----------|
| **jar** | spark-submit (separate process) | ✅ Full per-job control | Production Java jobs |
| **jar-class** | In-JVM with intelligent switching | ✅ Auto-switches based on config | Fast Java execution |
| **python** | spark-submit | ✅ Full per-job control | Inline Python code |
| **python-file** | spark-submit | ✅ Full per-job control | Python script files |
| **sql** | In-JVM (shared session) | ⚠️ SQL configs only | Quick SQL queries |

##### jar-class: Intelligent Mode Switching 🎯

Automatically chooses the optimal execution method:

**Without sparkConfig** → Fast in-JVM execution (shared resources)
**With sparkConfig** → Auto-switches to spark-submit (dedicated resources)

```bash
# Fast in-JVM (no resources specified)
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -d '{
    "kind": "jar-class",
    "filePath": "small-job.jar",
    "mainClass": "com.example.SmallJob"
  }'

# Auto-switches to spark-submit (custom resources)
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -d '{
    "kind": "jar-class",
    "filePath": "large-ml-job.jar",
    "mainClass": "com.example.MLTraining",
    "sparkConfig": {
      "spark.executor.memory": "16g",
      "spark.executor.instances": "50"
    }
  }'
```

#### 4. **Python Script Execution**

Execute Python/PySpark code via REST API:

```bash
# Execute Python file with custom resources
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "python-file",
    "filePath": "/path/to/etl_pipeline.py",
    "args": ["2025-11-03", "/data/input"],
    "sparkConfig": {
      "spark.driver.memory": "4g",
      "spark.executor.memory": "8g",
      "spark.executor.cores": "4"
    }
  }'

# Execute inline Python code
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -d '{
    "kind": "python",
    "code": "from pyspark.sql import SparkSession\ndf = spark.range(100)\nprint(df.count())"
  }'
```

#### 5. **Docker & Kubernetes Ready**

- **Base Image**: Red Hat UBI9 minimal (enterprise-grade, security-focused)
- **Multi-stage Build**: Optimized image size
- **OpenShift Compatible**: Proper permissions and security context
- **Health Checks**: Built-in liveness and readiness probes
- **Monitoring**: Actuator endpoints with Prometheus support

---

## 🏗️ Architecture Overview

### Libra as Spark Driver

**Critical Understanding:** Libra is not a proxy or gateway - it **IS** the Spark driver program.

```
┌──────────────────────────────────────────────────────────┐
│                      Client Layer                        │
│  Web Browsers, CLI, Scripts, CI/CD Pipelines             │
└────────────────────────┬─────────────────────────────────┘
                         │ HTTP/REST
                         ▼
┌──────────────────────────────────────────────────────────┐
│                    Libra REST API                        │
│               (Spring Boot Application)                  │
│  ┌────────────────────────────────────────────────────┐  │
│  │  SparkSession (Driver Running in Libra's JVM)      │  │
│  │  - Task Scheduling                                 │  │
│  │  - Job Coordination                                │  │
│  │  - Result Collection                               │  │
│  │  - Resource Management                             │  │
│  └────────────────────────────────────────────────────┘  │
└────────────────────────┬─────────────────────────────────┘
                         │ RPC/Network
                         ▼
┌──────────────────────────────────────────────────────────┐
│              Spark Cluster / Executors                   │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │
│  │ Executor 1  │  │ Executor 2  │  │ Executor N  │       │
│  │ - Tasks     │  │ - Tasks     │  │ - Tasks     │       │
│  │ - Data      │  │ - Data      │  │ - Data      │       │
│  │ - Cache     │  │ - Cache     │  │ - Cache     │       │
│  └─────────────┘  └─────────────┘  └─────────────┘       │
└──────────────────────────────────────────────────────────┘
         ▲                                      │
         │      Bidirectional Communication     │
         └──────────────────────────────────────┘
    (Shuffle, Broadcast, Results, Heartbeats)
```

### Resource Allocation Strategies

#### SHARED Mode (Recommended for Most Use Cases)

```
┌─────────────────────────────────────────────────────────┐
│              Single SparkSession (Libra JVM)            │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │         FAIR Scheduler (Resource Pools)          │   │
│  ├──────────────────────────────────────────────────┤   │
│  │                                                  │   │
│  │  Pool: default (40% resources)                   │   │
│  │    └─ Job A ─┐                                   │   │
│  │               ├─ Executor 1, 2, 3                │   │
│  │    └─ Job B ─┘                                   │   │
│  │                                                  │   │
│  │  Pool: high-priority (50% resources)             │   │
│  │    └─ Job C ─┬─ Executor 4, 5, 6, 7, 8           │   │
│  │                                                  │   │
│  │  Pool: interactive (10% resources)               │   │
│  │    └─ Job D ─┴─ Executor 9                       │   │
│  │                                                  │   │
│  └──────────────────────────────────────────────────┘   │
│                                                         │
│  Benefits:                                              │
│   Fast job startup (no SparkContext creation)           │
│   Efficient resource sharing                            │
│   Lower overhead                                        │
│   Better for many small/medium jobs                     │
└─────────────────────────────────────────────────────────┘
```

#### ISOLATED Mode (For Complete Job Isolation)

```
┌─────────────────────────────────────────────────────────┐
│                  Multiple SparkSessions                 │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  Session 1 (User A)          Session 2 (User B)         │
│  ┌────────────────┐          ┌────────────────┐         │
│  │ SparkContext   │          │ SparkContext   │         │
│  │ Executor 1,2,3 │          │ Executor 4,5,6 │         │
│  └────────────────┘          └────────────────┘         │
│                                                         │
│  Session 3 (User C)                                     │
│  ┌────────────────┐                                     │
│  │ SparkContext   │                                     │
│  │ Executor 7,8,9 │                                     │
│  └────────────────┘                                     │
│                                                         │
│  Benefits:                                              │
│   Complete isolation between users/jobs                 │
│   Different Spark configs per session                   │
│   Failuresdon't affect other sessions                   │
│   Better for multi-tenant environments                  │
└─────────────────────────────────────────────────────────┘
```

---

## 🚀 Deployment Models - In Depth

### Deployment Model Comparison

| Aspect | Local Dev | Docker Compose | K8s Standalone | K8s Native |
|--------|-----------|----------------|----------------|------------|
| **Complexity** | ⭐ | ⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| **Setup Time** | 5 min | 10 min | 30 min | 45 min |
| **Resource Efficiency** | High | Medium | Low (baseline cost) | Very High |
| **Job Isolation** | Shared | Shared | Shared | Complete |
| **Startup Latency** | <1s | <1s | <1s | 10-30s |
| **Scalability** | Limited | Limited | High | Very High |
| **Cost Model** | Dev only | Fixed | Fixed | Pay-per-job |
| **Best For** | Development | Small prod | Multi-user prod | On-demand jobs |
| **HA Support** | ❌ | ❌ | ✅ | ✅ |
| **Auto-scaling** | ❌ | ❌ | ⚠️ Manual | ✅ Automatic |

---

### 1. Local Development Mode

**Architecture:**
```
┌─────────────────────────────────────┐
│     Developer Laptop/Workstation    │
│                                     │
│  ┌────────────────────────────────┐ │
│  │  Libra Container               │ │
│  │  ├─ SparkSession (Driver)      │ │
│  │  └─ Local Executors (Threads)  │ │
│  │                                │ │
│  │  Ports:                        │ │
│  │  - 8080: REST API              │ │
│  │  - 4040: Spark UI              │ │
│  │  - 5005: Debug Port            │ │
│  └────────────────────────────────┘ │
│                                     │
│  Data: Local filesystem/volumes     │
└─────────────────────────────────────┘
```

**Configuration:**
```yaml
# docker-compose.local.yml
services:
  libra:
    image: whereq-libra:latest
    environment:
      - SPRING_PROFILES_ACTIVE=local
    ports:
      - "8080:8080"  # REST API
      - "4040:4040"  # Spark UI
      - "5005:5005"  # Debug
```

**Spark Configuration:**
```yaml
# application-local.yml
spark:
  master: local[*]  # Use all CPU cores
  config:
    spark.driver.memory: 2g
    spark.executor.memory: 2g
```

**Start Command:**
```bash
docker-compose -f docker-compose.local.yml up --build
```

**When to Use:**
- ✅ Feature development
- ✅ Unit testing
- ✅ API testing
- ✅ Debugging
- ✅ Quick prototyping

**Limitations:**
- ❌ Single machine resources
- ❌ No distributed execution
- ❌ Not production-ready

---

### 2. Docker Compose with Standalone Spark Cluster

**Architecture:**
```
┌──────────────────────────────────────────────────────────┐
│                    Docker Network                        │
│                                                          │
│  ┌─────────────────┐                                     │
│  │     Libra       │                                     │
│  │   Container     │                                     │
│  │  (Driver)       │                                     │
│  │                 │                                     │
│  │  Ports:         │                                     │
│  │  - 8080: API    │                                     │
│  │  - 4040: UI     │                                     │
│  │  - 7078: RPC    │                                     │
│  └────────┬────────┘                                     │
│           │                                              │
│           │ spark://spark-master:7077                    │
│           ▼                                              │
│  ┌─────────────────┐                                     │
│  │  Spark Master   │                                     │
│  │   Container     │                                     │
│  │                 │                                     │
│  │  Ports:         │                                     │
│  │  - 7077: Submit │                                     │
│  │  - 8081: Web UI │                                     │
│  └────────┬────────┘                                     │
│           │                                              │
│           │ Allocate executors                           │
│           ▼                                              │
│  ┌─────────────────┐      ┌─────────────────┐            │
│  │ Spark Worker 1  │      │ Spark Worker 2  │            │
│  │   Container     │      │   Container     │            │
│  │                 │      │                 │            │
│  │  Ports:         │      │  Ports:         │            │
│  │  - 8082: Web UI │      │  - 8083: Web UI │            │
│  └─────────────────┘      └─────────────────┘            │
│           │                        │                     │
│           └────────────┬───────────┘                     │
│                        │                                 │
│                        │ Bidirectional RPC               │
│                        ▼                                 │
│                 ┌─────────────┐                          │
│                 │    Libra    │                          │
│                 │  (Driver)   │                          │
│                 └─────────────┘                          │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

**Configuration:**
```yaml
# docker-compose.yml
version: '3.8'
services:
  spark-master:
    image: spark-facade-spark:latest
    command: bin/spark-class org.apache.spark.deploy.master.Master
    ports:
      - "8081:8080"  # Master Web UI
      - "7077:7077"  # Master RPC

  spark-worker:
    image: spark-facade-spark:latest
    command: bin/spark-class org.apache.spark.deploy.worker.Worker spark://spark-master:7077
    depends_on:
      - spark-master
    environment:
      - SPARK_WORKER_CORES=2
      - SPARK_WORKER_MEMORY=2g
    deploy:
      replicas: 2

  libra:
    image: whereq-libra:latest
    environment:
      - SPRING_PROFILES_ACTIVE=docker
    ports:
      - "8080:8080"  # REST API
      - "4040:4040"  # Spark Application UI
      - "7078:7078"  # Driver RPC port
    depends_on:
      - spark-master
```

**Spark Configuration:**
```yaml
# application-docker.yml
spark:
  master: spark://spark-master:7077
  config:
    spark.driver.host: libra  # Container name for callback
    spark.driver.port: 7078
    spark.driver.bindAddress: 0.0.0.0
```

**Network Flow:**
```
1. Libra → spark://spark-master:7077 (Submit job)
2. Master → Workers (Allocate executors)
3. Workers → Libra:7078 (Executor registration)
4. Libra ↔ Executors (Task execution, shuffle, results)
5. Libra → Client (HTTP response)
```

**Start Command:**
```bash
docker-compose up --build
```

**When to Use:**
- ✅ Small production deployments
- ✅ On-premise installations
- ✅ Multi-node testing
- ✅ Fixed workload environments

**Scaling:**
```bash
# Scale workers
docker-compose up --scale spark-worker=5
```

---

### 3. Kubernetes - Standalone Spark Cluster (Option 1)

**Architecture:**
```
┌─────────────────────────────────────────────────────────────┐
│              Kubernetes Namespace: spark-platform           │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                  Libra Service                       │   │
│  │      Type: LoadBalancer (External Access)            │   │
│  │      Ports: 8080 (API), 4040 (UI), 7078 (RPC)        │   │
│  └──────────────────────┬───────────────────────────────┘   │
│                         │                                   │
│                         ▼                                   │
│  ┌────────────────────────────────────────────────────────┐ │
│  │              Libra Deployment                          │ │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐        │ │
│  │  │  Libra Pod │  │  Libra Pod │  │  Libra Pod │        │ │
│  │  │  (Driver)  │  │  (Driver)  │  │  (Driver)  │        │ │
│  │  │  Replica 1 │  │  Replica 2 │  │  Replica 3 │        │ │
│  │  └────────────┘  └────────────┘  └────────────┘        │ │
│  └──────────────────────┬─────────────────────────────────┘ │
│                         │                                   │
│                         │ spark://spark-master:7077         │
│                         ▼                                   │
│  ┌───────────────────────────────────────────────────────┐  │
│  │           Spark Master Service                        │  │
│  │           ClusterIP: spark-master                     │  │
│  │           Ports: 7077, 8080                           │  │
│  └──────────────────────┬────────────────────────────────┘  │
│                         │                                   │
│                         ▼                                   │
│  ┌───────────────────────────────────────────────────────┐  │
│  │           Spark Master StatefulSet                    │  │
│  │  ┌──────────────────────────────────────────────────┐ │  │
│  │  │  spark-master-0                                  │ │  │
│  │  │  - Manages executors                             │ │  │
│  │  │  - Resource allocation                           │ │  │
│  │  │  - Job scheduling                                │ │  │
│  │  └──────────────────────────────────────────────────┘ │  │
│  └──────────────────────┬────────────────────────────────┘  │
│                         │                                   │
│                         ▼                                   │
│  ┌───────────────────────────────────────────────────────┐  │
│  │         Spark Worker StatefulSet                      │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │  │
│  │  │ worker-0    │  │ worker-1    │  │ worker-N    │    │  │
│  │  │ - Executors │  │ - Executors │  │ - Executors │    │  │
│  │  │ - 2 cores   │  │ - 2 cores   │  │ - 2 cores   │    │  │
│  │  │ - 2g memory │  │ - 2g memory │  │ - 2g memory │    │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘    │  │
│  └──────────────────────┬────────────────────────────────┘  │
│                         │                                   │
│                         │ Bidirectional RPC                 │
│                         ▼                                   │
│                  ┌─────────────┐                            │
│                  │  Libra Pods │                            │
│                  │  (Drivers)  │                            │
│                  └─────────────┘                            │
│                                                             │
│  Persistent Volumes:                                        │
│  - Spark warehouse                                          │
│  - Spark event logs                                         │
│  - Application JARs                                         │
└─────────────────────────────────────────────────────────────┘
```

**Key Configuration Files:**

**Libra Deployment:**
```yaml
# k8s/option1/libra-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: libra
  namespace: spark-platform
spec:
  replicas: 3  # HA setup
  selector:
    matchLabels:
      app: libra
  template:
    metadata:
      labels:
        app: libra
    spec:
      containers:
      - name: libra
        image: whereq/libra:latest
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "k8s"
        - name: JAVA_OPTS
          value: "-Xmx2g -Xms1g"
        ports:
        - containerPort: 8080  # REST API
        - containerPort: 4040  # Spark UI
        - containerPort: 7078  # Driver RPC
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
```

**Spark Configuration:**
```yaml
# application-k8s.yml
spark:
  master: spark://spark-master:7077
  config:
    spark.driver.host: libra-service  # K8s service name
    spark.driver.port: 7078
    spark.driver.bindAddress: 0.0.0.0
    spark.driver.memory: 2g
    spark.executor.memory: 2g
    spark.executor.cores: 2
    spark.scheduler.mode: FAIR

    # Network timeouts for K8s
    spark.network.timeout: 600s
    spark.executor.heartbeatInterval: 60s

    # Event logging
    spark.eventLog.enabled: true
    spark.eventLog.dir: /opt/spark-events
```

**Deploy Command:**
```bash
cd k8s
./deploy.sh option1
```

**When to Use:**
- ✅ Production multi-user environments
- ✅ Continuous workloads
- ✅ Need HA (multiple Libra replicas)
- ✅ Predictable resource usage
- ✅ Traditional Spark cluster model

**Resource Management:**
```bash
# Scale workers
kubectl scale statefulset spark-worker --replicas=5 -n spark-platform

# Scale Libra (HA)
kubectl scale deployment libra --replicas=5 -n spark-platform
```

---

### 4. Kubernetes - Native Spark Mode (Option 3) - Recommended for Cloud

**Architecture:**
```
┌─────────────────────────────────────────────────────────────┐
│              Kubernetes Namespace: spark-platform           │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                  Libra Service                        │  │
│  │      Type: LoadBalancer (External Access)             │  │
│  │      Ports: 8080 (API), 4040 (UI), 7078 (RPC)         │  │
│  └──────────────────────┬────────────────────────────────┘  │
│                         │                                   │
│                         ▼                                   │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              Libra Deployment                         │  │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐       │  │
│  │  │  Libra Pod │  │  Libra Pod │  │  Libra Pod │       │  │
│  │  │  (Driver)  │  │  (Driver)  │  │  (Driver)  │       │  │
│  │  └─────┬──────┘  └──────┬─────┘  └───────┬────┘       │  │
│  └────────┼────────────────┼────────────────┼────────────┘  │
│           │                │                │               │
│           ▼                ▼                ▼               │
│    k8s://https://kubernetes.default.svc:443                 │
│           │                │                │               │
│           ▼                ▼                ▼               │
│  ┌─────────────────────────────────────────────────── ───┐  │
│  │          Dynamically Created Executor Pods            │  │
│  │                                                       │  │
│  │  Job 1 Executors:        Job 2 Executors:             │  │
│  │  ┌─────────────┐         ┌─────────────┐              │  │
│  │  │ executor-1  │         │ executor-1  │              │  │
│  │  │ - 4 cores   │         │ - 8 cores   │              │  │
│  │  │ - 8g memory │         │ - 16g memory│              │  │
│  │  └─────────────┘         └─────────────┘              │  │
│  │  ┌─────────────┐         ┌─────────────┐              │  │
│  │  │ executor-2  │         │ executor-2  │              │  │
│  │  └─────────────┘         └─────────────┘              │  │
│  │         ↓                        ↓                    │  │
│  │  [Auto-deleted after job completes]                   │  │
│  └──────────────────────┬────────────────────────────────┘  │
│                         │                                   │
│                         │ Bidirectional RPC                 │
│                         ▼                                   │
│                  ┌─────────────┐                            │
│                  │  Libra Pods │                            │
│                  │  (Drivers)  │                            │
│                  └─────────────┘                            │
│                                                             │
│  Resource Efficiency:                                       │
│  - Executors created on-demand                              │
│  - Auto-scaled based on workload                            │
│  - Cleaned up after job completion                          │
│  - Zero baseline executor cost                              │
└─────────────────────────────────────────────────────────────┘
```

**Key Configuration:**

**Libra Deployment:**
```yaml
# k8s/option3/libra-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: libra
  namespace: spark-platform
spec:
  replicas: 3
  template:
    spec:
      serviceAccountName: spark  # RBAC for pod creation
      containers:
      - name: libra
        image: whereq/libra:latest
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "k8s-native"
        ports:
        - containerPort: 8080
        - containerPort: 4040
        - containerPort: 7078
```

**RBAC Configuration:**
```yaml
# k8s/option3/spark-rbac.yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: spark
  namespace: spark-platform
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: spark-role
  namespace: spark-platform
rules:
- apiGroups: [""]
  resources: ["pods", "services", "configmaps"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]
- apiGroups: [""]
  resources: ["pods/log"]
  verbs: ["get"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: spark-role-binding
  namespace: spark-platform
subjects:
- kind: ServiceAccount
  name: spark
  namespace: spark-platform
roleRef:
  kind: Role
  name: spark-role
  apiGroup: rbac.authorization.k8s.io
```

**Spark Configuration:**
```yaml
# application-k8s-native.yml
spark:
  master: k8s://https://kubernetes.default.svc:443
  config:
    # Kubernetes-specific settings
    spark.kubernetes.namespace: spark-platform
    spark.kubernetes.authenticate.driver.serviceAccountName: spark
    spark.kubernetes.container.image: whereq/spark:4.0.1

    # Executor pod configuration
    spark.kubernetes.executor.request.cores: "1"
    spark.kubernetes.executor.limit.cores: "2"
    spark.executor.memory: "2g"
    spark.executor.instances: "2"

    # Dynamic allocation
    spark.dynamicAllocation.enabled: true
    spark.dynamicAllocation.shuffleTracking.enabled: true
    spark.dynamicAllocation.minExecutors: "0"
    spark.dynamicAllocation.maxExecutors: "10"
    spark.dynamicAllocation.initialExecutors: "2"

    # Driver configuration
    spark.driver.host: libra-service
    spark.driver.port: 7078
    spark.driver.bindAddress: 0.0.0.0

    # Pod cleanup
    spark.kubernetes.executor.deleteOnTermination: true
```

**Deploy Command:**
```bash
cd k8s
./deploy.sh option3
```

**Watch Executors Being Created:**
```bash
# Terminal 1: Watch pods
kubectl get pods -n spark-platform -w

# Terminal 2: Submit job
curl -X POST http://${LIBRA_IP}:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "sql",
    "code": "SELECT COUNT(*) FROM range(1000000)",
    "sparkConfig": {
      "spark.executor.instances": "5",
      "spark.executor.memory": "4g"
    }
  }'

# Observe:
# 1. Executor pods being created
# 2. Job execution
# 3. Executor pods being deleted after completion
```

**When to Use:**
- ✅ Cloud-native deployments (AWS, GCP, Azure)
- ✅ Variable workloads
- ✅ Cost optimization (pay per job)
- ✅ On-demand job execution
- ✅ Complete job isolation
- ✅ Auto-scaling requirements

**Cost Optimization:**
```
Traditional Spark Cluster (Option 1):
- 10 workers × 24 hours × 30 days = 7,200 worker-hours
- Cost: $X per month (fixed)

Native K8s Mode (Option 3):
- Job 1: 10 executors × 0.5 hours = 5 executor-hours
- Job 2: 5 executors × 2 hours = 10 executor-hours
- Job 3: 20 executors × 0.25 hours = 5 executor-hours
- Total: 20 executor-hours per month
- Cost: $X × (20/7200) = 0.3% of traditional cost
```

---

## 📋 Tech Stack

| Component | Technology | Version |
|-----------|------------|---------|
| **Language** | Java (OpenJDK) | 21 |
| **Framework** | Spring Boot | 3.5.7 |
| **Big Data** | Apache Spark | 4.0.1 |
| **Scala** | Scala | 2.13 |
| **Build Tool** | Maven | 3.9.9 |
| **Base Image** | Red Hat UBI9 minimal | 9.6-1760515502 |
| **Container** | Docker / Podman | Latest |
| **Orchestration** | Kubernetes | 1.27+ |
| **API Docs** | SpringDoc OpenAPI | 2.8.4 |

---

## 🎮 API Documentation

### Complete API Reference

#### 1. Health & Monitoring

```bash
# Health check
GET /api/v1/health
Response: {"status": "UP"}

# Actuator health (detailed)
GET /actuator/health
Response: {
  "status": "UP",
  "components": {
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}

# Metrics (Prometheus format)
GET /actuator/metrics
GET /actuator/prometheus
```

#### 2. Session Management

```bash
# List all sessions
GET /api/v1/sessions
Response: [
  {
    "sessionId": "default",
    "appId": "local-1625234567890",
    "state": "RUNNING",
    "master": "local[*]",
    "sparkVersion": "4.0.1",
    "createdAt": "2025-11-03T10:00:00",
    "lastActivity": "2025-11-03T10:05:00"
  }
]

# Get specific session
GET /api/v1/sessions/{sessionId}

# Delete session (ISOLATED mode only)
DELETE /api/v1/sessions/{sessionId}
```

#### 3. Job Execution - SQL

```bash
POST /api/v1/sessions/default/statements
Content-Type: application/json

{
  "kind": "sql",
  "code": "SELECT * FROM range(100) WHERE id > 50",
  "pool": "default",
  "description": "Test query",
  "sparkConfig": {
    "spark.sql.shuffle.partitions": "200",
    "spark.sql.adaptive.enabled": "true"
  }
}

Response: {
  "jobId": "uuid-here",
  "sessionId": "default",
  "state": "SUCCESS",
  "result": "Schema: struct<id:bigint>\\n\\n[51]\\n[52]\\n...",
  "startedAt": "2025-11-03T10:00:00",
  "completedAt": "2025-11-03T10:00:05",
  "executionTimeMs": 5000
}
```

#### 4. Job Execution - Java JAR

```bash
# jar-class mode (fast, auto-switching)
POST /api/v1/sessions/default/statements
{
  "kind": "jar-class",
  "filePath": "/path/to/app.jar",
  "mainClass": "com.example.MySparkApp",
  "args": ["arg1", "arg2"],
  "pool": "default",
  "sparkConfig": {
    "spark.driver.memory": "4g",
    "spark.executor.memory": "8g",
    "spark.executor.cores": "4"
  }
}

# jar mode (always spark-submit)
POST /api/v1/sessions/default/statements
{
  "kind": "jar",
  "filePath": "/path/to/app.jar",
  "mainClass": "com.example.MySparkApp",
  "args": ["arg1", "arg2"],
  "sparkConfig": {
    "spark.driver.memory": "8g",
    "spark.executor.memory": "16g",
    "spark.executor.instances": "20",
    "spark.dynamicAllocation.enabled": "true"
  }
}
```

#### 5. Job Execution - Python

```bash
# Python file execution
POST /api/v1/sessions/default/statements
{
  "kind": "python-file",
  "filePath": "/path/to/script.py",
  "args": ["2025-11-03", "/data/input"],
  "pool": "high-priority",
  "sparkConfig": {
    "spark.driver.memory": "4g",
    "spark.executor.memory": "8g",
    "spark.executor.cores": "4",
    "spark.executor.instances": "10"
  }
}

# Inline Python code
POST /api/v1/sessions/default/statements
{
  "kind": "python",
  "code": "from pyspark.sql import SparkSession\\ndf = spark.range(1000)\\nprint(df.count())",
  "pool": "interactive"
}
```

---

## 🔧 Configuration Guide

### Session Modes

#### SHARED Mode (Default)
```yaml
libra:
  session:
    mode: SHARED  # Single SparkSession for all jobs
    max-sessions: 1  # Not used in SHARED mode
    timeout-minutes: 30  # Session idle timeout
```

**Benefits:**
- Faster job startup
- Efficient resource sharing
- Lower overhead
- FAIR scheduler for resource allocation

#### ISOLATED Mode
```yaml
libra:
  session:
    mode: ISOLATED  # One SparkSession per session ID
    max-sessions: 10  # Maximum concurrent sessions
    timeout-minutes: 30  # Auto-cleanup idle sessions
```

**Benefits:**
- Complete isolation
- Different configs per session
- Multi-tenant support
- Fault isolation

### Resource Pools (FAIR Scheduler)

```yaml
# src/main/resources/fairscheduler.xml
<?xml version="1.0"?>
<allocations>
  <pool name="default">
    <schedulingMode>FAIR</schedulingMode>
    <weight>1</weight>
    <minShare>2</minShare>
  </pool>

  <pool name="high-priority">
    <schedulingMode>FAIR</schedulingMode>
    <weight>3</weight>
    <minShare>4</minShare>
  </pool>

  <pool name="low-priority">
    <schedulingMode>FAIR</schedulingMode>
    <weight>0.5</weight>
    <minShare>1</minShare>
  </pool>

  <pool name="interactive">
    <schedulingMode>FIFO</schedulingMode>
    <weight>2</weight>
    <minShare>2</minShare>
  </pool>
</allocations>
```

### Spark Configuration

```yaml
spark:
  app-name: whereq-libra
  master: local[*]  # or spark://master:7077 or k8s://...
  config:
    # Memory
    spark.driver.memory: 2g
    spark.executor.memory: 2g

    # Cores
    spark.driver.cores: 1
    spark.executor.cores: 2
    spark.executor.instances: 2

    # Scheduler
    spark.scheduler.mode: FAIR

    # Dynamic allocation
    spark.dynamicAllocation.enabled: false
    spark.dynamicAllocation.minExecutors: 1
    spark.dynamicAllocation.maxExecutors: 10

    # Network
    spark.driver.host: localhost
    spark.driver.port: 7078
    spark.driver.bindAddress: 0.0.0.0

    # Warehouse
    spark.sql.warehouse.dir: /tmp/spark-warehouse
```

---

## 🚀 Quick Start

### Local Development

```bash
# Clone repository
git clone https://github.com/whereq/libra.git
cd libra

# Start in local mode
docker-compose -f docker-compose.local.yml up --build

# Test the API
curl http://localhost:8080/api/v1/health

# Execute a SQL query
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "sql",
    "code": "SELECT 1 as number, '\''hello'\'' as greeting"
  }'

# Access UIs
open http://localhost:8080/swagger-ui.html  # API docs
open http://localhost:4040                   # Spark UI
```

### Docker Compose with Spark Cluster

```bash
# Start cluster
docker-compose up --build

# Access UIs
open http://localhost:8080                   # Libra API
open http://localhost:8081                   # Spark Master UI
open http://localhost:8082                   # Spark Worker UI
open http://localhost:4040                   # Spark Application UI

# Stop cluster
docker-compose down
```

### Kubernetes Deployment

```bash
# Option 1: Standalone cluster
cd k8s
./deploy.sh option1

# Option 3: Native K8s mode
./deploy.sh option3

# Get service IP
LIBRA_IP=$(kubectl get svc libra-service -n spark-platform \
  -o jsonpath='{.status.loadBalancer.ingress[0].ip}')

# Test
curl http://${LIBRA_IP}:8080/api/v1/health
```

---

## 🐳 Building Custom Spark Images

WhereQ Libra includes a modular Docker build system for Apache Spark 4.0.1 with Python 3.14.0, supporting multiple cloud storage backends.

### 📦 Available Image Variants

| Variant | Base Size | Added Components | Use Case |
|---------|-----------|------------------|----------|
| **base** | ~800MB | Spark 4.0.1 + Python 3.14.0 + Java 21 | Local development, no cloud storage |
| **gcs** | ~850MB / ~1.25GB with gcloud | GCS Connector 2.2.20 + optional gcloud CLI | Google Cloud Platform, GKE |
| **s3** | ~850MB | Hadoop AWS 3.3.6 + AWS SDK 1.12.367 | AWS, EKS deployments |
| **minio** | ~850MB | Same as S3, path-style access | On-premise S3-compatible storage |

### 🛠️ Build Script Usage

#### Basic Builds

```bash
# Build base image
./bin/build-spark-image.sh --variant base

# Build GCS variant
./bin/build-spark-image.sh --variant gcs

# Build S3 variant
./bin/build-spark-image.sh --variant s3

# Build MinIO variant
./bin/build-spark-image.sh --variant minio

# Build all variants
for variant in base gcs s3 minio; do
  ./bin/build-spark-image.sh --variant $variant
done
```

#### Advanced Build Options

##### 1. **GCS with gcloud CLI for Debugging**

The GCS variant supports an optional `INSTALL_GCLOUD` build argument that installs gcloud CLI tools (~400MB additional size).

```bash
# Production build (no gcloud CLI) - ~850MB
./bin/build-spark-image.sh --variant gcs

# Debug build (with gcloud CLI) - ~1.25GB
docker build -f docker/gcs/Dockerfile \
  --build-arg INSTALL_GCLOUD=true \
  -t whereq/spark:4.0.1-gcs-debug .

# Verify gcloud installation
docker run -it whereq/spark:4.0.1-gcs-debug gcloud version
docker run -it whereq/spark:4.0.1-gcs-debug gsutil version
```

**When to use gcloud CLI:**
- ✅ Debugging GCS connectivity issues
- ✅ Testing authentication configurations
- ✅ Verifying bucket permissions
- ✅ Development and troubleshooting
- ❌ Production deployments (adds unnecessary size)

##### 2. **Secured Artifactory Authentication**

For environments with secured artifactories requiring HTTPS authentication:

```bash
# Using build script (recommended)
./bin/build-spark-image.sh \
  --variant gcs \
  --https-user "${ARTIFACTORY_USER}" \
  --https-pass "${ARTIFACTORY_PASS}"

# Direct Docker build
docker build -f docker/gcs/Dockerfile \
  --build-arg HTTPS_USERNAME="myuser" \
  --build-arg HTTPS_PASSWORD="mypass" \
  -t whereq/spark:4.0.1-gcs .

# Combined: gcloud CLI + secured artifactory
docker build -f docker/gcs/Dockerfile \
  --build-arg INSTALL_GCLOUD=true \
  --build-arg HTTPS_USERNAME="${ARTIFACTORY_USER}" \
  --build-arg HTTPS_PASSWORD="${ARTIFACTORY_PASS}" \
  -t whereq/spark:4.0.1-gcs-debug .
```

##### 3. **Custom Tags and Registry Push**

```bash
# Build with custom tag
./bin/build-spark-image.sh \
  --variant gcs \
  --tag myregistry.io/spark

# Build and push to registry
./bin/build-spark-image.sh \
  --variant s3 \
  --tag myregistry.io/spark \
  --push

# Build without cache (force rebuild)
./bin/build-spark-image.sh \
  --variant base \
  --no-cache
```

##### 4. **Custom Versions**

```bash
# Build with custom Spark/Python versions
./bin/build-spark-image.sh \
  --variant base \
  --spark-version 4.0.2 \
  --python-version 3.14.1
```

### 📋 Build Script Options

```
Usage: ./bin/build-spark-image.sh [OPTIONS]

OPTIONS:
    -v, --variant VARIANT      Spark variant (base, gcs, s3, minio)
    -t, --tag PREFIX           Docker image tag prefix (default: whereq/spark)
    -s, --spark-version VER    Spark version (default: 4.0.1)
    -p, --python-version VER   Python version (default: 3.14.0)
    -u, --https-user USERNAME  HTTPS username for secured artifactory
    -P, --https-pass PASSWORD  HTTPS password for secured artifactory
    --push                     Push image to registry after build
    --no-cache                 Build without using cache
    -h, --help                 Show help message

EXAMPLES:
    # Build GCS variant with debugging tools
    docker build -f docker/gcs/Dockerfile --build-arg INSTALL_GCLOUD=true .

    # Build with secured artifactory
    ./bin/build-spark-image.sh --variant s3 \
      --https-user myuser --https-pass mypass

    # Build and push to custom registry
    ./bin/build-spark-image.sh --variant minio \
      --tag myregistry.io/spark --push
```

### 🔧 Configuration Details

#### GCS Variant

**Pre-configured settings:**
- GCS Connector 2.2.20
- Service Account JSON authentication
- Hadoop GCS filesystem implementation
- Performance tuning (block size, retries, timeouts)

**Runtime configuration via environment variables:**
```bash
docker run -d \
  -e GCS_BUCKET=my-bucket \
  -e GCS_PROJECT_ID=my-project \
  -v /path/to/gcs-key.json:/opt/spark/conf/gcs-json-key/gcs-key.json:ro \
  whereq/spark:4.0.1-gcs
```

#### S3 Variant

**Pre-configured settings:**
- Hadoop AWS 3.3.6
- AWS SDK Bundle 1.12.367
- S3A filesystem with virtual-hosted style access
- IAM role and access key support

**Runtime configuration:**
```bash
docker run -d \
  -e S3_BUCKET=my-bucket \
  -e AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE \
  -e AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG \
  -e AWS_REGION=us-east-1 \
  whereq/spark:4.0.1-s3
```

#### MinIO Variant

**Pre-configured settings:**
- Same JARs as S3 variant
- Path-style access enabled (required for MinIO)
- Custom endpoint support

**Runtime configuration:**
```bash
docker run -d \
  -e MINIO_ENDPOINT=http://minio:9000 \
  -e MINIO_BUCKET=spark-data \
  -e MINIO_ACCESS_KEY=minioadmin \
  -e MINIO_SECRET_KEY=minioadmin \
  whereq/spark:4.0.1-minio
```

### 📖 Additional Documentation

For detailed information about the Docker build system, see:
- **[docker/README.md](docker/README.md)** - Complete Docker build system documentation
- **[docker/gcs/Dockerfile](docker/gcs/Dockerfile)** - GCS variant with inline documentation
- **[bin/build-spark-image.sh](bin/build-spark-image.sh)** - Universal build script

---

## 📚 Documentation

### 📖 Complete Documentation Index
- **[Documentation Index](docs/INDEX.md)** - Complete guide to all documentation

### 🎓 Essential Reading
- **[Spark Architecture Deep Dive](docs/SPARK_ARCHITECTURE_DEEP_DIVE.md)** ⭐ NEW - Understanding Driver, Master, Workers, and Executors
- **[Where Is The Driver?](docs/WHERE_IS_THE_DRIVER.md)** ⭐ NEW - Driver location in different deployments
- **[Driver Performance Impact](docs/DRIVER_PERFORMANCE_IMPACT.md)** ⭐ NEW - How driver location affects performance

### 📋 Feature Guides
- **[JAR Execution Guide](docs/JAR_EXECUTION.md)** - Complete guide for Java JAR execution
- **[Python Execution Guide](docs/PYTHON_EXECUTION.md)** - Python job execution
- **[Parallel Execution Guide](docs/PARALLEL_EXECUTION.md)** - SHARED vs ISOLATED modes
- **[Resource Allocation Guide](docs/RESOURCE_ALLOCATION.md)** - Resource configuration and best practices
- **[Resource Config Enhancement](docs/RESOURCE_CONFIG_ENHANCEMENT.md)** - Latest enhancements summary

### 🚀 Deployment
- **[Deployment Guide](docs/DEPLOYMENT.md)** - Deployment strategies and configuration
- **[Quick Start Guide](docs/QUICK_START.md)** - Get started in 5 minutes
- **[API Documentation](http://localhost:8080/swagger-ui.html)** - Interactive API docs (when running)

---

## 🐛 Debugging

### VSCode Debug Configurations

**Debug in Docker:**
1. Start services: `docker-compose -f docker-compose.local.yml up --build`
2. Press F5 and select "Debug Libra (Docker)"
3. Set breakpoints and test

**Debug Locally:**
1. Press F5 and select "Debug Libra (Local)"
2. Application starts with debugger attached

### Common Debug Scenarios

```java
// Debug SQL execution
SparkSessionService.java:executeJob()  // Line ~75

// Debug session management
SparkSessionController.java:executeStatement()  // Line ~47

// Debug Spark configuration
SparkConfig.java:sparkSession()  // Line ~41

// Debug JAR execution
JarJobExecutor.java:executeJarInJVM()  // Line ~42
JarJobExecutor.java:executeJarWithSparkSubmit()  // Line ~116
```

---

## 🔄 Roadmap

### Current Features ✅
- [x] Spark 4.x+ with Scala 2.13 support
- [x] Parallel job execution (FAIR scheduler)
- [x] Per-job resource configuration
- [x] Multiple execution modes (JAR, Python, SQL)
- [x] Intelligent jar-class mode switching
- [x] Docker and Kubernetes support
- [x] Red Hat UBI9 base image
- [x] SHARED and ISOLATED session modes
- [x] Python script execution
- [x] Health monitoring and metrics

### Planned Features 🚧
- [ ] Enhanced authentication and authorization
- [ ] Job history and persistence
- [ ] Asynchronous job execution with callbacks
- [ ] Support for additional languages (R, Julia)
- [ ] Advanced monitoring dashboard
- [ ] Cost tracking and optimization
- [ ] Integration with cloud storage (S3, GCS, ADLS)
- [ ] Job templates and scheduling
- [ ] Multi-cluster support
- [ ] Enhanced error handling and retry logic

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request. As an actively maintained project, we welcome community involvement to help make Libra the best Spark REST interface available.

### Development Setup

```bash
# Clone repository
git clone https://github.com/whereq/libra.git
cd libra

# Build
mvn clean package

# Run tests
mvn test

# Run locally
mvn spring-boot:run
```

---

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 💬 Support

For issues and questions, please create an issue in the repository. WhereQ is committed to the ongoing development and support of Libra to ensure it meets the evolving needs of the big data community.

### Community

- **GitHub Issues**: Bug reports and feature requests
- **Discussions**: Questions and community support
- **Documentation**: Comprehensive guides in the `/docs` folder

---

## 🙏 Acknowledgments

Special thanks to:
- Apache Spark community
- Spring Boot team
- Red Hat for UBI9 base images
- All contributors and users

---

**WhereQ Libra** - Making big data development easier, one REST call at a time. 🚀

---

*Developed with ❤️ by [WhereQ Inc.](https://whereq.com)*
