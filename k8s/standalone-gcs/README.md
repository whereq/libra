# Spark Standalone Cluster with GCS - Kubernetes Deployment

This directory contains a production-ready deployment configuration for Spark standalone cluster with GCS support.

## 📋 Features

- ✅ **Values-based configuration** (no hardcoded settings)
- ✅ **Environment-specific** (dev, staging, prod)
- ✅ **GCS credentials** via Kubernetes Secrets
- ✅ **Automated deployment** script
- ✅ **Template-based** YAML generation
- ✅ **Best practices** security and resource management

## 📁 Directory Structure

```
k8s/standalone-gcs/
├── values.yaml              # Default configuration values
├── values-dev.yaml          # Development environment
├── values-prod.yaml         # Production environment
├── deploy.sh                # Automated deployment script
├── cleanup.sh               # Cleanup script (removes master/worker only)
├── templates/               # YAML templates with placeholders
│   ├── namespace.yaml
│   ├── spark-master.yaml
│   └── spark-worker.yaml
├── generated/               # Generated YAMLs (created by deploy.sh)
├── README.md                # Complete documentation
├── QUICK-START.md           # Quick start guide with exact commands
└── TROUBLESHOOTING.md       # Troubleshooting guide
```

## 🚀 Quick Start

### Prerequisites

1. **Kubernetes cluster** running and `kubectl` configured
2. **GCS service account key** JSON file
3. **Docker image** `whereq/spark:4.0.1-gcs` built and available

### Step 1: Create GCS Service Account Key Secret

```bash
# Create namespace first
kubectl create namespace spark-platform

# Create secret from your GCS key file
kubectl create secret generic gcs-service-account-key \
  --from-file=gcs-key.json=./path/to/your-gcs-key.json \
  -n spark-platform

# Verify secret created
kubectl get secret gcs-service-account-key -n spark-platform
```

### Step 2: Configure Your Environment

Edit `values-dev.yaml` (or create your own):

```yaml
namespace: spark-platform

gcs:
  secretName: gcs-service-account-key
  bucket: "my-spark-bucket"              # ← Change this
  projectId: "my-gcp-project-id"         # ← Change this
  serviceAccountEmail: "spark-sa@my-gcp-project-id.iam.gserviceaccount.com"

# ... rest of configuration
```

### Step 3: Deploy Spark Cluster

```bash
# Navigate to deployment directory
cd k8s/standalone-gcs

# Deploy with dev configuration
./deploy.sh values-dev.yaml install
```

## 📝 Exact Commands

### Development Deployment

```bash
# 1. Create namespace
kubectl create namespace spark-platform

# 2. Create GCS secret
kubectl create secret generic gcs-service-account-key \
  --from-file=gcs-key.json=./my-dev-gcs-key.json \
  -n spark-platform

# 3. Edit values-dev.yaml with your settings
vi values-dev.yaml

# 4. Deploy
cd k8s/standalone-gcs
./deploy.sh values-dev.yaml install

# 5. Check status
./deploy.sh values-dev.yaml status
```

### Production Deployment

```bash
# 1. Create production namespace
kubectl create namespace spark-platform-prod

# 2. Create GCS secret in prod namespace
kubectl create secret generic gcs-service-account-key \
  --from-file=gcs-key.json=./my-prod-gcs-key.json \
  -n spark-platform-prod

# 3. Edit values-prod.yaml
vi values-prod.yaml

# 4. Deploy to production
./deploy.sh values-prod.yaml install

# 5. Verify deployment
./deploy.sh values-prod.yaml status
kubectl get pods -n spark-platform-prod -w
```

## 🔧 Deploy Script Usage

```bash
./deploy.sh [values-file] [action]
```

### Actions

| Action | Description | Example |
|--------|-------------|---------|
| `install` | Deploy Spark cluster | `./deploy.sh values-dev.yaml install` |
| `upgrade` | Update existing deployment | `./deploy.sh values-dev.yaml upgrade` |
| `uninstall` | Remove Spark cluster | `./deploy.sh values-dev.yaml uninstall` |
| `status` | Show deployment status | `./deploy.sh values-dev.yaml status` |
| `logs` | View component logs | `./deploy.sh values-dev.yaml logs master` |

### Examples

```bash
# Deploy development environment
./deploy.sh values-dev.yaml install

# Deploy production environment
./deploy.sh values-prod.yaml install

# Check deployment status
./deploy.sh values-dev.yaml status

# View master logs
./deploy.sh values-dev.yaml logs master

# View worker logs
./deploy.sh values-dev.yaml logs worker

# Upgrade deployment (after changing values)
vi values-dev.yaml
./deploy.sh values-dev.yaml upgrade

# Remove deployment
./deploy.sh values-dev.yaml uninstall
```

## 🧹 Cleanup Commands

