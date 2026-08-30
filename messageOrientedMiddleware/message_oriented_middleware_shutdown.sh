# Remove RabbitMQ cluster
# Source: https://www.rabbitmq.com/kubernetes/operator/using-operator#delete
kubectl delete rabbitmqcluster rabbit-mq-cluster

# Remove topology-operator
kubectl delete -f "https://github.com/rabbitmq/messaging-topology-operator/releases/download/v1.20.0/messaging-topology-operator-with-certmanager.yaml"

# Remove cluster-operator
kubectl delete -f "https://github.com/rabbitmq/cluster-operator/releases/download/v2.22.3/cluster-operator.yml"

# Remove cert-manager
kubectl delete -f https://github.com/jetstack/cert-manager/releases/download/v1.3.1/cert-manager.yaml

# Delete binding, queue and direct exchange for transmitting local density data
kubectl delete -f kubernetes/localDensityData/rabbit_mq_binding_local_density_data_deployment.yaml
kubectl delete -f kubernetes/localDensityData/rabbit_mq_queue_local_density_data_deployment.yaml
kubectl delete -f kubernetes/localDensityData/rabbit_mq_exchange_local_density_data_deployment.yaml

# Delete fanout exchange for transmitting global density data
kubectl delete -f kubernetes/rabbit_mq_exchange_global_density_data_deployment.yaml