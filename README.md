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
  alongside it; Day 3 validates the Ambiguous-requirement decisions with executable tests, runs a
  deliberate audit-trail demonstration (a plaintext-credential PR meant to be caught and rejected
  in human review, never merged), and closes with final documentation.
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
- Postman collection — see `ARCHITECTURE.md` §9.

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

## Quickstart

Full instructions in `ARCHITECTURE.md` §9. Summary:

1. Open the repo in GitHub Codespaces (preconfigures Java 17, Docker-in-Docker, `kind`/`k3d`).
2. `docker-compose up -d` (Postgres, Redis, RabbitMQ, Keycloak).
3. `mvn clean spring-boot:run`.

## Current status

See `ARCHITECTURE.md` §7 for the day-by-day plan and `AI_USAGE_LOG.md` for the complete,
PR-by-PR history.
