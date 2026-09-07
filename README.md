# URL Shortener — Prototype (AI-Assisted Engineering)

## What this is

A URL-shortening service prototype, built as an exercise in **AI-assisted engineering
execution**: the point isn't only the system itself, but demonstrating requirement
comprehension, task decomposition, disciplined AI-driven execution with full traceability, and
conscious risk management under a fixed timebox. The full methodology and technical detail live
in [`ARCHITECTURE.md`](./ARCHITECTURE.md); this document is the executive summary.

## Plan and Rationale

- **Stack:** Java 17 / Spring Boot 3.x, PostgreSQL (source of truth), Redis (redirect cache +
  rate limiting, never counters), RabbitMQ (separate queues for analytics and bulk), Keycloak
  (OIDC).
- **Scope:** full — real OIDC auth, asynchronous bulk creation, custom alias, expiration,
  enriched analytics. The most ambitious level on both axes was chosen deliberately, with an
  explicit cut plan for if time runs short (see below).
- **Deployment:** local Kubernetes (`kind`/`k3d`) inside GitHub Codespaces, with a documented but
  unexecuted path to GKE — engineering time is prioritized over spending the timebox on a real
  cloud provider's credentials and billing.
- **Architecture:** Strangler Fig pattern with a V1 Monolith and V2 Microservices, where V1 is a
  **didactic simulation** of a legacy system (no real legacy exists; it is built minimally on Day
  1 and then treated as legacy from Day 2 onward) — declared explicitly so the exercise is
  defensible to any technical reviewer.
- **Day-by-day plan** (full detail in `ARCHITECTURE.md` §7): Day 1 builds the system from scratch
  as the Greenfield foundation (V1, Gateway, V2 services, infra, CI); Day 2 proves the Brownfield
  story with two concrete scenarios — a standalone V1-only baseline, then the V2 cutover
  alongside it; Day 3 validates the Ambiguous-requirement decisions with executable tests and
  closes with final documentation.
- **Cut priority if time runs short** (explicitly confirmed): protect the three scenarios
  (Greenfield/Brownfield/Ambiguous) above everything else. Cut order: (1) live Kubernetes →
  docker-compose + manifests left unexecuted, (2) full OIDC → simple JWT, (3) asynchronous bulk →
  synchronous bulk.

Full rationale for every decision is in `ARCHITECTURE.md` §3.4 (Key Decisions).

## Artifacts

- [`ARCHITECTURE.md`](./ARCHITECTURE.md) — architecture, the three scenarios, setup, testing,
  security, risks, and the git/PR workflow.
- `README.md` (this document) — executive summary.
- [`.github/PULL_REQUEST_TEMPLATE.md`](./.github/PULL_REQUEST_TEMPLATE.md) — engineer↔AI
  traceability template for every change.
- [`infra/k8s/`](./infra/k8s/) — Kubernetes manifests and the `kind` deployment script (see
  `infra/k8s/README.md`).
- [`docs/url-shortener-enterprise.postman_collection.json`](./docs/url-shortener-enterprise.postman_collection.json) (with [`docs/url-shortener-enterprise.postman_environment.json`](./docs/url-shortener-enterprise.postman_environment.json)) — Postman collection covering a Greenfield flow (V2 as the primary system) and a Brownfield flow (V1 baseline, then a V2 cutover), including the live reproduction of the Gateway routing defect below.

## Risks, Trade-offs, and Validation

The full risks and guardrails are in `ARCHITECTURE.md` §12; the most relevant: silent loss of a
click event if RabbitMQ is down at the moment of publishing (accepted, non-critical, mitigable
later with an outbox pattern), and abuse of the shortener for phishing/open-redirect (mitigated
with scheme/host validation at creation time). Design trade-offs (Monolith+Microservices vs.
building everything at once, Redis cache vs. direct DB access, Keycloak vs. a homegrown
Authorization Server, async vs. synchronous bulk) are detailed in §14, each with its own
justification. Validation rests on unit tests, characterization tests for the brownfield
scenario, and Testcontainers integration tests (real Postgres/Redis/RabbitMQ) — detail in §10.

## Assumptions

