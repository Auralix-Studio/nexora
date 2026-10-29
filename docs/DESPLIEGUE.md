# Despliegue de NEXORA en Azure

Guía paso a paso para publicar NEXORA como contenedor en **Azure Container Apps**, con **Azure SQL Database** (oferta gratuita) y despliegue automático con **GitHub Actions**. Pensada para una cuenta de **Azure for Students**.

```
GitHub (repo privado) --push a main--> Actions: pruebas -> imagen -> E2E -> GHCR
                                                                         |
                                                    az containerapp update (OIDC o infra/desplegar.sh)
                                                                         v
Internet --HTTPS--> Container Apps (Tomcat 11, 0.5 vCPU / 1 GiB, 0..1 réplicas) --TLS--> Azure SQL (BD_NEXORA, serverless)
                                  |
                          registros --> Log Analytics
```

## Archivos que intervienen

| Archivo | Para qué sirve |
| --- | --- |
| `Dockerfile`, `.dockerignore` | Imagen multietapa: Maven compila y prueba, Tomcat 11 (Java 21, usuario no root) ejecuta el WAR como `ROOT.war` |
| `infra/tomcat/server.xml` | Tomcat endurecido: sin puerto de apagado ni AJP, `RemoteIpValve`, compresión, access log a stdout |
| `docker-compose.yml`, `.env.example` | Pila real en local: aplicación + SQL Server 2022 + carga de `sql/bd_nexora.sql` |
| `infra/main.bicep` | Infraestructura como código (Log Analytics, Container Apps, Azure SQL) |
| `infra/crear-infra.sh` | Crea la infraestructura; genera y guarda las claves en `infra/.secretos.local` |
| `infra/inicializar-bd.sh` | Carga el esquema y los datos, crea el usuario `nexora_app` y fija las claves privadas |
| `infra/desplegar.sh` | Publica una imagen de GHCR en el Container App y espera a `/salud` |
| `infra/esperar-salud.sh` | Espera a que `/salud` responda 200 (lo usan `desplegar.sh` y CI) |
| `.github/workflows/ci.yml` | Pruebas, imagen, E2E en contenedor, publicación en GHCR y despliegue |

## 1. Requisitos

- **Git Bash** (Windows). Todos los comandos de esta guía se escriben en Git Bash, desde la raíz del repositorio.
- **Azure CLI** (`az`): https://aka.ms/installazurecliwindows. Después, `az login` con tu cuenta de la universidad.
- **Docker Desktop**, abierto (el ícono de la ballena debe indicar que el motor está en ejecución).
- **sqlcmd** (`winget install sqlcmd`, o las herramientas de línea de comandos de SQL Server). Comprueba con `sqlcmd -?`.
- **JDK 17 o superior y Maven**, para compilar y calcular los hashes de las claves (`mvn -q package`).
- `curl` y `openssl` vienen con Git Bash. Opcional: **GitHub CLI** (`gh`).

Notas de Azure for Students:

- Tienes un crédito (alrededor de US$ 100) y sin tarjeta. Algunas suscripciones restringen las regiones permitidas: si el
  despliegue falla con `RequestDisallowedByAzure` o `RegionDoesNotAllowProvisioning`, borra el grupo y repite con otra región
  (`-l brazilsouth`, `-l canadacentral`, `-l centralus`, `-l westus3`...).
- La oferta gratuita de Azure SQL está limitada por suscripción. Si ya la usaste en otra base, ejecuta
  `USAR_OFERTA_GRATUITA=false bash infra/crear-infra.sh` (la base se cobra por consumo) o elimina esa otra base.
- Si Git Bash convierte rutas que empiezan con `/` (por ejemplo `--scope /subscriptions/...`), antepón `MSYS_NO_PATHCONV=1` al comando.
- Si algún valor de `az ... -o tsv` trae un salto de línea invisible (`\r`), añade `| tr -d '\r'`.

## 2. Verificación local con Docker Compose

Prueba la pila real (Tomcat en modo producción + SQL Server) antes de tocar Azure.

