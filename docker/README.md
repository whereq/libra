# WhereQ Spark Docker Images

Modular Docker build system for Apache Spark 4.0.1 with Python 3.14.0, supporting multiple cloud storage backends.

## 📁 Directory Structure

```
docker/
├── base/                     # Base Spark image (no cloud storage)
│   ├── Dockerfile
│   └── entrypoint.sh
├── gcs/                      # Google Cloud Storage variant
│   ├── Dockerfile
│   ├── entrypoint.sh
│   └── conf/
│       ├── core-site.xml
│       └── spark-defaults.conf
├── s3/                       # AWS S3 variant
│   ├── Dockerfile
│   ├── entrypoint.sh
│   └── conf/
│       ├── core-site.xml
│       └── spark-defaults.conf
├── minio/                    # MinIO (S3-compatible) variant
│   ├── Dockerfile
│   ├── entrypoint.sh
│   └── conf/
│       ├── core-site.xml
│       └── spark-defaults.conf
└── libra/                    # Libra REST API
    └── Dockerfile
```

## 🚀 Quick Start

### Using the Build Script (Recommended)

```bash
# Build base image
./bin/build-spark-image.sh --variant base

# Build GCS variant
./bin/build-spark-image.sh --variant gcs

# Build with secured artifactory authentication
./bin/build-spark-image.sh \
  --variant s3 \
  --https-user myuser \
  --https-pass mypass

# Build and push to registry
./bin/build-spark-image.sh \
  --variant minio \
  --tag myregistry.com/spark \
  --push
```

### Manual Build

```bash
# Build base image first
docker build -f docker/base/Dockerfile -t whereq/spark:4.0.1-base .

# Build GCS variant
docker build -f docker/gcs/Dockerfile -t whereq/spark:4.0.1-gcs .

# Build with authentication
docker build -f docker/s3/Dockerfile \
  --build-arg HTTPS_USERNAME=user \
  --build-arg HTTPS_PASSWORD=pass \
  -t whereq/spark:4.0.1-s3 .
```

## 📦 Image Variants

### Base (`base`)
- **Size**: ~800MB
- **Features**: Spark 4.0.1 + Python 3.14.0 + Java 21
- **Use case**: Local development, no cloud storage needed
- **Tag**: `whereq/spark:4.0.1-base`

### GCS (`gcs`)
- **Extends**: base
- **Added**: GCS connector (2.2.20)
- **Authentication**: Service Account JSON or Workload Identity
- **Use case**: Google Cloud Platform, GKE deployments
- **Tag**: `whereq/spark:4.0.1-gcs`

### S3 (`s3`)
- **Extends**: base
- **Added**: Hadoop AWS (3.3.6), AWS SDK (1.12.367)
- **Authentication**: Access Key/Secret or IAM Role
- **Use case**: AWS, EKS deployments
- **Tag**: `whereq/spark:4.0.1-s3`

### MinIO (`minio`)
- **Extends**: base
- **Added**: Same as S3, configured for path-style access
- **Authentication**: Access Key/Secret
- **Use case**: On-premise S3-compatible storage
- **Tag**: `whereq/spark:4.0.1-minio`

## 🔧 Configuration

### Environment Variables

#### Common (All Variants)
- `SPARK_MODE`: `master`, `worker`, `history`, `shell`, `submit`
- `SPARK_MASTER_HOST`: Master hostname (default: `spark-master`)
- `SPARK_MASTER_PORT`: Master port (default: `7077`)
- `SPARK_MASTER_WEBUI_PORT`: Master UI port (default: `8080`)
- `SPARK_WORKER_WEBUI_PORT`: Worker UI port (default: `8081`)
- `SPARK_WORKER_CORES`: Worker CPU cores
- `SPARK_WORKER_MEMORY`: Worker memory (e.g., `4g`)
- `UNSET_PROXY`: Set to `true` to unset proxy variables

#### GCS Variant
- `GCS_BUCKET`: GCS bucket name
- `GCS_PROJECT_ID`: Google Cloud project ID
- `GCS_KEY_FILE`: Path to service account JSON (default: `/opt/spark/conf/gcs-json-key/gcs-key.json`)

#### S3 Variant
- `S3_BUCKET`: S3 bucket name
- `S3_ENDPOINT`: S3 endpoint URL (optional)
- `AWS_ACCESS_KEY_ID`: AWS access key
- `AWS_SECRET_ACCESS_KEY`: AWS secret key
- `AWS_REGION`: AWS region (default: `us-east-1`)

