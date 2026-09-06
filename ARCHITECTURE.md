# Technical Architecture Documentation: Enterprise URL Shortener

## Executive Summary

This document is the result of a requirements analysis, task decomposition, and architecture engineering process carried out by Art (engineer) with assistance from Claude, for a URL shortener prototype to be built in 2–3 days. The goal of the exercise is not only to deliver a functional shortener, but to demonstrate: requirements understanding, task decomposition, AI-accelerated execution with traceability and engineer ownership, and conscious management of risks and trade-offs under a constrained timeframe.

This document covers the decisions already made, makes explicit those deliberately simplified due to time constraints, and defines a day-by-day execution plan with cut lines so that the scope remains defensible to any technical reviewer.

---

## 1. Requirement Understanding

**Original requirement (exercise brief):** build a URL shortener from scratch with core APIs, analytics, and "reliability features," demonstrating AI-accelerated engineering execution in 2–3 days, covering three scenarios (greenfield, brownfield, ambiguous).

**Identified ambiguities and how they were normalized:**

- The brief does not specify the stack, persistence, or level of auth/analytics → resolved through direct conversation with the stakeholder (see decisions in Section 2).
- "Reliability features" is vague → normalized to: read caching for the critical redirect path, asynchronous decoupling of analytics, versioned and auditable schema migrations, and explicit failure handling (Redis down, broker down, hash collision).
- The "brownfield" scenario cannot be literal because there is no pre-existing codebase (a purely greenfield project) → it was decided to **simulate** brownfield conditions: build a minimal V1 on Day 1 and treat it as a legacy system starting on Day 2, in order to demonstrate reasoning about evolving existing code, impact analysis, and zero regression. This is stated explicitly here so it is not read as a real pre-existing legacy system.
- The expected level of "production" readiness is not defined → interpreted as: production-quality code (modular, tested, secure) without the need to deploy to paid real-world cloud infrastructure, given the 2–3 day timebox (see Section 9, GKE Roadmap).

## 2. Assumptions

- Target prototype scale: hundreds of creations/day and thousands of redirects/day (enough to demonstrate the caching and async patterns, not real production traffic).
- Single-region, with no real multi-zone high-availability requirements (the path is documented, not implemented).
- There are no formal regulatory compliance requirements (GDPR/CCPA); IP anonymization is adopted as a best practice, not in response to a specific legal obligation.
- "V1" and "V2" links coexist under the same root domain; the short code must be indistinguishable in form to the end user (neither `/api/v1/` nor `/api/v2/` is exposed in the shared link).
- Access to GitHub Codespaces (or equivalent local Docker) is assumed as the execution environment; access to a GCP account with active billing is not assumed for this exercise.

---

## 3. Architecture Overview

### 3.1 Components

- **API Gateway (Spring Cloud Gateway):** single entry point. Applies the **Strangler Fig** pattern:
    - `/api/v1/...` (management control plane) → Legacy Monolith (V1).
    - `/api/v2/...` (management control plane) → Modern Microservices (V2).
    - `GET /{shortCode}` (data plane, the actual redirect) → the Gateway **does not** route by URL prefix (the public link must never contain `/api/`); instead, it queries a shared code registry: first checking whether the code exists in the V2 index (Redis), and if not, delegating to the V1 Monolith. This is what makes Strangler Fig genuinely work for a shortener: the public domain remains a single stable domain even as the backend behind it changes over time.
- **Legacy Monolith (V1):** Java/Spring Boot. Original shortening and basic redirect system, deliberately simple (see Section 1). PostgreSQL with Liquibase for schema management.
- **Microservices (V2):**
    - **Shortener Service:** URL creation (custom alias, expiration, conditional redirect rules), protected with OAuth2/OIDC.
    - **Redirect & Cache Service:** high-speed reads; cache-aside with Redis, falling back to PostgreSQL on cache miss or if Redis does not respond.
    - **Analytics Worker:** asynchronous RabbitMQ consumer (`click-events` queue) that processes V1 and V2 clicks without blocking the redirect.
    - **Bulk Processor:** asynchronous RabbitMQ consumer (`bulk-url-jobs` queue, separate from `click-events` to avoid mixing traffic) that processes bulk URL creation.
