# URL Shortener — Prototipo (AI-Assisted Engineering)

## Qué es esto

Prototipo de un servicio de acortamiento de URLs, construido en 2-3 días como ejercicio de **ejecución de ingeniería acelerada por IA**: el objetivo no es solo el sistema en sí, sino demostrar comprensión de requerimientos, decomposición de tareas, ejecución disciplinada con IA con trazabilidad completa, y manejo consciente de riesgos bajo un tiempo acotado. La metodología y el detalle técnico completo están en [`ARCHITECTURE.md`](./ARCHITECTURE.md); este documento es el resumen ejecutivo.

## Plan y Rationale

- **Stack:** Java 17 / Spring Boot 3.x, PostgreSQL (fuente de verdad), Redis (cache de redirect + rate limiting, nunca contadores), RabbitMQ (colas separadas para analíticas y bulk), Keycloak (OIDC).
- **Alcance:** completo — auth OIDC real, bulk creation asíncrono, custom alias, expiración, analytics enriquecido. Se optó por el nivel más ambicioso en ambos ejes de forma deliberada, con un plan explícito de qué recortar primero si el tiempo se acorta (ver abajo).
- **Despliegue:** Kubernetes local (`kind`/`k3d`) dentro de GitHub Codespaces, con una ruta a GKE documentada pero no ejecutada — se prioriza el tiempo de ingeniería sobre gastar el timebox en credenciales y billing de un proveedor cloud real.
- **Arquitectura:** patrón Strangler Fig con un Monolito V1 y Microservicios V2, donde V1 es una **simulación didáctica** de un sistema heredado (no existe legacy real; se construye mínimo el Día 1 y se trata como heredado desde el Día 2) — declarado así explícitamente para que sea defendible ante cualquier revisor.
- **Plan día a día** (detalle completo en `ARCHITECTURE.md` §7): Día 1 cimientos + V1 mínimo + arranque Greenfield; Día 2 Brownfield + Redirect&Cache + Auth; Día 3 Bulk async + Kubernetes local + seguridad + pruebas + documentación.
- **Prioridad de recorte si el tiempo se acorta** (confirmada explícitamente): proteger los 3 escenarios (Greenfield/Brownfield/Ambiguous) por encima de todo. Orden de corte: (1) Kubernetes en vivo → docker-compose + manifiestos sin ejecutar, (2) OIDC completo → JWT simple, (3) Bulk asíncrono → bulk síncrono.

Racional completo de cada decisión en `ARCHITECTURE.md` §3.4 (Key Decisions).

## Artifacts

- [`ARCHITECTURE.md`](./ARCHITECTURE.md) — arquitectura, los tres escenarios, setup, testing, seguridad, riesgos y flujo de git/PR.
- `README.md` (este documento) — resumen ejecutivo.
- [`.github/PULL_REQUEST_TEMPLATE.md`](./.github/PULL_REQUEST_TEMPLATE.md) — plantilla de trazabilidad ingeniero↔IA para cada cambio.
- [`infra/k8s/`](./infra/k8s/) — manifiestos de Kubernetes y script de despliegue a `kind` (ver `infra/k8s/README.md`).
- Pendiente: colección Postman (`ARCHITECTURE.md` §9 la referencia; todavía no existe en el repo).

## Riesgos, Trade-offs y Validación

Los riesgos y guardrails completos están en `ARCHITECTURE.md` §12; los más relevantes: pérdida silenciosa de eventos de clic si RabbitMQ cae en el instante de publicar (aceptado, no crítico, mitigable a futuro con patrón outbox), y abuso del shortener para phishing/open-redirect (mitigado con validación de esquema/host en la creación). Los trade-offs de diseño (Monolito+Microservicios vs. todo de una vez, Redis cache vs. DB directa, Keycloak vs. Authorization Server propio, bulk async vs. síncrono) están detallados en §14, cada uno con su justificación. La validación se apoya en pruebas unitarias, characterization tests para el escenario brownfield, e integración con Testcontainers (Postgres/Redis/RabbitMQ reales) — detalle en §10.

## Assumptions

Resumen (detalle en `ARCHITECTURE.md` §2): escala de prototipo (no tráfico de producción real); single-region; sin requisitos formales de compliance (la anonimización de IP es buena práctica, no una obligación legal específica); el link público es indistinguible entre V1 y V2; se asume Codespaces/Docker local, no una cuenta de GCP con billing activo.

## Limitations

Resumen (detalle en `ARCHITECTURE.md` §13): consistencia eventual de unos segundos en analíticas; sin almacenamiento persistente en el clúster local kind/k3d; sin verificación contra listas externas de phishing/malware; sin despliegue real a GCP dentro de este ejercicio.

## Quickstart

Instrucciones completas en `ARCHITECTURE.md` §9. Resumen:
1. Abrir el repo en GitHub Codespaces (preconfigura Java 17, Docker-in-Docker, `kind`/`k3d`).
2. `docker-compose up -d` (Postgres, Redis, RabbitMQ, Keycloak).
3. `mvn clean spring-boot:run`.

## Estado actual

Días 1 y 2 completos (V1 mínimo, Gateway, contrato V2, Brownfield, Redirect & Cache con Redis y
Circuit Breaker, Analytics Worker, Keycloak/OIDC). Día 3 en curso: Bulk Processor, validación
anti-open-redirect y rate limiting, y manifiestos de Kubernetes desplegados en `kind` ya
completos y mergeados a `main`; quedan pendientes cobertura de integración adicional y la
documentación final (colección Postman, esta sección). Historial completo, PR por PR, en
`AI_USAGE_LOG.md`.
