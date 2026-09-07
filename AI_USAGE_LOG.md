# AI Usage Log

Continuous log of decisions made during AI-assisted execution (see template and context in `ARCHITECTURE.md` §8). Each entry references the corresponding PR so the full diff is one click away.

## 2026-09-06 — [Process/Docs/Fix] PR #10 — Repository carryover, Day-plan restructure, Gateway V1/V2 dynamic dispatch

- **Task:** the engineer pushed this project's full history into a new repository/account
  (`arturomendezarg/urlshortener`) to keep the audit trail scoped to just the three exercise
  scenarios (greenfield/brownfield/ambiguous) going forward, with English-only documentation. Two
  intermediate commits landed directly on `main` before this PR (`arturomendezarg`, bypassing
  branch protection because it has not been reconfigured yet on the new repository — a real,
  declared gap, not hidden): a plain English translation of `README.md`/`ARCHITECTURE.md`, and an
  "Upload urlshorter services" commit that added two previously-delivered `.patch` files plus a
  roadmap markdown as inert files under `Claude outputs/`, rather than actually applying them.
- **Prompt:** "regenera el workflow y crea un nuevo PR" after confirming the new repository
  carries the old repository's full history rather than starting empty (the engineer's explicit
  choice, made after being shown the evidence: 90 commits, the same 9 Dependabot PRs, a merge
  commit literally referencing `artmendezarg/docs/dual-account-git-workflow`).
