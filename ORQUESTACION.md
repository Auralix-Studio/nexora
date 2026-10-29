# Orquestación — Pasarela de pago y mejoras NEXORA

Objetivo: cumplir los criterios de `PROCESO_REESTRUCTURACION_TIENDA.md`, añadir pasarela de pago simulada
(compra directa de un producto), corregir pantallas y eliminar basura sin tocar `entregables/`.

Verificación: `"C:\Program Files\NetBeans-25\netbeans\java\maven\bin\mvn" -q -o package`
Despliegue local: Tomcat 11.0.26 en `C:\Users\aless\Downloads\apache-tomcat`, contexto `Semana04.xml`
apunta a `target/Semana04`. Sin SQL Server activo, el sistema usa el respaldo en memoria.

## Contratos

### Modelos (ya creados)
- `model/Pago`: metodo (TARJETA|YAPE), marca, ultimosDigitos, titular, monto, estado (Aprobado|Rechazado),
  motivoRechazo, codigoAutorizacion, codigoOperacion, tipoComprobante (Boleta|Factura), numeroComprobante
  (B001-000001 / F001-000001), documentoCliente, razonSocial, fecha. Calculados: aprobado, factura,
  metodoTexto, subtotal/igvMonto (+ *Texto), montoTexto. IGV 18 % incluido en el precio.
- `model/SolicitudPago`: campos del formulario (solo vive en la petición; PAN y CVV nunca se guardan).
- `model/ResultadoCompra`: `exito(pedido)` | `invalido(errores)` | `fallo(mensaje)`.
- `model/Pedido`: nuevo `pago` + `estadoPago` ("Pagado" | "Pendiente de pago").

### Servicios
- `PasarelaPagoService`: `validar(SolicitudPago)`, `autorizar(SolicitudPago, monto)`, `luhnValido`,
  `detectarMarca`, `TARJETAS_PRUEBA`, `CODIGO_YAPE_PRUEBA` = 123456.
- `ProductoService`: `comprarConPago(cliente, codigo, cantidad, solicitud)`, `buscarPedido(id, usuario)`.

### Rutas
| Método | Ruta | Resultado |
|---|---|---|
| GET | `/productos?accion=comprar&codigo=P001` | Catálogo con ventana de cantidad → GET `/pago` |
| GET | `/pago?codigo=P001&cantidad=2` | Vista `pago/checkout` (solo Cliente) |
| POST | `/pago` | Errores → checkout de nuevo; rechazado → checkout con `errorPago`; aprobado → 302 al comprobante |
| GET | `/pago?accion=comprobante&pedido=N` | Vista `pago/comprobante` (dueño o personal) |

Parámetros POST `/pago`: `_csrf, tokenCheckout (un solo uso, emitido en el GET), codigo, cantidad, metodo, numeroTarjeta, titular, vencimiento, cvv,
celular, codigoYape, tipoComprobante, documento, razonSocial`.

Atributos de `pago/checkout`: `producto` (Producto), `cantidad` (int), `subtotalTexto`, `igvTexto`,
`totalTexto`, `form` (Map con valores previos, sin numeroTarjeta ni cvv ni codigoYape), `errores` (Map campo→mensaje),
`errorPago` (String), `tarjetasPrueba` (List<String[]> {número, marca, resultado}), `codigoYapePrueba`.

Atributos de `pago/comprobante`: `pedido` (Pedido con `pago`).

### CSRF
Token en sesión `csrfToken`; campo oculto `_csrf` en **todos** los formularios POST:
`<input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">`. `SesionFilter` lo crea en cada
petición no estática y rechaza los POST sin token válido.

### Permisos
Solo el rol Cliente compra (`Usuario.isPuedeComprar`). Personal: inventario y reportes.

## Contratos — Módulo de administración (26/10/2026)

### Modelos (ya creados, no cambiar firmas)
- `Pedido`: estados `CONFIRMADO, EN_PREPARACION, ENVIADO, ENTREGADO, ANULADO` (`ESTADOS`), `estado` (por defecto
  Confirmado), `fechaActualizacion`, `fechaHora` (LocalDateTime; si falta se obtiene de `fecha`), `anulado`,
  `siguienteEstado` (null al final), `anulable` (Confirmado o En preparación), `ventaValida` (pago aprobado y no anulado),
  `estadoPago` ahora también "Reembolsado".
- `Pago`: `REEMBOLSADO`, `reembolsado`.
- `Usuario`: `personal`, `puedeGestionarPedidos` (Admin/Almacén), `puedeAnularPedidos` (Admin), `puedeAjustarStock`
  (Admin/Almacén), `puedeAdministrarSistema` (Admin: usuarios, categorías, auditoría).