Summary (full detail in `ARCHITECTURE.md` §2): prototype-scale traffic (not real production
load); single region; no formal compliance requirements (IP anonymization is a good practice, not
a response to a specific legal obligation); the public link is indistinguishable between V1 and
V2; Codespaces/local Docker is assumed, not a GCP account with active billing.

## Limitations

Summary (full detail in `ARCHITECTURE.md` §13): a few seconds of eventual consistency in
analytics; no persistent storage in the local kind/k3d cluster; no verification against external
phishing/malware lists; no real GKE deployment within this exercise's timebox.

## For reviewers: setup and verification

Full detail in `ARCHITECTURE.md` §9. This section is the condensed path to get the system
running and exercise it yourself, with two setup options. You can drive it with the Postman
collection (see "Artifacts" above) or with the `curl` sequence below — both exercise the
same requests.

### 1. Open the Codespace

`<> Code` → **Codespaces** → **Create codespace on main**. `.devcontainer/setup.sh` preconfigures
Java 17, Maven, Docker and `kubectl`/`kind` on creation. **`k3d` is not preinstalled** — install
it once per Codespace if you take option B below:

```bash
curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | bash
```

### 2. Run the system — pick one

**Option A — fast dev loop (`docker-compose` + Maven):**

```bash
docker-compose up -d                    # Postgres, Redis, RabbitMQ, Keycloak
mvn clean install -DskipTests           # build all 7 reactor modules once
mvn -pl v1-legacy-monolith spring-boot:run       # :8080, each in its own terminal
mvn -pl api-gateway spring-boot:run              # :8082
mvn -pl analytics-worker spring-boot:run         # :8083
mvn -pl v2-shortener-service spring-boot:run     # :8084
mvn -pl bulk-processor spring-boot:run           # :8085
```

**Option B — the actual Kubernetes deployment (`k3d`), verified end-to-end:**

```bash
docker-compose down                     # the two cannot run at once, see infra/k8s/README.md
./infra/k8s/deploy-to-k3d.sh
```

This is the path documented and exercised in `infra/k8s/README.md` — all 9 pods reaching
`Ready`, real OIDC auth, and both the V1 and V2 redirect paths confirmed over HTTP. That
document also has troubleshooting for a suspended Codespace, port conflicts with option A, and
reading a pod that looks stuck but isn't.

Both options expose the same 3 ports (8081 Keycloak, 8082 Gateway, 8084 V2), so the commands
below work unchanged regardless of which one you ran.

### 3. Exercise it

This is the `curl` equivalent of the Postman collection (see "Artifacts") for a terminal-only
pass — the same sequence run against the live k3d deployment, transcript in
[`infra/k8s/README.md`](./infra/k8s/README.md#smoke-test):

```bash
# Token (the imported realm's demo/demo_local user)
RESP=$(curl -s -X POST http://localhost:8081/realms/urlshortener/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password&client_id=url-shortener-v2&username=demo&password=demo_local')
TOKEN=$(echo "$RESP" | sed -n 's/.*"access_token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')

# V1: create and redirect, both through the Gateway
V1=$(curl -s -X POST http://localhost:8082/api/v1/urls \
  -H 'Content-Type: application/json' -d '{"longUrl":"https://example.com/soy-v1"}')
V1CODE=$(echo "$V1" | sed -n 's/.*"shortCode"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
curl -i -s "http://localhost:8082/${V1CODE}" | head -1

# V2: create with the real JWT, then follow the redirect
curl -i -s -X POST http://localhost:8084/api/v2/urls \
  -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json' \
  -d '{"longUrl":"https://example.com/soy-v2","customAlias":"demoV2"}' | head -1
curl -i -s http://localhost:8084/demoV2 | head -1
```

Expect `201` on both creates and a `30x` on both redirects. If you also try `GET
http://localhost:8082/demoV2` (the V2 code, but *through the Gateway*) before and after the
direct read above, you will reproduce the Gateway's known routing defect on purpose — see
`ARCHITECTURE.md` §13 and `infra/k8s/README.md` ("Known defect").

## Current status

See `ARCHITECTURE.md` §7 for the day-by-day plan and `AI_USAGE_LOG.md` for the complete,
PR-by-PR history.