#### MinIO Variant
- `MINIO_BUCKET`: MinIO bucket name
- `MINIO_ENDPOINT`: MinIO endpoint URL (required)
- `MINIO_ACCESS_KEY`: MinIO access key
- `MINIO_SECRET_KEY`: MinIO secret key

## 🐳 Running Containers

### Spark Master
```bash
docker run -d \
  --name spark-master \
  -p 7077:7077 \
  -p 8080:8080 \
  -e SPARK_MODE=master \
  whereq/spark:4.0.1-base
```

### Spark Worker
```bash
docker run -d \
  --name spark-worker \
  -p 8081:8081 \
  -e SPARK_MODE=worker \
  -e SPARK_MASTER_URL=spark://spark-master:7077 \
  --link spark-master \
  whereq/spark:4.0.1-base
```

### GCS-enabled Master
```bash
docker run -d \
  --name spark-master-gcs \
  -p 7077:7077 \
  -p 8080:8080 \
  -e SPARK_MODE=master \
  -e GCS_BUCKET=my-bucket \
  -e GCS_PROJECT_ID=my-project \
  -v /path/to/gcs-key.json:/opt/spark/conf/gcs-json-key/gcs-key.json:ro \
  whereq/spark:4.0.1-gcs
```

### S3-enabled Worker
```bash
docker run -d \
  --name spark-worker-s3 \
  -p 8081:8081 \
  -e SPARK_MODE=worker \
  -e SPARK_MASTER_URL=spark://spark-master:7077 \
  -e S3_BUCKET=my-bucket \
  -e AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE \
  -e AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY \
  whereq/spark:4.0.1-s3
```

### MinIO-enabled Cluster
```bash
docker run -d \
  --name spark-worker-minio \
  -p 8081:8081 \
  -e SPARK_MODE=worker \
  -e SPARK_MASTER_URL=spark://spark-master:7077 \
  -e MINIO_ENDPOINT=http://minio:9000 \
  -e MINIO_BUCKET=spark-data \
  -e MINIO_ACCESS_KEY=minioadmin \
  -e MINIO_SECRET_KEY=minioadmin \
  whereq/spark:4.0.1-minio
```

## ☸️ Kubernetes Deployment

### Example ConfigMap for GCS
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: spark-config-prod
data:
  spark-env-overrides.conf: |
    spark.master=spark://spark-master-svc:7077
    spark.submit.deployMode=client
    spark.scheduler.mode=FAIR
    spark.hadoop.fs.default.name=gs://prod-bucket/
    spark.eventLog.dir=gs://prod-bucket/spark_events
    spark.history.fs.logDirectory=gs://prod-bucket/spark_events
```

### Example Deployment for GCS Master
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spark-master
spec:
  replicas: 1
  selector:
    matchLabels:
      app: spark
      component: master
  template:
    metadata:
      labels:
        app: spark
        component: master
    spec:
      containers:
      - name: spark-master
        image: whereq/spark:4.0.1-gcs
        ports:
        - containerPort: 7077
        - containerPort: 8080
        env:
        - name: SPARK_MODE
          value: "master"
        - name: GCS_BUCKET
          value: "prod-bucket"
        - name: GCS_PROJECT_ID
          value: "my-project"
        volumeMounts:
        - name: spark-config
          mountPath: /opt/spark/conf/spark-env-overrides.conf
          subPath: spark-env-overrides.conf
        - name: gcs-key
          mountPath: /opt/spark/conf/gcs-json-key
          readOnly: true
      volumes:
      - name: spark-config
        configMap:
          name: spark-config-prod
      - name: gcs-key
        secret:
          secretName: gcs-service-account-key
```

## 🔒 Secured Artifactory

For environments with secured artifactory requiring authentication:

```bash
./bin/build-spark-image.sh \
  --variant gcs \
  --https-user "${ARTIFACTORY_USER}" \
  --https-pass "${ARTIFACTORY_PASS}"
```

Or with Docker directly:
```bash
docker build -f docker/gcs/Dockerfile \
  --build-arg HTTPS_USERNAME="${ARTIFACTORY_USER}" \
  --build-arg HTTPS_PASSWORD="${ARTIFACTORY_PASS}" \
  -t whereq/spark:4.0.1-gcs .
```

