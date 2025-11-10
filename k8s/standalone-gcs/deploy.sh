#!/bin/bash
##############################################################################
# Deploy Spark Standalone Cluster with GCS to Kubernetes
#
# This script processes values.yaml and deploys Spark cluster components.
#
# Usage:
#   ./deploy.sh [values-file] [action]
#
# Examples:
#   ./deploy.sh values-dev.yaml install
#   ./deploy.sh values-prod.yaml install
#   ./deploy.sh values-dev.yaml uninstall
#   ./deploy.sh values-dev.yaml status
##############################################################################

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATES_DIR="${SCRIPT_DIR}/templates"
OUTPUT_DIR="${SCRIPT_DIR}/generated"

##############################################################################
# Functions
##############################################################################

print_banner() {
    echo -e "${BLUE}"
    echo "========================================"
    echo "  Spark Standalone GCS Deployment"
    echo "========================================"
    echo -e "${NC}"
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

print_usage() {
    cat << EOF
Usage: $0 [values-file] [action]

Arguments:
    values-file     Path to values YAML file (default: values.yaml)
    action          Action to perform (default: install)

Actions:
    install         Deploy Spark cluster
    uninstall       Remove Spark cluster
    upgrade         Update existing deployment
    status          Check deployment status
    logs            View logs

Examples:
    # Deploy with default values
    $0

    # Deploy development environment
    $0 values-dev.yaml install

    # Deploy production environment
    $0 values-prod.yaml install

    # Check status
    $0 values-dev.yaml status

    # View logs
    $0 values-dev.yaml logs

    # Remove deployment
    $0 values-dev.yaml uninstall

EOF
}

# Parse YAML file (simple key=value parser for our structure)
parse_yaml() {
    local yaml_file=$1
    local prefix=$2

    local s='[[:space:]]*'
    local w='[a-zA-Z0-9_]*'
    local fs=$(echo @|tr @ '\034')

    sed -ne "s|^\($s\):|\1|" \
         -e "s|^\($s\)\($w\)$s:$s[\"']\(.*\)[\"']$s\$|\1$fs\2$fs\3|p" \
         -e "s|^\($s\)\($w\)$s:$s\(.*\)$s\$|\1$fs\2$fs\3|p" $yaml_file |
    awk -F$fs '{
        indent = length($1)/2;
        vname[indent] = $2;
        for (i in vname) {if (i > indent) {delete vname[i]}}
        if (length($3) > 0) {
            vn=""; for (i=0; i<indent; i++) {vn=(vn)(vname[i])("_")}
            printf("%s%s%s=\"%s\"\n", "'$prefix'",vn, $2, $3);
        }
    }'
}

