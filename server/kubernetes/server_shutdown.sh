# Remove deployment for crowd monitoring server
kubectl delete deployment crowd-monitoring-server-deployment

# Output timestamp of server shutdown
date +%s