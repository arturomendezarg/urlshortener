# URL Shortener — Prototype (AI-Assisted Engineering)

## What is this

Prototype of a URL shortening service, built in 2–3 days as an exercise in **AI-assisted accelerated engineering**: the goal is not only the system itself, but also to demonstrate requirements understanding, task decomposition, disciplined AI-assisted execution with full traceability, and conscious risk management under a constrained timebox. The complete methodology and technical details are documented in [`ARCHITECTURE.md`](./ARCHITECTURE.md); this document is the executive summary.

## Plan and Rationale

- **Stack:** Java 17 / Spring Boot 3.x, PostgreSQL (source of truth), Redis (redirect cache + rate limiting, never counters), RabbitMQ (separate queues for analytics and bulk), Keycloak (OIDC).
- **Scope:** complete — real OIDC auth, asynchronous bulk creation, custom aliases, expiration, enriched analytics. The most ambitious level on both axes was deliberately chosen, with an explicit plan for what to cut first if time runs short (see below).
- **Deployment:** local Kubernetes (`kind`/`k3d`) inside GitHub Codespaces, with a documented GKE path that was not executed — engineering time is prioritized over spending the timebox on credentials and billing for a real cloud provider.
- **Architecture:** Strangler Fig pattern with a V1 Monolith and V2 Microservices, where V1 is an **educational simulation** of a legacy system (there is no real legacy system; a minimum version is built on Day 1 and treated as legacy from Day 2) — explicitly stated so the approach is defensible to any reviewer.
- **Day-by-day plan** (full details in `ARCHITECTURE.md` §7): Day 1 foundations + minimal V1 + Greenfield kickoff; Day 2 Brownfield + Redirect & Cache + Auth; Day 3 asynchronous Bulk + local Kubernetes + security + testing + documentation.
- **Cutback priority if time runs short** (explicitly confirmed): protect the 3 scenarios (Greenfield/Brownfield/Ambiguous) above all else. Cut order: (1) live Kubernetes → docker-compose + manifests without execution, (2) full OIDC → simple JWT, (3) asynchronous Bulk → synchronous bulk.

Full rationale for each decision is in `ARCHITECTURE.md` §3.4 (Key Decisions).

## Artifacts

- [`ARCHITECTURE.md`](./ARCHITECTURE.md) — architecture, the three scenarios, setup, testing, security, risks, and the git/PR workflow.
- `README.md` (this document) — executive summary.
- [`.github/PULL_REQUEST_TEMPLATE.md`](./.github/PULL_REQUEST_TEMPLATE.md) — engineer↔AI traceability template for every change.
- [`infra/k8s/`](./infra/k8s/) — Kubernetes manifests and deployment script for `kind` (see `infra/k8s/README.md`).
- Pending: Postman collection (`ARCHITECTURE.md` §9 is the reference; it does not yet exist in the repo).

## Risks, Trade-offs and Validation

The complete risks and guardrails are in `ARCHITECTURE.md` §12; the most relevant are silent loss of click events if RabbitMQ fails at the moment of publishing (accepted, non-critical, future mitigation with the outbox pattern), and abuse of the shortener for phishing/open redirects (mitigated through scheme/host validation during creation). The design trade-offs (Monolith + Microservices vs. building everything at once, Redis cache vs. direct DB access, Keycloak vs. a custom Authorization Server, asynchronous vs. synchronous bulk) are detailed in §14, each with its rationale. Validation relies on unit tests, characterization tests for the brownfield scenario, and integration testing with Testcontainers (real Postgres/Redis/RabbitMQ) — see §10 for details.

## Assumptions

Summary (details in `ARCHITECTURE.md` §2): prototype scale (no real production traffic); single-region; no formal compliance requirements (IP anonymization is a good practice, not a specific legal obligation); the public link is indistinguishable between V1 and V2; Codespaces/local Docker is assumed, not a GCP account with active billing.

## Limitations

Summary (details in `ARCHITECTURE.md` §13): analytics may be eventually consistent by a few seconds; no persistent storage in the local kind/k3d cluster; no verification against external phishing/malware lists; no actual GCP deployment within this exercise.

## Quickstart

Full instructions are in `ARCHITECTURE.md` §9. Summary:
1. Open the repository in GitHub Codespaces (preconfigured with Java 17, Docker-in-Docker, `kind`/`k3d`).
2. `docker-compose up -d` (Postgres, Redis, RabbitMQ, Keycloak).
3. `mvn clean spring-boot:run`.

## Current Status

Days 1 and 2 complete (minimal V1, Gateway, V2 contract, Brownfield, Redirect & Cache with Redis and
Circuit Breaker, Analytics Worker, Keycloak/OIDC). Day 3 in progress: Bulk Processor, anti-open-redirect
validation, and rate limiting, with Kubernetes manifests deployed to `kind` already completed and merged
into `main`; additional integration coverage and final documentation remain pending (Postman collection,
this section). The complete history, PR by PR, is in `AI_USAGE_LOG.md`.
