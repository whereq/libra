# WhereQ Libra - Deployment Guide

## Single Codebase, Multiple Deployment Models

### The Key Principle

**WhereQ Libra uses the same Java code for all deployment modes.** The differences are purely configuration-based through Spring profiles.

```java
// src/main/java/com/whereq/libra/config/SparkConfig.java
@Bean
public SparkSession sparkSession(SparkConf sparkConf) {
    log.info("Creating SparkSession with master: {}", master);
    return SparkSession.builder()
            .config(sparkConf)  // ← Configuration drives behavior
            .getOrCreate();
}
```

### How It Works

The `SparkSession.builder()` is intelligent enough to:
1. Parse the `spark.master` URL
2. Determine the deployment mode
3. Initialize appropriate connectors
4. Manage executors accordingly

**No code changes needed - just configuration!**

---

## Deployment Matrix

| Aspect | Local | Docker Compose | K8s Option 1 | K8s Option 3 |
|--------|-------|----------------|--------------|--------------|
| **Java Code** | ✅ Same | ✅ Same | ✅ Same | ✅ Same |
| **Docker Image** | ✅ Same | ✅ Same | ✅ Same | ✅ Same |
| **Spring Profile** | `local` | `docker` | `k8s` | `k8s-native` |
| **spark.master** | `local[*]` | `spark://master:7077` | `spark://master:7077` | `k8s://https://...` |
| **Executor Location** | In-process | Worker pods | Worker pods | Dynamic pods |
| **RBAC Required** | ❌ No | ❌ No | ❌ No | ✅ Yes |

---

## Configuration Comparison

### Local Mode (`application-local.yml`)
```yaml
spark:
  master: local[*]
  app-name: whereq-libra-local
```
- Executors run in same JVM as driver
- No network communication needed
- Best for development

### Docker Compose (`application-docker.yml`)
```yaml
spark:
  master: spark://spark-master:7077
  config:
    spark.driver.host: whereq-libra  # Container name
```
- Connects to standalone cluster
- Executors on worker containers
- Network: bridge mode

### Kubernetes Option 1 (`application-k8s.yml`)
```yaml
spark:
  master: spark://spark-master:7077
  config:
    spark.driver.host: libra-service  # K8s service
    spark.driver.port: 7078
```
- Connects to standalone cluster pods
- Persistent workers
- Production-ready for continuous workloads

### Kubernetes Option 3 (`application-k8s-native.yml`)
```yaml
spark:
  master: k8s://https://kubernetes.default.svc:443
  config:
    spark.kubernetes.namespace: spark-platform
    spark.kubernetes.container.image: apache/spark:4.0.1
    spark.driver.host: libra-service
```
- Spark creates executor pods dynamically
- Best resource efficiency
- Requires K8s permissions

---

## Why Same Code Works

### 1. Abstraction Layer

Spark's `SparkSession` abstracts away deployment details:
- Local mode: Creates threads
- Standalone: Connects via TCP/RPC
- K8s native: Uses K8s API client

### 2. Configuration-Driven

All deployment-specific logic is configuration:
```java
// This works everywhere:
sparkSession.sql("SELECT 1").show()

// Internally:
// - Local: Executes in local threads
// - Standalone: Sends tasks to executors
// - K8s: Creates pods, sends tasks, cleans up
```

### 3. Network Transparency

The driver (Libra) doesn't care where executors are:
- Executors connect back to driver
- RPC protocol is same everywhere
- Only endpoints differ (localhost vs service names)

---

## Switching Between Modes

### Via Environment Variable

**Docker Compose:**
```yaml
environment:
  - SPRING_PROFILES_ACTIVE=local
```

**Kubernetes:**
```yaml
env:
- name: SPRING_PROFILES_ACTIVE
  value: "k8s"
```

### Via Command Line

```bash
java -jar libra.jar --spring.profiles.active=k8s-native
```

### At Runtime

