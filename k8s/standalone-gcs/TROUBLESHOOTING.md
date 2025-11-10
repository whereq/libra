# Troubleshooting Guide

## Common Issues and Solutions

### Issue 1: "Invalid value: ${ENVIRONMENT:-default}" Error

**Symptoms:**
```
The Namespace "spark-dpdev" is invalid: metadata.labels: Invalid value: "${ENVIRONMENT:-default}"
```

**Cause:** The `envsubst` command doesn't process bash default value syntax `${VAR:-default}`.

**Solution:**

1. **If namespace already exists** (your case):
```bash
# Just run install again - the script now skips existing namespaces
./deploy.sh values-dev-dp.yaml install
```

2. **Add `environment` field to your values file:**
```yaml
namespace: spark-dpdev
environment: dev  # ← Add this line

gcs:
  secretName: gcs-json-key
  # ... rest of config
```

3. **If you need to fix the existing namespace label:**
```bash
# Update the label on existing namespace
kubectl label namespace spark-dpdev environment=dev --overwrite
```

---

### Issue 2: GCS Secret Not Found

**Error:**
```
[ERROR] GCS secret not found: gcs-json-key
```

**Solution:**
```bash
# Verify secret exists in the correct namespace
kubectl get secret gcs-json-key -n spark-dpdev

# If not found, create it
kubectl create secret generic gcs-json-key \
  --from-file=gcs-key.json=./path/to/your-key.json \
  -n spark-dpdev
```

---

### Issue 3: Image Pull Errors

**Error:**
```
Failed to pull image "registry.kube.dp.ist.bns/dp-spark:4.0.1-v0.2"
```

**Solutions:**

1. **Verify image exists:**
```bash
# Check if image is accessible
docker pull registry.kube.dp.ist.bns/dp-spark:4.0.1-v0.2
```

2. **Check image pull policy:**
```yaml
# In values file
image:
  repository: registry.kube.dp.ist.bns/dp-spark
  tag: 4.0.1-v0.2
  pullPolicy: IfNotPresent  # Try 'Always' if image updated
```

3. **Add image pull secrets (if private registry):**
```yaml
# In values file
image:
  repository: registry.kube.dp.ist.bns/dp-spark
  tag: 4.0.1-v0.2
  pullPolicy: IfNotPresent
  pullSecrets:
    - name: dp-registry-secret
```

Then create the secret:
```bash
kubectl create secret docker-registry dp-registry-secret \
  --docker-server=registry.kube.dp.ist.bns \
  --docker-username=<username> \
  --docker-password=<password> \
  -n spark-dpdev
```

---

### Issue 4: Pods Stuck in Pending

**Symptoms:**
```bash
kubectl get pods -n spark-dpdev
# NAME                            READY   STATUS    RESTARTS   AGE
# spark-master-xxx                0/1     Pending   0          2m
```

**Diagnosis:**
```bash
# Check pod events
kubectl describe pod -n spark-dpdev <pod-name>

# Look for:
# - Insufficient CPU/memory
# - Node affinity not satisfied
# - PVC not bound
```

**Solutions:**

1. **Reduce resource requests:**
```yaml
# In values file - reduce requests if cluster has limited resources
master:
  resources:
    requests:
      memory: "2Gi"   # ← Reduce from 10Gi
      cpu: "500m"
```

2. **Remove node selectors:**
```yaml
# Comment out if nodes don't have the label
# master:
#   nodeSelector:
#     node-role: spark-master
```

---

### Issue 5: Workers Not Connecting to Master

**Symptoms:**
- Workers show as running
- Master UI shows 0 workers

**Diagnosis:**
```bash
# Check worker logs
kubectl logs -n spark-dpdev -l app=spark-worker

# Look for connection errors
```

**Solutions:**

1. **Check SPARK_MASTER_URL:**
```bash
kubectl exec -n spark-dpdev <worker-pod> -- env | grep SPARK_MASTER_URL
# Should be: spark://spark-master:7077
```

2. **Verify master service exists:**
```bash
kubectl get svc -n spark-dpdev spark-master
```