```bash
cp .env.example .env            # ajusta MSSQL_SA_PASSWORD si quieres
docker compose up -d --build    # construye la imagen (ejecuta las pruebas JUnit) y carga la base
docker compose ps               # espera a que "app" aparezca como healthy (el arranque tarda ~1-2 minutos)
curl http://localhost:8080/salud
```

La tienda queda en http://localhost:8080 (cuentas en el README). Las pruebas de punta a punta suponen datos semilla recién
cargados, así que corre cada una sobre un arranque limpio:

```bash
bash docs/pruebas/e2e.sh http://localhost:8080
docker compose down && docker compose up -d      # vuelve a los datos semilla
bash docs/pruebas/e2e-admin.sh http://localhost:8080
```

Al terminar: `docker compose down` (los datos del SQL Server local no se conservan). Para construir sin pruebas:
`OMITIR_PRUEBAS=true docker compose up -d --build`. Si `docker compose up` falla al construir, `docker compose logs app`
y `docker compose logs sql-init` muestran la causa.

> `docker-compose.yml` usa `sa` solo para esta verificación local. En Azure la aplicación se conecta como `nexora_app`
> (lectura y escritura, sin permisos de administración).

## 3. Crear la infraestructura

```bash
az login
bash infra/crear-infra.sh                       # grupo rg-nexora, región eastus2
# otras opciones: bash infra/crear-infra.sh -g rg-nexora -l brazilsouth
```

El script:

1. Comprueba `az` y la sesión, crea el grupo de recursos y registra los proveedores necesarios.
2. Genera claves fuertes para el administrador SQL y para `nexora_app` y las guarda en `infra/.secretos.local`
   (Git lo ignora; **no lo subas ni lo compartas**; hazle una copia en tu gestor de contraseñas).
3. Muestra el **what-if** (lista de recursos que se crearán) y **pide confirmación** antes de crear nada.
4. Despliega `infra/main.bicep` y guarda en `infra/.secretos.local` la URL y el nombre del servidor SQL.

El primer despliegue usa una imagen pública de arranque rápido de Azure (puerto 80): la URL mostrará una página de bienvenida
de Azure, no la tienda. Eso es normal; la imagen real llega en el paso 6.

Si repites el script, reutiliza las claves y conserva la imagen, el registro y las réplicas que ya tenga el Container App.

## 4. Inicializar la base de datos

Compila antes (el script usa `target/classes` para calcular los hashes de las claves):

```bash
mvn -q package
bash infra/inicializar-bd.sh
```

Qué hace: abre el firewall de Azure SQL solo para tu IP pública (y **cierra la regla al terminar**, incluso si falla),
ejecuta `sql/bd_nexora.sql` desde la sección 2 (Azure SQL no admite `CREATE DATABASE` ni `USE`; la base ya la creó Bicep),
crea el usuario contenido `nexora_app` con `db_datareader` y `db_datawriter`, y genera claves privadas para `admin` y
`almacen`. **Esas dos claves se muestran una sola vez al final: guárdalas.** Las cuentas `cliente` e `invitado` quedan con
las claves públicas de demostración.

Si la base ya tiene tablas, el script avisa y pide escribir `BORRAR`, porque `sql/bd_nexora.sql` recrea todo (se pierden los datos).
Si la base serverless estaba pausada, el script espera mientras se reanuda (puede tardar un minuto).

## 5. Subir el código y publicar la imagen

Crea el repositorio **privado** y sube `main`:

```bash
gh repo create nexora --private --source=. --push      # o crea el repositorio en la web y haz git push
```

La ejecución de **CI** (pestaña Actions) compila, prueba, construye la imagen, la prueba de punta a punta dentro de un
contenedor y, en `main`, la publica en `ghcr.io/<propietario>/nexora` con las etiquetas `<sha corto>` y `latest`.
Como el repositorio es privado, el paquete también lo es.

## 6. Primer despliegue en Azure

Crea un token de GitHub **clásico** con el permiso `read:packages` (Settings > Developer settings > Personal access tokens)
para que Azure pueda descargar la imagen privada. Luego, con la etiqueta (sha corto) que publicó CI:

```bash
GHCR_USUARIO=<tu-usuario-github> GHCR_TOKEN=<token> bash infra/desplegar.sh <sha-corto>
```

La primera vez, el script detecta que el Container App aún tiene la imagen de arranque y vuelve a aplicar `main.bicep` con la
imagen real (puerto 8080, sondas a `/salud`, registro GHCR). Después solo ejecuta `az containerapp update`. Termina cuando
`/salud` responde 200 con la versión nueva. Con 0.5 vCPU, Tomcat tarda cerca de 2 minutos en arrancar.

Comprueba en el navegador la URL que muestra el script. Alternativa sin token: hacer público el paquete en GitHub
(Packages > nexora > Package settings), pero entonces cualquiera puede descargar la imagen.

## 7. Despliegue automático con GitHub Actions (OIDC)

El job `desplegar` de `ci.yml` solo corre en `main` y solo si existe la variable de repositorio `AZURE_CLIENT_ID`. Usa
**OIDC**: no hay contraseñas guardadas en GitHub. Requiere haber hecho antes el paso 6 (el Container App debe tener ya la
configuración definitiva).

```bash
REPO="<propietario>/<repositorio>"
APP_ID=$(az ad app create --display-name nexora-github --query appId -o tsv | tr -d '\r')
SP_ID=$(az ad sp create --id "$APP_ID" --query id -o tsv | tr -d '\r')

cat > federada.json <<EOF
{
  "name": "github-main",
  "issuer": "https://token.actions.githubusercontent.com",
  "subject": "repo:${REPO}:ref:refs/heads/main",
  "audiences": ["api://AzureADTokenExchange"]
}
EOF
az ad app federated-credential create --id "$APP_ID" --parameters @federada.json
rm federada.json

# Permiso solo sobre el grupo de recursos de NEXORA
az role assignment create --assignee-object-id "$SP_ID" --assignee-principal-type ServicePrincipal \
  --role Contributor --resource-group rg-nexora

# Variables del repositorio (no son secretos)
gh variable set AZURE_CLIENT_ID --body "$APP_ID" --repo "$REPO"
gh variable set AZURE_TENANT_ID --body "$(az account show --query tenantId -o tsv | tr -d '\r')" --repo "$REPO"
gh variable set AZURE_SUBSCRIPTION_ID --body "$(az account show --query id -o tsv | tr -d '\r')" --repo "$REPO"
```

Sin `gh`, crea las tres variables en GitHub: Settings > Secrets and variables > Actions > pestaña **Variables**. Opcionales:
`AZURE_RESOURCE_GROUP` (por defecto `rg-nexora`) y `AZURE_CONTAINERAPP_NAME` (por defecto `ca-nexora`).

> **Advertencia:** algunos tenants universitarios **no permiten registrar aplicaciones** en Microsoft Entra ID
> (`az ad app create` falla con "Insufficient privileges"). En ese caso no crees la variable `AZURE_CLIENT_ID`: el job
> `desplegar` se omite solo y los despliegues se hacen desde tu equipo con `bash infra/desplegar.sh <etiqueta>`
> (el token de GHCR queda guardado en el Container App, así que ya no hace falta repetirlo).

## 8. Costos esperados

| Recurso | Con la configuración de esta guía |
| --- | --- |
| Azure SQL Database serverless (oferta gratuita) | Sin costo dentro del límite mensual gratuito (100 000 segundos de vCore y 32 GB de datos). Al agotarlo, la base se **autopausa** hasta el mes siguiente (no cobra). Se pausa sola tras 60 minutos sin uso y se reanuda en ~1 minuto al primer acceso |
| Container Apps (plan Consumo, 0.5 vCPU / 1 GiB) | Con `minReplicas=0` solo se paga mientras hay réplica activa, dentro de la cuota gratuita mensual (180 000 vCPU-segundos, 360 000 GiB-segundos y 2 millones de solicitudes). Con `minReplicas=1` el consumo del mes entero supera esa cuota y se cobra (tarifa reducida cuando está inactiva) |
| Log Analytics | Los primeros 5 GB de ingesta al mes son gratuitos; retención de 30 días |
| GHCR | Gratis para repositorios privados dentro de los límites de tu plan de GitHub |

