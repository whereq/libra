#!/bin/bash
##############################################################################
# Cleanup Spark Standalone Cluster Deployments
#
# This script removes Spark master and worker deployments/pods while
# preserving the namespace and secrets for future redeployment.
#
# Usage:
#   ./cleanup.sh [values-file]
#
# Examples:
#   ./cleanup.sh values-dev.yaml
#   ./cleanup.sh values-dev-dp.yaml
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
OUTPUT_DIR="${SCRIPT_DIR}/generated"

##############################################################################
# Functions
##############################################################################

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
Usage: $0 [values-file]

Arguments:
    values-file     Path to values YAML file (default: values.yaml)

Description:
    Removes Spark master and worker StatefulSets/pods from Kubernetes.
    Preserves namespace and secrets for future redeployment.

Examples:
    # Clean up development deployment
    $0 values-dev.yaml

    # Clean up specific deployment
    $0 values-dev-dp.yaml

What Gets Removed:
    ✓ Spark Master StatefulSet
    ✓ Spark Master Service
    ✓ Spark Master Pods (spark-master-0)
    ✓ Spark Worker StatefulSet
    ✓ Spark Worker Service
    ✓ Spark Worker Pods (spark-worker-0, spark-worker-1, etc.)

What Gets Preserved:
    ✓ Namespace
    ✓ GCS Secret
    ✓ ConfigMaps (if any)
    ✓ PersistentVolumes (if any)

EOF
}

# Parse YAML file (simple key=value parser)
parse_yaml() {
    local yaml_file=$1
    local prefix=$2

    local s='[[:space:]]*'
    local w='[a-zA-Z0-9_]*'
    local fs=$(echo @|tr @ '\034')

    sed -ne "s|^\($s\):|\\1|" \
         -e "s|^\($s\)\($w\)$s:$s[\"']\(.*\)[\"']$s\$|\\1$fs\\2$fs\\3|p" \
         -e "s|^\($s\)\($w\)$s:$s\(.*\)$s\$|\\1$fs\\2$fs\\3|p" $yaml_file |
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

# Load namespace from values file
load_namespace() {
    local values_file=$1

    if [ ! -f "${values_file}" ]; then
        log_error "Values file not found: ${values_file}"
        exit 1
    fi

    eval $(parse_yaml "${values_file}")
    export NAMESPACE="${namespace}"

    if [ -z "${NAMESPACE}" ]; then
        log_error "Could not determine namespace from values file"
        exit 1
    fi

    log_info "Target namespace: ${NAMESPACE}"
}

# Clean up StatefulSets
cleanup() {
    log_warn "Cleaning up Spark StatefulSets..."
    echo ""

    # Check if generated files exist
    if [ ! -d "${OUTPUT_DIR}" ]; then
        log_warn "Generated directory not found, using direct kubectl commands"
        cleanup_direct
        return
    fi

    # Clean up using generated YAML files
    if [ -f "${OUTPUT_DIR}/spark-worker.yaml" ]; then
        log_info "Removing Spark Workers..."
        kubectl delete -f "${OUTPUT_DIR}/spark-worker.yaml" --ignore-not-found=true
    else
        log_warn "spark-worker.yaml not found, skipping"
    fi

    if [ -f "${OUTPUT_DIR}/spark-master.yaml" ]; then
        log_info "Removing Spark Master..."
        kubectl delete -f "${OUTPUT_DIR}/spark-master.yaml" --ignore-not-found=true
    else
        log_warn "spark-master.yaml not found, skipping"
    fi

    echo ""
    log_info "Cleanup complete!"
}

# Direct cleanup using kubectl (fallback)
cleanup_direct() {
    log_info "Using direct kubectl cleanup..."

    # Delete statefulsets
    log_info "Removing statefulsets..."
    kubectl delete statefulset spark-master -n "${NAMESPACE}" --ignore-not-found=true
    kubectl delete statefulset spark-worker -n "${NAMESPACE}" --ignore-not-found=true

    # Delete services
    log_info "Removing services..."
    kubectl delete service spark-master -n "${NAMESPACE}" --ignore-not-found=true
    kubectl delete service spark-worker -n "${NAMESPACE}" --ignore-not-found=true

    echo ""
    log_info "Cleanup complete!"
}

# Show remaining resources
show_status() {
    echo ""
    log_info "Remaining Resources in ${NAMESPACE}:"
    echo ""

    echo -e "${BLUE}Namespace:${NC}"
    kubectl get namespace "${NAMESPACE}" 2>/dev/null || echo "  Not found"
    echo ""

    echo -e "${BLUE}Secrets:${NC}"
    kubectl get secrets -n "${NAMESPACE}" 2>/dev/null || echo "  None found"
    echo ""

    echo -e "${BLUE}ConfigMaps:${NC}"
    kubectl get configmaps -n "${NAMESPACE}" 2>/dev/null || echo "  None found"
    echo ""

    echo -e "${BLUE}PersistentVolumes:${NC}"
    kubectl get pvc -n "${NAMESPACE}" 2>/dev/null || echo "  None found"
    echo ""

    echo -e "${BLUE}Pods (should be empty):${NC}"
    kubectl get pods -n "${NAMESPACE}" 2>/dev/null || echo "  None found"
    echo ""
}

##############################################################################
# Main
##############################################################################

echo -e "${BLUE}"
echo "========================================"
echo "  Spark Cluster Cleanup"
echo "========================================"
echo -e "${NC}"

# Parse arguments
VALUES_FILE="${1:-values.yaml}"

# Check for help
if [ "$1" = "help" ] || [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
    print_usage
    exit 0
fi

# Check dependencies
if ! command -v kubectl &> /dev/null; then
    log_error "kubectl not found. Please install kubectl first."
    exit 1
fi

# Load configuration
load_namespace "${VALUES_FILE}"

# Confirm cleanup
echo ""
log_warn "This will remove Spark master and worker StatefulSets from: ${NAMESPACE}"
echo ""
echo "What will be removed:"
echo "  - Spark Master StatefulSet & Service"
echo "  - Spark Worker StatefulSet & Service"
echo "  - All associated Pods (spark-master-0, spark-worker-*, etc.)"
echo ""
echo "What will be preserved:"
echo "  - Namespace: ${NAMESPACE}"
echo "  - Secrets (including GCS keys)"
echo "  - ConfigMaps"
echo "  - PersistentVolumes"
echo ""
read -p "Continue? (y/N) " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    log_info "Cleanup cancelled"
    exit 0
fi

# Execute cleanup
cleanup

# Show remaining resources
show_status

log_info "Done!"
echo ""
log_info "To redeploy: ./deploy.sh ${VALUES_FILE} install"
log_info "To remove namespace: kubectl delete namespace ${NAMESPACE}"
