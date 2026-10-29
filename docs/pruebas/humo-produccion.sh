#!/usr/bin/env bash
# Prueba de humo de producción: solo lectura (no compra ni crea datos).
# Comprueba salud, base de datos, cabeceras de seguridad, cookies, páginas públicas, ingreso demo y permisos.
# Uso: bash docs/pruebas/humo-produccion.sh https://<app>.azurecontainerapps.io
B="${1:?Indica la URL base, p. ej. https://nexora.example.azurecontainerapps.io}"
B="${B%/}"
D="$(mktemp -d)"
ok=0; fail=0
check() { if eval "$2"; then ok=$((ok+1)); echo "OK   $1"; else fail=$((fail+1)); echo "FAIL $1"; fi; }
get() { code=$(curl -s -b "$D/$1" -c "$D/$1" -D "$D/head" -o "$D/body" -w '%{http_code}|%{redirect_url}' "$B$2"); loc=${code#*|}; code=${code%%|*}; }
has() { grep -q "$1" "$D/body"; }
cab() { grep -qi "^$1:" "$D/head"; }
csrf() { grep -o 'name="_csrf" value="[^"]*"' "$D/body" | head -1 | sed 's/.*value="//;s/"//'; }

get anon /salud;          check "salud responde 200 con JSON" '[ "$code" = 200 ] && has "\"estado\":\"ok\""'
check "salud no crea sesión" '! grep -qi "^set-cookie:" "$D/head"'
get anon "/salud?bd=1";   check "base de datos conectada" '[ "$code" = 200 ] && has "\"bd\":\"ok\""'
get anon /;               check "portada pública 200" '[ "$code" = 200 ] && has "Comprar ahora"'
check "CSP estricta" 'grep -qi "^content-security-policy:.*script-src .self." "$D/head"'
check "nosniff" 'cab "x-content-type-options"'
check "X-Frame-Options" 'cab "x-frame-options"'
check "Referrer-Policy" 'cab "referrer-policy"'
check "Permissions-Policy" 'cab "permissions-policy"'
if [[ "$B" == https://* ]]; then
  check "HSTS en HTTPS" 'cab "strict-transport-security"'
  check "cookie de sesión Secure, HttpOnly y SameSite" 'grep -i "^set-cookie: JSESSIONID" "$D/head" | grep -qi "secure" && grep -i "^set-cookie: JSESSIONID" "$D/head" | grep -qi "httponly" && grep -i "^set-cookie: JSESSIONID" "$D/head" | grep -qi "samesite"'
  H=$(curl -s -o /dev/null -w '%{http_code}|%{redirect_url}' "http://${B#https://}/")
  check "HTTP redirige o se rechaza" '[[ "$H" == 30* ]] || [[ "$H" == 4* ]]'
fi
check "sin versión del servidor expuesta" '! grep -qiE "^server: .*tomcat|^x-powered-by" "$D/head"'
check "cinta de demostración visible" 'has "Entorno de demostración"'
get anon "/css/estilos.css"; check "CSS con caché larga" '[ "$code" = 200 ] && grep -qi "^cache-control:.*max-age" "$D/head"'
get anon /no-existe;      check "404 con página propia" '[ "$code" = 404 ] && has "no encontrada"'
check "404 sin traza ni versión" '! has "Apache Tomcat" && ! has "Exception"'
get anon /login;          check "login con cuentas demo" '[ "$code" = 200 ] && has "Cuentas de demostración"'
get cli /login; T=$(csrf)
code=$(curl -s -b "$D/cli" -c "$D/cli" -o "$D/body" -w '%{http_code}|%{redirect_url}' --data-urlencode "_csrf=$T" --data-urlencode "usuario=cliente" --data-urlencode "clave=cliente123" "$B/login"); loc=${code#*|}; code=${code%%|*}
check "ingreso con cuenta demo cliente" '[ "$code" = 302 ]'
get cli "/pago?codigo=P002&cantidad=1"; check "checkout carga para el cliente" '[ "$code" = 200 ] && has "tokenCheckout"'
get cli "/productos?accion=pedidos";    check "historial del cliente carga" '[ "$code" = 200 ]'
get cli /admin;                          check "cliente no entra al admin" '[ "$code" = 302 ]'
get anon "/css/..;/productos?accion=pedidos"; check "rutas manipuladas rechazadas" '[ "$code" != 200 ]'
echo "---- $ok OK, $fail FAIL"
