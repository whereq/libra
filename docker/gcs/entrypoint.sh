#!/bin/bash
# GCS-enabled Spark Entrypoint
# Extends base entrypoint with GCS-specific configuration
set -e

# Unset proxies if requested
if [ "${UNSET_PROXY:-false}" = "true" ]; then
    echo "Unsetting proxy variables..."
    unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY no_proxy NO_PROXY
fi

# Print welcome message
echo "========================================="
echo " WhereQ Spark ${SPARK_VERSION:-4.0.1}"
echo " Python ${PYTHON_VERSION:-3.14.0}"
echo " Build: GCS (Google Cloud Storage)"
echo "========================================="

# GCS Configuration
GCS_BUCKET=${GCS_BUCKET:-}
GCS_PROJECT_ID=${GCS_PROJECT_ID:-}
GCS_KEY_FILE=${GCS_KEY_FILE:-/opt/spark/conf/gcs-json-key/gcs-key.json}

echo "GCS Configuration:"
echo "  Bucket: ${GCS_BUCKET:-not set}"
echo "  Project ID: ${GCS_PROJECT_ID:-not set}"
echo "  Key File: ${GCS_KEY_FILE}"
echo "  Key File Exists: $([ -f "${GCS_KEY_FILE}" ] && echo 'Yes' || echo 'No (using Workload Identity?)')"

# Set SPARK_CONF_DIR
export SPARK_CONF_DIR=${SPARK_CONF_DIR:-${SPARK_HOME}/conf}
echo "SPARK_CONF_DIR: ${SPARK_CONF_DIR}"

# Process configuration templates if environment variables provided
if [ -n "${GCS_BUCKET}" ] || [ -n "${GCS_PROJECT_ID}" ]; then
    echo "Processing GCS configuration templates..."

    # Copy templates to active config if they exist
    if [ -f "${SPARK_HOME}/conf/core-site.xml.template" ]; then
        cp "${SPARK_HOME}/conf/core-site.xml.template" "${SPARK_HOME}/conf/core-site.xml"

        # Replace environment variables in core-site.xml
        if [ -n "${GCS_PROJECT_ID}" ]; then
            sed -i "s|\${GCS_PROJECT_ID}|${GCS_PROJECT_ID}|g" "${SPARK_HOME}/conf/core-site.xml"
        fi
    fi

    if [ -f "${SPARK_HOME}/conf/spark-defaults.conf.template" ]; then
        cp "${SPARK_HOME}/conf/spark-defaults.conf.template" "${SPARK_HOME}/conf/spark-defaults.conf"
    fi

    # Create or append runtime overrides
    if [ -n "${GCS_BUCKET}" ]; then
        echo "" >> "${SPARK_HOME}/conf/spark-defaults.conf"
        echo "# Runtime GCS Configuration" >> "${SPARK_HOME}/conf/spark-defaults.conf"
        echo "spark.hadoop.fs.default.name=gs://${GCS_BUCKET}/" >> "${SPARK_HOME}/conf/spark-defaults.conf"

        # Set event log and warehouse directories if not already set
        if ! grep -q "^spark.eventLog.dir=" "${SPARK_HOME}/conf/spark-defaults.conf" 2>/dev/null; then
            echo "spark.eventLog.dir=gs://${GCS_BUCKET}/spark_events" >> "${SPARK_HOME}/conf/spark-defaults.conf"
        fi

        if ! grep -q "^spark.history.fs.logDirectory=" "${SPARK_HOME}/conf/spark-defaults.conf" 2>/dev/null; then
            echo "spark.history.fs.logDirectory=gs://${GCS_BUCKET}/spark_events" >> "${SPARK_HOME}/conf/spark-defaults.conf"
        fi

        if ! grep -q "^spark.sql.warehouse.dir=" "${SPARK_HOME}/conf/spark-defaults.conf" 2>/dev/null; then
            echo "spark.sql.warehouse.dir=gs://${GCS_BUCKET}/spark_warehouse/" >> "${SPARK_HOME}/conf/spark-defaults.conf"
        fi
    fi

    if [ -n "${GCS_PROJECT_ID}" ]; then
        echo "spark.hadoop.fs.gs.project.id=${GCS_PROJECT_ID}" >> "${SPARK_HOME}/conf/spark-defaults.conf"
    fi
fi

# Load additional configuration from K8s ConfigMap if provided
if [ -f "${SPARK_CONF_DIR}/spark-env-overrides.conf" ]; then
    echo "Loading runtime configuration overrides..."
    cat "${SPARK_CONF_DIR}/spark-env-overrides.conf" >> "${SPARK_HOME}/conf/spark-defaults.conf"
fi

