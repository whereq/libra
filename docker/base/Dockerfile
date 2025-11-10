# Multi-stage build for Base Spark 4.0.1 Docker image
# This is the base image without any cloud storage integrations
#
# Build arguments for secured artifactory authentication (optional):
#   HTTPS_USERNAME - Username for secured downloads
#   HTTPS_PASSWORD - Password for secured downloads
#
# Example build:
#   docker build -f docker/base/Dockerfile -t whereq/spark:4.0.1-base .
#   docker build -f docker/base/Dockerfile \
#     --build-arg HTTPS_USERNAME=myuser \
#     --build-arg HTTPS_PASSWORD=mypass \
#     -t whereq/spark:4.0.1-base .

# Stage 1: Build Python 3.14.0 from source
FROM redhat/ubi9:latest AS python-builder

ARG HTTPS_USERNAME
ARG HTTPS_PASSWORD
ARG PYTHON_VERSION=3.14.0

# Configure wget/curl authentication if credentials provided
RUN if [ -n "${HTTPS_USERNAME}" ] && [ -n "${HTTPS_PASSWORD}" ]; then \
        echo "Configuring authenticated downloads..."; \
        echo "http_proxy_user = ${HTTPS_USERNAME}" >> /etc/wgetrc; \
        echo "http_proxy_password = ${HTTPS_PASSWORD}" >> /etc/wgetrc; \
        echo "https_proxy_user = ${HTTPS_USERNAME}" >> /etc/wgetrc; \
        echo "https_proxy_password = ${HTTPS_PASSWORD}" >> /etc/wgetrc; \
        echo "user = \"${HTTPS_USERNAME}:${HTTPS_PASSWORD}\"" > ~/.curlrc; \
    fi

# Install build dependencies for Python
RUN dnf install -y \
    gcc \
    make \
    openssl-devel \
    bzip2-devel \
    libffi-devel \
    zlib-devel \
    xz-devel \
    sqlite-devel \
    wget \
    tar \
    xz \
    && dnf clean all

# Build Python 3.14.0 from source
RUN wget -q https://www.python.org/ftp/python/${PYTHON_VERSION}/Python-${PYTHON_VERSION}.tar.xz && \
    tar -xf Python-${PYTHON_VERSION}.tar.xz && \
    cd Python-${PYTHON_VERSION} && \
    ./configure --prefix=/usr/local --enable-optimizations --with-ensurepip=install && \
    make -j$(nproc) && \
    make altinstall && \
    cd .. && \
    rm -rf Python-${PYTHON_VERSION} Python-${PYTHON_VERSION}.tar.xz

# Install essential Python packages
RUN /usr/local/bin/pip3.14 install --no-cache-dir \
    numpy \
    pandas \
    pyarrow

# Stage 2: Final minimal image
FROM redhat/ubi9-minimal:9.6-1760515502

ARG HTTPS_USERNAME
ARG HTTPS_PASSWORD
ARG SPARK_VERSION=4.0.1
ARG HADOOP_VERSION=3

ENV SPARK_VERSION=${SPARK_VERSION}
ENV HADOOP_VERSION=${HADOOP_VERSION}
ENV SPARK_HOME=/opt/spark
ENV PYTHON_VERSION=3.14.0

# Configure wget/curl authentication if credentials provided
RUN if [ -n "${HTTPS_USERNAME}" ] && [ -n "${HTTPS_PASSWORD}" ]; then \
        echo "Configuring authenticated downloads..."; \
        echo "http_proxy_user = ${HTTPS_USERNAME}" >> /etc/wgetrc; \
        echo "http_proxy_password = ${HTTPS_PASSWORD}" >> /etc/wgetrc; \
        echo "https_proxy_user = ${HTTPS_USERNAME}" >> /etc/wgetrc; \
        echo "https_proxy_password = ${HTTPS_PASSWORD}" >> /etc/wgetrc; \
        mkdir -p ~/.config; \
        echo "user = \"${HTTPS_USERNAME}:${HTTPS_PASSWORD}\"" > ~/.curlrc; \
    fi

# Install Java 21 and runtime dependencies
RUN microdnf install -y \
    java-21-openjdk-headless \
    libffi \
    openssl-libs \
    bzip2-libs \
    sqlite-libs \
    xz-libs \
    zlib \
    wget \
    procps-ng \
    tar \
    gzip \
    shadow-utils \
    nmap-ncat \
    && microdnf clean all

# Copy Python 3.14.0 from builder stage
COPY --from=python-builder /usr/local /usr/local

# Create symbolic links for Python
RUN ln -sf /usr/local/bin/python3.14 /usr/bin/python3 && \
    ln -sf /usr/local/bin/python3.14 /usr/bin/python && \
    ln -sf /usr/local/bin/pip3.14 /usr/bin/pip3 && \
    ln -sf /usr/local/bin/pip3.14 /usr/bin/pip

# Install tini manually (not available in ubi9 repos)
RUN wget -q https://github.com/krallin/tini/releases/download/v0.19.0/tini-static -O /usr/bin/tini && \
    chmod +x /usr/bin/tini

# Download and install Spark 4.0.1
RUN wget -q https://archive.apache.org/dist/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz && \
    tar -xzf spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz && \
    mv spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION} ${SPARK_HOME} && \
    rm spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz

# Set JAVA_HOME and Python environment for PySpark
ENV JAVA_HOME=/usr/lib/jvm/jre-21-openjdk
ENV PYSPARK_PYTHON=/usr/bin/python3
ENV PYSPARK_DRIVER_PYTHON=/usr/bin/python3
ENV PYTHONPATH=${SPARK_HOME}/python:${SPARK_HOME}/python/lib/py4j-0.10.9.9-src.zip

# Create spark user with home directory (OpenShift-compatible permissions)
RUN useradd -r -u 1000 -g root -m -d /home/spark spark && \
    mkdir -p ${SPARK_HOME}/work ${SPARK_HOME}/logs ${SPARK_HOME}/conf /home/spark/.config && \
    chown -R spark:root ${SPARK_HOME} /home/spark && \
    chmod -R g+rwX ${SPARK_HOME} /home/spark

# Set environment variables
ENV PATH=$PATH:${SPARK_HOME}/bin:${SPARK_HOME}/sbin
ENV SPARK_NO_DAEMONIZE=true
ENV HOME=/home/spark

WORKDIR ${SPARK_HOME}

# Copy entrypoint script
COPY docker/base/entrypoint.sh /opt/spark-entrypoint.sh
RUN chmod +x /opt/spark-entrypoint.sh

USER spark

ENTRYPOINT ["/usr/bin/tini", "--", "/opt/spark-entrypoint.sh"]
CMD ["master"]
