# Security Verification: No Credentials Baked Into Images

This document verifies that GCS credentials and environment-specific values are **NOT** hardcoded in Docker images.

## ✅ Verification Results

### 1. Docker Build Time (Dockerfile)

**File: `docker/gcs/Dockerfile`**

```dockerfile
# Line 70-71: Only TEMPLATE files are copied
COPY docker/gcs/conf/core-site.xml ${SPARK_HOME}/conf/core-site.xml.template
COPY docker/gcs/conf/spark-defaults.conf ${SPARK_HOME}/conf/spark-defaults.conf.template

# Line 74-76: Empty directory created (NO KEY FILE!)
RUN mkdir -p ${SPARK_HOME}/conf/gcs-json-key && \
    chown -R spark:root ${SPARK_HOME}/conf && \
    chmod -R g+rwX ${SPARK_HOME}/conf

# NO build arguments for bucket or project:
# ❌ ARG GCS_BUCKET          <- DOES NOT EXIST
# ❌ ARG GCS_PROJECT_ID      <- DOES NOT EXIST
# ❌ COPY gcs-key.json       <- DOES NOT EXIST
```

**Result:** ✅ **NO credentials or environment-specific values in image**

---

### 2. Runtime Configuration (entrypoint.sh)

**File: `docker/gcs/entrypoint.sh`**

```bash
# Lines 20-22: Read from RUNTIME environment variables
GCS_BUCKET=${GCS_BUCKET:-}           # From K8s ConfigMap
GCS_PROJECT_ID=${GCS_PROJECT_ID:-}   # From K8s ConfigMap
GCS_KEY_FILE=${GCS_KEY_FILE:-/opt/spark/conf/gcs-json-key/gcs-key.json}

# Lines 34-75: Substitute placeholders AT RUNTIME
if [ -n "${GCS_BUCKET}" ] || [ -n "${GCS_PROJECT_ID}" ]; then
    echo "Processing GCS configuration templates..."

    # Copy .template files to active configs
    cp "${SPARK_HOME}/conf/core-site.xml.template" "${SPARK_HOME}/conf/core-site.xml"

    # Replace ${GCS_PROJECT_ID} placeholder
    sed -i "s|\${GCS_PROJECT_ID}|${GCS_PROJECT_ID}|g" "${SPARK_HOME}/conf/core-site.xml"

    # Append runtime GCS_BUCKET configuration
    echo "spark.hadoop.fs.default.name=gs://${GCS_BUCKET}/" >> "${SPARK_HOME}/conf/spark-defaults.conf"
fi
```

**Result:** ✅ **Configuration happens at runtime, not build time**

---

### 3. Configuration Templates

**File: `docker/gcs/conf/core-site.xml`**

```xml
<!-- Line 33: PLACEHOLDER, not actual value -->
<property>
    <name>fs.gs.project.id</name>
    <value>${GCS_PROJECT_ID}</value>  <!-- ← Replaced at runtime -->
</property>

<!-- Lines 54, 69: PATH to key file (not the file content) -->
<property>
    <name>fs.gs.auth.service.account.json.keyfile</name>
    <value>/opt/spark/conf/gcs-json-key/gcs-key.json</value>  <!-- ← Path only -->
</property>
```

**File: `docker/gcs/conf/spark-defaults.conf`**

```properties
# Lines 32-33: Commented PLACEHOLDERS
# spark.eventLog.dir=gs://${GCS_BUCKET}/spark_events           ← PLACEHOLDER
# spark.history.fs.logDirectory=gs://${GCS_BUCKET}/spark_events ← PLACEHOLDER

# Line 18: PATH to key file (not the file content)
spark.hadoop.fs.gs.auth.service.account.json.keyfile=/opt/spark/conf/gcs-json-key/gcs-key.json
```

**Result:** ✅ **Only placeholders and paths, no actual credentials**

---

### 4. Kubernetes Deployment (Runtime)

**File: `k8s/examples/spark-master-gcs.yaml`**

```yaml
containers:
- name: spark-master
  image: whereq/spark:4.0.1-gcs

  # Environment variables from ConfigMap (NOT in image!)
  env:
  - name: GCS_BUCKET
    valueFrom:
      configMapKeyRef:
        name: spark-gcs-config-dev
        key: GCS_BUCKET

  - name: GCS_PROJECT_ID
    valueFrom:
      configMapKeyRef:
        name: spark-gcs-config-dev
        key: GCS_PROJECT_ID

  volumeMounts:
  # GCS key file mounted from Secret (NOT in image!)
  - name: gcs-key
    mountPath: /opt/spark/conf/gcs-json-key
    readOnly: true

volumes:
# Secret containing service account key
- name: gcs-key
  secret:
    secretName: gcs-service-account-key
    items:
    - key: gcs-key.json
      path: gcs-key.json
      mode: 0400
```

