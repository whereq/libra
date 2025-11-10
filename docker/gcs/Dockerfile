# Multi-stage build for GCS-enabled Spark 4.0.1 Docker image
# Extends base Spark image with Google Cloud Storage support
#
# Build arguments:
#   HTTPS_USERNAME - Username for secured downloads (optional)
#   HTTPS_PASSWORD - Password for secured downloads (optional)
#   GCS_CONNECTOR_VERSION - GCS connector version (default: 2.2.20)
#   INSTALL_GCLOUD - Install gcloud CLI for debugging (default: false)
#
# Example builds:
#   # Production (no gcloud CLI)
#   docker build -f docker/gcs/Dockerfile -t whereq/spark:4.0.1-gcs .
#
#   # With gcloud CLI for debugging (~400MB larger)
#   docker build -f docker/gcs/Dockerfile \
#     --build-arg INSTALL_GCLOUD=true \
#     -t whereq/spark:4.0.1-gcs-debug .
#
#   # With secured artifactory
#   docker build -f docker/gcs/Dockerfile \
#     --build-arg HTTPS_USERNAME=myuser \
#     --build-arg HTTPS_PASSWORD=mypass \
#     --build-arg INSTALL_GCLOUD=true \
#     -t whereq/spark:4.0.1-gcs .

# Use base image as foundation
FROM whereq/spark:4.0.1-base AS gcs-builder

USER root

ARG HTTPS_USERNAME
ARG HTTPS_PASSWORD
ARG GCS_CONNECTOR_VERSION=2.2.20
ARG INSTALL_GCLOUD=false

# Configure authentication if provided
RUN if [ -n "${HTTPS_USERNAME}" ] && [ -n "${HTTPS_PASSWORD}" ]; then \
        echo "Configuring authenticated downloads..."; \
        echo "http_proxy_user = ${HTTPS_USERNAME}" >> /etc/wgetrc; \
        echo "http_proxy_password = ${HTTPS_PASSWORD}" >> /etc/wgetrc; \
        echo "https_proxy_user = ${HTTPS_USERNAME}" >> /etc/wgetrc; \
        echo "https_proxy_password = ${HTTPS_PASSWORD}" >> /etc/wgetrc; \
    fi

# Install gcloud CLI if requested (adds ~400MB for debugging)
RUN if [ "${INSTALL_GCLOUD}" = "true" ]; then \
        echo "Installing gcloud CLI for debugging..."; \
        microdnf install -y python3 python3-pip && \
        curl -O https://dl.google.com/dl/cloudsdk/channels/rapid/downloads/google-cloud-cli-459.0.0-linux-x86_64.tar.gz && \
        tar -xf google-cloud-cli-459.0.0-linux-x86_64.tar.gz -C /opt && \
        rm google-cloud-cli-459.0.0-linux-x86_64.tar.gz && \
        /opt/google-cloud-sdk/install.sh --quiet --path-update false --usage-reporting false && \
        ln -s /opt/google-cloud-sdk/bin/gcloud /usr/local/bin/gcloud && \
        ln -s /opt/google-cloud-sdk/bin/gsutil /usr/local/bin/gsutil && \
        chown -R spark:root /opt/google-cloud-sdk && \
        chmod -R g+rwX /opt/google-cloud-sdk && \
        echo "gcloud CLI installed successfully"; \
    else \
        echo "Skipping gcloud CLI installation (use --build-arg INSTALL_GCLOUD=true to enable)"; \
    fi

# Download GCS connector JAR
RUN echo "Downloading GCS connector version ${GCS_CONNECTOR_VERSION}..." && \
    wget -q \
      https://repo1.maven.org/maven2/com/google/cloud/bigdataoss/gcs-connector/hadoop3-${GCS_CONNECTOR_VERSION}/gcs-connector-hadoop3-${GCS_CONNECTOR_VERSION}-shaded.jar \
      -O ${SPARK_HOME}/jars/gcs-connector-hadoop3-${GCS_CONNECTOR_VERSION}-shaded.jar && \
    echo "GCS connector downloaded successfully"

# Copy GCS configuration templates
COPY docker/gcs/conf/core-site.xml ${SPARK_HOME}/conf/core-site.xml.template
COPY docker/gcs/conf/spark-defaults.conf ${SPARK_HOME}/conf/spark-defaults.conf.template

# Create directory for GCS service account key
RUN mkdir -p ${SPARK_HOME}/conf/gcs-json-key && \
    chown -R spark:root ${SPARK_HOME}/conf && \
    chmod -R g+rwX ${SPARK_HOME}/conf

# Copy GCS-specific entrypoint
COPY docker/gcs/entrypoint.sh /opt/spark-entrypoint.sh
RUN chmod +x /opt/spark-entrypoint.sh

USER spark

# Override welcome message
ENV SPARK_BUILD_VARIANT="GCS"
ENV PATH="/opt/google-cloud-sdk/bin:${PATH}"

ENTRYPOINT ["/usr/bin/tini", "--", "/opt/spark-entrypoint.sh"]
CMD ["master"]
