# Quick Start - Exact Commands

This guide provides the **exact commands** to deploy Spark standalone cluster with GCS to Kubernetes.

## ✅ Prerequisites

- [ ] Kubernetes cluster running
- [ ] `kubectl` configured
- [ ] GCS service account key JSON file downloaded
- [ ] Docker image `whereq/spark:4.0.1-gcs` built
- [ ] `envsubst` installed (`brew install gettext` on macOS)

---

## 🚀 Deploy to Kubernetes - Exact Commands

### Step 1: Create Namespace

```bash
kubectl create namespace spark-platform
```

### Step 2: Create GCS Secret

**You mentioned you already have the secret**, but for reference:

```bash
# Create secret from your GCS key file
kubectl create secret generic gcs-service-account-key \
  --from-file=gcs-key.json=./path/to/your-gcs-key.json \
  -n spark-platform

# Verify secret exists
kubectl get secret gcs-service-account-key -n spark-platform
```

### Step 3: Configure Your Values

**Edit `values-dev.yaml` with YOUR settings:**

```bash
cd k8s/standalone-gcs

# Copy the example
cp values.yaml values-my-deploy.yaml

# Edit with your settings
vi values-my-deploy.yaml
```

**Update these values:**

```yaml
gcs:
  secretName: gcs-service-account-key    # ← Should match your secret name
  bucket: "YOUR-GCS-BUCKET-NAME"         # ← Change this
  projectId: "YOUR-GCP-PROJECT-ID"       # ← Change this
  serviceAccountEmail: "spark-sa@YOUR-PROJECT.iam.gserviceaccount.com"
```

### Step 4: Deploy Spark Cluster

```bash
# Make deploy script executable (if not already)
chmod +x deploy.sh

# Deploy!
./deploy.sh values-my-deploy.yaml install
```

---

## 📋 Full Command Sequence

**Copy-paste these commands** (after editing values-my-deploy.yaml):

```bash
# 1. Navigate to deployment directory
cd /home/whereq/git/spark-facade/k8s/standalone-gcs

# 2. Create namespace (if not exists)
kubectl create namespace spark-platform

# 3. Verify GCS secret exists
kubectl get secret gcs-service-account-key -n spark-platform

# 4. Edit your values file
vi values-my-deploy.yaml

# 5. Deploy Spark cluster
./deploy.sh values-my-deploy.yaml install

# 6. Watch pods start
kubectl get pods -n spark-platform -w
```

---

## 🔍 Verify Deployment

```bash
# Check deployment status
./deploy.sh values-my-deploy.yaml status

# Or manually check
kubectl get pods -n spark-platform
kubectl get svc -n spark-platform
```

**Expected output:**

```
NAME                            READY   STATUS    RESTARTS   AGE
spark-master-xxxxxxxxxx-xxxxx   1/1     Running   0          2m
spark-worker-xxxxxxxxxx-xxxxx   1/1     Running   0          2m
spark-worker-xxxxxxxxxx-xxxxx   1/1     Running   0          2m
spark-worker-xxxxxxxxxx-xxxxx   1/1     Running   0          2m
```

---

## 🌐 Access Spark Master UI

```bash
# Port forward to your local machine
kubectl port-forward -n spark-platform svc/spark-master 8080:8080

# Open browser to http://localhost:8080
```

---

## ✅ Test GCS Access

```bash
# Get master pod name
MASTER_POD=$(kubectl get pod -n spark-platform -l app=spark-master -o jsonpath='{.items[0].metadata.name}')

# Check GCS environment variables
kubectl exec -n spark-platform ${MASTER_POD} -- env | grep GCS

# Check GCS key file is mounted
kubectl exec -n spark-platform ${MASTER_POD} -- \
  ls -la /opt/spark/conf/gcs-json-key/

# Test Spark shell with GCS
kubectl exec -n spark-platform -it ${MASTER_POD} -- \
  /opt/spark/bin/spark-shell

# In Spark shell:
# scala> spark.range(100).write.parquet("gs://YOUR-BUCKET/test-output/")
```

---

## 🔄 Update Deployment

