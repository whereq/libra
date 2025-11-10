# Multi-stage build for MinIO-enabled Spark 4.0.1 Docker image
# Extends base Spark image with MinIO (S3-compatible) support
#
# Build arguments:
#   HTTPS_USERNAME - Username for secured downloads (optional)
#   HTTPS_PASSWORD - Password for secured downloads (optional)
#   HADOOP_AWS_VERSION - Hadoop AWS library version (default: 3.3.6)
#   AWS_SDK_VERSION - AWS SDK version (default: 1.12.367)
#
# Example build:
#   docker build -f docker/minio/Dockerfile -t whereq/spark:4.0.1-minio .

FROM whereq/spark:4.0.1-base AS minio-builder

USER root

ARG HTTPS_USERNAME
ARG HTTPS_PASSWORD
ARG HADOOP_AWS_VERSION=3.3.6
ARG AWS_SDK_VERSION=1.12.367

# Configure authentication if provided
RUN if [ -n "${HTTPS_USERNAME}" ] && [ -n "${HTTPS_PASSWORD}" ]; then \
        echo "Configuring authenticated downloads..."; \
        echo "http_proxy_user = ${HTTPS_USERNAME}" >> /etc/wgetrc; \
        echo "http_proxy_password = ${HTTPS_PASSWORD}" >> /etc/wgetrc; \
        echo "https_proxy_user = ${HTTPS_USERNAME}" >> /etc/wgetrc; \
        echo "https_proxy_password = ${HTTPS_PASSWORD}" >> /etc/wgetrc; \
    fi

# Download Hadoop AWS and AWS SDK JARs
RUN echo "Downloading Hadoop AWS ${HADOOP_AWS_VERSION}..." && \
    wget -q \
      https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-aws/${HADOOP_AWS_VERSION}/hadoop-aws-${HADOOP_AWS_VERSION}.jar \
      -O ${SPARK_HOME}/jars/hadoop-aws-${HADOOP_AWS_VERSION}.jar && \
    echo "Downloading AWS SDK ${AWS_SDK_VERSION}..." && \
    wget -q \
      https://repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-bundle/${AWS_SDK_VERSION}/aws-java-sdk-bundle-${AWS_SDK_VERSION}.jar \
      -O ${SPARK_HOME}/jars/aws-java-sdk-bundle-${AWS_SDK_VERSION}.jar && \
    echo "S3 dependencies downloaded successfully"

# Copy MinIO configuration templates
COPY docker/minio/conf/core-site.xml ${SPARK_HOME}/conf/core-site.xml.template
COPY docker/minio/conf/spark-defaults.conf ${SPARK_HOME}/conf/spark-defaults.conf.template

# Copy MinIO-specific entrypoint
COPY docker/minio/entrypoint.sh /opt/spark-entrypoint.sh
RUN chmod +x /opt/spark-entrypoint.sh && \
    chown -R spark:root ${SPARK_HOME}/conf && \
    chmod -R g+rwX ${SPARK_HOME}/conf

USER spark

ENV SPARK_BUILD_VARIANT="MinIO"

ENTRYPOINT ["/usr/bin/tini", "--", "/opt/spark-entrypoint.sh"]
CMD ["master"]
