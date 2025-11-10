#!/bin/bash
# Base Spark Entrypoint
# Supports master, worker, history server, and custom command modes
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
echo " Build: BASE (no cloud storage)"
echo "========================================="

# Initialize defaults
SPARK_MODE=${SPARK_MODE:-"master"}
SPARK_MASTER_HOST=${SPARK_MASTER_HOST:-"spark-master"}
SPARK_MASTER_PORT=${SPARK_MASTER_PORT:-7077}
SPARK_MASTER_WEBUI_PORT=${SPARK_MASTER_WEBUI_PORT:-8080}
SPARK_WORKER_WEBUI_PORT=${SPARK_WORKER_WEBUI_PORT:-8081}
SPARK_WORKER_CORES=${SPARK_WORKER_CORES:-}
SPARK_WORKER_MEMORY=${SPARK_WORKER_MEMORY:-}

echo "Mode: ${SPARK_MODE}"
echo "SPARK_HOME: ${SPARK_HOME}"
echo "SPARK_CONF_DIR: ${SPARK_CONF_DIR:-${SPARK_HOME}/conf}"

# Configure based on mode
case "$SPARK_MODE" in
  master)
    echo "Starting Spark Master..."
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
    echo "Starting Spark Worker..."

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
    echo "Starting Spark History Server..."
    echo "  Log Directory: ${SPARK_HISTORY_OPTS:-not configured}"

    # Start history server
    exec /opt/spark/sbin/start-history-server.sh
    ;;

  shell)
    echo "Starting Spark Shell..."
    exec /opt/spark/bin/spark-shell "$@"
    ;;

  submit)
    echo "Running spark-submit..."
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