- **AI-generated:**
  - Applied the `Claude outputs/0001-kind-health-check-and-limitation.patch` content for real
    (it had only ever existed as an inert file): `infra/k8s/deploy-to-kind.sh` now checks a Ready
    node, not just a same-named cluster, before reusing it; `infra/k8s/kind-config.yaml` pins
    `kindest/node:v1.34.0`. Confirmed `0001-bulk-job-integration-tests.patch`'s content was
    already real (carried over as part of merged PR #31) — deleted both `.patch` files and the
    roadmap copy; `Claude outputs/` is not a real project directory.
  - Replaced the engineer's plain-translation pass on `README.md`/`ARCHITECTURE.md` with the
    fuller rewrite already prepared and validated earlier this session: the Day-by-Day plan
    restructured around Day 1 (Greenfield construction), Day 2 (Brownfield — two concrete
    scenarios: V1-only baseline, V2 cutover alongside V1), Day 3 (Ambiguous-requirement tests,
    a deliberate plaintext-credential rejection demo, final docs); every `artmendezarg` reference
    updated to `arturomendezarg` to match the new account. Presented as a PR, per this project's
    own rule, rather than silently overwriting the engineer's own direct-to-`main` commit.
  - Translated `infra/k8s/README.md` to English (previously untranslated) and fixed the same
    `|---|---|` compact-table-separator `MD060` violation found and fixed in `ARCHITECTURE.md`
    earlier the same session.
  - **Real fix, not just documentation:** `api-gateway`'s Strangler Fig routing was still the
    Day-1 stub — confirmed by rereading `GatewayRoutesConfig`'s own long-standing comment
    ("added once V2 exists with its own index") and `infra/k8s/README.md`'s own "pre-existing
    limitation, not introduced by this PR" note, both of which already said so. V2 has written to
    its Redis index since it was built; nobody had wired the Gateway back up to read it. Without
    this, the new Day 2 "V2 cutover alongside V1" scenario would have nothing real to test. Added
    `DynamicShortCodeRoutingFilter` (checks `shortlink:v2:<code>` in Redis per request, routes to
    V2 if present, V1 otherwise, fails safe to V1 on a Redis error), wired it into
    `GatewayRoutesConfig`'s `/{shortCode}` route, added a real `/api/v2/**` route to the actual
    V2 service, and deleted `V2StubController` (dead code once that route exists). New tests:
    `GatewayRoutingIntegrationTest` (extended with real V2 routing + both dispatch directions,
    real Redis via Testcontainers) and `DynamicShortCodeRoutingFilterRedisOutageTest` (Redis
    stopped mid-test, asserts fail-safe to V1 — same chaos-test shape as
    `RateLimiterRedisOutageTest`/`ShortLinkRedisOutageIntegrationTest`). Added
    `spring-boot-starter-data-redis-reactive` (the Gateway's WebFlux stack cannot use the
    blocking client V2 itself uses) and excluded the new filter's Redis-client field from
    `EI_EXPOSE_REP2` in `spotbugs-exclude.xml`, joining the existing, documented group of
    constructor-injected-collaborator exclusions (`ShortLinkCache`, `RateLimiter`, etc.).
- **Researched first:** reread the actual current file contents in the new repository (not this
  session's own stale local copy) before writing anything, specifically to avoid silently
  clobbering the engineer's own direct commits; diffed the two translations section-by-section
  rather than assuming mine should simply replace theirs.
- **Verification:** `markdownlint-cli2` clean (0 errors) across every changed `.md` file.
  Mechanical import/tab/trailing-newline checks clean on every new/changed Java file. Real
  `mvn verify` was **not** possible from this AI's own sandbox — Maven Central still returns
  `403 Forbidden` through its proxy, the same standing, previously logged limitation — so
  `DynamicShortCodeRoutingFilter` and its tests are unverified by an actual build. Written
  against Spring Cloud Gateway's own documented `GATEWAY_REQUEST_URL_ATTR` mechanism, not a
  private API, but that is a design justification, not a substitute for the engineer's own
  `mvn verify`/CI run — flagged explicitly rather than presented as tested.
- **Not modified:** no other service's code; branch protection on the new repository (still
  needs to be reconfigured from scratch in GitHub Settings — it is a repository setting, not
  part of git history, so it did not carry over with the push).
- **Declared risks:** the two direct-to-`main` commits that preceded this PR were not themselves
  reviewed through the PR flow this project is built around — a fact this entry records rather
  than papers over. `DynamicShortCodeRoutingFilter` is new, unverified-by-build production code
  behind the entire Day 2 brownfield story; if `mvn verify`/CI surfaces a real defect in it,
  that failure should be diagnosed from CI's real output, per this project's own standing rule,
  not patched from guesswork.
- **Decision:** pending engineer review (see PR).

## 2026-09-06 — [Fix/Infra] PR #11 — Kubernetes deployment made real on k3d, and the four defects that only appeared once it ran

- **Task:** get the system actually running on Kubernetes. Until now `infra/k8s/` was a reviewed
  but never-executed deliverable: `kind` cannot bootstrap a control plane inside a Codespace
  (seven attempts, elimination table in `infra/k8s/README.md`), and the declared fallback was to
  demonstrate the stack with `docker-compose` instead.
- **Prompt:** "esto debe funcionar en kubernetes", after `docker-compose up -d` and a full
  `mvn clean install` had both succeeded on all seven modules.
- **AI-generated:**
  - `infra/k8s/k3d-config.yaml` and `infra/k8s/deploy-to-k3d.sh`. k3d runs k3s, which never
    invokes `kubeadm`, so the entire failure class that blocks `kind` here does not apply — a
    path this repo's own README had already identified as the known alternative but never tried.
    Everything below cluster creation and image loading is the same logic as
    `deploy-to-kind.sh`; the manifests are plain `Deployment`/`Service`/`NodePort` and moved
    across unmodified. `deploy-to-kind.sh` and `kind-config.yaml` were left untouched rather
    than generalised: `kind` still works on an ordinary Docker host, and a script that cannot be
    verified from here should not be edited to serve one that can.
  - **Defect 1 — the Gateway had no V2 wiring in Kubernetes.** `21-api-gateway.yaml` passed only
    `V1_LEGACY_MONOLITH_URL`. The dynamic V1/V2 dispatch added in the previous PR needs
    `V2_SHORTENER_SERVICE_URL` and Redis, and had neither. It would not have failed loudly:
    with no Redis reachable, `DynamicShortCodeRoutingFilter`'s documented outage fallback serves
    every short code from V1, so V2 links would silently never resolve through the Gateway.
  - **Defect 2 — probes that could not pass.** RabbitMQ's probes run `rabbitmq-diagnostics -q
    ping`, which boots a whole Erlang CLI node per invocation. No probe in `infra/k8s/` declared
    `timeoutSeconds`, so all of them used Kubernetes' default of **1 second**, and that probe
    could never finish inside it. Observed directly: the container logged `Server startup
    complete; 5 plugins started` and `Time to start RabbitMQ: 19407 ms` while its pod sat at
    `0/1` until the liveness probe — same command, same 1s timeout — killed it. Postgres and
    Redis were unaffected only because `pg_isready` and `redis-cli ping` are cheap C binaries.
    Fixed with an explicit `timeoutSeconds` on all 21 probes, plus a `startupProbe` on the seven
    slow-starting containers: `initialDelaySeconds` cannot distinguish a slow start from a hang,
    since one fixed number is either too short to boot or too long to detect a real hang.
  - **Defect 3 — the security filter answered `401` to the kubelet.** `SecurityConfig` permitted
    `"/actuator/health"` as an exact path, which does not match `/actuator/health/readiness` or
    `/actuator/health/liveness` — the sub-paths Spring Boot creates once it detects Kubernetes,
    and the two a kubelet probes with no `Authorization` header. They fell through to
    `.anyRequest().authenticated()`. `v2-shortener-service` is the monorepo's only OAuth2
    Resource Server, which is why it alone was affected. Diagnosed from its own log: `Started
    V2ShortenerServiceApplication in 44.357 seconds`, its `DispatcherServlet` initialising to
    answer the probe request itself, then exit `143` (`SIGTERM`) at almost exactly the 300s
    `startupProbe` budget — Kubernetes terminating a fully healthy application. Deliberately
    **not** fixed by pointing the probes at `/actuator/health`, the tempting one-line
    alternative: that endpoint aggregates every health indicator, so a Redis blip would mark the
    pod NotReady and, through the liveness probe, restart a working application — precisely what
    this service's Circuit Breaker and PostgreSQL fallback exist to prevent (section 6,
    Scenario A). The probe paths were right; the security rule was wrong.
  - **Defect 4 — Keycloak stamped two different issuers.** `KC_HOSTNAME` carried the bare
    hostname `keycloak`. Keycloak 26's hostname v2 provider treats that as the host only and
    still derives scheme and port from the incoming request — which defeats the very reason the
    variable was set, because the two callers arrive on different ports (external `curl`/Postman
    on the mapped host port 8081, `v2-shortener-service` on the cluster Service port 8080).
    Measured, not reasoned about: a token requested through `localhost:8081` came back with
    `"iss":"http://keycloak:8081/realms/urlshortener"`, and `POST /api/v2/urls` answered `401`
    with `error_description="The iss claim is not valid"`. Nothing outside the cluster could
    authenticate against V2 at all. Fixed by giving `KC_HOSTNAME` a full URL
    (`http://keycloak:8080`), which pins scheme, host and port together.
  - Regression coverage for defect 3: `KeycloakResourceServerIntegrationTest` already asserted
    that `/actuator/health` was public and had been green throughout — it agreed with the bug.
    It now also asserts both probe paths, with the health probes explicitly enabled for the test
    JVM (in the cluster Spring Boot enables them by platform detection, which a plain test JVM
    does not trigger).
  - Documentation brought in line with reality: `infra/k8s/README.md`'s "Verified status"
    callout (which stated the deployment could never be run end-to-end), its stale
    "Pre-existing limitation" paragraph about the Gateway returning `501` for `/api/v2/**`
    (closed by the previous PR), and its smoke test, which invoked `python3` — not present in
    this Codespace's image, so the documented smoke test could not run as written. Same for
    `ARCHITECTURE.md` sections 9 and 13, and `api-gateway/pom.xml`'s module description, which
    still advertised the removed 501 stub.
- **Researched first:** read every manifest's probe, resource and env block before changing any
  of them; read `ShortLinkService`, `ShortLinkCache` and `SecurityConfig` rather than inferring
  behaviour from names; read `infra/keycloak/realm-export.json` to confirm the client and test
  user rather than trusting the README, which had already proved stale in three places. Each
  hypothesis was checked against the cluster before any fix was written — `kubectl logs`,
  `describe`'s `Last State`, the decoded JWT payload, `redis-cli keys`.
- **Verification:** all 9 pods `Ready` on k3d. Smoke test over real HTTP, transcript in
  `infra/k8s/README.md`: Keycloak advertises `http://keycloak:8080/realms/urlshortener` and
  stamps that same `iss` on a token requested from outside the cluster; `POST /api/v1/urls`
  through the Gateway returns `201` and `GET /{code}` returns `301` to the target;
  `POST /api/v2/urls` with that JWT returns `201`; `GET /demoV2` against `v2-shortener-service`
  returns `302`. Not verified here: `mvn verify` was not run against these changes (the new
  regression test compiles during the image build, which uses `-DskipTests`; CI runs it), and
  `deploy-to-kind.sh` remains unexecutable in this environment as it always has been.
- **Not modified:** `deploy-to-kind.sh`, `kind-config.yaml`, the seven-attempt `kind`
  elimination table (kept as the historical record it is), and every service's business logic.
- **Declared risks:** one defect is knowingly left open — see the Decision below. The
  `startupProbe` budgets (300s) and the raised `kubectl wait` timeouts (180s → 300s) are sized
  for a loaded single-node Codespace and are generous for a real cluster. RabbitMQ restarted
  twice with exit code `1` during the rollout churn and has been stable since; not chased,
  and recorded here rather than left unmentioned.
- **Decision:** the Gateway's V1/V2 dispatch is **wrong in a way this PR does not fix**, and the
  defect is in code this assistant wrote in the previous PR. `DynamicShortCodeRoutingFilter`
  treats `shortlink:v2:<code>` in Redis as an index of which system owns a code, but that key is
  `ShortLinkCache`'s read-through cache: written only by `resolve()` on a miss, with a 300s TTL,
  and never written at creation. So a newly created V2 link is routed to V1 and 404s until
  something reads it directly, and a working link starts 404ing again once its entry expires.
  Demonstrated on the cluster: the identical `GET http://localhost:8082/demoV2` answered `404`
  before a direct read and `302` after, with `redis-cli keys` empty then populated either side.
  The filter's own test did not catch this because it **writes the key by hand** in its setup —
  it validates the filter's logic correctly while assuming into existence the index nothing in
  production ever writes; the test name, `routesShortCodeToV2WhenPresentInTheV2Index`, states
  the assumption out loud. Closing it is a design change, not a patch — a durable ownership
  record written at creation, or no shared state at all with the Gateway trying V2 and falling
  back to V1 on a 404 — and it is deliberately deferred to its own PR so the decision gets its
  own reasoning rather than riding along here. Pending engineer review (see PR).
- **Pattern worth naming:** all four defects were integration defects, all four lived in code and
  manifests that were carefully written, heavily commented and reviewed, and **not one was
  reachable by unit tests, static analysis, markdown lint, CI, or reading the diff**. Every one
  required the whole system standing up at once. That is the strongest argument in this
  repository for having pushed to make Kubernetes actually run instead of accepting the
  documented limitation and demonstrating with `docker-compose`.

## 2026-09-07 — [Docs/Test] PR #12 — Postman collection for reviewer verification (Greenfield & Brownfield)

- **Prompt (paraphrased):** build the Postman collection promised throughout this project's docs
  but never committed, structured around two flows the engineer specified explicitly: Greenfield
  exercises V2 as if it were the only system that ever existed (no V1 framing), and Brownfield
  exercises V1 first as the pre-existing baseline, then represents a commit that activates V2 and
  verifies it resolves afterward. The engineer also confirmed leaning toward Option B for the
  still-open Gateway cache-as-index defect (try V2, fall back to V1 on a 404), but asked to build
  and run this collection first and return to that implementation afterward.
- **What changed:**
  - Added `docs/url-shortener-enterprise.postman_collection.json` (Postman Collection v2.1):
    folder `0. Auth` (Resource Owner Password grant against the `urlshortener` realm, storing
    `{{accessToken}}` as a collection variable and logging the token's `iss` claim); folder
    `1. Greenfield — V2 as the primary system` (create/redirect, custom alias, duplicate alias
    409, reserved slug 400, invalid input 400, expiration 410, device-classified redirect rules
    for mobile/desktop User-Agents, unknown-code 404, and the async bulk submit+poll flow);
    folder `2. Brownfield — V1 baseline, then V2 cutover` (V1 create/301-redirect, a Gateway
    request confirming V1 fallback works before any V2 code exists, a `'Cutover commit'` request
    creating the same link on V2, a Gateway request for that brand-new V2 code with a
    **soft assertion** — it reports whether the observed status was 302 or 404 via
    `console.warn`/`console.log` rather than failing the run — a direct V2 read that warms
    `ShortLinkCache`, and a final Gateway request expected to return 302).
  - Added `docs/url-shortener-enterprise.postman_environment.json`: one environment covering both
    run options (docker-compose + `mvn spring-boot:run`, and k3d), since both expose the same
    `localhost` ports (8080 V1, 8081 Keycloak, 8082 Gateway, 8084 V2).
  - Updated `README.md` and `ARCHITECTURE.md` to stop saying the Postman collection was
    "planned, not yet created" now that it exists, while keeping the `curl` walkthrough as the
    terminal-only alternative it already was.
- **Researched first:** read `UrlController` (V1), `ShortLinkController`, `BulkJobController`,
  `CreateUrlRequest`, `CreateBulkUrlRequest`/`BulkUrlItemRequest`, `RedirectDeviceType`, and
  `RedirectDeviceClassifier` before writing a single request, so status codes, payload shapes,
  and the mobile/desktop User-Agent regex in the collection match the real contracts rather than
  assumed ones.
- **Verification:** both JSON files parse (`json.load`) and `markdownlint-cli2` is clean on the
  touched Markdown files. The collection itself was **not executed** from this assistant's
  environment — it can reach this repository's local clone but not the engineer's live
  Codespace, so there is no way to run Postman/Newman against the real stack from here. Running
  it for real (Postman GUI, import both files, run `0. Auth` then each folder; or
  `newman run docs/url-shortener-enterprise.postman_collection.json -e
  docs/url-shortener-enterprise.postman_environment.json`) and reporting the actual pass/fail
  output is left to the engineer, consistent with this project's standing rule of getting real
  evidence rather than assuming a script that was never run behaves as designed.
- **Declared risk:** the `keycloakUsername`/`keycloakPassword`/`clientSecret` environment values
  are placeholders (`REPLACE_WITH_REAL_...`) — no credential was invented or hardcoded, and the
  engineer must fill these in locally from the imported realm before running the collection, per
  this project's standing rule that this assistant never handles or generates credentials.
- **Not modified:** no application or infrastructure code — this entry is documentation/tooling
  only. The Gateway cache-as-index defect (Option B fix) remains open by design, deferred per the
  engineer's own sequencing, and the Brownfield folder's before/after step is deliberately built
  to keep passing its hard assertions both before and after that fix lands.