- Nuevos: `MovimientoStock` (tipos ENTRADA, SALIDA, AJUSTE, DEVOLUCION; `TIPOS_MANUALES`; cantidad con signo;
  `cantidadTexto`), `RegistroAuditoria` (constantes de acción y `ACCIONES`; `alerta`), `Categoria`
  (`cantidadProductos`, `eliminable`), `FilaReporte` (etiqueta, cantidad, monto, porcentaje, anchoBarra + *Texto),
  `ResumenVentas` (ingresos hoy/mes/total, pedidos hoy/mes/total, unidades, `pedidosPorEstado`, `topProductos`,
  `stockBajo`, `ultimosPedidos`, `ticketPromedio`, `pedidosPendientes` + *Texto).
- `util/Seguridad`: `hashClave`, `verificarClave`, `esHash` (PBKDF2-SHA256, 600 000 iteraciones; recifra al ingresar si el hash es más débil).

### Servicios (firmas en el código; los que modifican devuelven null si va bien o el mensaje de error)
- `ProductoService`: `filtrarPedidos`, `cambiarEstadoPedido`, `anularPedido`, `ajustarStock`, `listarMovimientos`,
  `listarCategorias`, `nombresCategorias`, `crearCategoria`, `editarCategoria`, `cambiarEstadoCategoria`,
  `eliminarCategoria`. `comprarConPago` además deja el pedido Confirmado y registra la Salida "Venta PED-0000".
- `UsuarioService`: `estaBloqueado`, `minutosRestantesBloqueo`, `listarUsuarios`, `buscarPorId`, `crearUsuario`,
  `cambiarEstadoUsuario`, `cambiarRol`, `restablecerClave`; contraseñas cifradas y migración de claves heredadas en claro.
- `AuditoriaService` (singleton): `registrar(...)` ×2, `listar(...)`. `AdminService` (singleton): `resumen`, `stockBajo`,
  `ventasPorDia/Categoria/Metodo/Producto`, `totalVentas`, `numeroVentas`, `exportarVentasCsv`.

### SQL (ya aplicado en `sql/bd_nexora.sql`, validado en una BD temporal)
`dbo.pedido.estado` + `fecha_actualizacion`; `dbo.pago.estado` admite Reembolsado; `dbo.movimiento_stock`
(codigo_producto, tipo, cantidad, stock_resultante, motivo, id_usuario NULL, fecha); `dbo.auditoria` (username, rol,
accion, entidad, detalle, ip, fecha); `dbo.categoria.activo` ya existía. Claves semilla cifradas; se quitó
`sp_validar_usuario`. Semillas: PED-0001 Entregado, PED-0002 Enviado, 2 Salidas en el kardex.

### Rutas `/admin` (AdminServlet). Sin sesión → login (filtro); Cliente → mensaje y /inicio
| GET `accion=` | Vista `admin/…` | Atributos | Permiso |
|---|---|---|---|
| `panel` (defecto) | panel | `resumen`, `umbralStock` | personal |
| `pedidos` (+`estado, cliente, desde, hasta` yyyy-MM-dd) | pedidos | `pedidos`, `estados`, `fEstado`, `fCliente`, `fDesde`, `fHasta`, `totalFiltradoTexto` | personal |
| `pedido&id=` | pedido | `pedido`, `siguienteEstado` | personal |
| `inventario` (+`codigo`) | inventario | `productos`, `movimientos` (50), `productoSel`, `tiposMovimiento`, `umbralStock`, `form`, `errorFormulario` | personal |
| `ventas` (+`desde, hasta`; `formato=csv` descarga) | ventas | `porDia`, `porCategoria`, `porMetodo`, `porProducto` (10), `totalVentasTexto`, `numeroVentas`, `ticketPromedioTexto`, `fDesde`, `fHasta` (defecto: 1.er día del mes → hoy) | personal |
| `usuarios` | usuarios | `usuarios`, `roles`, `form`, `errorFormulario` | Admin |
| `categorias` (+`editar=id`) | categorias | `categorias`, `editar` (Categoria o null), `form`, `errorFormulario` | Admin |
| `auditoria` (+`usuario, tipo, desde, hasta`) | auditoria | `registros` (300), `acciones`, `fUsuario`, `fAccion`, `fDesde`, `fHasta` | Admin |

