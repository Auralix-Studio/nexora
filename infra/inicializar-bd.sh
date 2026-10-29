#!/usr/bin/env bash
# Inicializa BD_NEXORA en Azure SQL: esquema y datos semilla, usuario contenido nexora_app y claves privadas
# para las cuentas admin y almacen (se muestran UNA sola vez al final).
#
# Uso: bash infra/inicializar-bd.sh [--forzar]
#   --forzar  no pide confirmación si la base ya tiene tablas (el script de la BD las borra y las recrea).
# Requisitos: Azure CLI con sesión, sqlcmd, java, curl y haber compilado con Maven (target/classes),
# porque los hashes de las claves los calcula pe.edu.sistema.util.Seguridad.
# Lee infra/.secretos.local (lo crea infra/crear-infra.sh).
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ"

ARCHIVO_SECRETOS="infra/.secretos.local"
SQL_ORIGEN="sql/bd_nexora.sql"
BD="BD_NEXORA"
REGLA="nexora-inicializacion-temporal"
TMP_ESQUEMA="infra/.tmp-esquema-$$.sql"
TMP_USUARIOS="infra/.tmp-usuarios-$$.sql"

info() { printf '%s\n' "$*"; }
fallar() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
azv() { az "$@" -o tsv 2>/dev/null | tr -d '\r' | sed 's/^None$//'; }
generar_clave() { printf 'Nx-%s-%s' "$(openssl rand -hex "${1:-10}")" "$(openssl rand -hex 3 | tr 'a-f' 'A-F')"; }
escapar_sql() { printf '%s' "$1" | sed "s/'/''/g"; }

FORZAR=0
for argumento in "$@"; do
  case "$argumento" in
    --forzar) FORZAR=1 ;;
    -h|--help) sed -n '2,9p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) fallar "Argumento desconocido: $argumento" ;;
  esac
done

# ---------------------------------------------------------------- Limpieza (siempre): temporales y regla de firewall
REGLA_CREADA=0
limpiar() {
  local codigo=$?
  rm -f "$TMP_ESQUEMA" "$TMP_USUARIOS"
  if [[ "$REGLA_CREADA" == 1 ]]; then
    info "Quitando la regla de firewall temporal ($REGLA)..."
    if ! az sql server firewall-rule delete -g "$GRUPO" -s "$SQL_SERVIDOR" -n "$REGLA" --only-show-errors </dev/null >/dev/null 2>&1 \
       && ! az sql server firewall-rule delete -g "$GRUPO" -s "$SQL_SERVIDOR" -n "$REGLA" --yes --only-show-errors </dev/null >/dev/null 2>&1; then
      printf 'AVISO: no pude borrar la regla %s del servidor %s; bórrala desde el portal de Azure.\n' "$REGLA" "$SQL_SERVIDOR" >&2
    fi
  fi
  exit "$codigo"
}
trap limpiar EXIT
trap 'exit 130' INT TERM

# ---------------------------------------------------------------- Requisitos
for herramienta in az sqlcmd java curl openssl; do
  command -v "$herramienta" >/dev/null 2>&1 || fallar "No se encontró '$herramienta' en el PATH."
done
[[ -f "$ARCHIVO_SECRETOS" ]] || fallar "No existe $ARCHIVO_SECRETOS. Ejecuta primero: bash infra/crear-infra.sh"
[[ -f "$SQL_ORIGEN" ]] || fallar "No se encontró $SQL_ORIGEN (ejecuta el script desde el repositorio de NEXORA)."
[[ -f target/classes/pe/edu/sistema/util/Seguridad.class ]] \
  || fallar "Falta target/classes. Compila antes con Maven (mvn -q package) para calcular los hashes de las claves."
az account show >/dev/null 2>&1 || fallar "No hay sesión de Azure. Ejecuta primero: az login"

GRUPO=""; SQL_ADMIN_LOGIN=""; SQL_ADMIN_CLAVE=""; APP_DB_CLAVE=""; SQL_SERVIDOR=""; SQL_FQDN=""
# shellcheck disable=SC1090
source "$ARCHIVO_SECRETOS"
[[ -n "$GRUPO" && -n "$SQL_ADMIN_LOGIN" && -n "$SQL_ADMIN_CLAVE" && -n "$APP_DB_CLAVE" ]] \
  || fallar "$ARCHIVO_SECRETOS está incompleto. Vuelve a ejecutar infra/crear-infra.sh."
if [[ -z "$SQL_SERVIDOR" || -z "$SQL_FQDN" ]]; then
  SQL_SERVIDOR="$(azv deployment group show -g "$GRUPO" -n nexora --query properties.outputs.nombreServidorSql.value)"
  SQL_FQDN="$(azv deployment group show -g "$GRUPO" -n nexora --query properties.outputs.fqdnServidorSql.value)"
fi
[[ -n "$SQL_SERVIDOR" && -n "$SQL_FQDN" ]] || fallar "No pude determinar el servidor SQL. ¿Terminó bien infra/crear-infra.sh?"

grep -q '^-- 2\. ELIMINACI' "$SQL_ORIGEN" || fallar "No encontré la sección '-- 2. ELIMINACI...' en $SQL_ORIGEN."

# La clave del administrador viaja por variable de entorno (no aparece en la lista de procesos).
export SQLCMDPASSWORD="$SQL_ADMIN_CLAVE"
# -N cifra la conexión, -b aborta ante errores, -I activa los identificadores entrecomillados, -f 65001 lee UTF-8.
sql_admin() { sqlcmd -S "$SQL_FQDN" -d "$BD" -U "$SQL_ADMIN_LOGIN" -l 30 -N -b -I -f 65001 "$@"; }

