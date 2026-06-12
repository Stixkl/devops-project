# Despliegue en Azure (AKS) — Circle Guard

> Guía operativa del despliegue real del entorno **dev** en Azure Kubernetes Service (AKS), sobre la suscripción **Azure for Students**.
> Estado: ✅ app corriendo (13/13 pods `Running` en `circleguard-dev`), pipeline `cd-dev.yml` verde end-to-end.
> Última actualización: 2026-06-12.

---

## 1. Arquitectura del despliegue

- **Cluster**: `cg-aks-dev`, 2× `Standard_B2s` (2 vCPU / 4 GiB c/u), Kubernetes v1.33.12, región `centralus`.
- **Resource Group del cluster**: `rg-circle-guard-dev`.
- **Estado de Terraform**: backend remoto en `rg-terraform-state`, Storage Account `cgtf816751`, container `tfstate`, key `dev.tfstate`.
- **Namespace de la app**: `circleguard-dev`.
- **Workloads** (13 pods, `replicas: 1` cada uno):
  - 8 microservicios: `auth`, `identity`, `gateway`, `form`, `notification`, `promotion`, `dashboard`, `file`.
  - 5 datastores: `postgres`, `kafka`, `zookeeper`, `neo4j`, `redis`.
- **Imágenes**: `stixk/circleguard-<servicio>` en DockerHub (repos públicos). CI publica tags `dev-<sha>` y `dev-latest`.
- **Secretos**: Bitnami Sealed Secrets (controller `sealed-secrets-controller` v0.27.1 en `kube-system`). El `SealedSecret` cifrado vive en el repo; el controller lo descifra al `Secret` `circleguard-secrets` (consumido vía `envFrom.secretRef`).

### Repos involucrados

- **devops-project**: aplicación, tests, `.github/workflows/` (`ci.yml`, `cd-dev.yml`, `cd-stage.yml`).
- **circleguard-infra**: `terraform/`, `k8s/`, `scripts/`. Los workflows de CD lo clonan en `./infra/` durante el job de deploy.

---

## 2. Prerrequisitos

Herramientas (probadas en **Azure Cloud Shell** PowerShell):

| Tool | Uso | Notas |
|---|---|---|
| `az` | provisión / credenciales del cluster | sesión iniciada (`az login`) |
| `kubectl` | aplicar manifests | preinstalado en Cloud Shell |
| `kubeseal` v0.27.1 | sellar secretos | bajar del release de bitnami-labs/sealed-secrets |
| `gh` | secrets y workflows de GitHub | autenticado como `Stixkl` |
| `git` | PRs a circleguard-infra | |

Instalar `kubeseal` en Cloud Shell (sin root):

```powershell
curl -L -o kubeseal.tar.gz "https://github.com/bitnami-labs/sealed-secrets/releases/download/v0.27.1/kubeseal-0.27.1-linux-amd64.tar.gz"
tar -xzf kubeseal.tar.gz kubeseal
chmod +x kubeseal     # se usa como ./kubeseal o con $HOME en PATH
```

### Restricciones de Azure for Students

- **Region restriction**: solo `westus3, northcentralus, centralus, chilecentral, canadacentral`. `eastus` bloqueada → se usa `centralus`.
- **Spot NO disponible**: pools Spot fallan → solo se desplegó dev (sin Spot).
- **Cluster AAD-enabled**: `kubectl` exigiría `kubelogin`. Workaround: kubeconfig **admin** por certificado (`az aks get-credentials --admin`), sin kubelogin.
- Registrar providers si faltan: `az provider register --namespace Microsoft.Storage` y `Microsoft.ContainerService`.

---

## 3. Provisión de infraestructura (Terraform)

Roots aislados por entorno en `circleguard-infra/terraform/environments/{dev,stage,prod}/`, cada uno con su propio `key` de state (evita que un `apply` de dev toque stage/prod).

```bash
cd terraform/environments/dev
terraform init -backend-config=backend.hcl
terraform apply -var-file=dev.tfvars
```

Outputs relevantes: `resource_group_name` (= `rg-circle-guard-dev`), nombre del cluster (`cg-aks-dev`).

---

## 4. Configuración de credenciales

### 4.1 kubeconfig admin → secret de GitHub

El cluster es AAD-enabled; CI/CD usa el kubeconfig **admin** (por certificado). El workflow hace `base64 -d` del secret, así que **debe guardarse en base64** (guardarlo en crudo da `base64: invalid input`).

```powershell
az aks get-credentials --admin -g rg-circle-guard-dev -n cg-aks-dev -f kc.yaml --overwrite-existing
base64 -w0 kc.yaml | gh secret set KUBE_CONFIG_DEV --env dev --repo Stixkl/devops-project
# (Cloud Shell mantiene kc.yaml en $HOME; apuntar kubectl: $env:KUBECONFIG = "$HOME/kc.yaml")
```

> ⚠️ El kubeconfig admin es acceso total al cluster. No compartirlo. Rotación: `az aks rotate-certs -g rg-circle-guard-dev -n cg-aks-dev`.
> Los kubeconfigs locales (`kubeconfig*`, `kc-*.yaml`) están ignorados por Git. Eliminar el archivo local cuando ya no sea necesario.

### 4.2 Secrets de DockerHub

`DOCKERHUB_USERNAME=stixk` + un PAT como `DOCKERHUB_TOKEN`, en GitHub Secrets del repo `Stixkl/devops-project`.

> El username real es `stixk` (la pantalla de DockerHub muestra `stixx`, pero ese da `unauthorized`).

---

## 5. Sealed Secrets (entorno dev)