- **Identity Provider (Keycloak):** issues and validates OIDC tokens. V2 services act as OAuth2 Resource Servers (they do not implement their own Authorization Server). It starts with a versioned `realm-export.json` in the repository to avoid manual configuration.

### 3.2 Technology Stack

| Category | Choice |
|---|---|
| Language | Java 17 / Spring Boot 3.x |
| Persistence | PostgreSQL (source of truth) |
| Cache | Redis (redirect cache-aside + rate limiting; **not** for click counters) |
| Identity Provider | Keycloak (OIDC), services as Resource Servers |
| Messaging | RabbitMQ (separate queues: `click-events`, `bulk-url-jobs`) |
| Migrations | Liquibase |
| Testing | JUnit 5, MockMvc, Testcontainers |
| Execution environment | GitHub Codespaces (Docker-in-Docker) + local kind/k3d cluster |

### 3.3 Control Flow (Redirect)

```mermaid
sequenceDiagram
    participant U as User
    participant GW as API Gateway
    participant R2 as V2 Redirect&Cache
    participant Redis
    participant V1 as V1 Monolith
    participant Q as RabbitMQ (click-events)
    participant AW as Analytics Worker

    U->>GW: GET /{shortCode}
    GW->>Redis: Does shortCode exist in V2 index?
    alt V2 code
        GW->>R2: route
        R2->>Redis: look up longUrl in cache
        alt Cache hit
            R2-->>U: 301 Moved Permanently
        else Cache miss
            R2->>R2: query Postgres, repopulate cache
            R2-->>U: 301 Moved Permanently
        end
        R2--)Q: publish click event (fire-and-forget)
    else V1 code (legacy)
        GW->>V1: route
        V1-->>U: 301 Moved Permanently
        V1--)Q: publish click event
    end
    Q-)AW: consume event
    AW->>AW: anonymize IP, store in click_events
```

### 3.4 Key Decisions

- **Strangler Fig, explicitly declared as a didactic brownfield simulation** (see Section 1) — demonstrates incremental modernization without pretending that a real legacy system exists.
- **Analytics decoupling via RabbitMQ:** prevents click counting from penalizing redirect latency. Accepted and documented risk: silent loss of an event if the broker is down at the moment of publishtion (see Section 8, Risks).
- **Redis only for reads and rate limiting, never as the source of truth for counters:** avoids inconsistency if Redis restarts; the real counters are derived from the `click_events` table in Postgres.
- **Keycloak as the Identity Provider instead of a custom Authorization Server:** drastically reduces the effort required to implement "real" OAuth2/OIDC within the timebox.
- **Liquibase over native DDL:** auditable and reversible schema changes, critical for the brownfield scenario.

## 4. API & Data Model

### 4.1 Main Endpoints

**V1 (legacy, no auth):**
- `POST /api/v1/urls` `{ longUrl }` → `{ shortCode, shortUrl }`
- `GET /{shortCode}` → `301` (public redirect)

**V2 (modern):**
- `POST /api/v2/urls` *(auth required)* `{ longUrl, customAlias?, expiresAt?, redirectRules? }` → `{ shortCode, shortUrl, ownerId }`
- `GET /{shortCode}` → `301`/`302`, o `410 Gone` if expired (public redirect, no auth)
- `POST /api/v2/urls/bulk` *(auth required)* `{ urls: [{ longUrl, customAlias? }, ...] }` → `{ jobId, status: "PENDING", totalItems }`
- `GET /api/v2/urls/bulk/{jobId}` *(auth required)* → `{ status, totalItems, processedItems, failedItems, items: [...] }`
- `GET /api/v2/urls` *(auth required)* → list of links belonging to the authenticated user
- `DELETE /api/v2/urls/{shortCode}` *(auth required, owner only)* → revocation
- `GET /api/v2/urls/{shortCode}/analytics` *(auth required, owner only)* → aggregated metrics from `click_events`

