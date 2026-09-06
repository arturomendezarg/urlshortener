# New Repository — PR Roadmap (Greenfield / Brownfield / Ambiguous)

Draft for review before any commit is made against the new repo. Reuses the existing,
already-tested codebase (Java/Spring Boot modules, infra, tests) — no code is rewritten from
scratch. What changes is the PR narrative: everything is renamed/regrouped/translated so the
audit trail (`AI_USAGE_LOG.md`, PR titles, commit messages) maps directly onto the three
scenarios the original brief asked for, entirely in English.

Total: 14 PRs across 3 days. Each PR keeps the same discipline as the current repo: bot account
opens it, human account reviews/approves/merges, `AI_USAGE_LOG.md` gets an entry, CI (Markdown
Lint / Secret Scanning / Build and Test) must pass.

## Day 1 — Construction (Greenfield)

The system is built as if for the first time — no legacy assumption yet. This day's PRs are the
foundation everything else stands on.

| # | PR title | Content (reused from current repo) |
|---|---|---|
| 1 | `feat: project scaffold, V1 legacy monolith, CI pipeline` | Maven reactor, `v1-legacy-monolith`, CI workflow (3 required checks), Checkstyle/SpotBugs config, `ARCHITECTURE.md`/`AI_USAGE_LOG.md` in English from commit one |
| 2 | `feat: API Gateway with Strangler Fig routing` | `api-gateway`, V1 routing + V2 stub |
| 3 | `feat: V2 shortener service — creation + OAuth2 resource server` | `v2-shortener-contract`, `v2-shortener-service` create endpoint, Keycloak integration |
| 4 | `feat: redirect & cache service (Redis cache-aside, Circuit Breaker)` | Cache-aside read path, fallback to Postgres |
| 5 | `feat: analytics worker (async click events via RabbitMQ)` | Fire-and-forget click tracking, IP anonymization |
| 6 | `feat: bulk processor (async bulk URL creation)` | Bulk submission + async processing, DLQ |
| 7 | `feat: rate limiting on create endpoints` | Redis-backed fixed window, Circuit-Breaker fail-open |
| 8 | `feat: Kubernetes manifests for local kind deployment` | `infra/k8s/*`, including the already-diagnosed kind-in-Codespaces limitation, carried forward honestly rather than re-litigated |
| 9 | `test: Testcontainers coverage for bulk-job submission` | Real-infra rollback proof (the work just merged as PR #31 here) |

## Day 2 — Brownfield scenarios

Two scenarios that only make sense once Day 1's system exists: proving the "legacy-only" starting
point, then proving the modernization cutover doesn't break it.

| # | PR title | What it proves |
|---|---|---|
| 10 | `test: brownfield baseline — V1 running standalone, V2 absent` | A deploy profile / compose override running **only** V1 + Postgres (no Keycloak, Redis, RabbitMQ, V2 services), plus an end-to-end test proving create+redirect works completely on its own. This is the literal "legacy system as found" starting point. |
| 11 | `test: brownfield cutover — V2 activated alongside V1` | V2 services turned on next to the still-running V1. A test proves: short codes created **before** the cutover still redirect correctly via V1, short codes created **after** go through V2, and both resolve under the same public domain with no visible difference to an end user — the actual Strangler Fig claim, proven rather than just described. |

## Day 3 — Ambiguous scenario, audit-trail demo, final docs

| # | PR title | Content |
|---|---|---|
| 12 | `test: validate the interpretations chosen for ambiguous requirements` | Turns `ARCHITECTURE.md` section 1's "ambiguities and how they were resolved" bullets into executable tests — e.g. anti-open-redirect behavior, the bulk-submission limit, the rate-limit windows — each test names which ambiguity it settles. |
| 13 | `feat: Keycloak admin credential via ConfigMap` *(deliberately flawed)* | A **low-entropy fake credential** (`admin_local`-style, same shape as the real PR #30 finding) committed in plain YAML. Secret Scanning passes it (as verified empirically already). **This PR is meant to be rejected in human review, not fixed** — closed without merging, so `main` never carries the credential. The rejection itself, on record, is the deliverable: proof the human-review guardrail catches what CI cannot. |
| 14 | `docs: Postman collection + final documentation pass` | The Postman collection (still missing from the original repo), final README/AI_USAGE_LOG polish |

## Open engineering questions this roadmap surfaces

These need a decision before PR 10/11 can be written — they're genuine design choices, not busywork:

1. **How does "V1-only" actually get expressed?** A `docker-compose.v1-only.yml` override (simplest, matches how docker-compose already works here) vs. a Spring profile flag inside the Gateway that disables V2 routing entirely. Compose override is my default unless you want it demonstrated at the application-config level too.
2. **What does the cutover test actually exercise?** Compose-level (two `docker-compose` runs, sequential) vs. Testcontainers-level (one JVM test class that creates a link, then "activates" V2 and creates another, asserting both redirect correctly) — the second is more valuable as regression-proof CI coverage; the first is more visually convincing as a demo. I'd default to Testcontainers, since that's this repo's established "real infra over mocks" pattern, and mention the manual compose demo as a secondary note in the PR body.

I'll proceed with translating `README.md` and `ARCHITECTURE.md` now (needed regardless of how 10-13 land), and hold PRs 10-13's exact test code until the two questions above are settled — happy to just make the calls above myself if you'd rather not stop to decide.