3. **Check network policies:**
```bash
# Ensure no network policies blocking traffic
kubectl get networkpolicies -n spark-dpdev
```

---

### Issue 6: GCS Access Denied

**Error in Spark logs:**
```
com.google.cloud.hadoop.util.AccessTokenProvider$AccessTokenProviderException:
Failed to get access token
```

**Solutions:**

1. **Verify GCS key file is mounted:**
```bash
kubectl exec -n spark-dpdev <master-pod> -- \
  ls -la /opt/spark/conf/gcs-json-key/

# Should show: gcs-key.json
```

2. **Check key file content:**
```bash
kubectl exec -n spark-dpdev <master-pod> -- \
  cat /opt/spark/conf/gcs-json-key/gcs-key.json | head -5

# Should show valid JSON with "type": "service_account"
```

3. **Verify service account permissions in GCP:**
   - Go to IAM & Admin > Service Accounts
   - Check the service account has:
     - `Storage Object Admin` (for read/write)
     - `Storage Object Viewer` (for read-only)

4. **Test GCS access:**
```bash
# Get pod name
MASTER_POD=$(kubectl get pod -n spark-dpdev -l app=spark-master -o jsonpath='{.items[0].metadata.name}')

# Test with Spark shell
kubectl exec -n spark-dpdev -it ${MASTER_POD} -- \
  /opt/spark/bin/spark-shell

# In Spark shell:
scala> spark.range(10).write.parquet("gs://dev-spark-bucket/test/")
```

---

### Issue 7: Worker Memory Parse Error

**Error:**
```
java.lang.NumberFormatException: Size must be specified as bytes (b), kibibytes (k), mebibytes (m), gibibytes (g), tebibytes (t), or pebibytes(p).
Failed to parse byte string: 8Gi # Leave ~2GB for overhead (10Gi limit - 8G = 2GB)
```

**Root Causes:**
1. **Inline comments captured by YAML parser** - Comments on the same line as value are included in the parsed value
2. **Wrong memory unit** - Spark expects lowercase `g` (gigabytes), not Kubernetes-style `Gi` (gibibytes)

**Solutions:**

1. **Use lowercase `g` notation for Spark memory:**
```yaml
worker:
  env:
    SPARK_WORKER_MEMORY: "8g"  # Correct: lowercase 'g'
    # NOT: "8Gi"               # Wrong: Kubernetes notation
    # NOT: "8G"                # Wrong: uppercase
```

2. **Move comments above the line, never inline:**
```yaml
# GOOD: Comment on separate line
worker:
  # Leave ~2GB overhead: if limit is 10Gi, use 8g for SPARK_WORKER_MEMORY
  env:
    SPARK_WORKER_MEMORY: "8g"

# BAD: Inline comment will be captured
worker:
  env:
    SPARK_WORKER_MEMORY: "8g"  # Leave ~2GB overhead <- YAML parser captures this!
```

3. **Memory sizing formula:**
   - Pod Memory Limit (Kubernetes, use `Gi` notation) = Kubernetes resource constraint
   - SPARK_WORKER_MEMORY (Spark, use `g` notation) = Pod Limit - Overhead (~2GB)

   Examples:
   ```yaml
   resources:
     limits:
       memory: "10Gi"     # Kubernetes limit (Gi notation)
   env:
     SPARK_WORKER_MEMORY: "8g"   # Spark memory (g notation), leaves 2GB overhead

   resources:
     limits:
       memory: "32Gi"     # Kubernetes limit
   env:
     SPARK_WORKER_MEMORY: "28g"  # Spark memory, leaves 4GB overhead
   ```

4. **Valid Spark memory units:**
   - `8g` or `8192m` - 8 gigabytes (lowercase only)
   - `16384m` - 16 gigabytes in megabytes
   - NOT `8Gi`, `8G`, `8GB` - These are invalid

**Verify Fix:**
```bash
# Check generated YAML has correct value
cat generated/spark-worker.yaml | grep SPARK_WORKER_MEMORY

# Should show (no comment, lowercase g):
# - name: SPARK_WORKER_MEMORY
#   value: "8g"
```

---

### Issue 8: Out of Memory Errors

**Error:**
```
Container killed due to memory limit
```

