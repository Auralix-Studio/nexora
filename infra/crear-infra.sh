#!/usr/bin/env bash
# Crea o actualiza la infraestructura de NEXORA en Azure con infra/main.bicep.
#
# Uso: bash infra/crear-infra.sh [-g grupo] [-l región] [-i imagen] [-p prefijo] [-m minReplicas] [-v versión]
#   -g  grupo de recursos            (defecto: rg-nexora)
#   -l  región de Azure              (defecto: eastus2; si el grupo ya existe se usa la suya)
#   -i  imagen del contenedor        (defecto: la actual del Container App o una imagen de arranque rápido)
#   -p  prefijo de los nombres       (defecto: nexora)
#   -m  réplicas mínimas 0 o 1       (defecto: la actual del Container App o 0; también MIN_REPLICAS)
#   -v  versión que informa /salud   (defecto: la etiqueta de la imagen)
# Variables opcionales: GHCR_USUARIO y GHCR_TOKEN (token con read:packages) para descargar de un GHCR privado;
# USAR_OFERTA_GRATUITA=false si la suscripción ya usó la oferta gratuita de Azure SQL en otra base;
# MODO_DEMO=false para quitar la cinta y las cuentas demo (NEXORA_DEMO).
#
# Es idempotente: las claves se generan una sola vez y se guardan en infra/.secretos.local (ignorado por Git);
# al repetir el script se reutilizan. Antes de crear nada muestra el what-if y pide confirmación.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ"

ARCHIVO_SECRETOS="infra/.secretos.local"
ARCHIVO_PARAMETROS="infra/main.parameters.local.json"
IMAGEN_INICIAL="mcr.microsoft.com/k8se/quickstart:latest"
NOMBRE_DESPLIEGUE="nexora"

info() { printf '%s\n' "$*"; }
fallar() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
# Ejecuta az y devuelve texto plano sin CR (Azure CLI en Windows) y sin el "None" de los valores nulos.
azv() { az "$@" -o tsv 2>/dev/null | tr -d '\r' | sed 's/^None$//'; }
# Clave fuerte con mayúsculas, minúsculas, dígitos y guion (cumple la política de Azure SQL; sin caracteres problemáticos).
generar_clave() { printf 'Nx-%s-%s' "$(openssl rand -hex "${1:-10}")" "$(openssl rand -hex 3 | tr 'a-f' 'A-F')"; }
escapar_json() { printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'; }

# El archivo de parámetros contiene claves: se borra siempre al terminar.
trap 'rm -f "$ARCHIVO_PARAMETROS"' EXIT
trap 'exit 130' INT TERM

# ---------------------------------------------------------------- Argumentos
GRUPO_ARG=""; UBICACION_ARG=""; IMAGEN_ARG=""; PREFIJO_ARG=""; MIN_ARG="${MIN_REPLICAS:-}"; VERSION_ARG=""
while getopts ":g:l:i:p:m:v:h" opcion; do
  case "$opcion" in
    g) GRUPO_ARG="$OPTARG" ;;
    l) UBICACION_ARG="$OPTARG" ;;
    i) IMAGEN_ARG="$OPTARG" ;;
    p) PREFIJO_ARG="$OPTARG" ;;
    m) MIN_ARG="$OPTARG" ;;
    v) VERSION_ARG="$OPTARG" ;;
    h) sed -n '2,16p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    \?) fallar "Opción desconocida: -$OPTARG (usa -h para ver la ayuda)" ;;
    :) fallar "Falta el valor de -$OPTARG" ;;
  esac
done
[[ -z "$MIN_ARG" || "$MIN_ARG" == "0" || "$MIN_ARG" == "1" ]] || fallar "minReplicas debe ser 0 o 1 (el máximo es 1 porque las sesiones viven en memoria)."

# ---------------------------------------------------------------- Requisitos
command -v az >/dev/null 2>&1 || fallar "No se encontró Azure CLI (az). Instálalo desde https://aka.ms/installazurecliwindows"
command -v openssl >/dev/null 2>&1 || fallar "No se encontró openssl (viene con Git Bash)."
az account show >/dev/null 2>&1 || fallar "No hay sesión de Azure. Ejecuta primero: az login"
info "Suscripción activa: $(azv account show --query name)"