# Export variables from values file
load_values() {
    local values_file=$1

    if [ ! -f "${values_file}" ]; then
        log_error "Values file not found: ${values_file}"
        exit 1
    fi

    log_info "Loading values from: ${values_file}"

    # Source the parsed YAML
    eval $(parse_yaml "${values_file}")

    # Export required variables for envsubst
    export NAMESPACE="${namespace}"

    # Set environment label from values file or default
    if [ -n "${environment}" ]; then
        export ENVIRONMENT="${environment}"
    else
        export ENVIRONMENT="default"
    fi

    # GCS configuration
    export GCS_SECRET_NAME="${gcs_secretName}"
    export GCS_BUCKET="${gcs_bucket}"
    export GCS_PROJECT_ID="${gcs_projectId}"

    # Image configuration
    export IMAGE_REPOSITORY="${image_repository}"
    export IMAGE_TAG="${image_tag}"
    export IMAGE_PULL_POLICY="${image_pullPolicy}"

    # Master configuration
    export MASTER_REPLICAS="${master_replicas}"
    export MASTER_SERVICE_TYPE="${master_service_type}"
    export MASTER_RPC_PORT="${master_service_rpcPort}"
    export MASTER_WEBUI_PORT="${master_service_webUIPort}"
    export MASTER_MEMORY_REQUEST="${master_resources_requests_memory}"
    export MASTER_CPU_REQUEST="${master_resources_requests_cpu}"
    export MASTER_MEMORY_LIMIT="${master_resources_limits_memory}"
    export MASTER_CPU_LIMIT="${master_resources_limits_cpu}"

    # Worker configuration
    export WORKER_REPLICAS="${worker_replicas}"
    export WORKER_WEBUI_PORT="${worker_service_webUIPort}"
    export WORKER_CORES="${worker_env_SPARK_WORKER_CORES}"
    export WORKER_MEMORY="${worker_env_SPARK_WORKER_MEMORY}"
    export WORKER_MEMORY_REQUEST="${worker_resources_requests_memory}"
    export WORKER_CPU_REQUEST="${worker_resources_requests_cpu}"
    export WORKER_MEMORY_LIMIT="${worker_resources_limits_memory}"
    export WORKER_CPU_LIMIT="${worker_resources_limits_cpu}"

    # History Server configuration
    # Strip inline comments and whitespace from boolean values
    local history_enabled_raw="${historyServer_enabled:-false}"
    export HISTORY_ENABLED=$(echo "${history_enabled_raw}" | awk '{print $1}')
    export HISTORY_REPLICAS="${historyServer_replicas:-1}"
    export HISTORY_SERVICE_TYPE="${historyServer_service_type:-ClusterIP}"
    export HISTORY_PORT="${historyServer_service_port:-18080}"
    export HISTORY_MEMORY_REQUEST="${historyServer_resources_requests_memory:-1Gi}"
    export HISTORY_CPU_REQUEST="${historyServer_resources_requests_cpu:-500m}"
    export HISTORY_MEMORY_LIMIT="${historyServer_resources_limits_memory:-2Gi}"
    export HISTORY_CPU_LIMIT="${historyServer_resources_limits_cpu:-1000m}"

    log_info "Configuration loaded successfully"
    log_info "  Namespace: ${NAMESPACE}"
    log_info "  GCS Bucket: ${GCS_BUCKET}"
    log_info "  GCS Project: ${GCS_PROJECT_ID}"
    log_info "  Image: ${IMAGE_REPOSITORY}:${IMAGE_TAG}"
    log_info "  History Server: ${HISTORY_ENABLED}"
}