### 4.2 Data Model (Summary)

- `urls` (V1): `id, short_code (unique), long_url, created_at, expires_at (nullable, added via Liquibase in the brownfield scenario)`
- `short_links` (V2): `id, short_code (unique), long_url, owner_user_id (FK, nullable if anonymous access is allowed), redirect_rules (jsonb), created_at, expires_at, is_active`
- `app_user` (V2): `id, keycloak_subject (unique), email, created_at` — passwords are not stored locally; Keycloak is the source of identity
- `click_events` (append-only, populated by V1 and V2): `id, short_code, service_origin, occurred_at, anonymized_ip, user_agent, device_type, referrer`
- `bulk_jobs`: `id, owner_user_id, status, total_items, processed_items, failed_items, created_at, completed_at`
- `bulk_job_items`: `id, bulk_job_id (FK), line_index, long_url, status, short_code (nullable), error_message (nullable)`

---

## 5. Security

- **AuthN/AuthZ:** OIDC via Keycloak. Creation, bulk, listing, and analytics endpoints require a valid Bearer JWT; `GET /{shortCode}` (the redirect) remains public by design.
- **Open redirect / phishing abuse prevention:** every `longUrl` is validated against the allowed scheme (`http`/`https` only), and internal/private hosts (RFC 1918, `localhost`, cloud metadata endpoints) are rejected to prevent SSRF; verification against a phishing/malware list (e.g. Google Safe Browsing API) is documented as a future improvement if time is insufficient to implement it.
- **Rate limiting:** on `POST /api/v2/urls` and `/bulk`, using Redis (sliding window or token bucket) to mitigate unauthorized bulk creation abuse.
- **Secret Management:**
    - In Codespaces: Postgres/RabbitMQ/Keycloak credentials are injected as *Codespaces secrets* (repository/user), and are never committed.
    - In Kubernetes: stored as a K8s `Secret` (with the documented caveat that it is only base64, not encrypted at rest — future improvement: SOPS or sealed-secrets).
    - If moving to real GCP: use Workload Identity Federation instead of service account JSON keys.
- **Reserved slugs:** codes such as `api`, `admin`, `health`, `actuator` are kept on an exclusion list so they are never assigned as custom aliases.

## 6. Three Scenarios

### Scenario A — Greenfield: High-Speed Redirect & Cache Service

**Decomposition:**
1. V2 REST contract for URL creation and resolution.
2. Integration with Spring Data Redis (cache-aside).
3. Base62 hash generator with collision handling (retry + fallback to a longer length).
4. Unit and integration testing with Testcontainers.

**AI-Assisted Execution (Traceability):**
- Initial prompt: *"Act as a Spring Boot expert. Generate a redirect service that queries Redis and falls back to PostgreSQL if the cache expires."*
- Human intervention: the AI did not handle the Redis-down case (not just a cache miss, but a refused connection). The initial proposal was rejected and a Circuit Breaker (Resilience4j) was added so that a Redis outage would not take down redirects, degrading to a direct Postgres query.

**Validation:** local load test simulating concurrency, verifying that the Postgres fallback does not generate 5xx errors when Redis is down (manual chaos test: shut down the Redis container halfway through the test).

### Scenario B — Brownfield: Evolution of the V1 Monolith

**Decomposition:**
1. Impact analysis of the current V1 schema.
2. Liquibase changelog (`changelog-v1.2.xml`) adding `expires_at` without breaking existing rows.
3. Characterization tests (JUnit + MockMvc) to freeze the current V1 behavior before modifying it.
4. Surgical refactor so V1 also publishes click events to RabbitMQ.

**AI-Assisted Execution (Traceability):**
- Initial prompt: *"Generate a Liquibase script to add an expiration column to the legacy URL table."*
- Human intervention: the AI proposed the column as `nullable="false"`, which would break existing rows. It was rejected and changed to `nullable="true"` with a default value, preserving backward compatibility.