# ---------------------------------------------------------------- Valores guardados y claves
OFERTA_ENV="${USAR_OFERTA_GRATUITA:-}"; DEMO_ENV="${MODO_DEMO:-}"
GRUPO=""; UBICACION=""; PREFIJO=""; SQL_ADMIN_LOGIN=""; SQL_ADMIN_CLAVE=""; APP_DB_CLAVE=""
SQL_SERVIDOR=""; SQL_FQDN=""; APP_NOMBRE=""; APP_URL=""; USAR_OFERTA_GRATUITA=""; MODO_DEMO=""
if [[ -f "$ARCHIVO_SECRETOS" ]]; then
  # shellcheck disable=SC1090
  source "$ARCHIVO_SECRETOS"
fi
if [[ -n "$GRUPO_ARG" && -n "$GRUPO" && "$GRUPO_ARG" != "$GRUPO" ]]; then
  fallar "$ARCHIVO_SECRETOS pertenece al grupo '$GRUPO'. Usa -g $GRUPO o bórralo si quieres empezar de cero."
fi
GRUPO="${GRUPO_ARG:-${GRUPO:-rg-nexora}}"
UBICACION="${UBICACION_ARG:-${UBICACION:-eastus2}}"
PREFIJO="${PREFIJO_ARG:-${PREFIJO:-nexora}}"
USAR_OFERTA_GRATUITA="${OFERTA_ENV:-${USAR_OFERTA_GRATUITA:-true}}"
MODO_DEMO="${DEMO_ENV:-${MODO_DEMO:-true}}"
[[ "$USAR_OFERTA_GRATUITA" == "true" || "$USAR_OFERTA_GRATUITA" == "false" ]] || fallar "USAR_OFERTA_GRATUITA debe ser true o false."
[[ "$MODO_DEMO" == "true" || "$MODO_DEMO" == "false" ]] || fallar "MODO_DEMO debe ser true o false."
SQL_ADMIN_LOGIN="${SQL_ADMIN_LOGIN:-nexoraadmin}"
[[ -n "$SQL_ADMIN_CLAVE" ]] || SQL_ADMIN_CLAVE="$(generar_clave 12)"
[[ -n "$APP_DB_CLAVE" ]] || APP_DB_CLAVE="$(generar_clave 12)"
APP="ca-${PREFIJO}"

guardar_secretos() {
  ( umask 077
    cat > "$ARCHIVO_SECRETOS" <<EOF
# Generado por infra/crear-infra.sh. Contiene claves: NO lo subas a Git (está en .gitignore) ni lo compartas.
GRUPO=$GRUPO
UBICACION=$UBICACION
PREFIJO=$PREFIJO
SQL_ADMIN_LOGIN=$SQL_ADMIN_LOGIN
SQL_ADMIN_CLAVE=$SQL_ADMIN_CLAVE
APP_DB_CLAVE=$APP_DB_CLAVE
SQL_SERVIDOR=$SQL_SERVIDOR
SQL_FQDN=$SQL_FQDN
APP_NOMBRE=$APP_NOMBRE
APP_URL=$APP_URL
USAR_OFERTA_GRATUITA=$USAR_OFERTA_GRATUITA
MODO_DEMO=$MODO_DEMO
EOF
  )
}
# Se guardan antes de desplegar: si algo falla a medias, las claves con las que se creó el servidor no se pierden.
guardar_secretos

# ---------------------------------------------------------------- Grupo de recursos y proveedores
if az group show -n "$GRUPO" >/dev/null 2>&1; then
  ACTUAL="$(azv group show -n "$GRUPO" --query location)"
  if [[ "$ACTUAL" != "$UBICACION" ]]; then
    info "El grupo $GRUPO ya existe en la región $ACTUAL: se usará esa región."
    UBICACION="$ACTUAL"
  fi
else
  info "Creando el grupo de recursos $GRUPO en $UBICACION (no tiene costo)..."
  az group create -n "$GRUPO" -l "$UBICACION" --only-show-errors -o none
fi
guardar_secretos

