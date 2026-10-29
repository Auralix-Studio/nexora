#!/usr/bin/env bash
# Prueba de punta a punta del flujo de compra con pasarela simulada (32 comprobaciones).
# Uso: bash docs/pruebas/e2e.sh [URL base]   (por defecto http://localhost:8090/Semana04)
# Los valores esperados (B001-000003, F001-000001, stock P001 = 8) suponen datos recién cargados:
# Tomcat recién iniciado en modo memoria o BD_NEXORA recién creada con sql/bd_nexora.sql.
B="${1:-http://localhost:8090/Semana04}"
D="$(mktemp -d)"
ok=0; fail=0
check() { if eval "$2"; then ok=$((ok+1)); echo "OK   $1"; else fail=$((fail+1)); echo "FAIL $1"; fi; }
# get JAR URL -> body in $D/body, status in $code, location in $loc
get() { code=$(curl -s -b "$D/$1" -c "$D/$1" -o "$D/body" -w '%{http_code}|%{redirect_url}' "$B$2"); loc=${code#*|}; code=${code%%|*}; }
post() { local jar=$1 path=$2; shift 2; code=$(curl -s -b "$D/$jar" -c "$D/$jar" -o "$D/body" -w '%{http_code}|%{redirect_url}' "$@" "$B$path"); loc=${code#*|}; code=${code%%|*}; }
csrf() { grep -o 'name="_csrf" value="[^"]*"' "$D/body" | head -1 | sed 's/.*value="//;s/"//'; }
has() { grep -q "$1" "$D/body"; }
tok() { grep -o 'name="tokenCheckout" value="[^"]*"' "$D/body" | head -1 | sed 's/.*value="//;s/"//'; }

get anon /inicio;                          check "inicio publico 200" '[ "$code" = 200 ] && has "Comprar ahora"'
get anon "/productos?accion=comprar&codigo=P001"; check "comprar sin sesion -> login" '[ "$code" = 302 ] && [[ "$loc" == *"/login" ]]'
get anon "/pago?codigo=P001&cantidad=1";   check "pago sin sesion -> login" '[ "$code" = 302 ] && [[ "$loc" == *"/login" ]]'
get anon /login; T=$(csrf);                check "login trae token csrf" '[ -n "$T" ]'
post anon /login --data-urlencode "usuario=cliente" --data-urlencode "clave=cliente123"
check "login sin csrf rechazado" '[ "$code" = 302 ] && [[ "$loc" == *"/login" ]]'
get anon /login; T=$(csrf)
post anon /login --data-urlencode "_csrf=$T" --data-urlencode "usuario=cliente" --data-urlencode "clave=cliente123"
check "login vuelve a la ultima url protegida" '[ "$code" = 302 ] && [[ "$loc" == *"/pago?codigo=P001&cantidad=1" ]]'
get anon "/productos?accion=comprar&codigo=P001"; check "modal de compra" '[ "$code" = 200 ] && has "Continuar al pago"'
get anon "/pago?codigo=P001&cantidad=2";  check "checkout 200" '[ "$code" = 200 ] && has "5,000.00"'
T=$(csrf); check "checkout trae token" '[ -n "$T" ]'
K=$(tok); check "checkout trae token de un solo uso" '[ -n "$K" ]'
base=(--data-urlencode "_csrf=$T" --data-urlencode "tokenCheckout=$K" --data-urlencode "codigo=P001" --data-urlencode "cantidad=2" --data-urlencode "titular=Andrea Paredes" --data-urlencode "vencimiento=12/30" --data-urlencode "cvv=123" --data-urlencode "tipoComprobante=Boleta" --data-urlencode "documento=70451236" --data-urlencode "tipoEnvio=DOMICILIO" --data-urlencode "departamento=Lima" --data-urlencode "distrito=Miraflores" --data-urlencode "direccion=Av. Larco 123" --data-urlencode "telefonoContacto=987654321")
post anon /pago "${base[@]}" --data-urlencode "metodo=TARJETA" --data-urlencode "numeroTarjeta=4111 1111 1111 1112"
check "luhn invalido muestra error" '[ "$code" = 200 ] && has "Número de tarjeta inválido"'
check "no repuebla numero de tarjeta" '! has "4111 1111 1111 1112"'
post anon /pago "${base[@]}" --data-urlencode "metodo=TARJETA" --data-urlencode "numeroTarjeta=4000 0000 0000 0002"
check "tarjeta rechazada" '[ "$code" = 200 ] && has "Fondos insuficientes"'
post anon /pago "${base[@]}" --data-urlencode "metodo=TARJETA" --data-urlencode "numeroTarjeta=4111 1111 1111 1111"
check "pago aprobado -> comprobante" '[ "$code" = 302 ] && [[ "$loc" == *"accion=comprobante&pedido="* ]]'
P1=${loc##*pedido=}
post anon /pago "${base[@]}" --data-urlencode "metodo=TARJETA" --data-urlencode "numeroTarjeta=4111 1111 1111 1111"
check "doble envio no crea otro pedido" '[ "$code" = 302 ] && [[ "$loc" == *"pedido=$P1" ]]'
post anon /pago "${base[@]/tokenCheckout=$K/tokenCheckout=falso}" --data-urlencode "metodo=TARJETA" --data-urlencode "numeroTarjeta=4111 1111 1111 1111"
check "token de checkout invalido rechazado" '[ "$code" = 302 ] && [[ "$loc" == *"accion=pedidos" ]]'
get anon "/pago?accion=comprobante&pedido=$P1"; check "comprobante boleta" '[ "$code" = 200 ] && has "B001-000003" && has "Visa •••• 1111" && has "5,000.00"'
check "comprobante sin PAN" '! has "4111 1111 1111 1111" && ! has "4111111111111111"'
get anon "/pago?codigo=P002&cantidad=1"; K2=$(tok)
post anon /pago --data-urlencode "_csrf=$T" --data-urlencode "tokenCheckout=$K2" --data-urlencode "codigo=P002" --data-urlencode "cantidad=1" --data-urlencode "metodo=YAPE" --data-urlencode "celular=987654321" --data-urlencode "codigoYape=000000" --data-urlencode "tipoComprobante=Factura" --data-urlencode "documento=20123456789" --data-urlencode "razonSocial=Empresa Demo SAC" --data-urlencode "tipoEnvio=DOMICILIO" --data-urlencode "departamento=Lima" --data-urlencode "distrito=Miraflores" --data-urlencode "direccion=Av. Larco 123" --data-urlencode "telefonoContacto=987654321"
check "yape codigo invalido rechazado" '[ "$code" = 200 ] && has "Yape"'
post anon /pago --data-urlencode "_csrf=$T" --data-urlencode "tokenCheckout=$K2" --data-urlencode "codigo=P002" --data-urlencode "cantidad=1" --data-urlencode "metodo=YAPE" --data-urlencode "celular=987654321" --data-urlencode "codigoYape=123456" --data-urlencode "tipoComprobante=Factura" --data-urlencode "documento=20123456789" --data-urlencode "razonSocial=Empresa Demo SAC" --data-urlencode "tipoEnvio=DOMICILIO" --data-urlencode "departamento=Lima" --data-urlencode "distrito=Miraflores" --data-urlencode "direccion=Av. Larco 123" --data-urlencode "telefonoContacto=987654321"
check "yape + factura aprobado" '[ "$code" = 302 ] && [[ "$loc" == *"accion=comprobante"* ]]'
get anon "${loc#$B}";                      check "factura F001" 'has "F001-000001" && has "Empresa Demo SAC" && has "FACTURA"'
get anon "/productos?accion=pedidos";     check "historial con pago" 'has "Pagado" && has "B001-000003"'
get anon "/pago?codigo=P001&cantidad=99"; check "cantidad > stock rechazada" '[ "$code" = 302 ]'
get anon "/reportes?accion=resumen";      check "cliente sin reportes" '[ "$code" = 302 ]'

get adm /login; T=$(csrf)
post adm /login --data-urlencode "_csrf=$T" --data-urlencode "usuario=admin" --data-urlencode "clave=admin123"
get adm "/pago?codigo=P001&cantidad=1";   check "admin no compra" '[ "$code" = 302 ] && [[ "$loc" == *"accion=listar"* ]]'
get adm "/productos?accion=listar";       check "stock P001 bajo a 8 (rechazos no descuentan)" 'tr -d "\n" < "$D/body" | grep -q "P001.\{0,600\}>8<"'
get adm "/pago?accion=comprobante&pedido=$P1"; check "personal ve comprobante" '[ "$code" = 200 ]'

get otro /login; T=$(csrf)
post otro /login --data-urlencode "_csrf=$T" --data-urlencode "accion=registrarCliente" --data-urlencode "nombreCompleto=Otro Cliente" --data-urlencode "dni=4$((RANDOM % 9000000 + 1000000))" --data-urlencode "correo=otro$RANDOM@nexora.pe" --data-urlencode "telefono=987654321" --data-urlencode "nuevoUsuario=otro$RANDOM" --data-urlencode "nuevaClave=clave123" --data-urlencode "confirmarClave=clave123"
get otro "/pago?accion=comprobante&pedido=$P1"; check "otro cliente no ve comprobante ajeno" '[ "$code" = 302 ]'

get anon /no-existe;                       check "404 con pagina propia" '[ "$code" = 404 ] && has "no encontrada"'
get nuevo "/css/..;/productos?accion=pedidos"; check "traversal ..; no expone pedidos" '[ "$code" != 200 ] && ! has "PED-0001"'
get nuevo "/img/..;/pago?accion=comprobante&pedido=1"; check "traversal a /pago bloqueado" '[ "$code" != 200 ]'
get esp /login; T=$(csrf)
post esp /login --data-urlencode "_csrf=$T" --data-urlencode "accion=registrarCliente" --data-urlencode "nombreCompleto=Con Espacios" --data-urlencode "dni=4$((RANDOM % 9000000 + 1000000))" --data-urlencode "correo=esp$RANDOM@nexora.pe" --data-urlencode "telefono=987654321" --data-urlencode "nuevoUsuario=esp$RANDOM" --data-urlencode "nuevaClave= clave123 " --data-urlencode "confirmarClave= clave123 "
check "registro con espacios en la clave sin error 500" '[ "$code" != 500 ] && ! has "Exception"'
echo "---- $ok OK, $fail FAIL"
