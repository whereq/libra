#!/bin/bash
# S3-enabled Spark Entrypoint
set -e

if [ "${UNSET_PROXY:-false}" = "true" ]; then
    unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY no_proxy NO_PROXY
fi

echo "========================================="
echo " WhereQ Spark ${SPARK_VERSION:-4.0.1}"
echo " Python ${PYTHON_VERSION:-3.14.0}"
echo " Build: S3 (AWS S3 / S3-compatible)"
echo "========================================="

# S3 Configuration
S3_BUCKET=${S3_BUCKET:-}
S3_ENDPOINT=${S3_ENDPOINT:-}
AWS_REGION=${AWS_REGION:-us-east-1}

echo "S3 Configuration:"
echo "  Bucket: ${S3_BUCKET:-not set}"
echo "  Endpoint: ${S3_ENDPOINT:-AWS S3 (default)}"
echo "  Region: ${AWS_REGION}"
echo "  Access Key: $([ -n "${AWS_ACCESS_KEY_ID}" ] && echo 'Set' || echo 'Not set (using IAM role?)')"

export SPARK_CONF_DIR=${SPARK_CONF_DIR:-${SPARK_HOME}/conf}

# Process templates
if [ -n "${S3_BUCKET}" ]; then
    echo "Processing S3 configuration templates..."

    if [ -f "${SPARK_HOME}/conf/core-site.xml.template" ]; then
        cp "${SPARK_HOME}/conf/core-site.xml.template" "${SPARK_HOME}/conf/core-site.xml"
        sed -i "s|\${AWS_ACCESS_KEY_ID}|${AWS_ACCESS_KEY_ID:-}|g" "${SPARK_HOME}/conf/core-site.xml"
        sed -i "s|\${AWS_SECRET_ACCESS_KEY}|${AWS_SECRET_ACCESS_KEY:-}|g" "${SPARK_HOME}/conf/core-site.xml"
        sed -i "s|\${S3_ENDPOINT}|${S3_ENDPOINT:-}|g" "${SPARK_HOME}/conf/core-site.xml"
    fi

    if [ -f "${SPARK_HOME}/conf/spark-defaults.conf.template" ]; then
        cp "${SPARK_HOME}/conf/spark-defaults.conf.template" "${SPARK_HOME}/conf/spark-defaults.conf"

        echo "" >> "${SPARK_HOME}/conf/spark-defaults.conf"
        echo "# Runtime S3 Configuration" >> "${SPARK_HOME}/conf/spark-defaults.conf"
        echo "spark.hadoop.fs.default.name=s3a://${S3_BUCKET}/" >> "${SPARK_HOME}/conf/spark-defaults.conf"
        echo "spark.eventLog.dir=s3a://${S3_BUCKET}/spark_events" >> "${SPARK_HOME}/conf/spark-defaults.conf"
        echo "spark.history.fs.logDirectory=s3a://${S3_BUCKET}/spark_events" >> "${SPARK_HOME}/conf/spark-defaults.conf"
        echo "spark.sql.warehouse.dir=s3a://${S3_BUCKET}/spark_warehouse/" >> "${SPARK_HOME}/conf/spark-defaults.conf"

        if [ -n "${S3_ENDPOINT}" ]; then
            echo "spark.hadoop.fs.s3a.endpoint=${S3_ENDPOINT}" >> "${SPARK_HOME}/conf/spark-defaults.conf"
        fi
    fi
fi

# Load K8s ConfigMap overrides
if [ -f "${SPARK_CONF_DIR}/spark-env-overrides.conf" ]; then
    cat "${SPARK_CONF_DIR}/spark-env-overrides.conf" >> "${SPARK_HOME}/conf/spark-defaults.conf"
fi

SPARK_MODE=${SPARK_MODE:-"master"}
SPARK_MASTER_HOST=${SPARK_MASTER_HOST:-"spark-master"}
SPARK_MASTER_PORT=${SPARK_MASTER_PORT:-7077}
SPARK_MASTER_WEBUI_PORT=${SPARK_MASTER_WEBUI_PORT:-8080}
SPARK_WORKER_WEBUI_PORT=${SPARK_WORKER_WEBUI_PORT:-8081}

case "$SPARK_MODE" in
  master|worker|history|shell|submit)
    # Reuse base logic
    exec /bin/bash -c "
        case '$SPARK_MODE' in
          master) exec /opt/spark/bin/spark-class org.apache.spark.deploy.master.Master --host 0.0.0.0 --port ${SPARK_MASTER_PORT} --webui-port ${SPARK_MASTER_WEBUI_PORT} ;;
          worker)
            SPARK_MASTER_URL=\${SPARK_MASTER_URL:-spark://${SPARK_MASTER_HOST}:${SPARK_MASTER_PORT}}
            until nc -z \$(echo \$SPARK_MASTER_URL | sed 's|spark://||' | cut -d: -f1) ${SPARK_MASTER_PORT} 2>/dev/null; do sleep 2; done
            exec /opt/spark/bin/spark-class org.apache.spark.deploy.worker.Worker --webui-port ${SPARK_WORKER_WEBUI_PORT} \$SPARK_MASTER_URL ;;
          history) exec /opt/spark/sbin/start-history-server.sh ;;
          shell) exec /opt/spark/bin/spark-shell \"\$@\" ;;
          submit) exec /opt/spark/bin/spark-submit \"\$@\" ;;
        esac
    "
    ;;
  *)
    [ $# -eq 0 ] && echo "ERROR: No mode specified" && exit 1
    exec "$@"
    ;;
esac