## 📊 Modular Architecture Benefits

### Modular Structure (docker/ Directory)
- ✅ **Modular and extensible**
- Separate variants for different storage backends
- Reusable base image
- Configurable authentication
- K8s-ready with ConfigMap support
- Universal build script

## 🔄 Quick Start

### Building Spark Images

**Build base image:**
```bash
./bin/build-spark-image.sh --variant base
```

**Build with cloud storage:**
```bash
# GCS variant
./bin/build-spark-image.sh --variant gcs

# S3 variant
./bin/build-spark-image.sh --variant s3

# MinIO variant
./bin/build-spark-image.sh --variant minio
```

### Multi-variant Build

```bash
# Build all variants
for variant in base gcs s3 minio; do
  ./bin/build-spark-image.sh --variant $variant
done
```

## 🔐 User Configuration and Permissions

### Spark User Setup

All images run as the **`spark` user** (UID 1000) with the following configuration:

```dockerfile
# User created with home directory for gcloud CLI and other tools
useradd -r -u 1000 -g root -m -d /home/spark spark

# Directories created with proper permissions:
# - /opt/spark (Spark installation)
# - /home/spark (User home directory)
# - /home/spark/.config (For gcloud and other config files)
```

### Why Home Directory is Required

The `spark` user has a proper home directory (`/home/spark`) with write permissions for:

1. **gcloud CLI** - Stores configuration in `~/.config/gcloud/` (when using `INSTALL_GCLOUD=true`)
2. **Python packages** - May write to `~/.local/` for user-installed packages
3. **Spark temporary files** - May use `~/.ivy2/` for dependency caching
4. **SSH keys** - For secure communication (if needed)

### OpenShift Compatibility

The configuration is OpenShift-compatible:
- User UID: `1000` (non-root)
- Group GID: `0` (root group)
- Group-writable permissions (`g+rwX`) on all directories
- Works with arbitrary UIDs (OpenShift Security Context Constraints)

### Environment Variables

```bash
HOME=/home/spark              # User home directory
SPARK_HOME=/opt/spark          # Spark installation directory
PATH includes $SPARK_HOME/bin and $SPARK_HOME/sbin
```

### Testing gcloud CLI (GCS Variant Only)

When using the GCS variant with `INSTALL_GCLOUD=true`:

```bash
# Inside container
kubectl exec -it spark-master-0 -n namespace -- /bin/bash

# Check home directory exists and is writable
cd ~
pwd    # Should show: /home/spark
touch test-write && rm test-write   # Test write permissions

# Authenticate gcloud
gcloud auth activate-service-account \
  --key-file=/opt/spark/conf/gcs-json-key/gcs-key.json

# Test GCS access
gcloud storage ls gs://your-bucket/
```

### Troubleshooting Permission Issues

If you see permission errors:

```bash
# Check current user
whoami    # Should be: spark

# Check home directory
echo $HOME    # Should be: /home/spark
ls -la ~      # Should show writable directory owned by spark:root

# Check Spark directories
ls -la /opt/spark    # Should show writable by spark:root

# Check gcloud config directory (GCS variant only)
ls -la ~/.config     # Should exist and be writable
```

## 📝 Notes

1. **Base image must be built first** - GCS, S3, and MinIO variants extend the base image
2. **Configuration precedence**: K8s ConfigMap > Environment Variables > spark-defaults.conf
3. **Authentication**: Credentials should be managed via K8s Secrets, not baked into images
4. **Image size**: Base (~800MB), GCS/S3/MinIO (~850MB with connectors)

## 🤝 Contributing

When adding new storage backends:

1. Create new directory: `docker/newbackend/`
2. Add Dockerfile extending base
3. Add configuration templates in `conf/`
4. Add custom entrypoint if needed
5. Update build script to support new variant
6. Document in this README

## 📚 Additional Resources

- [Apache Spark Documentation](https://spark.apache.org/docs/4.0.1/)
- [GCS Connector Documentation](https://github.com/GoogleCloudDataproc/hadoop-connectors)
- [Hadoop AWS Documentation](https://hadoop.apache.org/docs/stable/hadoop-aws/tools/hadoop-aws/index.html)
- [MinIO Documentation](https://min.io/docs/minio/kubernetes/upstream/)