| POST `accion=` (con `_csrf`; PRG con mensaje) | Parámetros | Vuelve a | Permiso |
|---|---|---|---|
| `estadoPedido` | `id, estado` | `?accion=pedido&id=` | Admin/Almacén |
| `anularPedido` | `id, motivo` | `?accion=pedido&id=` | Admin |
| `ajustarStock` | `codigo, tipo, cantidad, motivo` | `?accion=inventario&codigo=` | Admin/Almacén |
| `crearUsuario` | `username, nombreCompleto, correo, rol, clave` | `?accion=usuarios` (error: re-render con `form`, `errorFormulario`) | Admin |
| `estadoUsuario` / `rolUsuario` / `claveUsuario` | `id` + `activo` / `rol` / `clave` | `?accion=usuarios` | Admin |
| `crearCategoria` / `editarCategoria` / `estadoCategoria` / `eliminarCategoria` | `nombre, descripcion` / `id, nombre, descripcion` / `id, activo` / `id` | `?accion=categorias` | Admin |

Auditoría (la registran los controladores, con `req.getRemoteAddr()`): AdminServlet en cada POST exitoso; LoginServlet
INICIO_SESION, ACCESO_FALLIDO, CUENTA_BLOQUEADA; ProductoServlet PRODUCTO_*; PagoServlet COMPRA.
Otros cambios de controladores: `/inicio` del personal redirige a `/admin`; `SistemaServlet` da `categoriasTienda`
(List<String>) a inicio.jsp y `usuariosSistema = listarUsuarios()` a informacion.jsp; formularios y búsqueda usan
`nombresCategorias()`. Menú del personal: ids `admin-panel, admin-pedidos, admin-inventario, admin-ventas,
admin-usuarios, admin-categorias, admin-auditoria`.

## Contratos — Producción en Azure (29/10/2026)

Decisiones del usuario: contenedor Docker (Tomcat 11) en **Azure Container Apps**; animaciones **solo dentro de la
app** (sin video); **admin privado + cuentas demo públicas** (cliente, invitado); **repo privado en GitHub + Actions**.

### Configuración por variables de entorno (`util/ConfigApp`, nuevo: lee System property y luego env)
| Variable | Valores | Efecto |
|---|---|---|
| `NEXORA_ENTORNO` | `desarrollo` (defecto) / `produccion` | En producción **nunca** se usa el respaldo en memoria: si SQL falla, `IllegalStateException` → página 503 |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | cadena JDBC Azure SQL, usuario contenido `nexora_app`, secreto | En producción son obligatorias (sin valores por defecto) |
| `NEXORA_DEMO` | `true` / `false` | Cinta "Entorno de demostración · pagos simulados" y cuadro de cuentas demo en el login |
| `NEXORA_VERSION` | sha corto del commit (defecto `dev`) | `?v=` en CSS/JS y respuesta de `/salud` |
`ConfigApp`: `esProduccion()`, `esDemo()`, `version()`.

### Backend
- `ConexionBD` con pool **HikariCP** (`minimumIdle=0`, `idleTimeout` 5 min, `maximumPoolSize` 8, `connectionTimeout` 60 s,
  `initializationFailTimeout=-1`, sin keepalive) para que Azure SQL serverless pueda autopausarse.
- `controller/SaludServlet` `GET /salud` → 200 `{"estado":"ok","version","entorno"}` sin tocar la BD ni crear sesión;
  `GET /salud?bd=1` → `SELECT 1` → 200 o 503. Ruta pública; `SesionFilter` no crea sesión ni consulta productos en ella.
- `controller/CabecerasSeguridadFilter` (orden: primero, vía web.xml): CSP `default-src 'self'; script-src 'self';
  style-src 'self' https://fonts.googleapis.com; font-src https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self';
  form-action 'self'; frame-ancestors 'none'; base-uri 'self'; object-src 'none'`, `X-Content-Type-Options: nosniff`,
  `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy`, `X-Frame-Options: DENY`, COOP same-origin,
  HSTS solo si `request.isSecure()`; estáticos `/css /js /img` con `Cache-Control: public, max-age=604800`.
  **Sin estilos ni scripts en línea en las vistas** (la CSP los bloquea).
- Atributos de request para las vistas (los pone `SesionFilter`): `appVersion`, `modoDemo`, `entornoProduccion`.
- Entrega del pedido persistida en SQL: tabla `dbo.entrega` 1:1 con `dbo.pedido` (tipo_envio, departamento, distrito,
  direccion, referencia, telefono, correo_comprobante, notas). Hoy esos datos solo existen en memoria.
