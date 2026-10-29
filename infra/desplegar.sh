#!/usr/bin/env bash
# Despliega ghcr.io/<propietario>/nexora:<etiqueta> en el Container App y espera a que /salud responda 200.
#
# Uso: bash infra/desplegar.sh [etiqueta] [-g grupo] [-a app] [-o propietario]
#   etiqueta     etiqueta de la imagen (sha corto del commit; defecto: latest). También es NEXORA_VERSION.
#   -g           grupo de recursos (defecto: el de infra/.secretos.local o rg-nexora)
#   -a           nombre del Container App (defecto: el de infra/.secretos.local o ca-nexora)
#   -o           propietario de GitHub en GHCR (defecto: GHCR_PROPIETARIO o el del remoto git "origin")
# Variables opcionales: GHCR_USUARIO y GHCR_TOKEN (token clásico con read:packages). Si se definen, se configura
# el registro ghcr.io en el Container App (necesario cuando el paquete es privado).
#
# La primera vez, si el Container App todavía tiene la imagen de arranque rápido (puerto 80, sin sondas),
# se vuelve a aplicar infra/main.bicep con la imagen real mediante infra/crear-infra.sh.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ"

info() { printf '%s\n' "$*"; }
fallar() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
azv() { az "$@" -o tsv 2>/dev/null | tr -d '\r' | sed 's/^None$//'; }

# ---------------------------------------------------------------- Argumentos
ETIQUETA=""; GRUPO_ARG=""; APP_ARG=""; PROPIETARIO="${GHCR_PROPIETARIO:-}"
while (( $# > 0 )); do
  case "$1" in
    -g) [[ $# -ge 2 ]] || fallar "Falta el valor de -g"; GRUPO_ARG="$2"; shift 2 ;;
    -a) [[ $# -ge 2 ]] || fallar "Falta el valor de -a"; APP_ARG="$2"; shift 2 ;;
    -o) [[ $# -ge 2 ]] || fallar "Falta el valor de -o"; PROPIETARIO="$2"; shift 2 ;;
    -h|--help) sed -n '2,13p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*) fallar "Opción desconocida: $1" ;;
    *) [[ -z "$ETIQUETA" ]] || fallar "Solo se admite una etiqueta."; ETIQUETA="$1"; shift ;;
  esac
done
ETIQUETA="${ETIQUETA:-latest}"

GRUPO=""; APP_NOMBRE=""; PREFIJO=""
if [[ -f infra/.secretos.local ]]; then
  # shellcheck disable=SC1091
  source infra/.secretos.local
fi
GRUPO="${GRUPO_ARG:-${GRUPO:-rg-nexora}}"
APP="${APP_ARG:-${APP_NOMBRE:-ca-nexora}}"

if [[ -z "$PROPIETARIO" ]]; then
  PROPIETARIO="$(git config --get remote.origin.url 2>/dev/null | sed -nE 's#^.*github\.com[:/]+([^/]+)/.*$#\1#p' || true)"
fi
[[ -n "$PROPIETARIO" ]] || fallar "No pude determinar el propietario de GitHub. Usa -o <propietario> o GHCR_PROPIETARIO."
PROPIETARIO="${PROPIETARIO,,}"
IMAGEN="ghcr.io/${PROPIETARIO}/nexora:${ETIQUETA}"

# ---------------------------------------------------------------- Requisitos
command -v az >/dev/null 2>&1 || fallar "No se encontró Azure CLI (az). Instálalo desde https://aka.ms/installazurecliwindows"
command -v curl >/dev/null 2>&1 || fallar "No se encontró curl."
az account show >/dev/null 2>&1 || fallar "No hay sesión de Azure. Ejecuta primero: az login"
az extension show -n containerapp >/dev/null 2>&1 || az extension add -n containerapp --only-show-errors
if [[ -n "${GHCR_TOKEN:-}" && -z "${GHCR_USUARIO:-}" ]]; then
  fallar "Si defines GHCR_TOKEN, define también GHCR_USUARIO (tu usuario de GitHub)."
fi
az containerapp show -g "$GRUPO" -n "$APP" >/dev/null 2>&1 \
  || fallar "No existe el Container App '$APP' en el grupo '$GRUPO'. Ejecuta primero: bash infra/crear-infra.sh"

info "Imagen: $IMAGEN"
info "Destino: Container App $APP (grupo $GRUPO)"

# ---------------------------------------------------------------- Despliegue
PUERTO="$(azv containerapp show -g "$GRUPO" -n "$APP" --query "properties.configuration.ingress.targetPort")"
if [[ "$PUERTO" != "8080" ]]; then
  info "El Container App aún usa la configuración de arranque (puerto $PUERTO): se reaplica infra/main.bicep con la imagen real."
  PREFIJO_APP="${APP#ca-}"
  bash infra/crear-infra.sh -g "$GRUPO" -p "$PREFIJO_APP" -i "$IMAGEN" -v "$ETIQUETA"
else
  if [[ -n "${GHCR_TOKEN:-}" ]]; then
    info "Configurando el registro ghcr.io (usuario ${GHCR_USUARIO})..."
    az containerapp registry set -g "$GRUPO" -n "$APP" --server ghcr.io \
      --username "$GHCR_USUARIO" --password "$GHCR_TOKEN" --only-show-errors -o none
  else
    info "Sin GHCR_TOKEN: se usa el registro ya configurado (o ninguno si el paquete es público)."
  fi
  info "Actualizando la imagen y NEXORA_VERSION=$ETIQUETA..."
  az containerapp update -g "$GRUPO" -n "$APP" --image "$IMAGEN" \
    --set-env-vars "NEXORA_VERSION=$ETIQUETA" --only-show-errors -o none
fi

# ---------------------------------------------------------------- Espera de /salud
FQDN="$(azv containerapp show -g "$GRUPO" -n "$APP" --query "properties.configuration.ingress.fqdn")"
[[ -n "$FQDN" ]] || fallar "No pude obtener la dirección del Container App."
bash infra/esperar-salud.sh "https://${FQDN}" 420 "$ETIQUETA"
info "Despliegue terminado: https://${FQDN}"
