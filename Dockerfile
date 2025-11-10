# Multi-stage build for WhereQ Libra
# Builder stage - Use Red Hat UBI9 minimal
FROM redhat/ubi9-minimal:9.6-1760515502 AS builder

WORKDIR /app

# Install JDK 21 and Maven
RUN microdnf install -y \
    java-21-openjdk-devel \
    wget \
    tar \
    gzip \
    && microdnf clean all

# Install Maven 3.9.9 (latest stable)
ENV MAVEN_VERSION=3.9.9
ENV MAVEN_HOME=/opt/maven
RUN wget -q https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
    && tar xzf apache-maven-${MAVEN_VERSION}-bin.tar.gz -C /opt \
    && mv /opt/apache-maven-${MAVEN_VERSION} ${MAVEN_HOME} \
    && rm apache-maven-${MAVEN_VERSION}-bin.tar.gz \
    && ln -s ${MAVEN_HOME}/bin/mvn /usr/bin/mvn

# Set JAVA_HOME
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage - Use Red Hat UBI9 minimal
FROM redhat/ubi9-minimal:9.6-1760515502

WORKDIR /app

# Install JRE 21 and required packages
# Note: curl-minimal is pre-installed in ubi9-minimal, sufficient for health checks
RUN microdnf install -y \
    java-21-openjdk-headless \
    procps-ng \
    shadow-utils \
    && microdnf clean all

# Set JAVA_HOME
ENV JAVA_HOME=/usr/lib/jvm/jre-21-openjdk

# Copy the built jar from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Create non-root user
RUN useradd -r -u 1000 -g root spark-facade && \
    chown -R spark-facade:root /app && \
    chmod -R g+rwX /app

USER spark-facade

# Expose ports
EXPOSE 8080 4040

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM options for containerized environment
ENV JAVA_OPTS="-Xmx2g -Xms1g -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=docker"]