# Process templates with envsubst
process_templates() {
    log_info "Processing templates..."

    # Create output directory
    mkdir -p "${OUTPUT_DIR}"

    # Process each template
    for template in "${TEMPLATES_DIR}"/*.yaml; do
        local filename=$(basename "${template}")
        local output="${OUTPUT_DIR}/${filename}"

        log_info "  Processing: ${filename}"
        envsubst < "${template}" > "${output}"
    done

    log_info "Templates processed successfully"
}

# Install Spark cluster
install() {
    log_info "Deploying Spark standalone cluster with GCS..."

    # Create namespace (skip if exists)
    if kubectl get namespace "${NAMESPACE}" &> /dev/null; then
        log_info "Namespace already exists: ${NAMESPACE}"
    else
        log_info "Creating namespace: ${NAMESPACE}"
        kubectl apply -f "${OUTPUT_DIR}/namespace.yaml"
    fi

    # Verify GCS secret exists
    if ! kubectl get secret "${GCS_SECRET_NAME}" -n "${NAMESPACE}" &> /dev/null; then
        log_error "GCS secret not found: ${GCS_SECRET_NAME}"
        log_error "Please create it first:"
        log_error "  kubectl create secret generic ${GCS_SECRET_NAME} \\"
        log_error "    --from-file=gcs-key.json=./path/to/your-key.json \\"
        log_error "    -n ${NAMESPACE}"
        exit 1
    fi
    log_info "GCS secret verified: ${GCS_SECRET_NAME}"

    # Deploy Spark Master
    log_info "Deploying Spark Master..."
    kubectl apply -f "${OUTPUT_DIR}/spark-master.yaml"

    # Deploy Spark Workers
    log_info "Deploying Spark Workers..."
    kubectl apply -f "${OUTPUT_DIR}/spark-worker.yaml"

    # Deploy History Server (if enabled)
    if [ "${HISTORY_ENABLED}" = "true" ]; then
        log_info "Deploying Spark History Server..."
        kubectl apply -f "${OUTPUT_DIR}/spark-history-server.yaml"
    else
        log_info "Spark History Server is disabled (set historyServer.enabled: true to enable)"
    fi

    log_info "Deployment complete!"
    echo ""
    show_status
}

# Uninstall Spark cluster
uninstall() {
    log_warn "Uninstalling Spark standalone cluster..."

    kubectl delete -f "${OUTPUT_DIR}/spark-history-server.yaml" --ignore-not-found=true
    kubectl delete -f "${OUTPUT_DIR}/spark-worker.yaml" --ignore-not-found=true
    kubectl delete -f "${OUTPUT_DIR}/spark-master.yaml" --ignore-not-found=true

    log_info "Spark cluster removed"
    log_info "Note: Namespace and Secret were NOT removed"
    log_info "To remove namespace: kubectl delete namespace ${NAMESPACE}"
}

# Upgrade deployment
upgrade() {
    log_info "Upgrading Spark standalone cluster..."

    kubectl apply -f "${OUTPUT_DIR}/spark-master.yaml"
    kubectl apply -f "${OUTPUT_DIR}/spark-worker.yaml"

    if [ "${HISTORY_ENABLED}" = "true" ]; then
        kubectl apply -f "${OUTPUT_DIR}/spark-history-server.yaml"
    fi

    log_info "Upgrade complete!"
}

# Show deployment status
show_status() {
    log_info "Deployment Status:"
    echo ""

    echo -e "${BLUE}Namespace:${NC}"
    kubectl get namespace "${NAMESPACE}" 2>/dev/null || echo "  Not found"
    echo ""

    echo -e "${BLUE}Pods:${NC}"
    kubectl get pods -n "${NAMESPACE}" -l component=spark
    echo ""

    echo -e "${BLUE}Services:${NC}"
    kubectl get svc -n "${NAMESPACE}" -l app=spark-master
    echo ""

    echo -e "${BLUE}Master UI:${NC}"
    echo "  kubectl port-forward -n ${NAMESPACE} svc/spark-master ${MASTER_WEBUI_PORT}:${MASTER_WEBUI_PORT}"
    echo "  Then open: http://localhost:${MASTER_WEBUI_PORT}"
    echo ""

    echo -e "${BLUE}Worker UI:${NC}"
    local worker_pod=$(kubectl get pod -n "${NAMESPACE}" -l app=spark-worker -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
    if [ -n "${worker_pod}" ]; then
        echo "  kubectl port-forward -n ${NAMESPACE} ${worker_pod} ${WORKER_WEBUI_PORT}:${WORKER_WEBUI_PORT}"
        echo "  Then open: http://localhost:${WORKER_WEBUI_PORT}"
    fi
    echo ""

    if [ "${HISTORY_ENABLED}" = "true" ]; then
        echo -e "${BLUE}History Server UI:${NC}"
        echo "  kubectl port-forward -n ${NAMESPACE} svc/spark-history-server ${HISTORY_PORT}:${HISTORY_PORT}"
        echo "  Then open: http://localhost:${HISTORY_PORT}"
    fi
}

# Show logs
show_logs() {
    local component=${1:-master}

    case "${component}" in
        master)
            log_info "Showing Spark Master logs..."
            kubectl logs -n "${NAMESPACE}" -l app=spark-master --tail=100 -f
            ;;
        worker)
            log_info "Showing Spark Worker logs..."
            kubectl logs -n "${NAMESPACE}" -l app=spark-worker --tail=100 -f
            ;;
        history)
            log_info "Showing Spark History Server logs..."
            kubectl logs -n "${NAMESPACE}" -l app=spark-history-server --tail=100 -f
            ;;
        *)
            log_error "Unknown component: ${component}"
            log_info "Available components: master, worker, history"
            exit 1
            ;;
    esac
}

##############################################################################
# Main
##############################################################################

print_banner

# Parse arguments
VALUES_FILE="${1:-values.yaml}"
ACTION="${2:-install}"

# Validate action
case "${ACTION}" in
    install|uninstall|upgrade|status|logs)
        ;;
    help|-h|--help)
        print_usage
        exit 0
        ;;
    *)
        log_error "Unknown action: ${ACTION}"
        print_usage
        exit 1
        ;;
esac

# Check dependencies
if ! command -v kubectl &> /dev/null; then
    log_error "kubectl not found. Please install kubectl first."
    exit 1
fi

if ! command -v envsubst &> /dev/null; then
    log_error "envsubst not found. Please install gettext package."
    log_error "  Ubuntu/Debian: sudo apt-get install gettext-base"
    log_error "  macOS: brew install gettext"
    exit 1
fi

# Load configuration
load_values "${VALUES_FILE}"

# Process templates
process_templates

# Execute action
case "${ACTION}" in
    install)
        install
        ;;
    uninstall)
        uninstall
        ;;
    upgrade)
        upgrade
        ;;
    status)
        show_status
        ;;
    logs)
        show_logs "${3:-master}"
        ;;
esac

log_info "Done!"
