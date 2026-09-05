#!/usr/bin/env bash
# Builds every service's image, loads them into a local kind cluster, and applies every manifest
# in this directory -- the "Para demostrar el despliegue en Kubernetes local" step of
# ARCHITECTURE.md section 9. Meant to be run from inside the Codespace (kubectl/kind/helm are
# installed there by .devcontainer/setup.sh), from the repo root or anywhere else -- it locates
# the repo root itself so it never depends on the caller's current directory.
#
# Idempotent: safe to re-run after a code change. It reuses the existing kind cluster if one
# named "url-shortener" is already running (no destructive recreate), rebuilds/reloads all five
# images unconditionally (fast: mvn's own incremental compilation and Docker's layer cache both
# still apply), and `kubectl apply` on unchanged manifests is a no-op. Deployments are also
# rollout-restarted after re-applying so a new image with the *same* tag is actually picked up --
# `imagePullPolicy: Never` means the kubelet trusts whatever image already sits on the node under
# that tag and will NOT notice a same-tag image changed underneath it on its own.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
CLUSTER_NAME="url-shortener"
NAMESPACE="url-shortener"
MODULES=(v1-legacy-monolith api-gateway v2-shortener-service analytics-worker bulk-processor)

echo "==> Repo root: ${REPO_ROOT}"

echo "==> Ensuring kind cluster '${CLUSTER_NAME}' exists..."
if ! kind get clusters 2>/dev/null | grep -qx "${CLUSTER_NAME}"; then
  kind create cluster --name "${CLUSTER_NAME}" --config "${SCRIPT_DIR}/kind-config.yaml"
else
  echo "    Already running, reusing it."
fi
kubectl config use-context "kind-${CLUSTER_NAME}"

echo "==> Building images (repo root as build context, see ../../Dockerfile)..."
for module in "${MODULES[@]}"; do
  echo "    -> ${module}:kind"
  docker build --build-arg "MODULE=${module}" -t "${module}:kind" -f "${REPO_ROOT}/Dockerfile" "${REPO_ROOT}"
done

echo "==> Loading images into the kind node (no image registry involved)..."
for module in "${MODULES[@]}"; do
  kind load docker-image "${module}:kind" --name "${CLUSTER_NAME}"
done

echo "==> Applying namespace, secrets and infra manifests..."
kubectl apply -f "${SCRIPT_DIR}/00-namespace.yaml"
kubectl apply -f "${SCRIPT_DIR}/01-secrets.yaml"

echo "==> Generating the Keycloak realm-import ConfigMap from infra/keycloak/realm-export.json..."
# Generated, not hand-duplicated in a YAML file -- see 13-keycloak.yaml's own top comment for why.
kubectl create configmap keycloak-realm-import \
  --from-file=realm-export.json="${REPO_ROOT}/infra/keycloak/realm-export.json" \
  --namespace "${NAMESPACE}" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl apply -f "${SCRIPT_DIR}/10-postgres.yaml"
kubectl apply -f "${SCRIPT_DIR}/11-redis.yaml"
kubectl apply -f "${SCRIPT_DIR}/12-rabbitmq.yaml"
kubectl apply -f "${SCRIPT_DIR}/13-keycloak.yaml"

echo "==> Waiting for infra to be ready before starting the app tier..."
# Not strictly required (the app Deployments' own readinessProbes already tolerate infra coming
# up after them -- see 20-v1-legacy-monolith.yaml's comment), but waiting here means the first
# `kubectl get pods` a reviewer runs right after this script finishes already shows a clean
# picture, instead of a page of transient CrashLoopBackOff-looking (but harmless) not-ready pods.
kubectl -n "${NAMESPACE}" wait --for=condition=available --timeout=180s \
  deployment/postgres deployment/redis deployment/rabbitmq deployment/keycloak

echo "==> Applying the app tier..."
kubectl apply -f "${SCRIPT_DIR}/20-v1-legacy-monolith.yaml"
kubectl apply -f "${SCRIPT_DIR}/21-api-gateway.yaml"
kubectl apply -f "${SCRIPT_DIR}/22-v2-shortener-service.yaml"
kubectl apply -f "${SCRIPT_DIR}/23-analytics-worker.yaml"
kubectl apply -f "${SCRIPT_DIR}/24-bulk-processor.yaml"

echo "==> Restarting app Deployments so a rebuilt same-tag image is actually picked up..."
kubectl -n "${NAMESPACE}" rollout restart \
  deployment/v1-legacy-monolith deployment/api-gateway deployment/v2-shortener-service \
  deployment/analytics-worker deployment/bulk-processor

echo "==> Waiting for the app tier to become available..."
kubectl -n "${NAMESPACE}" wait --for=condition=available --timeout=180s \
  deployment/v1-legacy-monolith deployment/api-gateway deployment/v2-shortener-service \
  deployment/analytics-worker deployment/bulk-processor

echo "==> Done. See infra/k8s/README.md for how to reach it and smoke-test it."
kubectl -n "${NAMESPACE}" get pods