```bash
# Edit values
vi values-my-deploy.yaml

# Apply changes
./deploy.sh values-my-deploy.yaml upgrade
```

---

## 🗑️ Remove Deployment

### Quick Cleanup (Recommended)

Remove only master and worker pods while keeping namespace and secrets:

```bash
# Make cleanup script executable
chmod +x cleanup.sh

# Clean up deployments
./cleanup.sh values-my-deploy.yaml

# What gets removed:
#   ✓ Spark Master & Worker Deployments
#   ✓ Services
#   ✓ Pods
#
# What stays:
#   ✓ Namespace
#   ✓ GCS Secret (for redeployment)
```

### Complete Removal

To remove everything including namespace:

```bash
# Option 1: Use deploy.sh
./deploy.sh values-my-deploy.yaml uninstall
kubectl delete namespace spark-platform

# Option 2: Delete namespace directly (removes everything)
kubectl delete namespace spark-platform
```

### Cleanup and Redeploy

```bash
# Clean up current deployment
./cleanup.sh values-my-deploy.yaml

# Make changes to values
vi values-my-deploy.yaml

# Redeploy with new settings
./deploy.sh values-my-deploy.yaml install
```

---

## 📝 Example: Complete Deployment Script

Save this as `my-deploy.sh` for automation:

```bash
#!/bin/bash
set -e

# Configuration
NAMESPACE="spark-platform"
SECRET_NAME="gcs-service-account-key"
GCS_KEY_FILE="./my-gcs-key.json"
VALUES_FILE="values-my-deploy.yaml"

echo "=== Deploying Spark Standalone with GCS ==="

# 1. Create namespace
echo "Creating namespace..."
kubectl create namespace ${NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -

# 2. Create/update GCS secret
echo "Creating GCS secret..."
kubectl create secret generic ${SECRET_NAME} \
  --from-file=gcs-key.json=${GCS_KEY_FILE} \
  -n ${NAMESPACE} \
  --dry-run=client -o yaml | kubectl apply -f -

# 3. Deploy Spark cluster
echo "Deploying Spark cluster..."
cd k8s/standalone-gcs
./deploy.sh ${VALUES_FILE} install

echo "=== Deployment Complete! ==="
echo "Check status: ./deploy.sh ${VALUES_FILE} status"
echo "Access UI: kubectl port-forward -n ${NAMESPACE} svc/spark-master 8080:8080"
```

---

## 🎯 What You Asked For

You asked: **"what's the exact command I should use to deploy the spark cluster to k8s as standalone mode?"**

**Answer:**

```bash
cd k8s/standalone-gcs
./deploy.sh values-my-deploy.yaml install
```

That's it! The script handles:
- ✅ Validating GCS secret exists
- ✅ Processing templates with your values
- ✅ Deploying namespace, master, and workers
- ✅ Showing deployment status

---

## 💡 Pro Tips

1. **Dry Run First**
   ```bash
   # Check what will be created
   cat generated/spark-master.yaml
   cat generated/spark-worker.yaml
   ```

2. **Watch Deployment Progress**
   ```bash
   kubectl get pods -n spark-platform -w
   ```

3. **Check Logs**
   ```bash
   ./deploy.sh values-my-deploy.yaml logs master
   ./deploy.sh values-my-deploy.yaml logs worker
   ```

4. **Scale Workers**
   ```bash
   # Edit values file: worker.replicas: 10
   vi values-my-deploy.yaml
   ./deploy.sh values-my-deploy.yaml upgrade
   ```

---

## 🆘 Troubleshooting

**Error: "GCS secret not found"**
```bash
# Create the secret
kubectl create secret generic gcs-service-account-key \
  --from-file=gcs-key.json=./your-key.json \
  -n spark-platform
```

**Error: "envsubst: command not found"**
```bash
# macOS
brew install gettext

# Ubuntu/Debian
sudo apt-get install gettext-base
```

**Pods not starting?**
```bash
# Check pod events
kubectl describe pod -n spark-platform <pod-name>

# Check logs
kubectl logs -n spark-platform <pod-name>
```

---

## 📞 Need Help?

See the full README.md for detailed documentation and troubleshooting.