Los valores de dev son **mock** (definidos como defaults en `scripts/seal-dev-secrets.sh`). Para (re)generar `k8s/dev/sealed-secrets.yaml` contra cg-aks-dev:

```powershell
cd circleguard-infra
$env:PATH = "$HOME" + ":" + $env:PATH    # kubeseal en PATH para el script
# fetch del cert público del controller (nombre real del deployment):
./kubeseal --fetch-cert --controller-name sealed-secrets-controller --controller-namespace kube-system > k8s/dev/sealed-secrets-cert.pem
bash scripts/seal-dev-secrets.sh         # sella offline con el cert; salida: k8s/dev/sealed-secrets.yaml
```

Commitear `sealed-secrets.yaml` y `sealed-secrets-cert.pem` (ambos seguros: cifrado + clave pública).

---

## 6. Despliegue (cd-dev.yml)

Disparadores: push a la rama `dev`, o manual.

```powershell
gh workflow run cd-dev.yml --repo Stixkl/devops-project --ref dev
gh run watch <run-id> --repo Stixkl/devops-project
```

El pipeline:
1. `build-push` (matriz de 8 servicios): build + push a `stixk/circleguard-<svc>:dev-<sha>` y `:dev-latest`.
2. `deploy-dev`: clona infra, configura kubeconfig desde `KUBE_CONFIG_DEV`, `kubectl apply -f infra/k8s/namespaces/` y `-f infra/k8s/dev/`, `kubectl set image` a `dev-<sha>`, `rollout status`, smoke test del gateway.

---

## 7. Verificación

```powershell
$env:KUBECONFIG = "$HOME/kc.yaml"
kubectl get pods -n circleguard-dev          # esperado: 13/13 Running

# health del gateway (port-forward en background, Cloud Shell de una pestaña):
$pf = Start-Job { $env:KUBECONFIG = "$using:HOME/kc.yaml"; kubectl port-forward svc/gateway-service 8087:8087 -n circleguard-dev }
Start-Sleep 6
curl http://localhost:8087/actuator/health   # esperado: {"status":"UP",...}
Stop-Job $pf; Remove-Job $pf
```

---

## 8. Troubleshooting — fallos resueltos

Depuración iterativa contra el cluster real. Cada fix quedó en el repo (reproducible):

| Síntoma | Causa raíz | Fix |
|---|---|---|
| deploy-dev muere en 6s, `base64: invalid input` | `KUBE_CONFIG_DEV` guardado en crudo, no base64 | re-guardar `base64 -w0` del kubeconfig |
| ImagePullBackOff en los 8 servicios | manifests con `:latest` (tag inexistente; CI sube `dev-<sha>`/`dev-latest`) | `:latest` → `dev-latest` |
| kafka/neo4j CrashLoopBackOff | k8s inyecta `KAFKA_PORT`/`NEO4J_PORT_*` que las imágenes parsean como config | `enableServiceLinks: false` en el pod spec |
| `CreateContainerConfigError` en servicios | SealedSecret cifrado con la clave del cluster viejo | re-sellar con el cert de cg-aks-dev |
| `FATAL: database "circleguard_auth" does not exist` | postgres solo creaba la DB base; 5 servicios usan DB propia | initdb configmap crea `circleguard_{auth,identity,promotion,dashboard,form}` |
| muchos `Pending` + rollouts en deadlock | sobre-suscripción (replicas:2 + requests altos) vs 3800m CPU | `replicas:1`, requests bajos, `maxSurge:0`, `startupProbe`, cap heap datastores |
| auth `WeakKeyException` (200 bits) | `QR_SECRET` de 25 bytes; JJWT exige ≥256 bits HMAC | QR_SECRET ≥32 bytes + re-sellar |

### Comandos útiles de diagnóstico

```powershell
kubectl get pods -n circleguard-dev
kubectl logs -n circleguard-dev -l app=<svc>-service --tail=40 --previous   # log del intento que crasheó
kubectl describe pod -n circleguard-dev -l app=<svc>-service
kubectl describe nodes | Select-String "Allocated resources" -Context 0,6   # capacidad vs requests
kubectl exec -n circleguard-dev deploy/postgres -- psql -U admin -d circleguard -c "\l"   # DBs existentes
```

---

## 9. Operación y fallos anticipados

- **postgres usa `emptyDir`**: en reschedule del pod o stop/start del cluster se pierden datos, pero el initdb recrea las 5 DBs y Flyway re-migra (auto-sana). Para persistencia real → PVC.
- **SealedSecret atado a la clave del cluster**: si se recrea cg-aks-dev, hay que re-sellar (sección 5).
- **`dev-latest` es mutable**, pero cd-dev parchea a `dev-<sha>` inmutable → los deploys reales quedan pineados.
- **`maxSurge: 0`** implica breve downtime del pod único durante updates (aceptable en dev).
- **Capacidad**: tras el right-sizing, requests ≈ 1750m de 3800m CPU (≈54% libre). Hay holgura para updates y datastores.

### FinOps — apagar entre demos (crédito Students)

```powershell
az aks stop  -g rg-circle-guard-dev -n cg-aks-dev    # apagar
az aks start -g rg-circle-guard-dev -n cg-aks-dev    # encender
```

Alternativa con el script del repo:

```powershell
$env:AZURE_RG_DEV = "rg-circle-guard-dev"
$env:AKS_CLUSTER_DEV = "cg-aks-dev"
bash scripts/scale-to-zero.sh stop --env dev
# Cambiar stop por start para encender.
```

Tras `start`, postgres reinicializa (emptyDir) → las DBs se recrean solas; los servicios pueden reiniciar 1-2 veces hasta que postgres/kafka/neo4j estén listos.
