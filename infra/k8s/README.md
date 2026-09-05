# Despliegue en Kubernetes local (kind)

Manifiestos y script para correr el sistema completo (Postgres, Redis, RabbitMQ, Keycloak, y los
5 servicios Spring Boot del monorepo) dentro de un clúster `kind` de un solo nodo, dentro del
Codespace de este repo. Ver `ARCHITECTURE.md` sección 7 (Día 3: "manifiestos de K8s desplegados en kind dentro de Codespaces") y sección 9
(Setup Instructions) para el contexto y las prioridades de recorte de este ejercicio.

## Requisitos

`kubectl`, `kind` y `docker` disponibles en el PATH -- ya instalados por
`.devcontainer/setup.sh` al crear el Codespace (ver el comentario de ese script sobre por qué se
instalan como binarios directos en vez de una devcontainer feature de terceros).

## Deploy

Desde cualquier directorio del repo:

```bash
./infra/k8s/deploy-to-kind.sh
```

Esto: crea el clúster `kind` (si no existe uno con ese nombre), construye las 5 imágenes de la
aplicación con el `Dockerfile` compartido de la raíz del repo, las carga directamente al nodo de
`kind` (sin registry intermedio), genera los 3 `Secret`s y el `ConfigMap` del realm de Keycloak
(ver "Credenciales" abajo -- ninguno de los dos vive como YAML committeado con un valor adentro),
aplica todos los manifiestos de este directorio en orden, y espera a que cada `Deployment` quede
`available` antes de terminar. Es idempotente -- se puede volver a correr después de un cambio de
código.

Script alternativo, paso a paso, si se prefiere no correr el script completo:

```bash
kind create cluster --name url-shortener --config infra/k8s/kind-config.yaml
docker build --build-arg MODULE=v2-shortener-service -t v2-shortener-service:kind .
kind load docker-image v2-shortener-service:kind --name url-shortener
# ... repetir build+load para v1-legacy-monolith, api-gateway, analytics-worker, bulk-processor

# Secrets: mismos nombres de variable que docker-compose.yml/.env.example, mismo default si no
# hay override -- ver la sección "Credenciales" abajo.
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

## Credenciales

No hay ningún archivo YAML en este directorio con una contraseña adentro -- los 3 `Secret`s
(`postgres-credentials`, `rabbitmq-credentials`, `keycloak-admin-credentials`) los genera
`deploy-to-kind.sh` en el momento del deploy, leyendo las mismas variables de entorno que
`docker-compose.yml`/`.env.example` ya usan (`POSTGRES_PASSWORD`, `RABBITMQ_USER`,
`RABBITMQ_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`), con el mismo valor de desarrollo por default si
no hay override. El script carga `.env` en la raíz del repo automáticamente si existe (nunca
committeado, ver `.gitignore`) -- copiar `.env.example` a `.env` y cambiar un valor ahí alcanza
para que se propague igual a `docker-compose` y a `kind`, sin tocar ningún manifiesto.

## Cómo llegar a los servicios desde fuera del clúster

`infra/k8s/kind-config.yaml` mapea 3 puertos del Codespace directamente a los `NodePort` fijos de
sus Services -- los mismos 3 puertos que `docker-compose.yml` y la colección de Postman ya usan,
para que apuntar a "localhost" funcione igual con o sin `kind`:

| Puerto (Codespace) | Servicio             | Uso                                                                     |
| ------------------- | --------------------- | -------------------------------------------------------------------------- |
| `8081`               | Keycloak              | Obtener un token (`/realms/urlshortener/protocol/openid-connect/token`) |
| `8082`               | API Gateway            | `/api/v1/**` y `GET /{shortCode}` (ver limitación abajo)                |
| `8084`               | v2-shortener-service   | `/api/v2/urls`, `/api/v2/urls/bulk`, etc.                               |

`v1-legacy-monolith`, `analytics-worker`, `bulk-processor` y la UI de administración de RabbitMQ
(15672) son deliberadamente internos -- no forman parte de la superficie pública de este sistema
(v1 solo se llega vía el Gateway) o no tienen ningún endpoint pensado para un caller externo. Para
inspeccionarlos igual, sin agregar un mapeo permanente:

```bash
kubectl -n url-shortener port-forward svc/rabbitmq 15672:15672
kubectl -n url-shortener port-forward svc/v1-legacy-monolith 8080:8080
```

**Limitación pre-existente, no introducida por este PR:** el API Gateway todavía no enruta
`/api/v2/**` a los microservicios reales -- `GatewayRoutesConfig`/`V2StubController` (Día 1,
Task #3) devuelven `501 Not Implemented` para ese prefijo, y nunca se actualizaron una vez que
`v2-shortener-service` existió de verdad (Día 2, Task #4 en adelante). Por eso el tráfico V2 se
expone directo contra `v2-shortener-service:30084` en vez de pasar por el Gateway -- exactamente
como ya se prueba hoy vía Postman/curl fuera de Kubernetes. Cerrar esa brecha del Gateway es un
cambio de código separado, fuera del alcance de este PR (solo K8s).

## Smoke test

```bash
# Token de Keycloak (usuario demo/demo_local del realm importado)
TOKEN=$(curl -s -X POST http://localhost:8081/realms/urlshortener/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password&client_id=url-shortener-v2&username=demo&password=demo_local' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

# Crear un short link (V2)
curl -s -X POST http://localhost:8084/api/v2/urls \
  -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json' \
  -d '{"longUrl":"https://example.com"}'

# V1, vía el Gateway
curl -s http://localhost:8082/api/v1/urls -H 'Content-Type: application/json' \
  -d '{"longUrl":"https://example.com"}' -X POST
```

## Diseño y decisiones (detalle también en `AI_USAGE_LOG.md`)

- **Un solo `Dockerfile` parametrizado** (`ARG MODULE`) en la raíz del repo, en vez de uno por
  módulo -- ver su propio comentario de cabecera.
- **Ningún `Secret` vive como YAML committeado con un valor adentro** -- los tres se generan en
  `deploy-to-kind.sh` a partir de variables de entorno (ver "Credenciales" arriba). Una revisión
  de PR sobre una versión anterior de este directorio señaló justo esto en
  `KEYCLOAK_ADMIN_PASSWORD`; el detalle de por qué ese cambio importa incluso cuando el valor de
  por sí nunca fue real está en `AI_USAGE_LOG.md`.
- **Sin almacenamiento persistente** (Postgres usa `emptyDir`, no una `PersistentVolumeClaim`):
  ya declarado como limitación aceptada en `ARCHITECTURE.md` sección 13 para este clúster local y
  descartable.
- **`KC_HOSTNAME=keycloak` fijo en el Deployment de Keycloak**, la única divergencia deliberada
  respecto a `docker-compose.yml` -- ver el comentario en `13-keycloak.yaml` sobre por qué el
  claim `iss` de un token tiene que ser el mismo sin importar si quien lo pidió fue un pod del
  clúster o un `curl` desde el Codespace.
- **Namespace propio (`url-shortener`)**: `kubectl delete namespace url-shortener` limpia todo de
  una vez.
- **Sin Helm ni Kustomize**: 10 manifiestos `kubectl apply`-ables (más `kind-config.yaml`, que
  no se aplica con `kubectl`), sin templating -- proporcional al tamaño de
  este ejercicio; un despliegue real (ver el roadmap a GKE en `ARCHITECTURE.md` sección 9)
  justificaría Helm/Kustomize para manejar de verdad múltiples entornos.

## Troubleshooting

- `kubectl -n url-shortener get pods` -- si algo queda en `Pending`, casi siempre es un recurso
  de CPU/memoria insuficiente en el Codespace; bajar los `resources.requests` de este directorio
  es la salida documentada por `ARCHITECTURE.md` sección 7 ("si kind da problemas de recursos, se
  demuestra todo con docker-compose").
- `kubectl -n url-shortener logs deployment/<nombre>` -- el primer paso real de diagnóstico,
  igual que pedir el output real de `mvn verify` en vez de adivinar (ver `AI_USAGE_LOG.md`).
- `kubectl -n url-shortener describe pod <pod>` -- para un pod que nunca llega a `Ready`, revisa
  el final: eventos de `readinessProbe`/`livenessProbe` fallando ahí mismo.

## Teardown

```bash
kind delete cluster --name url-shortener
```