for proveedor in Microsoft.App Microsoft.OperationalInsights Microsoft.Sql; do
  if [[ "$(azv provider show -n "$proveedor" --query registrationState)" != "Registered" ]]; then
    info "Registrando el proveedor $proveedor (puede tardar un par de minutos)..."
    az provider register -n "$proveedor" --wait --only-show-errors -o none
  fi
done
az extension show -n containerapp >/dev/null 2>&1 || az extension add -n containerapp --only-show-errors

# ---------------------------------------------------------------- Imagen, versión y réplicas (conserva lo ya desplegado)
IMAGEN="$IMAGEN_ARG"; VERSION_ACTUAL=""; MIN_ACTUAL=""
if az containerapp show -g "$GRUPO" -n "$APP" >/dev/null 2>&1; then
  if [[ -z "$IMAGEN" ]]; then
    IMAGEN="$(azv containerapp show -g "$GRUPO" -n "$APP" --query "properties.template.containers[0].image")"
    if [[ -n "$IMAGEN" ]]; then info "El Container App ya existe: se conserva su imagen actual ($IMAGEN)."; fi
  fi
  VERSION_ACTUAL="$(azv containerapp show -g "$GRUPO" -n "$APP" --query "properties.template.containers[0].env[?name=='NEXORA_VERSION'].value | [0]")"
  MIN_ACTUAL="$(azv containerapp show -g "$GRUPO" -n "$APP" --query "properties.template.scale.minReplicas")"
  # Sin token nuevo, se reutilizan las credenciales del registro ya configuradas (si las hay).
  if [[ -z "${GHCR_TOKEN:-}" ]]; then
    USUARIO_REG="$(azv containerapp show -g "$GRUPO" -n "$APP" --query "properties.configuration.registries[0].username")"
    if [[ -n "$USUARIO_REG" ]]; then
      GHCR_USUARIO="$USUARIO_REG"
      GHCR_TOKEN="$(azv containerapp secret show -g "$GRUPO" -n "$APP" --secret-name registry-password --query value)"
      if [[ -z "$GHCR_TOKEN" ]]; then
        info "AVISO: no pude leer el secreto del registro; pásalo con GHCR_USUARIO y GHCR_TOKEN."
      fi
    fi
  fi
fi
IMAGEN="${IMAGEN:-$IMAGEN_INICIAL}"
MINIMO="${MIN_ARG:-${MIN_ACTUAL:-0}}"
if [[ -n "$VERSION_ARG" ]]; then
  VERSION="$VERSION_ARG"
elif [[ "$IMAGEN" != "$IMAGEN_INICIAL" && "${IMAGEN##*/}" == *:* ]]; then
  VERSION="${IMAGEN##*:}"
elif [[ -n "$VERSION_ACTUAL" ]]; then
  VERSION="$VERSION_ACTUAL"
else
  VERSION="dev"
fi

REGISTRO_SERVIDOR=""; REGISTRO_USUARIO=""; REGISTRO_CLAVE=""
if [[ -n "${GHCR_TOKEN:-}" ]]; then
  [[ -n "${GHCR_USUARIO:-}" ]] || fallar "Si defines GHCR_TOKEN, define también GHCR_USUARIO (tu usuario de GitHub)."
  REGISTRO_SERVIDOR="ghcr.io"; REGISTRO_USUARIO="$GHCR_USUARIO"; REGISTRO_CLAVE="$GHCR_TOKEN"
