#!/bin/bash
# WhereQ Libra Kubernetes Deployment Script
# Author: WhereQ Inc.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NAMESPACE="spark-platform"
IMAGE_NAME="whereq/libra"
IMAGE_TAG="1.0.0"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

usage() {
    echo "Usage: $0 [option1|option3] [--image IMAGE_NAME:TAG] [--namespace NAMESPACE]"
    echo ""
    echo "Options:"
    echo "  option1    Deploy with standalone Spark cluster (master + workers)"
    echo "  option3    Deploy with Spark native Kubernetes mode"
    echo ""
    echo "Flags:"
    echo "  --image      Docker image for Libra (default: whereq/libra:1.0.0)"
    echo "  --namespace  Kubernetes namespace (default: spark-platform)"
    echo ""
    echo "Examples:"
    echo "  $0 option1"
    echo "  $0 option3 --image myregistry/libra:latest"
    exit 1
}

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_prerequisites() {
    log_info "Checking prerequisites..."

    if ! command -v kubectl &> /dev/null; then
        log_error "kubectl not found. Please install kubectl."
        exit 1
    fi

    if ! kubectl cluster-info &> /dev/null; then
        log_error "Cannot connect to Kubernetes cluster. Please check your kubeconfig."
        exit 1
    fi

    log_info "Prerequisites check passed."
}

build_and_push_image() {
    log_info "Building and pushing Docker image..."

    cd "$SCRIPT_DIR/.."

    log_info "Building image: ${IMAGE_NAME}:${IMAGE_TAG}"
    docker build -t "${IMAGE_NAME}:${IMAGE_TAG}" .

    read -p "Push image to registry? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        log_info "Pushing image to registry..."
        docker push "${IMAGE_NAME}:${IMAGE_TAG}"
    else
        log_warn "Skipping image push. Make sure the image is available in your cluster."
    fi
}

deploy_option1() {
    log_info "Deploying Option 1: Standalone Spark Cluster..."

    # Create namespace
    log_info "Creating namespace..."
    kubectl apply -f "$SCRIPT_DIR/option1-standalone/namespace.yaml"

    # Deploy Spark Master
    log_info "Deploying Spark Master..."
    kubectl apply -f "$SCRIPT_DIR/option1-standalone/spark-master.yaml"

    # Wait for master to be ready
    log_info "Waiting for Spark Master to be ready..."
    kubectl wait --for=condition=ready pod -l app=spark-master -n "$NAMESPACE" --timeout=300s

    # Deploy Spark Workers
    log_info "Deploying Spark Workers..."
    kubectl apply -f "$SCRIPT_DIR/option1-standalone/spark-worker.yaml"

    # Wait for workers to be ready
    log_info "Waiting for Spark Workers to be ready..."
    kubectl wait --for=condition=ready pod -l app=spark-worker -n "$NAMESPACE" --timeout=300s

    # Update Libra YAML with custom image
    log_info "Updating Libra deployment with image: ${IMAGE_NAME}:${IMAGE_TAG}"
    sed -i.bak "s|image: whereq/libra:1.0.0|image: ${IMAGE_NAME}:${IMAGE_TAG}|g" \
        "$SCRIPT_DIR/option1-standalone/libra.yaml"

    # Deploy Libra
    log_info "Deploying Libra..."
    kubectl apply -f "$SCRIPT_DIR/option1-standalone/libra.yaml"

    # Restore original file
    mv "$SCRIPT_DIR/option1-standalone/libra.yaml.bak" "$SCRIPT_DIR/option1-standalone/libra.yaml"

    # Wait for Libra to be ready
    log_info "Waiting for Libra to be ready..."
    kubectl wait --for=condition=ready pod -l app=libra -n "$NAMESPACE" --timeout=300s

    show_status_option1
}