```bash
export SPRING_PROFILES_ACTIVE=local
mvn spring-boot:run
```

---

## Migration Path

### Development → Staging → Production

1. **Develop locally:**
   ```bash
   SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
   ```

2. **Test with Docker Compose:**
   ```bash
   docker-compose up --build
   ```

3. **Deploy to K8s (Option 1) for staging:**
   ```bash
   cd k8s && ./deploy.sh option1 --namespace staging
   ```

4. **Production with Option 3 for cost efficiency:**
   ```bash
   cd k8s && ./deploy.sh option3 --namespace production
   ```

**Zero code changes throughout the entire pipeline!**

---

## Architecture Insight

### Libra's Role

Libra is the **Spark Driver**, not a proxy:

```
┌─────────────────────────────────┐
│          Libra Pod              │
│                                 │
│  ┌───────────────────────────┐  │
│  │   Spring Boot App         │  │
│  │   - REST API              │  │
│  │   - Controllers           │  │
│  │   - Services              │  │
│  └───────────┬───────────────┘  │
│              │                  │
│  ┌───────────▼───────────────┐  │
│  │   SparkSession (Driver)   │  │◄─── This IS the Spark Driver!
│  │   - Task scheduling       │  │
│  │   - Executor management   │  │
│  │   - Result aggregation    │  │
│  └───────────┬───────────────┘  │
└──────────────┼──────────────────┘
               │
               ▼
        [Executors]
```

### Network Flow

**Option 1 (Standalone):**
```
Client → Libra:8080 (REST)
Libra → Spark Master:7077 (Request executors)
Master → Workers (Launch executors)
Executors → Libra:7078 (Connect back for tasks)
Libra ↔ Executors (Bidirectional RPC)
```

**Option 3 (Native K8s):**
```
Client → Libra:8080 (REST)
Libra → K8s API:443 (Create executor pods)
K8s → Executor Pods (Launch)
Executors → Libra:7078 (Connect back for tasks)
Libra ↔ Executors (Bidirectional RPC)
```

---

## Common Pitfalls

### 1. Executor Cannot Reach Driver

**Symptom:** Jobs hang, executors can't connect

**Solution:**
- Ensure `spark.driver.host` is resolvable from executors
- Use K8s service names, not pod names
- Check network policies

### 2. Wrong Profile Active

**Symptom:** Connection refused, timeout errors

**Solution:**
```bash
# Check active profile in logs:
kubectl logs -f deployment/libra -n spark-platform | grep "The following profiles are active"
```

### 3. RBAC Permissions (Option 3 only)

**Symptom:** "Forbidden: pods is forbidden"

**Solution:**
```bash
# Verify RBAC:
kubectl get sa spark-driver -n spark-platform
kubectl describe role spark-driver-role -n spark-platform
```

---

## Testing Configuration

### Verify Active Profile

```bash
# Check which profile is active
curl http://localhost:8080/actuator/env | jq '.activeProfiles'
```

### Test Spark Connectivity

```bash
# Health endpoint shows Spark connection
curl http://localhost:8080/api/v1/health | jq '.spark'

# Expected output:
{
  "spark": {
    "applicationId": "local-1234567890",
    "version": "4.0.1",
    "master": "local[*]",  # or spark://... or k8s://...
    "status": "CONNECTED"
  }
}
```

### Execute Test Job

```bash
curl -X POST http://localhost:8080/api/v1/sessions/default/statements \
  -H "Content-Type: application/json" \
  -d '{
    "code": "SELECT current_database(), version()",
    "kind": "sql"
  }'
```

---

## Summary

✅ **Same Java code** for all deployment modes
✅ **Same Docker image** for all Kubernetes deployments
✅ **Configuration-driven** behavior through Spring profiles
✅ **Seamless migration** from dev to production
✅ **No vendor lock-in** - works on any K8s cluster

**The power of abstraction and configuration over code!**