# ---------------------------------------------------------------- Claves privadas de admin y almacen
# Se calculan antes de tocar la base: si Seguridad falla, no se ha borrado nada.
hash_clave() {
  local salida
  # La clave entra por la entrada estándar (printf es interno de bash): no aparece en la lista de procesos.
  salida="$(printf '%s' "$1" | java -cp target/classes pe.edu.sistema.util.Seguridad 2>/dev/null | tr -d '\r' | tail -n 1 || true)"
  [[ "$salida" == pbkdf2_sha256\$* ]] \
    || fallar "Seguridad no devolvió un hash válido. ¿Recompilaste con Maven y existe Seguridad.main(String[])?"
  printf '%s' "$salida"
}
CLAVE_ADMIN="$(generar_clave 8)"
CLAVE_ALMACEN="$(generar_clave 8)"
HASH_ADMIN="$(hash_clave "$CLAVE_ADMIN")"
HASH_ALMACEN="$(hash_clave "$CLAVE_ALMACEN")"

# ---------------------------------------------------------------- Regla de firewall temporal con la IP de este equipo
IP="$(curl -fsS -m 10 https://api.ipify.org 2>/dev/null || curl -fsS -m 10 https://checkip.amazonaws.com 2>/dev/null || true)"
IP="$(printf '%s' "$IP" | tr -d '[:space:]')"
[[ "$IP" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] || fallar "No pude detectar la IP pública de este equipo (¿sin Internet?)."
info "Abriendo el firewall de $SQL_SERVIDOR solo para $IP (regla temporal $REGLA)..."
az sql server firewall-rule create -g "$GRUPO" -s "$SQL_SERVIDOR" -n "$REGLA" \
  --start-ip-address "$IP" --end-ip-address "$IP" --only-show-errors -o none
REGLA_CREADA=1

# La regla tarda unos segundos en aplicarse y la base serverless puede estar pausada (se reanuda al primer acceso).
conectado=0
for intento in $(seq 1 18); do
  if sql_admin -Q "SELECT 1" >/dev/null 2>&1; then conectado=1; break; fi
  info "  ... esperando la conexión (regla de firewall o reanudación de la base), intento $intento/18"
  sleep 10
done
if [[ "$conectado" != 1 ]]; then
  info "No se pudo conectar. Último error de sqlcmd:"
  sql_admin -Q "SELECT 1" || true
  fallar "Sin conexión a $SQL_FQDN."
fi
info "Conectado a $SQL_FQDN / $BD."

# ---------------------------------------------------------------- Aviso si ya hay datos
EXISTE="$(sql_admin -h -1 -W -Q "SET NOCOUNT ON; SELECT COUNT(*) FROM sys.tables WHERE name = N'usuario';" 2>/dev/null \
          | tr -d '\r' | grep -E '^[0-9]+$' | tail -n 1 || true)"
if [[ "${EXISTE:-0}" != "0" && "$FORZAR" != 1 ]]; then
  info ""
  info "ATENCIÓN: $BD ya tiene tablas. Continuar BORRA y recrea las tablas (usuarios, pedidos, pagos...) con los datos semilla."
  respuesta=""
  read -r -p "Escribe BORRAR para continuar: " respuesta || respuesta=""
  [[ "$respuesta" == "BORRAR" ]] || fallar "Cancelado: no se modificó la base."
fi

# ---------------------------------------------------------------- Esquema y datos semilla (desde la sección 2)
# Azure SQL no admite CREATE DATABASE ni USE: la base ya la creó Bicep y aquí se omite la sección 1.
{ printf 'SET NOCOUNT ON;\nGO\n'; sed -n '/^-- 2\. ELIMINACI/,$p' "$SQL_ORIGEN"; } > "$TMP_ESQUEMA"
info "Ejecutando $SQL_ORIGEN desde la sección 2 (tablas, vistas, procedimientos y datos semilla)..."
sql_admin -i "$TMP_ESQUEMA"

# ---------------------------------------------------------------- Usuario contenido nexora_app y hashes
CLAVE_APP_SQL="$(escapar_sql "$APP_DB_CLAVE")"
( umask 077
  cat > "$TMP_USUARIOS" <<EOF
SET NOCOUNT ON;
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'nexora_app')
    CREATE USER [nexora_app] WITH PASSWORD = N'$CLAVE_APP_SQL';
ELSE
    ALTER USER [nexora_app] WITH PASSWORD = N'$CLAVE_APP_SQL';
GO
ALTER ROLE db_datareader ADD MEMBER [nexora_app];
ALTER ROLE db_datawriter ADD MEMBER [nexora_app];
GO
UPDATE dbo.usuario SET clave = N'$HASH_ADMIN' WHERE username = N'admin';
IF @@ROWCOUNT <> 1 THROW 50001, N'No se encontró la cuenta admin', 1;
UPDATE dbo.usuario SET clave = N'$HASH_ALMACEN' WHERE username = N'almacen';
IF @@ROWCOUNT <> 1 THROW 50001, N'No se encontró la cuenta almacen', 1;
GO
EOF
)
info "Creando el usuario contenido nexora_app (lectura y escritura) y fijando las claves privadas..."
sql_admin -i "$TMP_USUARIOS"

# ---------------------------------------------------------------- Resultado (única vez que se muestran las claves)
info ""
info "============================================================================"
info " BD_NEXORA lista en $SQL_FQDN"
info " Usuario de la aplicación: nexora_app (la clave está en $ARCHIVO_SECRETOS y en el secreto db-password)"
info ""
info " CLAVES PRIVADAS: se muestran UNA sola vez. Guárdalas ahora en un gestor de contraseñas."
info "   admin   : $CLAVE_ADMIN"
info "   almacen : $CLAVE_ALMACEN"
info ""
info " Cuentas demo públicas (sin cambios): cliente / cliente123 e invitado / invitado"
info "============================================================================"
