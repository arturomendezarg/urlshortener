#!/usr/bin/env bash
# Builds every service's image, imports them into a local k3d cluster, and applies every manifest
# in this directory -- the same "demonstrate the local Kubernetes deployment" step of
# ARCHITECTURE.md section 9 that deploy-to-kind.sh was written for, on a runtime that actually
# works inside a GitHub Codespace.
#
# Why a second script instead of a --runtime flag on deploy-to-kind.sh: `kind` cannot bootstrap a
# control plane inside a Codespace at all (7 attempts, elimination table in README.md), and k3d
# can, because k3s never invokes kubeadm. Both remain legitimate targets -- kind works fine on an
# ordinary Docker Desktop host -- so neither script is deleted and neither is made to depend on
# the other's assumptions. Everything below cluster creation and image loading is the same logic
# as deploy-to-kind.sh: these manifests are plain Deployment/Service/NodePort and portable as-is.
#
# Idempotent: safe to re-run after a code change. It reuses the existing k3d cluster if one named
# "url-shortener" is already running AND has a Ready node, rebuilds/reimports all five images
# unconditionally (fast: mvn's incremental compilation and Docker's layer cache both still
# apply), and `kubectl apply` on unchanged manifests is a no-op. Deployments are rollout-restarted
# after re-applying so a new image with the *same* tag is actually picked up -- `imagePullPolicy:
# Never` means the kubelet trusts whatever image already sits on the node under that tag and will
# NOT notice a same-tag image changed underneath it on its own.
#
# Self-healing for the most common failure seen running this in a Codespace: the Codespace's own
# Docker daemon gets restarted (idle timeout, VM resume) out from under a running cluster, leaving
# a k3d cluster that "exists" but whose node container simply isn't running any more -- not
# corrupted, just stopped. On that case this script tries `k3d cluster start` first (non-
# destructive: the same node, same data, just started back up) before ever considering a
# destructive recreate, and only deletes-and-recreates the cluster if that restart doesn't bring a
# Ready node back within a short wait. Either way, it prints the diagnostic commands (docker logs
# on the node container) BEFORE acting, so the real cause is never silently discarded even when
# the script goes on to recover on its own. k3d itself is auto-installed if missing, since it is
# NOT one of the tools .devcontainer/setup.sh installs (that script installs kind, not k3d -- see
# the block comment above about why this is a second script instead of a --runtime flag).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
CLUSTER_NAME="url-shortener"
CONTEXT="k3d-${CLUSTER_NAME}"
NAMESPACE="url-shortener"
MODULES=(v1-legacy-monolith api-gateway v2-shortener-service analytics-worker bulk-processor)
# The tag stays ":kind" on purpose. It is the tag the five Deployment manifests already declare,
# and those manifests are shared by both local-cluster paths -- renaming it would mean editing all
# five plus deploy-to-kind.sh, a script that cannot be verified in this environment at all, for a
# purely cosmetic gain. Read it as "the local build tag", not as "built for kind".
IMAGE_TAG="kind"

echo "==> Repo root: ${REPO_ROOT}"

for tool in docker kubectl; do
  if ! command -v "${tool}" >/dev/null 2>&1; then
    echo "ERROR: '${tool}' is not on the PATH." >&2
    echo "       Both are installed by .devcontainer/setup.sh -- if this is a fresh shell outside" >&2
    echo "       that devcontainer, install them yourself before re-running this script." >&2
    exit 1
  fi
done

if ! command -v k3d >/dev/null 2>&1; then
  # k3d is deliberately NOT one of the tools .devcontainer/setup.sh installs (it installs kind --
  # see the block comment above), so a fresh clone/Codespace/reviewer checkout genuinely won't
  # have it yet. Install it here instead of just printing the command and exiting: the whole point
  # of this branch existing is to make "clone the repo and run this script" work unattended.
  echo "==> 'k3d' not found on PATH -- installing it (see https://k3d.io/#installation)..."
  curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | bash
  if ! command -v k3d >/dev/null 2>&1; then
    echo "ERROR: the k3d install script ran but 'k3d' is still not on the PATH." >&2
    echo "       It may have installed to a directory outside your current PATH -- open a new" >&2
    echo "       shell and retry, or see https://k3d.io/#installation for a manual install." >&2
    exit 1
  fi
fi

node_is_ready() {
  kubectl --context "${CONTEXT}" get nodes \
      -o jsonpath='{.items[*].status.conditions[?(@.type=="Ready")].status}' 2>/dev/null \
      | grep -q True
}

# Polls up to (attempts * 3)s for the node to report Ready, instead of a fixed sleep -- most
# recoveries are much faster than the worst case this has to tolerate.
wait_for_ready_node() {
  local attempts="${1}"
  for ((i = 1; i <= attempts; i++)); do
    if node_is_ready; then
      return 0
    fi
    sleep 3
  done
  return 1
}

