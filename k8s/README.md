# Kubernetes Deployment Guide for WhereQ Libra

This directory contains Kubernetes manifests for deploying WhereQ Libra in two different modes.

## Option 1: Standalone Spark Cluster (Recommended for Production)

Deploy Libra alongside a standalone Spark cluster with dedicated master and worker pods.

### Architecture
```
┌─────────────────────────────────────────────────┐
│         Kubernetes Namespace: spark-platform     │
│                                                  │
│  ┌──────────┐    ┌──────────────┐              │
│  │  Libra   │───→│ Spark Master │              │
│  │  (Driver)│    │   (Port 7077)│              │
│  │  Pod     │    └──────────────┘              │
│  │          │           ↓                       │
│  │  Port:   │    ┌──────────────┐              │
│  │  - 8080  │    │ Spark Worker │              │
│  │  - 4040  │    │   Pod 1      │              │
│  │  - 7078  │    └──────────────┘              │
│  └──────────┘    ┌──────────────┐              │
│       ↑          │ Spark Worker │              │
│       │          │   Pod 2      │              │
│       └──────────└──────────────┘              │
│              Bidirectional RPC                  │
└─────────────────────────────────────────────────┘
```

### Deployment Steps

1. **Create namespace:**
   ```bash
   kubectl apply -f option1-standalone/namespace.yaml
   ```

2. **Deploy Spark Master:**
   ```bash
   kubectl apply -f option1-standalone/spark-master.yaml
   ```

3. **Deploy Spark Workers:**
   ```bash
   kubectl apply -f option1-standalone/spark-worker.yaml
   ```

4. **Build and push Libra Docker image:**
   ```bash
   docker build -t whereq/libra:1.0.0 .
   docker push whereq/libra:1.0.0
   ```

5. **Deploy Libra:**
   ```bash
   kubectl apply -f option1-standalone/libra.yaml
   ```

6. **Verify deployment:**
   ```bash
   kubectl get pods -n spark-platform
   kubectl get svc -n spark-platform
   ```

### Access Services

- **Libra REST API:** `http://<EXTERNAL-IP>`
- **Libra Spark UI:** `http://<EXTERNAL-IP>:4040`
- **Spark Master UI:** `http://<spark-master-ip>:8080`

### Pros
- Persistent Spark cluster
- Better resource utilization for multiple jobs
- Easier monitoring of Spark cluster
- Master and workers can be scaled independently

### Cons
- More resources (always-on workers)
- Requires cluster management

---

## Option 3: Spark Native Kubernetes Mode

Spark dynamically creates executor pods for each job.

### Architecture
```
┌─────────────────────────────────────────────────┐
│         Kubernetes Namespace: spark-platform     │
│                                                  │
│  ┌──────────┐                                   │
│  │  Libra   │                                   │
│  │  (Driver)│                                   │
│  │  Pod     │    (Dynamically created)          │
│  │          │    ┌──────────────┐              │
│  │  Port:   │───→│ Executor Pod │              │
│  │  - 8080  │    │      1       │              │
│  │  - 4040  │    └──────────────┘              │
│  │  - 7078  │    ┌──────────────┐              │
│  └──────────┘───→│ Executor Pod │              │
│       ↑          │      2       │              │
│       │          └──────────────┘              │
│       └──────────────────────────              │
│    Executors deleted after job completes        │
└─────────────────────────────────────────────────┘
```

### Deployment Steps

1. **Create namespace:**
   ```bash
   kubectl apply -f option1-standalone/namespace.yaml
   ```

2. **Create RBAC (Service Account, Role, RoleBinding):**
   ```bash
   kubectl apply -f option3-native/rbac.yaml
   ```

3. **Build and push Libra Docker image:**
   ```bash
   docker build -t whereq/libra:1.0.0 .
   docker push whereq/libra:1.0.0
   ```

4. **Deploy Libra:**
   ```bash
   kubectl apply -f option3-native/libra-native.yaml
   ```

5. **Verify deployment:**
   ```bash
   kubectl get pods -n spark-platform
   kubectl get svc -n spark-platform
   ```

6. **Watch executors being created during job execution:**
   ```bash
   kubectl get pods -n spark-platform -w
   ```

### Access Services

- **Libra REST API:** `http://<EXTERNAL-IP>`
- **Libra Spark UI:** `http://<EXTERNAL-IP>:4040`

### Pros
- Resource efficient (executors only when needed)
- Perfect isolation per job
- Auto-cleanup of executor pods
- More cloud-native

### Cons
- Executor startup latency
- More complex networking
- Requires RBAC permissions
- Docker image must contain Spark binaries

---

## Configuration Files

- **application-k8s.yml**: For Option 1 (standalone cluster)
- **application-k8s-native.yml**: For Option 3 (native k8s mode)

Set the profile via environment variable:
```yaml
env:
- name: SPRING_PROFILES_ACTIVE
  value: "k8s"  # or "k8s-native"
```

---

## Critical Networking Requirements

### For Option 1:
1. **Libra must be reachable by executors** on port 7078
2. Service name `libra-service` resolves to Libra pod
3. Executors connect to `libra-service:7078` for task communication

### For Option 3:
1. **Libra needs K8s API access** to create executor pods
2. ServiceAccount `spark-driver` must have proper RBAC
3. Executor image must be accessible from K8s registry

---

## Monitoring

Check Libra logs:
```bash
kubectl logs -f deployment/libra -n spark-platform
```

Check executor logs (Option 3):
```bash
kubectl logs -f <executor-pod-name> -n spark-platform
```

---

## Troubleshooting

### Executors cannot reach driver:
```bash
# Check if libra-service is accessible
kubectl exec -it <executor-pod> -n spark-platform -- nc -zv libra-service 7078
```

### Permission errors (Option 3):
```bash
# Check service account
kubectl get sa spark-driver -n spark-platform
kubectl describe role spark-driver-role -n spark-platform
```

### Network policies:
If you have network policies, ensure:
- Libra can reach spark-master:7077
- Executors can reach libra-service:7078

---

## Comparison

| Feature | Option 1 (Standalone) | Option 3 (Native K8s) |
|---------|----------------------|----------------------|
| Resource Efficiency | Lower | Higher |
| Job Isolation | Shared executors | Dedicated executors |
| Startup Time | Fast | Slower (pod creation) |
| Complexity | Medium | Higher |
| Best For | Multi-user, continuous workloads | On-demand, isolated jobs |

---

## Next Steps

1. Choose your deployment option
2. Update Docker image reference in YAML files
3. Configure external access (LoadBalancer, Ingress, NodePort)
4. Set up monitoring (Prometheus, Grafana)
5. Configure resource limits based on your workload