- `Seguridad.main(clave)` imprime el hash (lo usa el script de inicialización para las claves privadas).

### Contenedor e infraestructura
- `Dockerfile` multietapa: Maven (compila + pruebas) → `tomcat:11.0-jdk21-temurin`; WAR como `ROOT.war`; sin apps
  de ejemplo; usuario no root; `server.xml` con `RemoteIpValve` (X-Forwarded-Proto → cookies Secure), compresión,
  `ErrorReportValve` sin versión; cookie de sesión `HttpOnly`, `Secure`, `SameSite=Lax`; `-XX:MaxRAMPercentage=75`; puerto 8080.
- `docker-compose.yml`: app + `mcr.microsoft.com/mssql/server` + inicialización con `sql/bd_nexora.sql` (verificación local).
- `infra/main.bicep`: Log Analytics, entorno de Container Apps, Container App (ingress externo 8080, réplicas 0..1 por
  parámetro — sesiones en memoria → máximo 1, sondas a `/salud`, 0.5 vCPU / 1 GiB, secreto `db-password`), Azure SQL
  server + `BD_NEXORA` con **oferta gratuita** (autopausa al agotar el límite) + regla de firewall para servicios de Azure.
- `infra/inicializar-bd`: ejecuta `sql/bd_nexora.sql` desde la sección 2 (Azure SQL no admite `CREATE DATABASE`/`USE`),
  crea el usuario contenido `nexora_app` (lectura/escritura) y fija claves privadas generadas para `admin` y `almacen`
  (se muestran una sola vez al usuario; nunca se guardan en el repo).
- `.github/workflows/ci.yml`: compila y prueba; construye la imagen; E2E en contenedor (modo memoria); publica en GHCR
  en `main`; despliega a Container Apps si existen los secretos `AZURE_*` (OIDC).
- `docs/DESPLIEGUE.md`: guía paso a paso en español.

### Acciones que hace el usuario (credenciales y costos)
Instalar Azure CLI y `az login`; abrir Docker Desktop; confirmar región, nombres y costo estimado antes de crear
recursos; guardar las claves privadas que se muestren.

## Estado

| Tarea | Modelo | Estado | Notas |
|---|---|---|---|
| Ola 0: basura + contratos | opus (orquestador) | Hecho | Borrados: cookie_test.txt, _plantilla_netbeans/, sql duplicado, descargar-imagenes.mjs; fuentes.json → docs/ |
| Ola 1A: backend pasarela | sonnet | Hecho | Sin escalados (se reanudó tras un corte por límite de uso) |
| Ola 1B: pantallas | sonnet | Hecho | Sin escalados (se reanudó tras un corte por límite de uso) |
| Ajustes del orquestador | opus | Hecho | Nombres semilla sin sufijo de rol; título "Mis compras" |
| Ola 2A: CSS/JS sin uso | haiku | Hecho | estilos.css 1305 → 1244 líneas; JS sin bloques muertos |
| Ola 2B: pruebas + docs | sonnet | Hecho | 132 pruebas JUnit; README y bitácora al día; scripts/ migrado y eliminado |
| Ola 3: E2E + revisión | opus | Hecho | Revisión: 2 bloqueantes (traversal `..;` en SesionFilter, NPE en registro) + 16 sugerencias |
| Ola 4: correcciones de revisión | sonnet | Hecho | 17 de 18 hallazgos aplicados (omitido: candado del singleton durante la transacción SQL) |
| Verificación final | opus (orquestador) | Hecho | 145 pruebas JUnit en verde; E2E 32/32 en Tomcat 11 (puerto 8090, modo memoria) |
| Módulo admin: ola 0 (contratos, SQL, cifrado) | opus (orquestador) | Hecho 26/10 | SQL validado en BD temporal |
| Módulo admin: ola 1 (servicios, controladores, vistas) | sonnet ×3 en paralelo | Hecho | Causa de 14 fallos E2E: curl de Git Bash enviaba acentos en cp1252 (no era bug); se añadió 400 ante parámetros mal codificados |
| Módulo admin: pruebas y docs | sonnet | Hecho | 221 pruebas |
| Módulo admin: revisión | opus | Hecho | 4 bloqueantes (evasión de bloqueo con Unicode, revocación de sesiones, auditoría "Enviado → Enviado", fechas extremas 500) + 15 sugerencias |
| Módulo admin: correcciones | sonnet | Hecho 29/10 | 17 puntos; se reanudó tras límite semanal; 236 pruebas; E2E compra 32/32 y admin 65/65 en memoria y SQL Server |
| Cambios externos 26/10 12:04-12:58 | (fuera de la orquestación) | Integrados | Datos de entrega en checkout y DNI/celular en registro; se completan en SQL en la ola P1-A |
| Producción: P1 (backend, UI motion, infra) | sonnet ×3 en paralelo | En curso | |

