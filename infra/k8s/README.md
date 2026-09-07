# Local Kubernetes deployment (k3d / kind)

Manifests and scripts to run the whole system (Postgres, Redis, RabbitMQ, Keycloak, and the
monorepo's 5 Spring Boot services) inside a single-node local cluster, within this repo's
Codespace. See `ARCHITECTURE.md` section 7 (Day 1: Kubernetes manifests for local deployment)
and section 9 (Setup Instructions) for the context and this exercise's cut priorities.

> **Verified status.** The system runs end-to-end on Kubernetes, via **k3d**
> (`./infra/k8s/deploy-to-k3d.sh`). All 9 pods reach `Ready`, a JWT issued by the in-cluster
> Keycloak is accepted by `v2-shortener-service`, and both the V1 and V2 redirect paths were
> exercised with real HTTP calls — the transcript is in the "Smoke test" section below, and the
> four defects that run uncovered are recorded in `AI_USAGE_LOG.md`.
>
> **`kind` remains unverified in a Codespace and is expected to stay that way**: it cannot
> bootstrap a control plane there at all (7 attempts, elimination table under Troubleshooting).
> `deploy-to-kind.sh` and `kind-config.yaml` are kept because `kind` works normally on an
> ordinary Docker host, but nothing in this repo has ever seen them succeed. k3d runs k3s, which
> never invokes `kubeadm`, which is why that whole failure class does not apply to it.
>
> A defect that used to live here — the Gateway deciding V1-vs-V2 from a TTL'd cache instead
> of asking V2 directly, so a freshly created V2 link was routed to V1 and 404'd until
> something read it directly — is fixed. See "Gateway routing: V1-vs-V2" below for what
> changed.

## Requirements

`kubectl`, `kind` and `docker` are installed by `.devcontainer/setup.sh` when the Codespace is
created (see that script's own comment on why they are installed as direct binaries instead of a
third-party devcontainer feature).

**`k3d` is not**, and it is the one that actually works here. Install it once per Codespace:

```bash
curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | bash
```

`deploy-to-k3d.sh` checks for it up front and prints that same command if it is missing.

## Deploy

From any directory in the repo:

```bash
docker-compose down          # not optional -- see the port note below
./infra/k8s/deploy-to-k3d.sh
```

**Budget about 10 minutes** on a Codespace's default resources: building and importing all 5
images is the slow part (roughly 6-7 minutes the first time, faster on a re-run since Docker
layer caching kicks in), then cluster bring-up and the 9 pods reaching `Ready` takes another
couple of minutes. A quiet terminal during the image build/import steps is expected, not a
hang.

**Bringing the compose stack down first is not optional.** This cluster claims the same host
ports (8081/8082/8084) that `docker-compose.yml` does, deliberately, so that "point Postman at
localhost" needs no new environment either way. With compose still up, cluster creation fails
with `Bind for 0.0.0.0:8081 failed: port is already allocated`. The cluster brings up its own
Postgres, Redis, RabbitMQ and Keycloak; it does not reuse compose's.

This: creates the `k3d` cluster (if one with that name does not already exist and is usable —
see the script's own comment on why "exists" and "usable" are checked separately), builds all 5
application images with the repo root's shared `Dockerfile`, imports them directly into the k3d
node (no intermediate registry involved), generates the 3 `Secret`s and the Keycloak realm-import
`ConfigMap` (see "Credentials" below — neither one lives as a committed YAML file with a value
inside it), applies every manifest in this directory in order, and waits for each `Deployment` to
become `available` before finishing. It is idempotent — safe to re-run after a code change.

Alternative, step-by-step script, if you'd rather not run the whole script at once:

```bash
k3d cluster create --config infra/k8s/k3d-config.yaml
docker build --build-arg MODULE=v2-shortener-service -t v2-shortener-service:kind .
k3d image import v2-shortener-service:kind --cluster url-shortener
# ... repeat build+import for v1-legacy-monolith, api-gateway, analytics-worker, bulk-processor
# (the ":kind" tag is just the local build tag both cluster paths share -- see deploy-to-k3d.sh)

# Secrets: same variable names as docker-compose.yml/.env.example, same default if there is no
# override — see the "Credentials" section below.
kubectl create secret generic postgres-credentials --namespace url-shortener \
  --from-literal=POSTGRES_DB=urlshortener --from-literal=POSTGRES_USER=urlshortener \
  --from-literal=POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-urlshortener_local}" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl create secret generic rabbitmq-credentials --namespace url-shortener \
  --from-literal=RABBITMQ_USER="${RABBITMQ_USER:-urlshortener}" \
  --from-literal=RABBITMQ_PASSWORD="${RABBITMQ_PASSWORD:-urlshortener_local}" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl create secret generic keycloak-admin-credentials --namespace url-shortener \
  --from-literal=KEYCLOAK_ADMIN=admin \
  --from-literal=KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin_local}" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl create configmap keycloak-realm-import \
  --from-file=realm-export.json=infra/keycloak/realm-export.json \
  --namespace url-shortener --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f infra/k8s/
```

## Credentials

No YAML file in this directory has a password inside it — the 3 `Secret`s
(`postgres-credentials`, `rabbitmq-credentials`, `keycloak-admin-credentials`) are generated by
`deploy-to-k3d.sh`/`deploy-to-kind.sh` at deploy time, reading the same environment variables
`docker-compose.yml`/`.env.example` already use (`POSTGRES_PASSWORD`, `RABBITMQ_USER`,
`RABBITMQ_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`), with the same development default if there is no
override. The script loads `.env` at the repo root automatically if it exists (never committed,
see `.gitignore`) — copying `.env.example` to `.env` and changing a value there is enough for it
to propagate identically to `docker-compose`, k3d and kind, with no manifest to touch.

## Reaching the services from outside the cluster

`infra/k8s/k3d-config.yaml` (and `kind-config.yaml`, identically) maps 3 Codespace ports directly
to the fixed `NodePort`s of their Services — the same 3 ports `docker-compose.yml` and the
Postman collection already use, so pointing at "localhost" works identically with compose, k3d or
kind. Neither tool can add a mapping after the cluster exists, which is why both files are
committed rather than living in whatever flags someone last typed:

| Codespace port | Service | Use |
| --- | --- | --- |
| `8081` | Keycloak | Obtain a token (`/realms/urlshortener/protocol/openid-connect/token`) |
| `8082` | API Gateway | `/api/v1/**` and `GET /{shortCode}` (see the limitation below) |
| `8084` | v2-shortener-service | `/api/v2/urls`, `/api/v2/urls/bulk`, etc. |

`v1-legacy-monolith`, `analytics-worker`, `bulk-processor`, and RabbitMQ's management UI (15672)
are deliberately internal — either they are not part of this system's public surface (V1 is only
reached via the Gateway) or they have no endpoint meant for an external caller. To inspect them
anyway, without adding a permanent mapping:

```bash
kubectl -n url-shortener port-forward svc/rabbitmq 15672:15672
kubectl -n url-shortener port-forward svc/v1-legacy-monolith 8080:8080
```

The Gateway routes `/api/v1/**`, `/api/v2/**` and the public `GET /{shortCode}` redirect;
`v2-shortener-service` keeps its own NodePort anyway, so the Postman collection and this repo's
curl examples can address it directly and the Gateway can be exercised as a cutover switch
rather than being the only way in.

## Gateway routing: V1-vs-V2

`DynamicShortCodeRoutingFilter` used to decide whether a short code lives in V2 by checking
whether `shortlink:v2:<code>` existed in Redis. That key was not an index — it was
`ShortLinkCache`'s read-through **cache**, written only by `ShortLinkService.resolve()` on a
cache miss, with a 300-second TTL (`app.shortlink.cache-ttl-seconds`). Creating a link wrote
to PostgreSQL and nothing else, so:

- A newly created V2 link was routed to V1 by the Gateway and answered `404`, until something
  read it directly against `v2-shortener-service`.
- A working V2 link started 404ing again after 300 seconds without traffic, when its cache
  entry expired, and recovered the moment anything warmed it. Intermittent and time-dependent.

This was demonstrated on the running cluster: the same `GET http://localhost:8082/demoV2`
answered `404` before the link had been read and `302` after (see the previous Smoke test
transcript, kept in git history rather than reproduced here now that it no longer reflects
current behavior).

**Fixed** by removing the shared state instead of patching around it: the filter now sends a
`HEAD /{shortCode}` probe directly to `v2-shortener-service` and routes to V2 on anything
other than a `404` (a `410 Gone` for an expired V2 link still means the code exists in V2).
There is no Redis dependency left in the Gateway at all, and no warm-up step for a newly
cut-over code to wait on — see `DynamicShortCodeRoutingFilter`'s own Javadoc for the full
reasoning, including the accepted trade-off (an extra round trip to V2 for requests that end
up going to V1) and `AI_USAGE_LOG.md` for why the original design chose a cache in the first
place.

## Smoke test

Uses `sed` rather than `python3`: this Codespace image has no `python3`, which the previous
version of this section assumed it did.

```bash
# Token from the in-cluster Keycloak (the imported realm's demo/demo_local user)
RESP=$(curl -s -X POST http://localhost:8081/realms/urlshortener/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password&client_id=url-shortener-v2&username=demo&password=demo_local')
TOKEN=$(echo "$RESP" | sed -n 's/.*"access_token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')

# V1: create and redirect, both through the Gateway
V1=$(curl -s -X POST http://localhost:8082/api/v1/urls \
  -H 'Content-Type: application/json' -d '{"longUrl":"https://example.com/soy-v1"}')
V1CODE=$(echo "$V1" | sed -n 's/.*"shortCode"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
curl -s -o /dev/null -w "%{http_code} -> %{redirect_url}\n" "http://localhost:8082/${V1CODE}"

# V2: create with a real JWT, then follow the redirect against the service itself
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8084/api/v2/urls \
  -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json' \
  -d '{"longUrl":"https://example.com/soy-v2","customAlias":"demoV2"}'
curl -s -o /dev/null -w "%{http_code} -> %{redirect_url}\n" http://localhost:8084/demoV2

# Which system the Gateway picks for that same code -- expect 302 immediately, no prior
# direct read against V2 required (see "Gateway routing: V1-vs-V2" above)
curl -s -o /dev/null -w "%{http_code} -> %{redirect_url}\n" http://localhost:8082/demoV2
```

Observed on the cluster this documentation was originally written against, before the Gateway
routing fix described above landed (kept here as a historical record of the defect it
demonstrates; a fresh transcript against the current code would show `302` on both of the last
two rows instead of `404` then `302`):

| Check | Result |
| --- | --- |
| Keycloak's advertised issuer | `http://keycloak:8080/realms/urlshortener` |
| `iss` on a token requested from outside the cluster | the same value |
| `POST /api/v1/urls` through the Gateway | `201`, short code `UK2iBV` |
| `GET /{code}` through the Gateway, V1 link | `301 -> https://example.com/soy-v1` |
| `POST /api/v2/urls` with that JWT | `201` |
| `GET /demoV2` directly against `v2-shortener-service` | `302 -> https://example.com/soy-v2` |
| `GET /demoV2` through the Gateway, before that read | `404` |
| `GET /demoV2` through the Gateway, after that read | `302 -> https://example.com/soy-v2` |

The last two rows were the same request, and were the defect described above (fixed since), not a flaky test.

## Design and decisions (more detail in `AI_USAGE_LOG.md`)

- **A single parameterized `Dockerfile`** (`ARG MODULE`) at the repo root, instead of one per
  module — see its own header comment.
- **No `Secret` lives as committed YAML with a value inside it** — all three are generated in
  both deploy scripts from environment variables (see "Credentials" above). A PR review on an
  earlier version of this directory flagged exactly this on `KEYCLOAK_ADMIN_PASSWORD`; why that
  change matters even when the value itself was never real is detailed in `AI_USAGE_LOG.md`.
- **No persistent storage** (Postgres uses `emptyDir`, not a `PersistentVolumeClaim`): already
  declared as an accepted limitation in `ARCHITECTURE.md` section 13 for this local, disposable
  cluster.
- **`KC_HOSTNAME=keycloak` fixed in Keycloak's Deployment**, the one deliberate divergence from
  `docker-compose.yml` — see the comment in `13-keycloak.yaml` on why a token's `iss` claim has
  to be the same regardless of whether the caller was a pod inside the cluster or a `curl` from
  the Codespace.
- **Its own namespace (`url-shortener`)**: `kubectl delete namespace url-shortener` cleans up
  everything at once.
- **No Helm or Kustomize**: 10 `kubectl apply`-able manifests (plus `k3d-config.yaml` and
  `kind-config.yaml`, which are cluster configs, not applied with `kubectl`), no templating — proportional to this exercise's scope; a real
  deployment (see the GKE roadmap in `ARCHITECTURE.md` section 9) would justify Helm/Kustomize to
  actually manage multiple environments.

## Troubleshooting

### `k3d cluster create` fails with "port is already allocated"

```text
docker failed to start container for node 'k3d-url-shortener-serverlb':
Bind for 0.0.0.0:8081 failed: port is already allocated
```

`docker-compose` is still up, or the five services are still running from `mvn spring-boot:run`.
Both claim the same host ports this cluster maps, by design (see Deploy). Stop them and re-run —
k3d rolls the half-created cluster back on its own, so there is nothing to clean up first.

### The cluster is gone after the Codespace was closed and reopened

It is not gone; its nodes are Docker containers that do not restart themselves:

```bash
k3d cluster start url-shortener
```

Note that `kubectl get pods` reports `AGE` from the object's creation timestamp, which does not
pause while the Codespace is suspended. A pod that has actually been running for 90 seconds can
show `AGE 40m` right after a restart — do not read that as "stuck for 40 minutes".

### A pod stays `0/1` while its own log says the service started

That is a probe problem, not a service problem, and this directory has now hit two of them.
Check what the container itself says before touching anything else:

```bash
kubectl -n url-shortener logs -l app=<name> --tail=50
kubectl -n url-shortener describe pod -l app=<name> | grep -B2 -A10 "Last State"
```

Use `-l app=<name>`, not a pod name: a rollout or a cluster restart renames pods, and a stale
name is the most common reason these two commands answer `NotFound`.

In `Last State`, the exit code says which kind of failure it was: `137` is an OOM kill, `143` is
`SIGTERM` — Kubernetes deliberately terminating the container, which after a `startupProbe` has
been added means the probe never passed rather than that the app is slow. `AI_USAGE_LOG.md`
records both instances found here (an exec probe that could not finish inside the default
1-second timeout, and probe paths that a security filter answered `401` to).

### `kind create cluster` fails at "Starting control-plane" (a known state in Codespaces)

If running the script shows something like:

```text
 ✗ Starting control-plane 🕹️
ERROR: failed to create cluster: failed to remove control plane taint: ...
The connection to the server url-shortener-control-plane:6443 was refused
```

...it is not a problem in this repo and does not need to be re-diagnosed: it already was, and
the result is documented as a limitation in `ARCHITECTURE.md` section 13. `kind` **cannot bring
up a cluster inside GitHub Codespaces**. These are the seven attempts and what each one ruled
out:

| # | Cores | `kind` | Node image | Config | Failure |
| --- | --- | --- | --- | --- | --- |
| 1 | 2 | v0.24.0 | v1.31.0 | this repo's | remove LB label — connection refused |
| 2 | 2 | v0.24.0 | v1.31.0 | this repo's | remove taint — connection refused |
| 3 | 2 | latest | v1.37.0 | this repo's | remove LB label — connection refused |
| 4 | 4 | latest | v1.37.0 | this repo's | control-plane, CNI and StorageClass OK; fails exporting kubeconfig: `admin.conf` missing |
| 5 | 4 | latest | v1.31.0 | this repo's | remove taint — connection refused |
| 6 | 4 | latest | v1.34.0 | this repo's | remove taint — `admin.conf` missing |
| 7 | 4 | latest | v1.37.0 | **none** | remove taint — connection refused |

Hypotheses ruled out, in order: `inotify` limits (already at 524288/1024), CPU and memory (the
Codespace was upgraded from 2 to 4 cores and 8 to 16 GB — attempt 4 got much further, but 5 and 6
failed again with 13 GB free), disk space (23 GB free, a clean `docker system df`), `kind`'s
version, Kubernetes' version, and finally **this repo's own configuration**: attempt 7 created a
bare cluster, with no `--config` and no file from this directory at all, and it failed the same
way. That is what exonerates `kind-config.yaml` and the manifests.

Known alternative if a genuinely running cluster is needed: `k3d` (k3s in Docker) does not use
`kubeadm`, so this whole class of failure does not apply, and this directory's manifests are
portable as-is (standard `Deployment`/`Service`/`NodePort`). Not adopted within this exercise's
timebox.

### General diagnostics

- `kubectl -n url-shortener get pods` — if something stays `Pending`, it is almost always
  insufficient CPU/memory in the Codespace; lowering this directory's `resources.requests` is the
  documented way out per `ARCHITECTURE.md` section 7 ("if kind has resource trouble, demonstrate
  everything with docker-compose instead").
- `kubectl -n url-shortener logs deployment/<name>` — the first real diagnostic step, same
  discipline as asking for `mvn verify`'s real output instead of guessing (see `AI_USAGE_LOG.md`).
- `kubectl -n url-shortener describe pod <pod>` — for a pod that never reaches `Ready`, check the
  end: `readinessProbe`/`livenessProbe` failure events show up right there.

## Teardown

```bash
k3d cluster delete url-shortener       # kind: kind delete cluster --name url-shortener
```

To keep the cluster but free the host ports (to go back to `docker-compose`, say),
`k3d cluster stop url-shortener` is enough — `k3d cluster start url-shortener` brings it back
with its images and manifests intact.
