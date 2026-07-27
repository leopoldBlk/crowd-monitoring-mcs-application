# Remove RabbitMQ cluster incl. cluster operator
# Source: https://www.rabbitmq.com/kubernetes/operator/using-operator#delete
kubectl delete rabbitmqcluster rabbit-mq-cluster

# Remove deployment for topology operator
kubectl delete deployment messaging-topology-operator

# Remove deployments for cert-manager
kubectl delete --all deployments --namespace=cert-manager