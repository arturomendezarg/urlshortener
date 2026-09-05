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

echo "==> Applying namespace..."
kubectl apply -f "${SCRIPT_DIR}/00-namespace.yaml"

echo "==> Loading .env overrides if present (never committed -- see .gitignore)..."
# Same file docker-compose.yml already reads automatically; sourcing it here too means a
# customized POSTGRES_PASSWORD/RABBITMQ_PASSWORD/KEYCLOAK_ADMIN_PASSWORD applies identically
# whether the stack is run via docker-compose or kind, instead of the two drifting apart.
if [ -f "${REPO_ROOT}/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  source "${REPO_ROOT}/.env"
  set +a
fi

echo "==> Generating Secrets (never a static YAML file -- see AI_USAGE_LOG.md, PR #30 review)..."
# A PR review on this exact directory (artmendezarg, on the previous version of this file) asked
# "can you hide this password" on a Secret manifest that had KEYCLOAK_ADMIN_PASSWORD committed in
# plain text. The value itself was never real (the same fake dev default docker-compose.yml
# already declares in ARCHITECTURE.md section 8.2), so the fix is not about that string being
# secret -- it is about the PATTERN: a committed YAML file with a real-looking credential baked
# in is exactly the shape that turns into an actual leak the day someone reuses it with a real
# value and forgets to change it back. Fixed by generating every Secret here, at deploy time,
# from environment variables that fall back to the same declared-fake defaults if unset -- the
# exact same "${VAR:-default}" shape docker-compose.yml already uses for these same three
# passwords, so nothing with a real value ever has to be committed to get a custom one in.
kubectl create secret generic postgres-credentials \
  --namespace "${NAMESPACE}" \
  --from-literal=POSTGRES_DB=urlshortener \
  --from-literal=POSTGRES_USER=urlshortener \
  --from-literal=POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-urlshortener_local}" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic rabbitmq-credentials \
  --namespace "${NAMESPACE}" \
  --from-literal=RABBITMQ_USER="${RABBITMQ_USER:-urlshortener}" \
  --from-literal=RABBITMQ_PASSWORD="${RABBITMQ_PASSWORD:-urlshortener_local}" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic keycloak-admin-credentials \
  --namespace "${NAMESPACE}" \
  --from-literal=KEYCLOAK_ADMIN=admin \
  --from-literal=KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin_local}" \
  --dry-run=client -o yaml | kubectl apply -f -

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