deploy_option3() {
    log_info "Deploying Option 3: Spark Native Kubernetes Mode..."

    # Create namespace
    log_info "Creating namespace..."
    kubectl apply -f "$SCRIPT_DIR/option1-standalone/namespace.yaml"

    # Create RBAC
    log_info "Creating RBAC resources..."
    kubectl apply -f "$SCRIPT_DIR/option3-native/rbac.yaml"

    # Update Libra YAML with custom image
    log_info "Updating Libra deployment with image: ${IMAGE_NAME}:${IMAGE_TAG}"
    sed -i.bak "s|image: whereq/libra:1.0.0|image: ${IMAGE_NAME}:${IMAGE_TAG}|g" \
        "$SCRIPT_DIR/option3-native/libra-native.yaml"

    # Deploy Libra
    log_info "Deploying Libra..."
    kubectl apply -f "$SCRIPT_DIR/option3-native/libra-native.yaml"

    # Restore original file
    mv "$SCRIPT_DIR/option3-native/libra-native.yaml.bak" "$SCRIPT_DIR/option3-native/libra-native.yaml"

    # Wait for Libra to be ready
    log_info "Waiting for Libra to be ready..."
    kubectl wait --for=condition=ready pod -l app=libra-native -n "$NAMESPACE" --timeout=300s

    show_status_option3
}

show_status_option1() {
    echo ""
    log_info "=========================================="
    log_info "Deployment Complete (Option 1)"
    log_info "=========================================="
    echo ""

    log_info "Pods:"
    kubectl get pods -n "$NAMESPACE"
    echo ""

    log_info "Services:"
    kubectl get svc -n "$NAMESPACE"
    echo ""

    EXTERNAL_IP=$(kubectl get svc libra-service -n "$NAMESPACE" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "<pending>")

    log_info "Access URLs:"
    echo "  - Libra API: http://${EXTERNAL_IP}"
    echo "  - Libra Spark UI: http://${EXTERNAL_IP}:4040"
    echo "  - Spark Master UI: http://<master-ip>:8080"
    echo ""

    log_info "Test the API:"
    echo "  curl http://${EXTERNAL_IP}/api/v1/health"
    echo ""
}

show_status_option3() {
    echo ""
    log_info "=========================================="
    log_info "Deployment Complete (Option 3)"
    log_info "=========================================="
    echo ""

    log_info "Pods:"
    kubectl get pods -n "$NAMESPACE"
    echo ""

    log_info "Services:"
    kubectl get svc -n "$NAMESPACE"
    echo ""

    EXTERNAL_IP=$(kubectl get svc libra-service -n "$NAMESPACE" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "<pending>")

    log_info "Access URLs:"
    echo "  - Libra API: http://${EXTERNAL_IP}"
    echo "  - Libra Spark UI: http://${EXTERNAL_IP}:4040"
    echo ""

    log_info "Watch executor pods being created:"
    echo "  kubectl get pods -n $NAMESPACE -w"
    echo ""

    log_info "Test the API:"
    echo "  curl http://${EXTERNAL_IP}/api/v1/health"
    echo ""
}

# Parse arguments
DEPLOYMENT_MODE=""

while [[ $# -gt 0 ]]; do
    case $1 in
        option1|option3)
            DEPLOYMENT_MODE="$1"
            shift
            ;;
        --image)
            if [[ "$2" == *":"* ]]; then
                IMAGE_NAME="${2%:*}"
                IMAGE_TAG="${2##*:}"
            else
                IMAGE_NAME="$2"
            fi
            shift 2
            ;;
        --namespace)
            NAMESPACE="$2"
            shift 2
            ;;
        -h|--help)
            usage
            ;;
        *)
            log_error "Unknown option: $1"
            usage
            ;;
    esac
done

if [ -z "$DEPLOYMENT_MODE" ]; then
    usage
fi

# Main execution
check_prerequisites

read -p "Build and push Docker image? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    build_and_push_image
fi

case $DEPLOYMENT_MODE in
    option1)
        deploy_option1
        ;;
    option3)
        deploy_option3
        ;;
esac

log_info "Deployment script completed successfully!"