**Validation:** characterization tests were run before and after the change — zero regressions in V1 API responses.

### Scenario C — Ambiguous: "Smart and Private Links"

**Original business requirement:** *"We want links to be smart, expire properly, and respect privacy while still providing metrics."*

**Disambiguation (tech lead assumptions):** "Inteligente" → redirect condicional simple por tipo de dispositivo (móvil vs. desktop) vía `redirect_rules`. "Expirar bien" → campo `expires_at`, devolviendo `410 Gone` si venció. "Respetar privacidad" → anonymizer el último octeto de la IP antes de persistir el evento de clic.

**Decomposition:**
1. Extend the creation DTO to accept `redirectRules` and `expiresAt`.
2. IP anonymization middleware in the analytics pipeline (before inserting into `click_events`).
3. Expiration validation at redirect time.

**Validation:** unit tests verifying the anonymized IP format (`192.168.1.0` instead of `192.168.1.45`) and that an expired link returns `410` instead of redirecting.

---

## 7. Execution Plan (Day by Day)

Availability assumption: 6–8 dedicated hours per day. Overall priority if time is shortened (confirmed with the stakeholder): **protect all 3 scenarios above everything else** — the cut order is (1) Kubernetes running live → fall back to docker-compose with versioned but unexecuted manifests, (2) full OIDC → fall back to simple JWT with Spring Security, (3) asynchronous Bulk → fall back to synchronous bulk. A scenario is never partially cut to save an infrastructure component.

**Day 1 — Foundations + Minimal V1 + Greenfield Start**
- Must-ship: monorepo scaffold; Codespaces devcontainer (Docker-in-Docker + kind/k3d feature); docker-compose (Postgres, Redis, RabbitMQ, Keycloak); minimal V1 monolith (initial Liquibase, create + redirect, no auth); Gateway with basic routes; OpenAPI V2 contract + Base62 generator.
- Emergency cut: the Gateway may remain as a stub without real routing if time is needed.

**Day 2 — Brownfield + Redirect&Cache (Greenfield) + Auth**
- Must-ship: complete Brownfield scenario (Liquibase + characterization tests + V1 emitting events); Redirect & Cache Service with Redis and Circuit Breaker; Analytics Worker; Keycloak running and V2 services as Resource Servers; Ambiguous scenario (device-based redirection, expiration, IP anonymization).
- Emergency cut: if Keycloak cannot be completed in time, fall back to simple JWT and document the decision.

**Day 3 — Async Bulk + Local Kubernetes + Security + Testing + Documentation**
- Must-ship: complete Bulk Processor (consumer, status endpoint, idempotency, dead-letter queue); anti-open-redirect validation and rate limiting; K8s manifests deployed in kind inside Codespaces; integration tests with Testcontainers; final documentation (README, `AI_USAGE_LOG.md`, Postman collection).
- Emergency cut: if kind has resource problems, demonstrate everything with docker-compose and leave the K8s manifests versioned but not executed live (an accepted and declared limitation, not a hidden one).

---

## 8. AI-Assisted Execution & Traceability

In addition to the scenario-specific examples (Section 6), the repository maintains an `AI_USAGE_LOG.md` file with one entry for each relevant decision made during the 3 days, using this format:

```
### [Date] [Component] Prompt: "..."
- AI-generated: <what it produced>
- Accepted / Modified / Rejected: <decision>
- Reason: <why, based on the engineer's judgment>
```

This provides continuous traceability (not just 3 isolated examples) to support "depth of decomposition" and "effectiveness of AI-assisted engineering execution" for any reviewer.

---

### 8.1 Git and Pull Request Flow