elif [[ "$IMAGEN" == ghcr.io/* ]]; then
  info "AVISO: la imagen es de GHCR y no hay credenciales. Si el paquete es privado, define GHCR_USUARIO y GHCR_TOKEN."
fi

# ---------------------------------------------------------------- Archivo de parámetros (con claves; se borra al salir)
( umask 077
  cat > "$ARCHIVO_PARAMETROS" <<EOF
{
  "\$schema": "https://schema.management.azure.com/schemas/2019-04-01/deploymentParameters.json#",
  "contentVersion": "1.0.0.0",
  "parameters": {
    "location": { "value": "$(escapar_json "$UBICACION")" },
    "prefijo": { "value": "$(escapar_json "$PREFIJO")" },
    "imagen": { "value": "$(escapar_json "$IMAGEN")" },
    "registroServidor": { "value": "$(escapar_json "$REGISTRO_SERVIDOR")" },
    "registroUsuario": { "value": "$(escapar_json "$REGISTRO_USUARIO")" },
    "registroClave": { "value": "$(escapar_json "$REGISTRO_CLAVE")" },
    "sqlAdminLogin": { "value": "$(escapar_json "$SQL_ADMIN_LOGIN")" },
    "sqlAdminClave": { "value": "$(escapar_json "$SQL_ADMIN_CLAVE")" },
    "appDbClave": { "value": "$(escapar_json "$APP_DB_CLAVE")" },
    "minReplicas": { "value": $MINIMO },
    "nexoraVersion": { "value": "$(escapar_json "$VERSION")" },
    "modoDemo": { "value": $MODO_DEMO },
    "usarOfertaGratuita": { "value": $USAR_OFERTA_GRATUITA }
  }
}
EOF
)

# ---------------------------------------------------------------- Resumen, what-if y confirmación
info ""
info "=== Resumen ==="
info "  Grupo de recursos : $GRUPO ($UBICACION)"
info "  Container App     : $APP  (imagen $IMAGEN, versión $VERSION, réplicas $MINIMO..1)"
if [[ "$USAR_OFERTA_GRATUITA" == "true" ]]; then
  info "  Base de datos     : Azure SQL serverless BD_NEXORA con la oferta gratuita (se autopausa al agotar el límite mensual)"
else
  info "  Base de datos     : Azure SQL serverless BD_NEXORA SIN oferta gratuita (se cobra el consumo de vCore y almacenamiento)"
fi
info "  Costo esperado    : cercano a cero con la cuota gratuita (SQL, Container Apps y 5 GB de registros al mes);"
info "                      con minReplicas=1 el Container App consume horas de vCPU y memoria todo el mes."
info ""
info "Calculando los cambios (what-if)..."
az deployment group what-if -g "$GRUPO" -n "$NOMBRE_DESPLIEGUE" -f infra/main.bicep -p "@$ARCHIVO_PARAMETROS" --only-show-errors
info ""
respuesta=""
read -r -p "¿Crear o actualizar estos recursos en Azure? Escribe SI para continuar: " respuesta || respuesta=""
respuesta="${respuesta,,}"
[[ "$respuesta" == "si" || "$respuesta" == "sí" ]] || fallar "Cancelado: no se creó nada."

info "Desplegando (el servidor SQL y el entorno tardan unos minutos)..."
az deployment group create -g "$GRUPO" -n "$NOMBRE_DESPLIEGUE" -f infra/main.bicep -p "@$ARCHIVO_PARAMETROS" --only-show-errors -o none

# ---------------------------------------------------------------- Salidas
salida() { azv deployment group show -g "$GRUPO" -n "$NOMBRE_DESPLIEGUE" --query "properties.outputs.$1.value"; }
SQL_SERVIDOR="$(salida nombreServidorSql)"
SQL_FQDN="$(salida fqdnServidorSql)"
APP_NOMBRE="$(salida nombreContainerApp)"
APP_URL="$(salida urlApp)"
guardar_secretos

info ""
info "=== Listo ==="
info "  URL de la tienda  : $APP_URL"
info "  Servidor SQL      : $SQL_FQDN (base BD_NEXORA)"
info "  Container App     : $APP_NOMBRE  |  Grupo: $GRUPO"
info "  Claves guardadas en $ARCHIVO_SECRETOS (ignorado por Git; no se imprimen)."
info ""
if [[ "$IMAGEN" == "$IMAGEN_INICIAL" ]]; then
  info "Siguientes pasos:"
  info "  1. bash infra/inicializar-bd.sh      (crea tablas, datos semilla y el usuario nexora_app)"
  info "  2. Sube el código a main para que CI publique la imagen en GHCR."
  info "  3. GHCR_USUARIO=<usuario> GHCR_TOKEN=<token> bash infra/desplegar.sh <etiqueta>"
  info "La URL mostrará por ahora la página de arranque rápido de Azure, no la tienda."
else
  info "Siguiente paso: bash infra/esperar-salud.sh $APP_URL 420"
fi
