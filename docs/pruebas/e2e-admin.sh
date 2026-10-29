#!/usr/bin/env bash
# Prueba de punta a punta del módulo de administración: permisos por rol, flujo de pedidos,
# anulación con devolución de stock, kardex, ventas CSV, usuarios, categorías, bloqueo y auditoría.
# Uso: bash docs/pruebas/e2e-admin.sh [URL base]   (por defecto http://localhost:8090/Semana04)
# Supone datos semilla recién cargados (Tomcat recién iniciado en memoria o BD recién creada).
B="${1:-http://localhost:8090/Semana04}"
D="$(mktemp -d)"
R=$RANDOM
ok=0; fail=0
check() { if eval "$2"; then ok=$((ok+1)); echo "OK   $1"; else fail=$((fail+1)); echo "FAIL $1"; fi; }
get() { code=$(curl -s -b "$D/$1" -c "$D/$1" -o "$D/body" -w '%{http_code}|%{redirect_url}' "$B$2"); loc=${code#*|}; code=${code%%|*}; }
post() { local jar=$1 path=$2; shift 2; code=$(curl -s -b "$D/$jar" -c "$D/$jar" -o "$D/body" -w '%{http_code}|%{redirect_url}' "$@" "$B$path"); loc=${code#*|}; code=${code%%|*}; }
seguir() { get "$1" "${loc#$B}"; }
csrf() { grep -o 'name="_csrf" value="[^"]*"' "$D/body" | head -1 | sed 's/.*value="//;s/"//'; }
tok() { grep -o 'name="tokenCheckout" value="[^"]*"' "$D/body" | head -1 | sed 's/.*value="//;s/"//'; }
has() { grep -q "$1" "$D/body"; }
sinError() { ! grep -qiE "exception|jasper|PropertyNotFound|stacktrace" "$D/body"; }
# Primer value del campo hidden "id" en la fila cuya celda contiene exactamente el texto $1
idTras() { perl -0ne 'if (/>\s*\Q'"$1"'\E\s*<.*?name="id" value="(\d+)"/s) { print $1 }' "$D/body"; }
# Stock mostrado en la fila del producto $1 en el inventario
stockDe() { perl -0ne 'if (/>\Q'"$1"'\E<.*?data-titulo="Stock"[^>]*>\s*(?:<[^>]+>\s*)*(-?\d+)/s) { print $1 }' "$D/body"; }
login() { get "$1" /login; local t; t=$(csrf); post "$1" /login --data-urlencode "_csrf=$t" --data-urlencode "usuario=$2" --data-urlencode "clave=$3"; }
T=""; csrfDe() { get "$1" "$2"; T=$(csrf); }

# ---------------------------------------------------------------- acceso por rol
login adm admin admin123;             check "admin entra directo al panel" '[ "$code" = 302 ] && [[ "$loc" == *"/admin" ]]'
get adm /inicio;                      check "inicio del personal redirige a /admin" '[ "$code" = 302 ] && [[ "$loc" == *"/admin" ]]'
get adm /admin;                       check "panel 200" '[ "$code" = 200 ] && has "Ingresos" && sinError'
for p in "pedidos" "pedido&id=1" "inventario" "inventario&codigo=P002" "ventas" "usuarios" "categorias" "auditoria"; do
  get adm "/admin?accion=$p";         check "admin ve $p" '[ "$code" = 200 ] && sinError'
done
login cli cliente cliente123
get cli /admin;                       check "cliente no entra al admin" '[ "$code" = 302 ] && [[ "$loc" == *"/inicio" ]]'
login alm almacen almacen123
for p in usuarios categorias auditoria; do
  get alm "/admin?accion=$p";         check "almacén sin acceso a $p" '[ "$code" = 302 ] && [[ "$loc" == *"/admin" ]]'
done
get alm "/admin?accion=inventario";   check "almacén ve inventario" '[ "$code" = 200 ] && sinError'
login inv invitado invitado
get inv "/admin?accion=pedidos";      check "consulta ve pedidos" '[ "$code" = 200 ] && sinError'
get inv "/admin?accion=pedido&id=2";  check "consulta no ve botones de gestión" '! has "Marcar como" && ! grep -q 'name="_csrf"' "$D/body"'
post inv /admin --data-urlencode "accion=estadoPedido" --data-urlencode "id=2" --data-urlencode "estado=Entregado"
get adm "/admin?accion=pedido&id=2";  check "consulta no cambia estados" 'has "Marcar como Entregado"'

# ---------------------------------------------------------------- compra y flujo de estados
get adm "/admin?accion=inventario";   S0=$(stockDe P001)
get cli "/pago?codigo=P001&cantidad=1"; TC=$(csrf); K=$(tok)
post cli /pago --data-urlencode "_csrf=$TC" --data-urlencode "tokenCheckout=$K" --data-urlencode "codigo=P001" --data-urlencode "cantidad=1" --data-urlencode "metodo=TARJETA" --data-urlencode "numeroTarjeta=4111 1111 1111 1111" --data-urlencode "titular=Andrea Paredes" --data-urlencode "vencimiento=12/30" --data-urlencode "cvv=123" --data-urlencode "tipoComprobante=Boleta" --data-urlencode "documento=70451236" --data-urlencode "tipoEnvio=DOMICILIO" --data-urlencode "departamento=Lima" --data-urlencode "distrito=Miraflores" --data-urlencode "direccion=Av. Larco 123" --data-urlencode "telefonoContacto=987654321"
N=${loc##*pedido=};                   check "compra aprobada crea pedido" '[[ "$N" =~ ^[0-9]+$ ]]'
get adm "/admin?accion=inventario";   S1=$(stockDe P001); check "compra descuenta stock ($S0 → $S1)" '[ -n "$S0" ] && [ "$S1" = "$((S0-1))" ]'
get adm "/admin?accion=pedido&id=$N"; check "pedido nuevo Confirmado" 'has "Confirmado" && has "Marcar como En preparación"'
csrfDe alm "/admin?accion=pedido&id=$N"
post alm /admin --data-urlencode "_csrf=$T" --data-urlencode "accion=estadoPedido" --data-urlencode "id=$N" --data-urlencode "estado=Entregado"
seguir alm;                           check "no se salta estados" '! has "Marcar como Enviado"'
post alm /admin --data-urlencode "_csrf=$T" --data-urlencode "accion=estadoPedido" --data-urlencode "id=$N" --data "estado=En%20preparaci%C3%B3n"
seguir alm;                           check "almacén avanza a En preparación" 'has "Marcar como Enviado"'
post alm /admin --data-urlencode "_csrf=$T" --data-urlencode "accion=anularPedido" --data-urlencode "id=$N" --data-urlencode "motivo=Prueba"
check "almacén no anula" '[ "$code" = 302 ] && [[ "$loc" != *"pedido&id=$N"* ]]'
csrfDe adm "/admin?accion=pedido&id=$N"
post adm /admin --data-urlencode "_csrf=$T" --data-urlencode "accion=anularPedido" --data-urlencode "id=$N" --data-urlencode "motivo="
seguir adm;                           check "anular exige motivo" '! has "Anulado" || has "Marcar como Enviado"'
post adm /admin --data-urlencode "_csrf=$T" --data-urlencode "accion=anularPedido" --data-urlencode "id=$N" --data-urlencode "motivo=Cliente desiste de la compra"
seguir adm;                           check "admin anula el pedido" 'has "Anulado" && ! has "Marcar como Enviado"'
get adm "/admin?accion=inventario";   S2=$(stockDe P001); check "anulación devuelve stock ($S2)" '[ "$S2" = "$S0" ]'
get adm "/admin?accion=inventario&codigo=P001"; check "kardex con salida y devolución" 'has "Devolución" && has "Venta PED-" && has "+1"'
get cli "/pago?accion=comprobante&pedido=$N"; check "cliente ve comprobante anulado" 'has "anulado"'
get cli "/productos?accion=pedidos";  check "cliente ve estado de sus pedidos" 'has "Anulado" && has "Entregado"'

# ---------------------------------------------------------------- inventario
csrfDe alm "/admin?accion=inventario&codigo=P003"; get adm "/admin?accion=inventario"; P3=$(stockDe P003)
post alm /admin --data-urlencode "_csrf=$T" --data-urlencode "accion=ajustarStock" --data-urlencode "codigo=P003" --data-urlencode "tipo=Entrada" --data-urlencode "cantidad=10" --data-urlencode "motivo=Reposicion proveedor"
seguir alm;                           check "entrada de stock en kardex" 'has "+10" && has "Reposicion proveedor"'
get adm "/admin?accion=inventario";   check "stock P003 sube 10" '[ "$(stockDe P003)" = "$((P3+10))" ]'
post alm /admin --data-urlencode "_csrf=$T" --data-urlencode "accion=ajustarStock" --data-urlencode "codigo=P003" --data-urlencode "tipo=Ajuste" --data-urlencode "cantidad=-999" --data-urlencode "motivo=Prueba negativa"
get adm "/admin?accion=inventario";   check "ajuste que deja stock negativo rechazado" '[ "$(stockDe P003)" = "$((P3+10))" ]'
post alm /admin --data-urlencode "_csrf=$T" --data-urlencode "accion=ajustarStock" --data-urlencode "codigo=P003" --data-urlencode "tipo=Entrada" --data-urlencode "cantidad=abc" --data-urlencode "motivo=x"
check "cantidad no numérica sin error 500" '[ "$code" != 500 ]'

post alm /admin --data "_csrf=$T" --data "accion=estadoPedido" --data "id=1" --data "estado=En%20preparaci%F3n"
check "parámetro con codificación inválida responde 400" '[ "$code" = 400 ]'

# ---------------------------------------------------------------- ventas
get inv "/admin?accion=ventas";       check "reporte de ventas 200" '[ "$code" = 200 ] && sinError'
H=$(curl -s -b "$D/inv" -D - -o "$D/csv" "$B/admin?accion=ventas&formato=csv&desde=2026-01-01&hasta=2030-12-31")
check "CSV descargable" 'grep -qi "content-type: text/csv" <<< "$H" && grep -qi "attachment" <<< "$H"'
check "CSV con encabezado y pedido anulado" 'grep -q "Pedido;Fecha" "$D/csv" && grep -q "Anulado" "$D/csv"'

# ---------------------------------------------------------------- usuarios
csrfDe adm "/admin?accion=usuarios"
post adm /admin --data-urlencode "_csrf=$T" --data-urlencode "accion=crearUsuario" --data-urlencode "username=oper$R" --data-urlencode "nombreCompleto=Operador Prueba" --data-urlencode "correo=oper$R@nexora.pe" --data "rol=Almac%C3%A9n" --data-urlencode "clave=corta"
check "clave débil rechazada" '[ "$code" = 200 ] && has "oper$R"'
post adm /admin --data-urlencode "_csrf=$T" --data-urlencode "accion=crearUsuario" --data-urlencode "username=oper$R" --data-urlencode "nombreCompleto=Operador Prueba" --data-urlencode "correo=oper$R@nexora.pe" --data "rol=Almac%C3%A9n" --data-urlencode "clave=Operador2026"
check "admin crea usuario de almacén" '[ "$code" = 302 ]'
login op "oper$R" Operador2026;       check "nuevo usuario entra" '[ "$code" = 302 ] && [[ "$loc" == *"/admin" ]]'
get adm "/admin?accion=usuarios";     UID_OP=$(idTras "oper$R")
post adm /admin --data-urlencode "_csrf=$T" --data-urlencode "accion=estadoUsuario" --data-urlencode "id=$UID_OP" --data-urlencode "activo=false"
get op "/admin?accion=inventario";    check "sesión abierta de cuenta desactivada se cierra" '[ "$code" = 302 ] && [[ "$loc" == *"/login" ]]'
login op2 "oper$R" Operador2026;      check "usuario desactivado no entra" '[ "$code" = 200 ] && has "incorrectos"'
post adm /admin --data-urlencode "_csrf=$T" --data-urlencode "accion=estadoUsuario" --data-urlencode "id=1" --data-urlencode "activo=false"
get adm /admin;                       check "admin no se desactiva a sí mismo" '[ "$code" = 200 ]'
post adm /admin --data-urlencode "_csrf=$T" --data-urlencode "accion=crearUsuario" --data-urlencode "username=bloq$R" --data-urlencode "nombreCompleto=Bloqueo Prueba" --data-urlencode "correo=bloq$R@nexora.pe" --data-urlencode "rol=Consulta" --data-urlencode "clave=Bloqueo2026"
for i in 1 2 3 4 5; do login bq "bloq$R" "mala$i"; done
login bq "bloq$R" Bloqueo2026;        check "bloqueo tras 5 intentos fallidos" 'has "bloqueada"'
# Variantes que SQL Server trata como el mismo usuario (ancho completo, carácter invisible): no evaden el bloqueo
get bq2 /login; TB=$(csrf)
post bq2 /login --data "_csrf=$TB" --data "usuario=%EF%BD%82loq$R" --data "clave=Bloqueo2026"
check "variante de ancho completo no entra" '[ "$code" = 200 ]'
get bq3 /login; TB=$(csrf)
post bq3 /login --data "_csrf=$TB" --data "usuario=bl%E2%80%8Doq$R" --data "clave=Bloqueo2026"
check "variante con carácter invisible no entra" '[ "$code" = 200 ]'
login bq "bloq$R" otraMala1; login bq "bloq$R" otraMala2
get adm "/admin?accion=auditoria&usuario=bloq$R&tipo=Cuenta%20bloqueada"
check "bloqueo se audita una sola vez" 'grep -qE "1(&#32;| )registro<" "$D/body"'
# Restablecer la clave revoca sesiones abiertas, y un ingreso nuevo no rehabilita la sesión vieja
post adm /admin --data-urlencode "_csrf=$T" --data-urlencode "accion=crearUsuario" --data-urlencode "username=rst$R" --data-urlencode "nombreCompleto=Reset Prueba" --data-urlencode "correo=rst$R@nexora.pe" --data-urlencode "rol=Consulta" --data-urlencode "clave=Inicial2026"
login vieja "rst$R" Inicial2026
get adm "/admin?accion=usuarios";     UID_RST=$(idTras "rst$R")
post adm /admin --data-urlencode "_csrf=$T" --data-urlencode "accion=claveUsuario" --data-urlencode "id=$UID_RST" --data-urlencode "clave=Nueva2026x"
login nueva "rst$R" Nueva2026x;      check "ingreso con la clave restablecida" '[ "$code" = 302 ]'
get vieja "/admin?accion=pedidos";    check "sesión vieja revocada aunque haya un ingreso nuevo" '[ "$code" = 302 ] && [[ "$loc" == *"/login" ]]'
get nueva "/admin?accion=pedidos";    check "sesión nueva sigue válida" '[ "$code" = 200 ]'

# ---------------------------------------------------------------- productos y kardex
csrfDe adm "/productos?accion=nuevo"; PC="T$R"
post adm /productos --data-urlencode "_csrf=$T" --data-urlencode "accion=registrar" --data-urlencode "codigo=$PC" --data-urlencode "nombre=Producto Prueba $R" --data-urlencode "categoria=Redes" --data-urlencode "precio=50.00" --data-urlencode "stock=7" --data-urlencode "estado=Disponible"
get adm "/admin?accion=inventario&codigo=$PC"; check "producto nuevo deja Entrada de stock inicial" 'has "Stock inicial" && has "+7"'
csrfDe adm "/productos?accion=editar&codigo=$PC"
post adm /productos --data-urlencode "_csrf=$T" --data-urlencode "accion=actualizar" --data-urlencode "codigo=$PC" --data-urlencode "nombre=Producto Prueba $R editado" --data-urlencode "categoria=Redes" --data-urlencode "precio=55.00" --data-urlencode "stock=999" --data-urlencode "estado=Disponible"
get adm "/admin?accion=inventario";   check "editar producto no cambia el stock" '[ "$(stockDe $PC)" = "7" ]'
csrfDe adm "/productos?accion=eliminar&codigo=$PC"
post adm /productos --data-urlencode "_csrf=$T" --data-urlencode "accion=eliminar" --data-urlencode "codigo=$PC"
seguir adm;                           check "producto con movimientos no se elimina" 'has "movimientos de inventario"'
get adm "/admin?accion=ventas&formato=csv&desde=%2B10000-01-01"; check "fecha extrema en CSV sin 500" '[ "$code" != 500 ]'
get adm "/admin?accion=auditoria&hasta=%2B999999999-12-31";      check "fecha extrema en auditoría sin 500" '[ "$code" != 500 ]'
get adm "/admin?accion=pedidos&desde=-0001-01-01";               check "año negativo en pedidos sin 500" '[ "$code" != 500 ]'

# ---------------------------------------------------------------- categorías
csrfDe adm "/admin?accion=categorias"
post adm /admin --data-urlencode "_csrf=$T" --data-urlencode "accion=crearCategoria" --data-urlencode "nombre=Gaming$R" --data-urlencode "descripcion=Consolas y accesorios"
get anon /inicio;                     check "categoría nueva aparece en la tienda" 'has "Gaming$R"'
get adm "/productos?accion=nuevo"; check "categoría nueva en el formulario de producto" 'has "Gaming$R"'
get adm "/admin?accion=categorias";   CID=$(idTras "Gaming$R")
post adm /admin --data-urlencode "_csrf=$T" --data-urlencode "accion=eliminarCategoria" --data-urlencode "id=$CID"
get anon /inicio;                     check "categoría sin productos se elimina" '! has "Gaming$R"'
get adm "/admin?accion=categorias";   RID=$(idTras "Redes")
post adm /admin --data-urlencode "_csrf=$T" --data-urlencode "accion=eliminarCategoria" --data-urlencode "id=$RID"
seguir adm;                           check "categoría con productos no se elimina" 'has "Redes" && has "productos"'

# ---------------------------------------------------------------- auditoría y panel
get adm "/admin?accion=auditoria";    check "auditoría registra acciones" 'has "Pedido anulado" && has "Acceso fallido" && has "Usuario creado" && has "Movimiento de stock" && has "Cuenta bloqueada"'
check "auditoría no guarda contraseñas" '! has "Operador2026" && ! has "Bloqueo2026"'
get adm /admin;                       check "panel con stock bajo" 'has "P008" || has "P012"'
echo "---- $ok OK, $fail FAIL"