### Quick Cleanup (Recommended)

Use the dedicated cleanup script to remove only master and worker pods while preserving namespace and secrets:

```bash
# Clean up deployments only
./cleanup.sh values-dev.yaml

# What gets removed:
#   ✓ Spark Master Deployment & Service
#   ✓ Spark Worker Deployment & Service
#   ✓ All associated Pods
#
# What gets preserved:
#   ✓ Namespace
#   ✓ GCS Secret
#   ✓ ConfigMaps
#   ✓ PersistentVolumes
```

The cleanup script will:
1. Ask for confirmation before removing resources
2. Show what will be removed and what will be preserved
3. Display remaining resources after cleanup
4. Provide commands for redeployment

### Complete Removal

To completely remove everything including namespace and secrets:

```bash
# Option 1: Use deploy.sh uninstall + manual namespace cleanup
./deploy.sh values-dev.yaml uninstall
kubectl delete namespace spark-platform

# Option 2: Direct kubectl commands
kubectl delete namespace spark-platform
# This removes everything in the namespace
```

### Cleanup Examples

```bash
# Development environment cleanup
cd k8s/standalone-gcs
./cleanup.sh values-dev.yaml

# Production environment cleanup (with confirmation)
./cleanup.sh values-prod.yaml

# Redeploy after cleanup
./deploy.sh values-dev.yaml install

# Complete removal with namespace
./cleanup.sh values-dev.yaml
kubectl delete namespace spark-platform
```

### Manual Cleanup (Alternative)

If you prefer to clean up manually:

```bash
# Delete deployments only
kubectl delete deployment spark-master spark-worker -n spark-platform

# Delete services only
kubectl delete service spark-master spark-worker -n spark-platform

# Delete specific pods (they will be recreated by deployment)
kubectl delete pod -l app=spark-master -n spark-platform
kubectl delete pod -l app=spark-worker -n spark-platform

# Check what remains
kubectl get all -n spark-platform
```

## 📊 Accessing Spark UIs

### Spark Master UI

```bash
# Port forward to local machine
kubectl port-forward -n spark-platform svc/spark-master 8080:8080

# Open browser
open http://localhost:8080
```

### Spark Worker UI

```bash
# Get worker pod name
WORKER_POD=$(kubectl get pod -n spark-platform -l app=spark-worker -o jsonpath='{.items[0].metadata.name}')

# Port forward
kubectl port-forward -n spark-platform ${WORKER_POD} 8081:8081

# Open browser
open http://localhost:8081
```

## 🔍 Verification & Testing

### 1. Check Pods are Running

```bash
kubectl get pods -n spark-platform

# Expected output:
# NAME                            READY   STATUS    RESTARTS   AGE
# spark-master-xxxxxxxxxx-xxxxx   1/1     Running   0          2m
# spark-worker-xxxxxxxxxx-xxxxx   1/1     Running   0          2m
# spark-worker-xxxxxxxxxx-xxxxx   1/1     Running   0          2m
```

### 2. Verify GCS Configuration

```bash
# Get master pod name
MASTER_POD=$(kubectl get pod -n spark-platform -l app=spark-master -o jsonpath='{.items[0].metadata.name}')

# Check environment variables
kubectl exec -n spark-platform ${MASTER_POD} -- env | grep GCS

# Expected output:
# GCS_BUCKET=my-spark-bucket
# GCS_PROJECT_ID=my-gcp-project-id

# Check GCS key file is mounted
kubectl exec -n spark-platform ${MASTER_POD} -- \
  ls -la /opt/spark/conf/gcs-json-key/

# Expected output:
# -r-------- 1 spark root 2345 ... gcs-key.json
```

### 3. Test GCS Access

```bash
# Access Spark shell
kubectl exec -n spark-platform -it ${MASTER_POD} -- \
  /opt/spark/bin/spark-shell

# In Spark shell, test GCS read:
scala> val df = spark.read.text("gs://my-spark-bucket/test.txt")
scala> df.show()

# Or test write:
scala> spark.range(100).write.parquet("gs://my-spark-bucket/test-output/")
```

### 4. Test with gcloud CLI (if installed with INSTALL_GCLOUD=true)

```bash
# Authenticate with service account
kubectl exec -n spark-platform ${MASTER_POD} -- \
  gcloud auth activate-service-account \
  --key-file=/opt/spark/conf/gcs-json-key/gcs-key.json

# List bucket contents
kubectl exec -n spark-platform ${MASTER_POD} -- \
  gsutil ls gs://my-spark-bucket/
```

## 🔄 Updating Configuration

### Modify Existing Deployment

```bash
# 1. Edit values file
vi values-dev.yaml

# Example: Change worker replicas from 2 to 5
# worker:
#   replicas: 5

# 2. Apply changes
./deploy.sh values-dev.yaml upgrade

# 3. Verify
kubectl get pods -n spark-platform -l app=spark-worker
```

