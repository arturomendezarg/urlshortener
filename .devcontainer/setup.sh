#!/usr/bin/env bash
# Instala kubectl, kind y helm con binarios/scripts oficiales, en vez de una
# devcontainer feature de terceros para Kubernetes — la primera versión del
# devcontainer usaba la feature `kubectl-helm-minikube` y el build del
# Codespace falló al construir las features (error 1302), sin log suficiente
# para diagnosticar la causa exacta. Se reemplaza por instalación directa,
# con menos piezas moviéndose y más fácil de depurar si algo falla aquí.
set -euo pipefail

ARCH="$(dpkg --print-architecture)"

echo "==> Instalando kubectl..."
KUBECTL_VERSION="$(curl -L -s https://dl.k8s.io/release/stable.txt)"
sudo curl -Lo /usr/local/bin/kubectl "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/${ARCH}/kubectl"
sudo chmod +x /usr/local/bin/kubectl

echo "==> Instalando kind..."
sudo curl -Lo /usr/local/bin/kind "https://kind.sigs.k8s.io/dl/v0.24.0/kind-linux-${ARCH}"
sudo chmod +x /usr/local/bin/kind

echo "==> Instalando helm..."
curl -fsSL -o /tmp/get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3
chmod +x /tmp/get_helm.sh
sudo /tmp/get_helm.sh

echo "==> Versiones instaladas:"
kubectl version --client
kind version
helm version
