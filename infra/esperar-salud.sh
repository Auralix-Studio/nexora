#!/usr/bin/env bash
# Espera a que GET <url>/salud responda 200 (y, si se indica, que informe la versión esperada).
# Uso: bash infra/esperar-salud.sh <url-base> [segundos-máximo=300] [versión-esperada]
# Ejemplo: bash infra/esperar-salud.sh https://mi-app.azurecontainerapps.io 300 abc1234
# Lo usan infra/desplegar.sh y el flujo de CI.
set -euo pipefail

URL="${1:?Uso: bash infra/esperar-salud.sh <url-base> [segundos-máximo] [versión-esperada]}"
URL="${URL%/}"
ESPERA="${2:-300}"
VERSION="${3:-}"
INTERVALO=5

inicio=$(date +%s)
codigo="000"
echo "Esperando a que ${URL}/salud responda 200${VERSION:+ con la versión ${VERSION}} (máximo ${ESPERA} s)..."
while true; do
  # -w agrega el código HTTP en una línea aparte; si curl no conecta, imprime 000.
  respuesta="$(curl -s -m 10 -w $'\n%{http_code}' "${URL}/salud" 2>/dev/null || true)"
  codigo="${respuesta##*$'\n'}"
  cuerpo="${respuesta%$'\n'*}"
  if [[ "$codigo" == "200" ]]; then
    if [[ -z "$VERSION" || "$cuerpo" == *"\"version\":\"${VERSION}\""* || "$cuerpo" == *"\"version\": \"${VERSION}\""* ]]; then
      echo "Listo: /salud respondió 200 -> ${cuerpo}"
      exit 0
    fi
  fi
  transcurrido=$(( $(date +%s) - inicio ))
  if (( transcurrido >= ESPERA )); then
    echo "Tiempo agotado tras ${transcurrido} s: último código HTTP ${codigo}." >&2
    [[ -n "${cuerpo:-}" ]] && echo "Última respuesta: ${cuerpo}" >&2
    exit 1
  fi
  echo "  ... aún no (código ${codigo}, ${transcurrido} s)"
  sleep "$INTERVALO"
done