**Result:** ✅ **Credentials provided at runtime via K8s Secret/ConfigMap**

---

## 🔒 Security Benefits

| Aspect | Build Time | Runtime |
|--------|------------|---------|
| **GCS Bucket** | ❌ Not in image | ✅ ConfigMap |
| **GCS Project ID** | ❌ Not in image | ✅ ConfigMap |
| **Service Account Key** | ❌ Not in image | ✅ Secret (file mount) |
| **Spark Config** | ✅ Templates only | ✅ ConfigMap override |

### Why This Design is Secure

1. **Single Image, Multiple Environments**: Same Docker image for dev/staging/prod
2. **No Credential Leakage**: Image can be pushed to public registries safely
3. **Easy Rotation**: Update K8s Secret/ConfigMap without rebuilding image
4. **Audit Trail**: K8s logs all Secret/ConfigMap changes
5. **RBAC Control**: Fine-grained access control via K8s RBAC

---

## 🧪 Verification Commands

### Build and Inspect Image

```bash
# Build GCS image
docker build -f docker/gcs/Dockerfile -t whereq/spark:4.0.1-gcs .

# Inspect for credentials (should find NONE)
docker run --rm whereq/spark:4.0.1-gcs find /opt/spark -name "gcs-key.json"
# Output: <empty> (directory exists but file doesn't)

docker run --rm whereq/spark:4.0.1-gcs cat /opt/spark/conf/core-site.xml.template
# Output: Contains ${GCS_PROJECT_ID} placeholder

docker run --rm whereq/spark:4.0.1-gcs env | grep GCS
# Output: <empty> (no GCS environment variables set)
```

### Runtime Configuration Check

```bash
# Deploy to K8s with ConfigMap and Secret
kubectl apply -f k8s/examples/gcs-configmap-secret.yaml
kubectl apply -f k8s/examples/spark-master-gcs.yaml

# Check runtime environment variables
kubectl exec -n spark-platform <spark-master-pod> -- env | grep GCS
# Output:
# GCS_BUCKET=my-spark-bucket          ← From ConfigMap
# GCS_PROJECT_ID=my-gcp-project-id    ← From ConfigMap

# Check key file is mounted
kubectl exec -n spark-platform <spark-master-pod> -- \
  ls -la /opt/spark/conf/gcs-json-key/
# Output: gcs-key.json (from Secret)

# Check effective configuration
kubectl exec -n spark-platform <spark-master-pod> -- \
  cat /opt/spark/conf/spark-defaults.conf
# Output: Contains actual bucket name, NOT placeholder
```

---

## 📋 Design Comparison

### ❌ BAD: Credentials Baked Into Image

```dockerfile
# NEVER DO THIS!
ARG GCS_BUCKET=my-bucket        # ← BAD: hardcoded
ARG GCS_PROJECT_ID=my-project   # ← BAD: hardcoded

COPY gcs-key.json /opt/spark/conf/gcs-json-key/  # ← VERY BAD!

RUN sed -i "s/BUCKET/${GCS_BUCKET}/g" core-site.xml  # ← BAD: baked in
```

**Problems:**
- 🔴 Different image per environment
- 🔴 Credentials in image layers (irrecoverable)
- 🔴 Can't push to registries
- 🔴 No credential rotation without rebuild

### ✅ GOOD: Runtime Configuration (Current Design)

```dockerfile
# What we actually do:
COPY core-site.xml.template /opt/spark/conf/  # ← Template with placeholders
RUN mkdir -p /opt/spark/conf/gcs-json-key     # ← Empty directory

# At runtime (entrypoint.sh):
# - Read GCS_BUCKET from environment
# - Read GCS_PROJECT_ID from environment
# - Mount gcs-key.json from K8s Secret
# - Substitute placeholders in templates
```

**Benefits:**
- ✅ Same image for all environments
- ✅ No credentials in image layers
- ✅ Safe to push to public registries
- ✅ Easy credential rotation (just update K8s Secret)

---

## 🎯 Conclusion

**VERIFIED ✅**: The current design is secure and follows best practices:

1. ✅ **No credentials in Docker images**
2. ✅ **Templates with placeholders at build time**
3. ✅ **Runtime substitution via entrypoint**
4. ✅ **K8s Secrets for sensitive data (key file)**
5. ✅ **K8s ConfigMaps for environment-specific config**
6. ✅ **Single image, multiple environments**

**This is the correct approach for container security!** 🔒
