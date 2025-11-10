# Kubernetes GCS Configuration Examples

This directory contains complete examples for deploying Spark with GCS support on Kubernetes.

## 🔐 Security Best Practices

**CRITICAL**: Credentials and environment-specific values are **NEVER** baked into Docker images!

- ✅ **Docker Build Time**: Only JARs, templates, and empty directories
- ✅ **Runtime**: Credentials provided via Kubernetes Secrets
- ✅ **Configuration**: Environment-specific values via ConfigMaps

## 📁 Files

| File | Description |
|------|-------------|
| `gcs-configmap-secret.yaml` | ConfigMaps and Secret definitions for GCS |
| `spark-master-gcs.yaml` | Spark Master deployment with GCS support |
| `spark-worker-gcs.yaml` | Spark Worker deployment with GCS support |

## 🚀 Quick Start

### 1. Create GCS Service Account Key

```bash
# In Google Cloud Console:
# 1. Go to IAM & Admin > Service Accounts
# 2. Create service account with roles:
#    - Storage Object Admin (for read/write)
#    - Storage Object Viewer (for read-only)
# 3. Create JSON key and download as gcs-key.json

# Verify key file
ls -lh gcs-key.json
```

### 2. Create Kubernetes Namespace

```bash
kubectl create namespace spark-platform
```

### 3. Create GCS Secret from Key File

```bash
# Create secret from your GCS service account key
kubectl create secret generic gcs-service-account-key \
  --from-file=gcs-key.json=./gcs-key.json \
  -n spark-platform

# Verify secret created
kubectl get secret gcs-service-account-key -n spark-platform

# Check secret content (base64 encoded)
kubectl get secret gcs-service-account-key -n spark-platform -o yaml
```

### 4. Create GCS ConfigMaps

```bash
# Create ConfigMaps for different environments
kubectl apply -f gcs-configmap-secret.yaml
```

### 5. Deploy Spark Master with GCS

```bash
# Deploy Spark Master
kubectl apply -f spark-master-gcs.yaml

# Check deployment
kubectl get pods -n spark-platform -l app=spark-master
kubectl logs -n spark-platform -l app=spark-master

# Check GCS configuration in pod
kubectl exec -n spark-platform -it <spark-master-pod> -- \
  cat /opt/spark/conf/spark-defaults.conf

# Verify GCS key file is mounted
kubectl exec -n spark-platform -it <spark-master-pod> -- \
  ls -la /opt/spark/conf/gcs-json-key/
```

### 6. Deploy Spark Worker with GCS

```bash
# Deploy Spark Workers (3 replicas)
kubectl apply -f spark-worker-gcs.yaml

# Check workers
kubectl get pods -n spark-platform -l app=spark-worker
```

### 7. Test GCS Access

```bash
# Get pod name
MASTER_POD=$(kubectl get pod -n spark-platform -l app=spark-master -o jsonpath='{.items[0].metadata.name}')

# Test gcloud CLI (if installed with INSTALL_GCLOUD=true)
kubectl exec -n spark-platform -it ${MASTER_POD} -- gcloud auth activate-service-account --key-file=/opt/spark/conf/gcs-json-key/gcs-key.json
kubectl exec -n spark-platform -it ${MASTER_POD} -- gsutil ls gs://your-bucket-name/

# Test Spark GCS access
kubectl exec -n spark-platform -it ${MASTER_POD} -- \
  /opt/spark/bin/spark-shell \
  --conf spark.hadoop.fs.gs.project.id=your-project-id

# In Spark shell:
# scala> val df = spark.read.parquet("gs://your-bucket/path/to/data.parquet")
# scala> df.show()
```

## 🔄 Environment-Specific Deployment

### Development

```bash
# Use dev ConfigMap
kubectl apply -f spark-master-gcs.yaml
# Edit deployment to use: spark-gcs-config-dev

# Or using kustomize
kubectl apply -k overlays/dev/
```

### Staging

```bash
# Edit deployments to reference: spark-gcs-config-staging
# Or update via patch:
kubectl patch deployment spark-master-gcs -n spark-platform \
  --type='json' \
  -p='[{"op": "replace", "path": "/spec/template/spec/containers/0/env/0/valueFrom/configMapKeyRef/name", "value":"spark-gcs-config-staging"}]'
```

