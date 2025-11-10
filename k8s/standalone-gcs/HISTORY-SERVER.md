# Spark History Server Deployment Guide

The Spark History Server provides a web UI to view completed and running Spark applications by reading event logs from GCS.

## 📋 Overview

The History Server:
- Reads Spark application event logs from GCS (`gs://your-bucket/spark-events`)
- Provides historical data about completed applications
- Shows application metrics, stages, tasks, and execution details
- Accessible at port 18080 by default

## 🚀 Deployment

### Option 1: Enable in Values File (Recommended)

Edit your `values-dev-dp.yaml`:

```yaml
historyServer:
  enabled: true  # ← Set this to true
  replicas: 1

  service:
    type: ClusterIP
    port: 18080

  resources:
    requests:
      memory: "1Gi"
      cpu: "500m"
    limits:
      memory: "2Gi"
      cpu: "1000m"
```

Then deploy:

```bash
cd k8s/standalone-gcs

# Deploy with history server enabled
./deploy.sh values-dev-dp.yaml install
```

### Option 2: Upgrade Existing Deployment

If you already have master and workers running:

```bash
# 1. Edit values file to enable history server
vi values-dev-dp.yaml
# Set: historyServer.enabled: true

# 2. Upgrade deployment
./deploy.sh values-dev-dp.yaml upgrade
```

## 🔍 Verification

### Check History Server Pod

```bash
# Get history server pod
kubectl get pods -n spark-dpdev -l app=spark-history-server

# Expected output:
# NAME                        READY   STATUS    RESTARTS   AGE
# spark-history-server-0      1/1     Running   0          1m
```

### Check History Server Logs

```bash
# View history server logs
./deploy.sh values-dev-dp.yaml logs history

# Or directly
kubectl logs -n spark-dpdev -l app=spark-history-server -f
```

### Access History Server UI

```bash
# Port-forward to local machine
kubectl port-forward -n spark-dpdev svc/spark-history-server 18080:18080

# Open browser
open http://localhost:18080
```

## 📝 Event Log Configuration

### Master Configuration

The master automatically enables event logging when History Server is enabled. Event logs are written to:

```
gs://your-bucket/spark-events/
```

This is configured in `spark-defaults.conf`:

```properties
spark.eventLog.enabled=true
spark.eventLog.dir=gs://your-bucket/spark-events
spark.history.fs.logDirectory=gs://your-bucket/spark-events
```

### Verify Event Logs

```bash
# Check GCS bucket for event logs
gcloud storage ls gs://your-bucket/spark-events/

# Or from within master pod
kubectl exec -it spark-master-0 -n spark-dpdev -- \
  gcloud storage ls gs://gbmdp-bf3n-dev-devops-spark-cluster-3/spark-events/
```

## 🎯 Usage Examples

### View Running Applications

1. Submit a Spark application
2. Open History Server UI: `http://localhost:18080`
3. See the application listed while it's running

### View Completed Applications

1. After application completes, event log is written to GCS
2. History Server reads the log file
3. Application appears in History Server UI with full details

### Example: Run Test Application

```bash
# Submit a test job
kubectl exec -it spark-master-0 -n spark-dpdev -- \
  /opt/spark/bin/spark-submit \
  --class org.apache.spark.examples.SparkPi \
  --master spark://spark-master:7077 \
  /opt/spark/examples/jars/spark-examples_2.12-4.0.1.jar \
  1000

# Wait for completion, then check History Server
kubectl port-forward -n spark-dpdev svc/spark-history-server 18080:18080
# Open http://localhost:18080 to see the completed job
```

## 🔧 Configuration Options

### Resource Sizing

For development:
```yaml
historyServer:
  resources:
    requests:
      memory: "1Gi"
      cpu: "500m"
    limits:
      memory: "2Gi"
      cpu: "1000m"
```

For production:
```yaml
historyServer:
  resources:
    requests:
      memory: "2Gi"
      cpu: "1000m"
    limits:
      memory: "4Gi"
      cpu: "2000m"
```

### High Availability

For production HA setup:

```yaml
historyServer:
  enabled: true
  replicas: 2  # Multiple replicas for HA

  service:
    type: LoadBalancer  # Or use Ingress
    port: 18080
```

### Custom Event Log Directory

Override the default log directory:

```yaml
historyServer:
  enabled: true
  # In template, this becomes:
  # SPARK_HISTORY_OPTS="-Dspark.history.fs.logDirectory=gs://your-bucket/custom-path"
```

## 📊 History Server UI Features

### Application List

- Shows all completed and running applications
- Displays application name, duration, and status
- Clickable links to detailed views

### Application Details

- **Jobs**: All jobs run by the application
- **Stages**: Detailed stage information and DAG visualization
- **Storage**: RDD/DataFrame persistence information
- **Environment**: Configuration and system properties
- **Executors**: Executor metrics and logs
- **SQL**: SQL query execution plans (if using Spark SQL)

### Timeline View

- Visual timeline of application execution
- Shows stage parallelism and dependencies
- Identifies bottlenecks and performance issues

## 🛠️ Troubleshooting

### History Server Not Starting

```bash
# Check pod status
kubectl describe pod -n spark-dpdev -l app=spark-history-server

# Check logs
kubectl logs -n spark-dpdev -l app=spark-history-server

# Common issues:
# - GCS permission denied (check service account permissions)
# - Event log directory doesn't exist in GCS
# - Invalid SPARK_HISTORY_OPTS configuration
```

### No Applications Showing

```bash
# Verify event logging is enabled on master
kubectl exec -it spark-master-0 -n spark-dpdev -- \
  grep eventLog /opt/spark/conf/spark-defaults.conf

# Should show:
# spark.eventLog.enabled=true
# spark.eventLog.dir=gs://your-bucket/spark-events

# Check if event logs exist in GCS
gcloud storage ls gs://your-bucket/spark-events/

# If empty, run a test application first
```

### GCS Permission Denied

```bash
# Check service account has Storage Object Viewer role
gcloud projects get-iam-policy <project-id> \
  --flatten="bindings[].members" \
  --format="table(bindings.role)" \
  --filter="bindings.members:serviceAccount:your-sa@project.iam.gserviceaccount.com"

# Should have:
# roles/storage.objectViewer (or objectAdmin)

# Test GCS access from history server pod
kubectl exec -it spark-history-server-0 -n spark-dpdev -- \
  gcloud auth activate-service-account \
  --key-file=/opt/spark/conf/gcs-json-key/gcs-key.json

kubectl exec -it spark-history-server-0 -n spark-dpdev -- \
  gcloud storage ls gs://your-bucket/
```

## 📈 Performance Tips

### 1. Configure Log Retention

Limit how many completed applications to retain:

```properties
# In spark-defaults.conf or via SPARK_HISTORY_OPTS
spark.history.fs.cleaner.enabled=true
spark.history.fs.cleaner.interval=1d
spark.history.fs.cleaner.maxAge=7d
```

### 2. Enable Log Compression

Reduce storage costs:

```properties
spark.eventLog.compress=true
```

### 3. Increase Cache Size

For better performance with many applications:

```properties
spark.history.fs.cleaner.maxNum=1000
spark.history.retainedApplications=1000
```

## 🔐 Security Considerations

### Authentication

For production, consider adding authentication:

```yaml
historyServer:
  service:
    type: ClusterIP
  # Use Ingress with authentication
  # Or use OAuth2 Proxy
```

### GCS Permissions

Use least privilege:
- **Read-only** access to event log directory
- No write permissions needed
- Consider separate service account for history server

## 📚 Additional Resources

- [Spark History Server Documentation](https://spark.apache.org/docs/4.0.1/monitoring.html#viewing-after-the-fact)
- [Spark Event Logging](https://spark.apache.org/docs/4.0.1/configuration.html#spark-properties)
- [GCS Connector Documentation](https://github.com/GoogleCloudDataproc/hadoop-connectors)

## 🎯 Quick Reference

```bash
# Enable history server
vi values-dev-dp.yaml  # Set historyServer.enabled: true
./deploy.sh values-dev-dp.yaml install

# Check status
kubectl get pods -n spark-dpdev -l app=spark-history-server

# View logs
./deploy.sh values-dev-dp.yaml logs history

# Access UI
kubectl port-forward -n spark-dpdev svc/spark-history-server 18080:18080
open http://localhost:18080

# Disable history server
vi values-dev-dp.yaml  # Set historyServer.enabled: false
./deploy.sh values-dev-dp.yaml upgrade
```