### Update GCS Bucket

```bash
# 1. Edit values file
vi values-dev.yaml
# Change: bucket: "new-spark-bucket"

# 2. Upgrade deployment
./deploy.sh values-dev.yaml upgrade

# 3. Restart pods to pick up new env vars
kubectl rollout restart deployment/spark-master -n spark-platform
kubectl rollout restart deployment/spark-worker -n spark-platform
```

### Rotate GCS Service Account Key

```bash
# 1. Create new key in GCP console and download

# 2. Update secret
kubectl create secret generic gcs-service-account-key \
  --from-file=gcs-key.json=./new-gcs-key.json \
  --dry-run=client -o yaml | kubectl apply -n spark-platform -f -

# 3. Restart pods
kubectl rollout restart deployment/spark-master -n spark-platform
kubectl rollout restart deployment/spark-worker -n spark-platform
```

## 🐛 Troubleshooting

### Pods Not Starting

```bash
# Check pod status
kubectl get pods -n spark-platform

# Describe pod for events
kubectl describe pod -n spark-platform <pod-name>

# Check logs
kubectl logs -n spark-platform <pod-name>
```

### GCS Secret Not Found Error

```bash
# Error: secret "gcs-service-account-key" not found

# Solution: Create the secret
kubectl create secret generic gcs-service-account-key \
  --from-file=gcs-key.json=./your-key.json \
  -n spark-platform
```

### GCS Access Denied

```bash
# Check GCS key file is mounted
kubectl exec -n spark-platform ${MASTER_POD} -- \
  cat /opt/spark/conf/gcs-json-key/gcs-key.json | jq .

# Verify service account has permissions
# In GCP Console:
# IAM & Admin > Service Accounts > Check roles:
#   - Storage Object Admin (for read/write)
#   - Storage Object Viewer (for read-only)
```

### Workers Not Connecting to Master

```bash
# Check master service
kubectl get svc -n spark-platform spark-master

# Check worker logs
kubectl logs -n spark-platform -l app=spark-worker

# Verify SPARK_MASTER_URL
kubectl exec -n spark-platform <worker-pod> -- env | grep SPARK_MASTER_URL
```

## 📚 Configuration Reference

### values.yaml Structure

```yaml
namespace: string              # Kubernetes namespace
gcs:
  secretName: string          # K8s Secret name for GCS key
  bucket: string              # GCS bucket name
  projectId: string           # GCP project ID
image:
  repository: string          # Docker image repository
  tag: string                 # Docker image tag
  pullPolicy: string          # Always, IfNotPresent, Never
master:
  replicas: int               # Number of master replicas (1 or 3)
  resources:
    requests:
      memory: string          # e.g., "2Gi"
      cpu: string             # e.g., "1000m"
    limits:
      memory: string
      cpu: string
worker:
  replicas: int               # Number of worker replicas
  env:
    SPARK_WORKER_CORES: string
    SPARK_WORKER_MEMORY: string
  resources:
    requests:
      memory: string
      cpu: string
    limits:
      memory: string
      cpu: string
```

## 🔐 Security Best Practices

1. **Never commit GCS keys** to version control
2. **Use RBAC** to limit access to GCS secrets
3. **Rotate keys regularly** (quarterly recommended)
4. **Use minimal IAM permissions** for service accounts
5. **Enable Pod Security Policies** in production
6. **Use separate namespaces** per environment
7. **Audit secret access** via K8s audit logs

## 🚀 Advanced Usage

### Multi-Environment Deployment

```bash
# Deploy to multiple environments
for env in dev staging prod; do
  ./deploy.sh values-${env}.yaml install
done
```

### Custom Values File

```bash
# Create custom values
cp values.yaml values-custom.yaml
vi values-custom.yaml

# Deploy with custom values
./deploy.sh values-custom.yaml install
```

### Integration with CI/CD

```yaml
# GitLab CI example
deploy:
  script:
    - kubectl create secret generic gcs-service-account-key \
        --from-file=gcs-key.json=$GCS_KEY_FILE \
        -n spark-platform \
        --dry-run=client -o yaml | kubectl apply -f -
    - cd k8s/standalone-gcs
    - ./deploy.sh values-${CI_ENVIRONMENT_NAME}.yaml upgrade
```

## 📖 Additional Resources

- [Kubernetes ConfigMaps](https://kubernetes.io/docs/concepts/configuration/configmap/)
- [Kubernetes Secrets](https://kubernetes.io/docs/concepts/configuration/secret/)
- [GCS Connector Documentation](https://github.com/GoogleCloudDataproc/hadoop-connectors)
- [Spark Standalone Mode](https://spark.apache.org/docs/latest/spark-standalone.html)