echo "==> Ensuring k3d cluster '${CLUSTER_NAME}' exists and is usable..."
if k3d cluster list 2>/dev/null | awk 'NR>1 {print $1}' | grep -qx "${CLUSTER_NAME}"; then
  # "Exists" is not the same as "usable" -- the same distinction deploy-to-kind.sh documents. A
  # failed creation, or (far more commonly in a Codespace) the Docker daemon restarting out from
  # under an otherwise-fine cluster, can leave a cluster listed whose node isn't Ready; going on to
  # build five images and apply every manifest against it fails minutes later with a far more
  # confusing error than the real one. So verify readiness, not just existence.
  if node_is_ready; then
    echo "    Already running with a Ready node, reusing it."
  else
    echo "    Cluster '${CLUSTER_NAME}' exists but has no Ready node." >&2
    echo "    Real cause, if this repeats, is usually visible in:" >&2
    echo "        docker logs k3d-${CLUSTER_NAME}-server-0 --tail 100" >&2
    echo "==> Attempting a non-destructive recovery: 'k3d cluster start ${CLUSTER_NAME}'..." >&2
    k3d cluster start "${CLUSTER_NAME}" || true
    if wait_for_ready_node 20; then
      echo "    Recovered: the existing node is Ready again, no data lost."
    else
      echo "    Node still not Ready 60s after 'k3d cluster start'." >&2
      echo "==> Falling back to a destructive recreate (delete + create from scratch)..." >&2
      k3d cluster delete "${CLUSTER_NAME}"
      k3d cluster create --config "${SCRIPT_DIR}/k3d-config.yaml"
    fi
  fi
else
  # Fails fast and clearly if docker-compose is still up: both claim host ports 8081/8082/8084 by
  # design (see k3d-config.yaml). The remedy is `docker-compose down`, not a different port.
  k3d cluster create --config "${SCRIPT_DIR}/k3d-config.yaml"
fi
kubectl config use-context "${CONTEXT}"

echo "==> Building images (repo root as build context, see ../../Dockerfile)..."
for module in "${MODULES[@]}"; do
  echo "    -> ${module}:${IMAGE_TAG}"
  docker build --build-arg "MODULE=${module}" -t "${module}:${IMAGE_TAG}" -f "${REPO_ROOT}/Dockerfile" "${REPO_ROOT}"
done

echo "==> Importing images into the k3d node (no image registry involved)..."
# k3d's equivalent of `kind load docker-image`: it streams the image straight into the node's
# containerd store, which is what makes `imagePullPolicy: Never` in the manifests work.
k3d image import "${MODULES[@]/%/:${IMAGE_TAG}}" --cluster "${CLUSTER_NAME}"

echo "==> Applying namespace..."
kubectl apply -f "${SCRIPT_DIR}/00-namespace.yaml"

echo "==> Loading .env overrides if present (never committed -- see .gitignore)..."
# Same file docker-compose.yml already reads automatically; sourcing it here too means a
# customized POSTGRES_PASSWORD/RABBITMQ_PASSWORD/KEYCLOAK_ADMIN_PASSWORD applies identically
# whether the stack is run via docker-compose, kind, or k3d, instead of the three drifting apart.
if [ -f "${REPO_ROOT}/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  source "${REPO_ROOT}/.env"
  set +a
fi

echo "==> Generating Secrets (never a static YAML file -- see AI_USAGE_LOG.md)..."
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
kubectl create configmap keycloak-realm-import \
  --from-file=realm-export.json="${REPO_ROOT}/infra/keycloak/realm-export.json" \
  --namespace "${NAMESPACE}" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl apply -f "${SCRIPT_DIR}/10-postgres.yaml"
kubectl apply -f "${SCRIPT_DIR}/11-redis.yaml"
kubectl apply -f "${SCRIPT_DIR}/12-rabbitmq.yaml"
kubectl apply -f "${SCRIPT_DIR}/13-keycloak.yaml"

echo "==> Waiting for infra to be ready before starting the app tier..."
# 300s, not 180s: Keycloak alone was measured at ~41s to boot with its realm import, and every
# container here now owns its boot window through a startupProbe worth up to 5 minutes. A wait
# shorter than the startupProbe it is waiting on just reports a failure the cluster does not have.
kubectl -n "${NAMESPACE}" wait --for=condition=available --timeout=300s \
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
kubectl -n "${NAMESPACE}" wait --for=condition=available --timeout=300s \
  deployment/v1-legacy-monolith deployment/api-gateway deployment/v2-shortener-service \
  deployment/analytics-worker deployment/bulk-processor

echo "==> Done. See infra/k8s/README.md for how to reach it and smoke-test it."
kubectl -n "${NAMESPACE}" get pods