**Solutions:**

1. **Increase pod memory limits:**
```yaml
master:
  resources:
    limits:
      memory: "16Gi"  # ← Increase limit

worker:
  resources:
    limits:
      memory: "32Gi"  # ← Increase limit
```

2. **Reduce Spark memory allocation:**
```yaml
worker:
  env:
    SPARK_WORKER_MEMORY: "8g"  # ← Should be < pod limit (leave ~2GB for overhead)
```

3. **Formula:** Pod Memory Limit ≥ SPARK_WORKER_MEMORY + 2GB overhead

---

## Quick Recovery Commands

### Clean Reinstall (Recommended)

```bash
# 1. Clean up existing deployment (keeps namespace & secrets)
./cleanup.sh values-dev-dp.yaml

# 2. Clean up generated files
rm -rf generated/

# 3. Reinstall
./deploy.sh values-dev-dp.yaml install
```

### Complete Reinstall (Full Cleanup)

```bash
# 1. Remove existing deployment
./deploy.sh values-dev-dp.yaml uninstall

# 2. Remove namespace (optional - removes secrets too)
kubectl delete namespace spark-dpdev

# 3. Recreate namespace and secrets
kubectl create namespace spark-dpdev
kubectl create secret generic gcs-json-key \
  --from-file=gcs-key.json=./path/to/your-key.json \
  -n spark-dpdev

# 4. Clean up generated files
rm -rf generated/

# 5. Reinstall
./deploy.sh values-dev-dp.yaml install
```

### Update After Values Change

```bash
# Edit values
vi values-dev-dp.yaml

# Upgrade deployment
./deploy.sh values-dev-dp.yaml upgrade

# Force pod restart
kubectl rollout restart deployment/spark-master -n spark-dpdev
kubectl rollout restart deployment/spark-worker -n spark-dpdev
```

### Check All Resources

```bash
# Namespace
kubectl get namespace spark-dpdev

# Secrets
kubectl get secrets -n spark-dpdev

# Deployments
kubectl get deployments -n spark-dpdev

# Pods
kubectl get pods -n spark-dpdev

# Services
kubectl get svc -n spark-dpdev

# Events (last hour)
kubectl get events -n spark-dpdev --sort-by='.lastTimestamp'
```

### View All Logs

```bash
# Master logs
kubectl logs -n spark-dpdev -l app=spark-master --tail=100 -f

# Worker logs (all workers)
kubectl logs -n spark-dpdev -l app=spark-worker --tail=100 -f

# Specific pod
kubectl logs -n spark-dpdev <pod-name> --tail=100 -f
```

---

## Debugging Workflow

1. **Check pod status:**
   ```bash
   kubectl get pods -n spark-dpdev
   ```

2. **If pending, check events:**
   ```bash
   kubectl describe pod -n spark-dpdev <pod-name>
   ```

3. **If running but not working, check logs:**
   ```bash
   kubectl logs -n spark-dpdev <pod-name>
   ```

4. **If logs show GCS errors, verify secret:**
   ```bash
   kubectl get secret gcs-json-key -n spark-dpdev
   kubectl exec -n spark-dpdev <pod> -- ls -la /opt/spark/conf/gcs-json-key/
   ```

5. **If network issues, check services:**
   ```bash
   kubectl get svc -n spark-dpdev
   kubectl get endpoints -n spark-dpdev
   ```

---

## Getting Help

1. **Export all diagnostics:**
   ```bash
   # Save to file for troubleshooting
   kubectl get all -n spark-dpdev > diagnostics.txt
   kubectl describe pods -n spark-dpdev >> diagnostics.txt
   kubectl logs -n spark-dpdev -l app=spark-master --tail=200 >> diagnostics.txt
   kubectl logs -n spark-dpdev -l app=spark-worker --tail=200 >> diagnostics.txt
   ```

2. **Check generated YAMLs:**
   ```bash
   # Review what was actually deployed
   cat generated/spark-master.yaml
   cat generated/spark-worker.yaml
   ```

3. **Validate values file:**
   ```bash
   # Check for syntax errors
   cat values-dev-dp.yaml
   ```
