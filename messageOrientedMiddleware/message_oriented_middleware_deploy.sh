# Deployment of cert-manager
# ---------------------------------------------------------
# Install cert-manager
# Source: https://www.rabbitmq.com/kubernetes/operator/install-topology-operator
kubectl apply -f https://github.com/jetstack/cert-manager/releases/download/v1.3.1/cert-manager.yaml

# Wait till cert-manager components are deployed
# Source: https://kubernetes.io/docs/reference/kubectl/generated/kubectl_rollout/kubectl_rollout_status/
kubectl rollout status deployment/cert-manager -n cert-manager
kubectl rollout status deployment/cert-manager-cainjector -n cert-manager
kubectl rollout status deployment/cert-manager-webhook -n cert-manager


# Deployment of RabbitMQ cluster operator
# ---------------------------------------
# Install cluster operator for running RabbitMQ cluster in Kubernetes
# Source: https://www.rabbitmq.com/kubernetes/operator/install-operator
kubectl apply -f "https://github.com/rabbitmq/cluster-operator/releases/download/v2.22.3/cluster-operator.yml"

# Wait till cluster operator is deployed
# Source: https://kubernetes.io/docs/reference/kubectl/generated/kubectl_rollout/kubectl_rollout_status/
kubectl rollout status deployment/rabbitmq-cluster-operator -n rabbitmq-system


# Deployment of RabbitMQ topology operator
# ---------------------------------------------------------
# Install topology operator for configuration of RabbitMQ cluster in Kubernetes
# Source: https://www.rabbitmq.com/kubernetes/operator/install-topology-operator
kubectl apply -f "https://github.com/rabbitmq/messaging-topology-operator/releases/download/v1.20.0/messaging-topology-operator-with-certmanager.yaml"

# Wait till topology operator is deployed
# Source: https://kubernetes.io/docs/reference/kubectl/generated/kubectl_rollout/kubectl_rollout_status/
kubectl rollout status deployment/messaging-topology-operator -n rabbitmq-system


# Deployment of RabbitMQ cluster incl. prometheus operator, exchanges and queue
# ---------------------------------------------------------
# Deploy RabbitMQ cluster
kubectl apply -f kubernetes/rabbit_mq_cluster_deployment.yaml

# Wait till the single nodes comprising the RabbitMQ cluster are deployed
# Source: https://kubernetes.io/docs/reference/kubectl/generated/kubectl_wait/
kubectl wait --for=condition=Ready pod --all

# Create direct exchange with quorum queue for transmitting local density data and corresponding binding
kubectl apply -f kubernetes/localDensityData/rabbit_mq_exchange_local_density_data_deployment.yaml
kubectl apply -f kubernetes/localDensityData/rabbit_mq_queue_local_density_data_deployment.yaml
kubectl apply -f kubernetes/localDensityData/rabbit_mq_binding_local_density_data_deployment.yaml

# Create fanout exchange for transmitting global density data
kubectl apply -f kubernetes/rabbit_mq_exchange_global_density_data_deployment.yaml