# StatefulSet Migration Guide

## What Changed

The Spark deployment templates have been converted from **Deployment** to **StatefulSet** to provide predictable, friendly pod names.

### Before (Deployment):
```
NAME                            READY   STATUS    RESTARTS   AGE
spark-master-787d708797-jbbbt   1/1     Running   0          5m
spark-worker-6f9c8d7f5c-9xk2p   1/1     Running   0          5m
spark-worker-6f9c8d7f5c-h7m4q   1/1     Running   0          5m
```

### After (StatefulSet):
```
NAME              READY   STATUS    RESTARTS   AGE
spark-master-0    1/1     Running   0          5m
spark-worker-0    1/1     Running   0          5m
spark-worker-1    1/1     Running   0          5m
```

## Benefits of StatefulSet

1. **Predictable Names**: Pods have stable, predictable names (spark-master-0, spark-worker-0, spark-worker-1, etc.)
2. **Stable Network Identity**: Each pod gets a stable DNS name (e.g., spark-master-0.spark-master.namespace.svc.cluster.local)
3. **Ordered Deployment**: Pods are created in order (0, 1, 2, ...) and deleted in reverse order
4. **Better for Stateful Apps**: Ideal for distributed systems like Spark that benefit from stable identities

## Migration Steps

### Step 1: Clean Up Existing Deployment

```bash
cd k8s/standalone-gcs

# Clean up old Deployment-based installation
./cleanup.sh values-dev-dp.yaml

# Or manually if needed:
kubectl delete deployment spark-master spark-worker -n spark-dpdev
kubectl delete service spark-master -n spark-dpdev
```

### Step 2: Remove Generated Files

```bash
# Force regeneration with new templates
rm -rf generated/
```

### Step 3: Deploy with StatefulSet

```bash
# Deploy using the updated StatefulSet templates
./deploy.sh values-dev-dp.yaml install
```

### Step 4: Verify New Pod Names

```bash
# Check pod names
kubectl get pods -n spark-dpdev

# Expected output:
# NAME              READY   STATUS    RESTARTS   AGE
# spark-master-0    1/1     Running   0          1m
# spark-worker-0    1/1     Running   0          1m
# spark-worker-1    1/1     Running   0          1m
```

### Step 5: Verify Workers Connected to Master

```bash
# Check master UI
kubectl port-forward -n spark-dpdev spark-master-0 8080:8080

# Open http://localhost:8080 and verify workers are connected
```

## Changes to Files

### Modified Templates

1. **templates/spark-master.yaml**
   - Changed `kind: Deployment` → `kind: StatefulSet`
   - Added `serviceName: spark-master`
   - Pod names: `spark-master-0`

2. **templates/spark-worker.yaml**
   - Changed `kind: Deployment` → `kind: StatefulSet`
   - Added `serviceName: spark-worker`
   - Added headless service (clusterIP: None)
   - Pod names: `spark-worker-0`, `spark-worker-1`, etc.

3. **cleanup.sh**
   - Updated to delete StatefulSets instead of Deployments
   - Updated help text to reflect StatefulSet usage

### No Changes Needed

- ✅ Values files (values.yaml, values-dev.yaml, values-prod.yaml)
- ✅ deploy.sh script
- ✅ Namespace template
- ✅ GCS configuration

## Accessing Individual Pods

### Master Pod

```bash
# By pod name
kubectl exec -it spark-master-0 -n spark-dpdev -- /bin/bash

# By stable DNS (from within cluster)
# spark-master-0.spark-master.spark-dpdev.svc.cluster.local
```

### Worker Pods

```bash
# Access specific worker by index
kubectl exec -it spark-worker-0 -n spark-dpdev -- /bin/bash
kubectl exec -it spark-worker-1 -n spark-dpdev -- /bin/bash

# Port-forward to specific worker UI
kubectl port-forward -n spark-dpdev spark-worker-0 8081:8081
kubectl port-forward -n spark-dpdev spark-worker-1 8081:8082  # Different local port
```

## Scaling Workers

Scaling with StatefulSet maintains predictable names:

```bash
# Scale to 5 workers (creates spark-worker-2, spark-worker-3, spark-worker-4)
kubectl scale statefulset spark-worker -n spark-dpdev --replicas=5

# Scale down to 2 workers (deletes in reverse: spark-worker-4, spark-worker-3, ...)
kubectl scale statefulset spark-worker -n spark-dpdev --replicas=2
```

Or update your values file:

```yaml
worker:
  replicas: 5  # Change this
```

Then upgrade:

```bash
./deploy.sh values-dev-dp.yaml upgrade
```

## StatefulSet Behavior

### Ordered Creation

Pods are created in order:
1. spark-master-0 (waits until Running)
2. spark-worker-0 (waits until Running)
3. spark-worker-1 (waits until Running)
4. ...

### Ordered Deletion

Pods are deleted in reverse order:
1. spark-worker-2 (waits until deleted)
2. spark-worker-1 (waits until deleted)
3. spark-worker-0 (waits until deleted)
4. spark-master-0

### Rolling Updates

When updating the StatefulSet:
- Pods are updated in reverse order (highest index first)
- Each pod must be Running and Ready before updating the next
- This ensures the master stays available during updates

## Troubleshooting

### Pods Stuck in Pending

If a pod is stuck in Pending, check:

```bash
# Describe the stuck pod
kubectl describe pod spark-master-0 -n spark-dpdev

# Common causes:
# - Insufficient resources
# - PVC not bound (if using persistent volumes)
# - Node affinity not satisfied
```

### StatefulSet Not Creating Pods

```bash
# Check StatefulSet status
kubectl describe statefulset spark-master -n spark-dpdev

# Check events
kubectl get events -n spark-dpdev --sort-by='.lastTimestamp'
```

### Service Not Found

StatefulSet requires the Service to exist:

```bash
# Verify services exist
kubectl get svc -n spark-dpdev

# Should show:
# spark-master   ClusterIP   ...
# spark-worker   ClusterIP   None (headless)
```

## Reverting to Deployment (Not Recommended)

If you need to revert to Deployment:

1. Checkout previous templates from git
2. Clean up StatefulSet
3. Redeploy with Deployment templates

But StatefulSet is **recommended** for Spark clusters due to stable identities.

## Summary

| Aspect | Deployment | StatefulSet |
|--------|-----------|-------------|
| Pod Names | Random suffix | Sequential (0, 1, 2, ...) |
| Network Identity | Unstable | Stable DNS names |
| Creation Order | Parallel | Sequential |
| Deletion Order | Random | Reverse order |
| Best For | Stateless apps | Stateful apps like Spark |

**Recommendation**: Use StatefulSet for production Spark clusters for better management and debugging.