- **Branches:** one branch per task in the plan (Section 7), named `feature/<scenario-or-task>` (e.g. `feature/greenfield-redirect-service`, `feature/brownfield-liquibase-expiration`), so the branch/PR history is an exact mirror of the documented task decomposition.
- **Two real GitHub identities, not just a commit convention:** `artmendezarg` (the engineer and repository owner) and `art-claude-dev` (a dedicated account added as a collaborator with write permission, used exclusively for AI-generated work). This turns PR review into a real GitHub-enforced review — the engineer cannot approve their own PRs, so when AI-assisted task PRs are opened by `art-claude-dev`, approval by `artmendezarg` is a genuine review, not an administrator bypass.
- **Commits:** Conventional Commits. AI-generated commits use the git identity of `art-claude-dev` (name `Claude AI Assistant`, verified email for that account) so GitHub correctly attributes the author/avatar in the history. Manual engineer adjustments use the identity of `artmendezarg`. Hard rule: never `git commit --amend` an AI commit after a human adjustment — always create a new commit, so the diff between "AI-proposed vs. engineer-corrected" remains permanently visible in the history, with authorship distinguishable by account.
- **Work invocation:** manual, inside the Codespace. The engineer decides when to invoke Claude Code for each task; changes are committed and pushed under the `art-claude-dev` identity, and the engineer reviews the diff before approval. GitHub Action automation (`@claude` opening the PR from an issue by itself) is not used as the primary method because the exercise explicitly requires engineer-led execution ("engineer-led execution accelerated by AI, not autonomous orchestration"); it remains documented as an additional capability, used selectively and always with manual approval, not as the default workflow.
- **PR template** (`.github/PULL_REQUEST_TEMPLATE.md`) with fixed sections: original task/intent, prompt(s) used, summary of AI-generated output, quality-gate results (build, tests, lint, dependency scan — Section 11), and a required **"Engineer Decision"** field with three cases:
    - *Accepted:* `arturomendezarg` approves the PR opened by `art-claude-dev` and merges it.
    - *Rejected:* the PR is closed without merging, with a review comment explaining the reason, and the corresponding entry is added to `AI_USAGE_LOG.md` (Section 8) — the reason remains both in the PR's technical history and in the project's narrative summary.
    - *Adjusted:* a new commit is added to the same branch under the `artmendezarg` identity (or a new iteration from `art-claude-dev` if the AI is asked to fix a specific issue); the PR keeps all commits visible with distinguishable authorship; the original is never rewritten.
- **Branch protection on `main`:** direct pushes are blocked for everyone, including the repository owner (`enforce_admins: true`); merges are allowed only via PR with 1 required approval and, once the CI pipeline exists (Section 11), all checks passing. Because there are two real accounts, the required approval is a genuine human review, not a formality.
- **PR labels:** `ai:accepted`, `ai:rejected`, `ai:adjusted` so the PR history can be scanned at a glance by any external reviewer without reading each one.

---

### 8.2 Secure AI Usage

Concrete controls (not merely declared — verified through the GitHub API at the time of writing) that limit what the AI can do and what it can access:

- **No real credentials, ever:** all Postgres/RabbitMQ/Keycloak passwords in `docker-compose.yml` are invented local-development values (`urlshortener_local`, `admin_local`), with defaults in the file itself — there is no real secret to protect in this exercise. `.env` is in `.gitignore` (with an explicit exception for `.env.example`, which contains only placeholders) so the pattern remains correct if a real value is used at some point. `*.pem` and `*.key` are also excluded.
- **No access to production infrastructure:** the AI never received credentials for GCP or any real cloud provider — the Kubernetes deployment is local (kind/k3d in Codespaces), and GKE remains a *documented roadmap*, not an executed deployment (see Section 9). The blast radius of any AI error or misuse is limited to a disposable environment.
- **Restricted network for the AI execution environment:** the sandbox where the AI runs in this session has a narrow network allowlist (in practice, it could not even reach Maven Central to compile locally — see `AI_USAGE_LOG.md`). This was not configured specifically for this project, but it is a real containment layer worth documenting: the AI cannot exfiltrate data or reach arbitrary internet endpoints from its own working environment.
- **Secret scanning (`gitleaks`) on every PR:** an automatic guardrail specifically against the scenario "the AI accidentally commits a credential" — it runs in CI from the repository's first PR, even before any Java code existed.
- **Dependency scanning (Dependabot):** any library the AI proposes adding to `pom.xml` remains under continuous monitoring for known vulnerabilities, rather than relying blindly on "the AI chose a reasonable version" (see Section 11).
- **Zero administrator bypass, verified:** `enforce_admins: true` on `main` protection — confirmed via `GET /repos/.../branches/main/protection`, not merely documented. Even the repository owner cannot bypass the PR + review + green CI flow. Combined with `required_approving_review_count: 1` and `dismiss_stale_reviews: true` (any new commit on an already-approved PR — such as post-CI fixes made that same day — invalidates the previous approval and requires a new review), the only path for a change generated by `art-claude-dev` to reach `main` is for `artmendezarg` to actively review it every time.
- **Least privilege for the bot token:** the `art-claude-dev` token has scopes `repo`, `read:org`, `workflow` — required to open PRs and push to `.github/workflows/`, but without `admin:org` or repository administration permissions (confirmed: `admin: false` in that account's collaborator permissions). It cannot change branch protection, delete the repository, or manage other collaborators.

## 9. Setup Instructions

**Recommended environment: GitHub Codespaces**
1. `<> Code` button → Codespaces tab → `Create codespace on main`. The devcontainer preconfigures Java 17, Maven, Docker (the `docker-outside-of-docker` feature, not Docker-in-Docker — correction from an earlier version of this paragraph: `kind` recommends avoiding DinD when the host already exposes its own Docker socket, which is exactly what this feature does), and `kubectl`/`kind`/`helm`, installed directly by `.devcontainer/setup.sh` (the script documents why: the third-party Kubernetes feature failed to build in the first version of the devcontainer).
2. Start the infrastructure dependencies for day-to-day development:
   ```bash
   docker-compose up -d
   ```
3. Run the services:
   ```bash
   mvn clean spring-boot:run
   ```
4. To demonstrate deployment to local Kubernetes (a single-node `kind` cluster, with all 5 services and their entire infrastructure running inside the cluster, without reusing the `docker-compose` setup from step 2):
   ```bash
   ./infra/k8s/deploy-to-kind.sh
   ```
   Full details (how to reach each service from outside the cluster, design decisions, troubleshooting, teardown) are in [`infra/k8s/README.md`](./infra/k8s/README.md).

**Postman testing:** collection at `docs/url-shortener-enterprise.postman_collection.json`, with preconfigured environments for V1, V2, expiration, and asynchronous bulk.

**GKE roadmap (documented, not executed in this exercise):** Artifact Registry for images, GKE Autopilot, Cloud SQL for Postgres, Memorystore for Redis, Workload Identity Federation instead of service account keys. This path is documented to demonstrate productization judgment without consuming the prototype timebox on GCP credentials and billing.

---

## 10. Testing Approach

- **Unit tests:** isolated business logic (hash generator, expiration rules, IP anonymization) using JUnit 5 and Mockito.
- **Characterization Tests:** exclusive to the Brownfield scenario; they protect the current V1 behavior before refactoring.
- **Integration:** Testcontainers running real Postgres, Redis, and RabbitMQ, verifying that Liquibase migrations run as they would in production.
- **Manual chaos:** shut down Redis/RabbitMQ during a test to verify that the documented fallbacks (Circuit Breaker, degradation to Postgres) work as expected.

## 11. Observability & Quality Gates

**Status:** the components in this section are already implemented (not merely planned) since the "quality gates" PR (see `AI_USAGE_LOG.md`) — this is stated explicitly so the point at which they stopped being a design promise and became real code remains traceable.

- **Static analysis (Checkstyle + SpotBugs):** declared under `<build><plugins>` in the root `pom.xml` (not `<pluginManagement>`), so child modules inherit and execute them automatically during the `verify` phase without repeating configuration. Checkstyle uses a custom, deliberately scoped ruleset (`checkstyle.xml` at the repository root) — it starts as a real gate (the build can fail) without creating a wave of style violations across code that was already written; it can be tightened progressively. SpotBugs analyzes compiled bytecode for known bug patterns, with a "Medium" threshold.
- **Dependency scanning (security):** **GitHub Dependabot** was chosen (`.github/dependabot.yml` + vulnerability alerts enabled at repository level) instead of OWASP Dependency-Check as a Maven plugin, which was the original plan. Reason for the change: Dependency-Check depends on the NVD API, which without a registered API key applies aggressive rate limiting and can make CI jobs slow or unstable — a poor fit for a pipeline that runs on every PR in a time-constrained exercise. Dependabot is native to GitHub, requires no additional infrastructure, and covers the same objective.
- **Observability (Micrometer/Actuator):** `spring-boot-starter-actuator` + `micrometer-registry-prometheus` in the V1 Monolith and API Gateway (the two executable services so far), with `/actuator/health`, `/actuator/info`, and `/actuator/prometheus` exposed — sufficient to verify latency and health live without setting up a full Grafana stack for the exercise. Pending: add the same to the V2 services as they are built (Day 2/3).
- **Performance:** honestly, there is not yet an automated performance gate (e.g. a latency threshold that fails the build). The planned performance validation (load test + Redis chaos test, see Section 6 Scenario A) is manual and executable, not a CI gate — this is stated explicitly rather than implying coverage that does not exist.
- **CI pipeline** (`.github/workflows/ci.yml`), runs on every PR against `main` and on every push to `main`, with three jobs:
    - *Markdown Lint* — validates documentation (`.markdownlint-cli2.jsonc` disables noisy rules such as line length and inline HTML, which is necessary for Mermaid diagrams).
    - *Secret Scanning* (`gitleaks`) — runs from the beginning of the repository, even when it initially contained only documentation, so a credential can never slip into the history.
    - *Build and Test* — runs `mvn verify`, where Checkstyle and SpotBugs (static analysis) are integrated as Maven plugins rather than separate CI steps.
- These three jobs are *required status checks* in the protection of `main` (Section 8.1).

## 12. Risks & Guardrails

| Risk | Guardrail / decision |
|---|---|
| Silent loss of click events if RabbitMQ is down when publishing (fire-and-forget) | Accepted as an analytics risk (non-critical); explicitly documented, not hidden. Future improvement: outbox pattern. |
| Base62 hash collision under extreme concurrency | Database-level uniqueness constraint + retry; future improvement: Snowflake ID-style generator. |
| Use of the shortener for phishing/open redirects | Scheme and internal-host validation during creation (Section 5); an external blocklist remains a future improvement if time is insufficient. |
| Redis outage affects redirect latency | Circuit Breaker with direct fallback to Postgres (Scenario A). |
| Limited Codespace resources for running kind + all services | Cut plan: demonstrate with docker-compose if kind is not viable, without blocking the rest of the deliverable. |

## 13. Limitations

- Eventual consistency in analytics (a few seconds) due to decoupling through RabbitMQ — acceptable for this domain, but not for strict transactional systems.
- No persistent storage (volumes) in the kind/k3d cluster — valid for a demo, not for production.
- No verification against external phishing/malware lists (documented as a future improvement).
- No actual GCP deployment within the exercise timebox (see GKE Roadmap).

## 14. Trade-offs

- **Monolith + Microservices (Strangler Fig) vs. building everything at once:** more operational complexity in exchange for demonstrating incremental modernization without abruptly shutting down a system in use — valid here as an educational exercise, already declared as a simulation in Section 1.
- **Redis cache vs. direct DB query:** prioritizes redirect latency at the cost of taking on the classic cache invalidation problem, mitigated with a short TTL and Circuit Breaker.
- **Keycloak vs. custom Authorization Server:** prioritizes delivery time at the cost of an additional infrastructure component to manage.
- **Asynchronous vs. synchronous Bulk:** prioritizes demonstrating a more sophisticated pattern (reusing RabbitMQ) at the cost of greater state complexity (jobs, idempotency, dead-letter queue).