### Production

```bash
# Edit deployments to reference: spark-gcs-config-prod
# Recommended: Use separate namespaces per environment
kubectl create namespace spark-platform-prod
kubectl apply -f gcs-configmap-secret.yaml -n spark-platform-prod
kubectl apply -f spark-master-gcs.yaml -n spark-platform-prod
```

## 🔐 Security Considerations

### 1. Secret Management

```bash
# Rotate service account keys regularly
# 1. Create new key in GCP
# 2. Update secret:
kubectl create secret generic gcs-service-account-key \
  --from-file=gcs-key.json=./new-gcs-key.json \
  --dry-run=client -o yaml | kubectl apply -f -

# 3. Restart pods to pick up new secret
kubectl rollout restart deployment/spark-master-gcs -n spark-platform
```

### 2. RBAC (Role-Based Access Control)

```bash
# Limit access to GCS secret
kubectl create role secret-reader \
  --verb=get,list \
  --resource=secrets \
  --resource-name=gcs-service-account-key \
  -n spark-platform

kubectl create rolebinding spark-secret-reader \
  --role=secret-reader \
  --serviceaccount=spark-platform:default \
  -n spark-platform
```

### 3. Workload Identity (GKE Alternative)

For GKE, you can use Workload Identity instead of service account keys:

```yaml
# spark-master-gcs.yaml modifications
spec:
  serviceAccountName: spark-gsa  # GKE service account
  containers:
  - name: spark-master
    env:
    # Remove GCS_KEY_FILE mounting
    - name: GOOGLE_APPLICATION_CREDENTIALS
      value: /var/run/secrets/workload-identity/token
```

## 📊 Monitoring and Troubleshooting

### Check GCS Configuration

```bash
# View effective Spark configuration
kubectl exec -n spark-platform ${MASTER_POD} -- \
  cat /opt/spark/conf/spark-defaults.conf

# Check environment variables
kubectl exec -n spark-platform ${MASTER_POD} -- env | grep GCS
```

### Debug GCS Connection Issues

```bash
# Check GCS key file
kubectl exec -n spark-platform ${MASTER_POD} -- \
  cat /opt/spark/conf/gcs-json-key/gcs-key.json | jq .

# Test network connectivity to GCS
kubectl exec -n spark-platform ${MASTER_POD} -- \
  curl -I https://storage.googleapis.com

# Check GCS connector JAR
kubectl exec -n spark-platform ${MASTER_POD} -- \
  ls -la /opt/spark/jars/ | grep gcs-connector
```

### View Logs

```bash
# Master logs
kubectl logs -n spark-platform -l app=spark-master --tail=100 -f

# Worker logs
kubectl logs -n spark-platform -l app=spark-worker --tail=100 -f

# Check for GCS-related errors
kubectl logs -n spark-platform ${MASTER_POD} | grep -i gcs
kubectl logs -n spark-platform ${MASTER_POD} | grep -i "google.cloud"
```

## 🧪 Validation Checklist

After deployment, verify:

- [ ] Secret `gcs-service-account-key` exists and contains valid JSON
- [ ] ConfigMaps created for all environments (dev/staging/prod)
- [ ] Spark Master pod is running
- [ ] Spark Worker pods are running
- [ ] GCS key file is mounted at `/opt/spark/conf/gcs-json-key/gcs-key.json`
- [ ] Environment variables `GCS_BUCKET` and `GCS_PROJECT_ID` are set
- [ ] Spark Master UI accessible (port-forward to 8080)
- [ ] Workers registered with Master
- [ ] Can list GCS buckets using gsutil (if gcloud CLI installed)
- [ ] Spark can read/write to GCS bucket

## 📚 Additional Resources

- [GCS Connector Documentation](https://github.com/GoogleCloudDataproc/hadoop-connectors)
- [Kubernetes Secrets](https://kubernetes.io/docs/concepts/configuration/secret/)
- [Kubernetes ConfigMaps](https://kubernetes.io/docs/concepts/configuration/configmap/)
- [GKE Workload Identity](https://cloud.google.com/kubernetes-engine/docs/how-to/workload-identity)