# Initialize mode defaults
SPARK_MODE=${SPARK_MODE:-"master"}
SPARK_MASTER_HOST=${SPARK_MASTER_HOST:-"spark-master"}
SPARK_MASTER_PORT=${SPARK_MASTER_PORT:-7077}
SPARK_MASTER_WEBUI_PORT=${SPARK_MASTER_WEBUI_PORT:-8080}
SPARK_WORKER_WEBUI_PORT=${SPARK_WORKER_WEBUI_PORT:-8081}
SPARK_WORKER_CORES=${SPARK_WORKER_CORES:-}
SPARK_WORKER_MEMORY=${SPARK_WORKER_MEMORY:-}

echo "Mode: ${SPARK_MODE}"

# Configure based on mode
case "$SPARK_MODE" in
  master)
    echo "Starting Spark Master with GCS support..."
    echo "  Host: 0.0.0.0"
    echo "  Port: ${SPARK_MASTER_PORT}"
    echo "  WebUI Port: ${SPARK_MASTER_WEBUI_PORT}"

    exec /opt/spark/bin/spark-class \
      org.apache.spark.deploy.master.Master \
      --host 0.0.0.0 \
      --port ${SPARK_MASTER_PORT} \
      --webui-port ${SPARK_MASTER_WEBUI_PORT}
    ;;

  worker)
    echo "Starting Spark Worker with GCS support..."

    # Wait for master to be ready
    if [ -n "${SPARK_MASTER_URL}" ]; then
        MASTER_HOST=$(echo ${SPARK_MASTER_URL} | sed 's|spark://||' | cut -d: -f1)
        MASTER_PORT=$(echo ${SPARK_MASTER_URL} | sed 's|spark://||' | cut -d: -f2)

        echo "Waiting for Spark master at ${MASTER_HOST}:${MASTER_PORT}..."
        until nc -z ${MASTER_HOST} ${MASTER_PORT} 2>/dev/null; do
            echo "  Master not ready, retrying..."
            sleep 2
        done
        echo "  Master is ready!"
    else
        # Construct master URL from defaults
        SPARK_MASTER_URL="spark://${SPARK_MASTER_HOST}:${SPARK_MASTER_PORT}"

        echo "Waiting for Spark master at ${SPARK_MASTER_HOST}:${SPARK_MASTER_PORT}..."
        until nc -z ${SPARK_MASTER_HOST} ${SPARK_MASTER_PORT} 2>/dev/null; do
            echo "  Master not ready, retrying..."
            sleep 2
        done
        echo "  Master is ready!"
    fi

    echo "  Master URL: ${SPARK_MASTER_URL}"
    echo "  WebUI Port: ${SPARK_WORKER_WEBUI_PORT}"
    [ -n "${SPARK_WORKER_CORES}" ] && echo "  Cores: ${SPARK_WORKER_CORES}"
    [ -n "${SPARK_WORKER_MEMORY}" ] && echo "  Memory: ${SPARK_WORKER_MEMORY}"

    WORKER_OPTS="--webui-port ${SPARK_WORKER_WEBUI_PORT}"
    [ -n "${SPARK_WORKER_CORES}" ] && WORKER_OPTS="${WORKER_OPTS} --cores ${SPARK_WORKER_CORES}"
    [ -n "${SPARK_WORKER_MEMORY}" ] && WORKER_OPTS="${WORKER_OPTS} --memory ${SPARK_WORKER_MEMORY}"

    exec /opt/spark/bin/spark-class \
      org.apache.spark.deploy.worker.Worker \
      ${WORKER_OPTS} \
      ${SPARK_MASTER_URL}
    ;;

  history)
    echo "Starting Spark History Server with GCS support..."
    echo "  Log Directory: ${SPARK_HISTORY_OPTS:-gs://${GCS_BUCKET}/spark_events}"

    # Ensure history server log directory is configured
    if [ -n "${GCS_BUCKET}" ] && [ -z "${SPARK_HISTORY_OPTS}" ]; then
        export SPARK_HISTORY_OPTS="-Dspark.history.fs.logDirectory=gs://${GCS_BUCKET}/spark_events"
    fi

    exec /opt/spark/sbin/start-history-server.sh
    ;;

  shell)
    echo "Starting Spark Shell with GCS support..."
    exec /opt/spark/bin/spark-shell "$@"
    ;;

  submit)
    echo "Running spark-submit with GCS support..."
    exec /opt/spark/bin/spark-submit "$@"
    ;;

  *)
    if [ $# -eq 0 ]; then
        echo "ERROR: No mode or command specified"
        echo ""
        echo "Available modes:"
        echo "  master  - Start Spark Master"
        echo "  worker  - Start Spark Worker"
        echo "  history - Start Spark History Server"
        echo "  shell   - Start Spark Shell"
        echo "  submit  - Run spark-submit"
        echo ""
        echo "Or provide a custom command to execute"
        exit 1
    fi

    echo "Custom command mode..."
    echo "Executing: $@"
    exec "$@"
    ;;
esac