Los precios cambian: confirma con la [calculadora de Azure](https://azure.microsoft.com/pricing/calculator/) y vigila
Cost Management en el portal.

**Arranque en frío.** Con `minReplicas=0` la primera visita tras un rato sin tráfico tarda ~2 minutos (arranca Tomcat) y, si
la base estaba pausada, un minuto más. Para una **evaluación o demostración**, mantén la aplicación encendida y vuelve a
apagarla después:

```bash
az containerapp update -g rg-nexora -n ca-nexora --min-replicas 1    # antes de la evaluación
az containerapp update -g rg-nexora -n ca-nexora --min-replicas 0    # al terminar
```

El máximo es siempre 1 réplica: las sesiones viven en la memoria de Tomcat. La base se despierta con la primera consulta,
así que abre la tienda unos minutos antes de presentar.

## 9. Ver los registros

```bash
az containerapp logs show -g rg-nexora -n ca-nexora --type console --tail 100          # aplicación y access log
az containerapp logs show -g rg-nexora -n ca-nexora --type console --follow            # en vivo (Ctrl+C para salir)
az containerapp logs show -g rg-nexora -n ca-nexora --type system --tail 50            # arranque, sondas, reinicios
```

Si la aplicación está escalada a cero no hay réplica de la cual leer: usa Log Analytics en el portal (Container App >
Monitoring > Logs, tabla `ContainerAppConsoleLogs_CL`) o abre la URL para despertarla. Comprobaciones rápidas:
`curl https://<url>/salud` (estado, versión y entorno) y `curl "https://<url>/salud?bd=1"` (además hace `SELECT 1` en la base).

## 10. Rotar claves

Las claves actuales están en `infra/.secretos.local`. Carga los valores y abre una regla temporal de firewall cuando necesites
entrar a la base:

```bash
source infra/.secretos.local
IP=$(curl -fsS https://api.ipify.org)
az sql server firewall-rule create -g "$GRUPO" -s "$SQL_SERVIDOR" -n rotacion --start-ip-address "$IP" --end-ip-address "$IP"
# ... los pasos de abajo ...
az sql server firewall-rule delete -g "$GRUPO" -s "$SQL_SERVIDOR" -n rotacion
```

**Clave de `nexora_app`** (la que usa la aplicación):

```bash
NUEVA="Nx-$(openssl rand -hex 12)-$(openssl rand -hex 3 | tr a-f A-F)"
SQLCMDPASSWORD="$SQL_ADMIN_CLAVE" sqlcmd -S "$SQL_FQDN" -d BD_NEXORA -U "$SQL_ADMIN_LOGIN" -N -b \
  -Q "ALTER USER [nexora_app] WITH PASSWORD = N'$NUEVA'"
az containerapp secret set -g "$GRUPO" -n "$APP_NOMBRE" --secrets db-password="$NUEVA"
REV=$(az containerapp revision list -g "$GRUPO" -n "$APP_NOMBRE" --query "[?properties.active].name | [0]" -o tsv | tr -d '\r')
az containerapp revision restart -g "$GRUPO" -n "$APP_NOMBRE" --revision "$REV"      # aplica el secreto nuevo
sed -i "s|^APP_DB_CLAVE=.*|APP_DB_CLAVE=$NUEVA|" infra/.secretos.local
```

**Administrador del servidor SQL:**

```bash
NUEVA="Nx-$(openssl rand -hex 12)-$(openssl rand -hex 3 | tr a-f A-F)"
az sql server update -g "$GRUPO" -n "$SQL_SERVIDOR" --admin-password "$NUEVA"
sed -i "s|^SQL_ADMIN_CLAVE=.*|SQL_ADMIN_CLAVE=$NUEVA|" infra/.secretos.local
```

**Claves de `admin` y `almacen` de la tienda** (después de `mvn -q package`):

```bash
NUEVA="Nx-$(openssl rand -hex 8)-$(openssl rand -hex 3 | tr a-f A-F)"
HASH=$(java -cp target/classes pe.edu.sistema.util.Seguridad "$NUEVA" | tail -n 1)
SQLCMDPASSWORD="$SQL_ADMIN_CLAVE" sqlcmd -S "$SQL_FQDN" -d BD_NEXORA -U "$SQL_ADMIN_LOGIN" -N -b \
  -Q "UPDATE dbo.usuario SET clave = N'$HASH' WHERE username = N'admin'"
echo "Nueva clave de admin (guárdala): $NUEVA"
```

**Token de GHCR:** `GHCR_USUARIO=<usuario> GHCR_TOKEN=<token nuevo> bash infra/desplegar.sh <etiqueta>`. Revoca el token anterior en GitHub.

## 11. Eliminar todo

```bash
az group delete -n rg-nexora --yes --no-wait        # borra el Container App, el servidor SQL, los registros: todo
rm -f infra/.secretos.local
```

Si configuraste OIDC: `az ad app delete --id <APP_ID>` y borra las variables `AZURE_*` del repositorio. Si ya no los necesitas,
elimina también las imágenes (GitHub > Packages > nexora) y el token de GHCR. Verifica en el portal (Grupos de recursos) que
`rg-nexora` desapareció y no queda ningún recurso cobrando.

## 12. Problemas frecuentes

| Síntoma | Causa probable y solución |
| --- | --- |
| La URL muestra una página de bienvenida de Azure | Aún no hiciste el paso 6 (imagen de arranque) |
| `/salud` no responde tras desplegar | Tomcat tarda ~2 minutos; mira `az containerapp logs show --type system`. La imagen debe ser de la misma versión que `NEXORA_VERSION` |
| Error 503 en la tienda | Producción nunca usa el respaldo en memoria: la base no responde. Revisa `/salud?bd=1`, que `inicializar-bd.sh` terminó bien y que la clave de `nexora_app` coincide con el secreto `db-password` |
| `ImagePullBackOff` / no baja la imagen | El paquete de GHCR es privado y falta el token: repite el paso 6 con `GHCR_USUARIO` y `GHCR_TOKEN` |
| `sqlcmd` falla con error 40613 o 40615 | La base está pausada (reintenta) o tu IP no está permitida (el script abre la regla; espera unos segundos) |
| `Seguridad no devolvió un hash válido` | Falta compilar con Maven (`mvn -q package`) |
| `az deployment` falla con `RequestDisallowedByPolicy` | Una política del tenant universitario bloquea el recurso (por ejemplo, exigir solo autenticación Microsoft Entra en Azure SQL). Pide a la administración una excepción o usa otra suscripción |
| `az deployment` falla por la oferta gratuita | La suscripción ya la usó: `USAR_OFERTA_GRATUITA=false bash infra/crear-infra.sh` |
| El job `desplegar` no aparece en Actions | Falta la variable `AZURE_CLIENT_ID` (paso 7) o el push no fue a `main` |
| El job `desplegar` falla por el puerto | El Container App sigue con la imagen de arranque: ejecuta `infra/desplegar.sh` una vez |

## Variables de entorno de la aplicación

| Variable | Valores | Efecto |
| --- | --- | --- |
| `NEXORA_ENTORNO` | `produccion` (imagen) / `desarrollo` | En producción nunca se usa el respaldo en memoria: sin SQL la tienda responde 503 |
| `DB_URL` | cadena JDBC | Obligatoria en producción |
| `DB_USER` | `nexora_app` | Obligatoria en producción |
| `DB_PASSWORD` | secreto | Obligatoria en producción; en Azure vive en el secreto `db-password` del Container App |
| `NEXORA_DEMO` | `true` / `false` | Cinta "Entorno de demostración" y cuadro de cuentas demo en el inicio de sesión |
| `NEXORA_VERSION` | sha corto (defecto `dev`) | Versión que informa `/salud` y se usa en `?v=` de CSS y JS |
| `NEXORA_COOKIE_SEGURA` | `true` / `false` (defecto: `true` en producción) | Atributo Secure de la cookie de sesión; `false` solo para probar el contenedor por `http://localhost` |