| Prueba contra SQL Server | opus (orquestador) | Hecho 26/10 | E2E 32/32 con "Conectado a MS SQL Server (BD_NEXORA)"; filas verificadas en dbo.pedido/dbo.pago (B001-000003, F001-000001, solo últimos 4 dígitos), stock P001 10→8, eliminar P002 → "tiene pedidos asociados" (FK). TCP 1433 y autenticación mixta ya habilitados por el usuario |

## Pendiente: SQL Server (diagnóstico del 25/10/2026)
- Instancia `MSSQLSERVER` (SQL Server 2025 Developer, servidor `gaspar`) en ejecución; `SQLBrowser` detenido.
- La instancia solo tiene `master`, `tempdb`, `model` y `msdb`: **`BD_NEXORA` no existe ahí**, así que
  `sql/bd_nexora.sql` no se aplicó sobre esa instancia (o se ejecutó contra otra).
- TCP no escucha en 1433 (solo el puerto DAC 1434 en 127.0.0.1). JDBC necesita TCP/IP habilitado en
  SQL Server Configuration Manager → Protocolos de MSSQLSERVER → TCP/IP → puerto 1433, y reiniciar el servicio.
- La app se conecta por autenticación SQL (`sa` / valor por defecto de `ConexionBD`, o variables
  `DB_URL`, `DB_USER`, `DB_PASSWORD`): requiere modo de autenticación mixto y la clave real de `sa`.
- 26/10/2026: `sql/bd_nexora.sql` ejecutado con `sqlcmd -E` (el usuario de Windows es sysadmin): BD_NEXORA
  creada sin errores (7 tablas, 12 productos, 2 pedidos y 2 pagos semilla). Siguen pendientes, a cargo del
  usuario por ser ajustes de seguridad del servidor: habilitar TCP/IP (hoy `Enabled=0`, IPAll ya en 1433),
  activar autenticación mixta (hoy solo Windows) y habilitar `sa` con la clave por defecto de `ConexionBD`
  (hoy deshabilitado); después, reiniciar el servicio MSSQLSERVER.
- 26/10/2026 08:26: `sa` ya habilitado y `LoginMode=2` escrito, pero el servicio no se reinició (sqlservr sigue
  desde el 25/10 14:54, por eso aún reporta solo Windows) y TCP sigue en `Enabled=0` en
  `HKLM\SOFTWARE\Microsoft\Microsoft SQL Server\MSSQL17.MSSQLSERVER\MSSQLServer\SuperSocketNetLib\Tcp`.
  Falta: habilitar TCP y reiniciar MSSQLSERVER (requiere administrador).
- Siguiente paso al retomar: con TCP habilitado y el script aplicado (`sqlcmd -S localhost -E -C -i sql/bd_nexora.sql`),
  arrancar Tomcat con `DB_URL`/`DB_USER`/`DB_PASSWORD`, confirmar "Conectado a MS SQL Server" en `/informacion`
  (como admin) y correr el E2E. Valores esperados con la BD recién creada: primer pedido nuevo PED-0003,
  boleta B001-000003, factura F001-000001, stock P001 10 → 8; eliminar P002 debe responder
  "tiene pedidos asociados" (FK). Revisar en `dbo.pago` que solo hay últimos 4 dígitos.
- El E2E crea usuarios de prueba (`otro…`, `esp…`) en `dbo.usuario`; reejecutar el script los limpia.

## Continuidad guardada
- `CLAUDE.md`: convenciones, comandos, despliegue y reglas de orquestación del repo.
- `.claude/agents/`: explorador y implementador-rapido (haiku), implementador (sonnet), planificador y revisor (opus).
- `.claude/skills/orquestacion-eficiente/`: copia de la skill con sus `assets/` (agentes, bloque de CLAUDE.md, plantilla).
- `docs/pruebas/e2e.sh`: prueba de punta a punta (32 comprobaciones) con la URL base como argumento.

## Notas para retomar
- El puerto 8080 de este equipo lo ocupa otra aplicación (Node). Para probar se usó una base de Tomcat
  aislada en el puerto 8090 (scratchpad de la sesión), sin modificar la instalación de Tomcat.
- El camino SQL Server de la compra no se ha ejecutado (no hay instancia activa); solo está revisado.
