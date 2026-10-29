# NEXORA

Tienda en línea de equipamiento tecnológico con inventario por roles y **pasarela de pago simulada** (tarjeta o Yape, boleta o factura). Aplicación Java 17 / Jakarta EE con servlets, JSP y JSTL. Usa SQL Server si está disponible; si no, trabaja con un respaldo en memoria.

## Requisitos

- JDK 17 o superior.
- Maven 3.x (probado con 3.9.9; también sirve el que incluye NetBeans).
- Apache Tomcat 11 (Jakarta EE).
- SQL Server o Azure SQL Database: **opcional**. Sin base de datos, el catálogo, los usuarios y los pedidos viven en memoria y se pierden al reiniciar Tomcat.

## Compilar, probar y desplegar

```
mvn test       # pruebas JUnit 5 (221 casos), sin base de datos
mvn package    # compila, ejecuta las pruebas y genera target/Semana04.war
```

Para desplegar, copia `target/Semana04.war` a la carpeta `webapps` de Tomcat 11 y abre `http://localhost:8080/Semana04/`. En NetBeans basta con abrir el `pom.xml` y ejecutar el proyecto sobre Tomcat 11.

Las pruebas viven en `src/test/java/pe/edu/sistema`: `PasarelaPagoServiceTest` (Luhn, marca, validación y autorización), `ProductoServiceTest` (validación de productos, compras con pago, correlativo de comprobantes, orden y permisos sobre los pedidos), `UsuarioServiceTest` (registro y acceso), `PagoTest` (desglose del IGV) y, del módulo de administración, `SeguridadTest` (cifrado de contraseñas), `UsuarioServiceAdminTest` (cuentas, bloqueo y revocación de sesiones), `ProductoServiceAdminTest` (estados, anulación, kardex, filtros y categorías), `AuditoriaServiceTest` y `AdminServiceTest` (panel, reportes y CSV). El plugin surefire fija `DB_URL` en un puerto cerrado, de modo que siempre usan el respaldo en memoria y nunca escriben en SQL Server.

## Base de datos

Para usar SQL Server, ejecuta `sql/bd_nexora.sql` (crea `BD_NEXORA`, las tablas `rol`, `usuario`, `categoria`, `producto`, `pedido`, `pago`, `sesion_acceso`, `movimiento_stock` y `auditoria`, los datos iniciales y los procedimientos; si los objetos ya existen, los elimina y los vuelve a crear, por lo que se pierden sus datos).

**Si ya tenías `BD_NEXORA`, vuelve a ejecutar `sql/bd_nexora.sql`:** el módulo de administración cambió el esquema (estado del pedido, pago `Reembolsado`, kardex y auditoría) y las contraseñas de las cuentas semilla ahora se guardan cifradas (PBKDF2). Una base creada con la versión anterior no tiene las columnas ni las tablas nuevas.

La conexión se configura sin tocar el código, con variables de entorno o propiedades de la JVM (`-DDB_URL=...`); la propiedad tiene prioridad. Defínelas antes de iniciar Tomcat (por ejemplo en `bin\setenv.bat`):

| Variable | Valor por defecto |
| --- | --- |
| `DB_URL` | `jdbc:sqlserver://localhost:1433;databaseName=BD_NEXORA;encrypt=true;trustServerCertificate=true;loginTimeout=2;` |
| `DB_USER` | `sa` |
| `DB_PASSWORD` | contraseña de ejemplo definida en `ConexionBD.java`; cámbiala por la tuya |

Si la conexión falla, la aplicación pasa al modo memoria sin interrumpirse. El modo activo se muestra en la pantalla de información para el personal. Detalle en `src/main/java/pe/edu/sistema/util/ConexionBD.java`.

## Estructura del proyecto

```
semana04/
├── pom.xml                         WAR con Java 17, JUnit 5 y surefire
├── PROCESO_REESTRUCTURACION_TIENDA.md   bitácora técnica del proyecto
├── docs/fuentes-imagenes.json      origen de cada foto de producto (variant = imagen referencial)
├── docs/pruebas/                   e2e.sh (compra) y e2e-admin.sh (administración), pruebas HTTP con curl
├── sql/bd_nexora.sql               modelo físico, datos iniciales y procedimientos
├── entregables/                    informes académicos (.docx) e imágenes
└── src/
    ├── main/java/pe/edu/sistema/
    │   ├── controller/             SesionFilter (sesión + CSRF), Login, Sistema, Producto, Pago, Reporte y Admin
    │   ├── service/                ProductoService, PasarelaPagoService, UsuarioService, AdminService, AuditoriaService
    │   ├── model/                  Producto, Pedido, Pago, SolicitudPago, ResultadoCompra, Usuario, CategoriaResumen,
    │   │                           MovimientoStock, RegistroAuditoria, Categoria, FilaReporte, ResumenVentas
    │   └── util/                   ConexionBD, Formato, Seguridad
    ├── main/webapp/
    │   ├── css/estilos.css  js/validaciones.js  img/productos/
    │   ├── includes/               cabecera, menú por perfil y pie
    │   └── WEB-INF/                web.xml, vistas/ (login, inicio, panel, información, error,
    │                               productos/, pago/, reportes/, admin/) y tags/ (icono, fotoProducto,
    │                               catalogoProductos, tablaProductos)
    └── test/java/pe/edu/sistema/           pruebas JUnit (model y service)
```

`target/` es generado por Maven: se modifican los fuentes en `src`, no esa carpeta.

## Rutas y pantallas

| Ruta | Acceso | Contenido |
| --- | --- | --- |
| `/login` (POST `accion=registrarCliente` crea una cuenta) y `/logout` | Público | Acceso y registro de clientes. |
| `/inicio` | Público | Tienda para visitantes y clientes; panel operativo para el personal. |
| `/productos?accion=listar` | Público | Catálogo con fotos para visitantes y clientes; inventario para el personal. |
| `/productos?accion=buscar` | Público | Búsqueda por código, nombre o categoría. |
| `/productos?accion=comprar&codigo=P001` | Cliente | Catálogo con la ventana de cantidad; continúa en `/pago`. |
| `/pago?codigo=P001&cantidad=2` | Cliente | Checkout: método de pago, comprobante y resumen con IGV. |
| `POST /pago` | Cliente | Procesa el pago. Con errores o pago rechazado vuelve al checkout; si se aprueba, redirige al comprobante. |
| `/pago?accion=comprobante&pedido=N` | Dueño del pedido o personal | Boleta o factura de demostración, imprimible. |
| `/productos?accion=pedidos` | Con sesión | Historial de pedidos (el cliente ve solo los suyos). |
| `/productos?accion=nuevo`, `editar`, `eliminar` | Administrador y Almacén (eliminar: solo Administrador) | Alta, edición y baja de productos. |
| `/reportes?accion=resumen` y `disponibles` | Personal (no Cliente) | Indicadores y productos disponibles. |
| `/admin` (`?accion=panel`, `pedidos`, `pedido`, `inventario`, `ventas`, `usuarios`, `categorias`, `auditoria`) y `POST /admin` | Personal (según la matriz de abajo) | Módulo de administración. |
| `/informacion` | Público | Información de la tienda; datos técnicos para el personal. |

Quien no ha iniciado sesión y pide una ruta protegida (por ejemplo, comprar) es enviado a `/login` y, al ingresar, vuelve a esa misma ruta. Solo el rol Cliente compra; el personal gestiona inventario y reportes. La navegación refleja los permisos, pero cada controlador los vuelve a comprobar.

## Módulo de administración

El personal (Administrador, Almacén y Consulta) entra a `/admin`: `/inicio` lo redirige ahí. Un Cliente que lo pida recibe un aviso y vuelve a `/inicio`; sin sesión se va a `/login`.

**Funciones**
- **Panel:** ingresos y pedidos de hoy, del mes y totales, pedidos por estado, los 5 productos más vendidos, stock bajo (5 unidades o menos) y los últimos 8 pedidos. Solo cuentan como venta los pedidos con pago aprobado y no anulados.
- **Pedidos:** listado con filtros (estado, cliente y fechas) y detalle. El estado avanza solo al siguiente: Confirmado, En preparación, Enviado, Entregado. Un pedido Confirmado o En preparación se anula con un motivo: queda Anulado, su pago pasa a Reembolsado, se devuelve el stock y se registra una Devolución en el kardex.
- **Inventario:** kardex por producto (Salida por venta, Entrada, Ajuste, Devolución) y registro manual de entradas y ajustes con motivo, sin dejar el stock en negativo.
- **Ventas:** reportes por día, categoría, método de pago y producto, con rango de fechas, y descarga en CSV (`formato=csv`).
- **Usuarios:** crear cuentas (contraseña de 8 a 100 caracteres con letras y números), activar o desactivar, cambiar el rol y restablecer la contraseña. Nadie cambia su propio estado ni rol y el sistema no se queda sin administradores activos. Tras 5 intentos fallidos la cuenta se bloquea 15 minutos; desactivar una cuenta, cambiar su rol o restablecer su contraseña cierra sus sesiones abiertas (las sesiones nuevas siguen siendo válidas).
- **Categorías:** crear, renombrar (sus productos la siguen), activar o desactivar y eliminar (solo si no tiene productos).
- **Auditoría:** accesos, compras y cambios de productos, pedidos, stock, usuarios y categorías, con filtros por usuario, acción y fechas. No guarda contraseñas ni números de tarjeta.

**Permisos por rol**

| Función | Administrador | Almacén | Consulta | Cliente |
| --- | :---: | :---: | :---: | :---: |
| Panel, pedidos, inventario y ventas (ver y descargar CSV) | Sí | Sí | Sí | No |
| Avanzar el estado de un pedido | Sí | Sí | No | No |
| Registrar entradas y ajustes de stock | Sí | Sí | No | No |
| Anular un pedido | Sí | No | No | No |
| Usuarios, categorías y auditoría | Sí | No | No | No |

**Rutas de `/admin`.** GET `accion=`: `panel` (por defecto), `pedidos` (`estado`, `cliente`, `desde`, `hasta` en `yyyy-MM-dd`), `pedido&id=`, `inventario` (`codigo`), `ventas` (`desde`, `hasta`, `formato=csv`), `usuarios`, `categorias` (`editar=id`) y `auditoria` (`usuario`, `tipo`, `desde`, `hasta`). POST `accion=` (con `_csrf`; responde con redirección y un mensaje): `estadoPedido`, `anularPedido`, `ajustarStock`, `crearUsuario`, `estadoUsuario`, `rolUsuario`, `claveUsuario`, `crearCategoria`, `editarCategoria`, `estadoCategoria` y `eliminarCategoria`. Una petición con parámetros que no son UTF-8 válido recibe 400.

**Cómo probarlo.** Con Tomcat recién iniciado (modo memoria) o con `BD_NEXORA` recién creada con `sql/bd_nexora.sql`, porque los valores esperados suponen los datos semilla:

```
bash docs/pruebas/e2e-admin.sh http://localhost:8090/Semana04    # administración
bash docs/pruebas/e2e.sh http://localhost:8090/Semana04          # compra con pasarela
```

Sustituye la URL por la de tu Tomcat (si no la indicas, usan `http://localhost:8090/Semana04`). `e2e-admin.sh` necesita `bash`, `curl` y `perl`.

**Si ya tenías `BD_NEXORA`, ejecuta `sql/bd_nexora.sql` de nuevo:** el esquema cambió y las claves semilla ahora van cifradas (véase "Base de datos").

## Perfiles y credenciales de prueba

| Perfil | Usuario | Contraseña | Alcance |
| --- | --- | --- | --- |
| Administrador | `admin` | `admin123` | Registrar, editar y eliminar productos; ver cualquier comprobante; todo el módulo de administración (incluye anular pedidos, usuarios, categorías y auditoría). |
| Almacén | `almacen` | `almacen123` | Registrar y editar productos; reportes; avanzar pedidos y registrar entradas y ajustes de stock. |
| Cliente | `cliente` | `cliente123` | Comprar, ver sus pedidos y sus comprobantes. |
| Consulta | `invitado` | `invitado` | Solo lectura de productos, reportes, pedidos, inventario y ventas. |

Son cuentas de demostración, iguales en el respaldo en memoria y en `sql/bd_nexora.sql`; en ambos las contraseñas se guardan cifradas.

## Pasarela de pago simulada

La pasarela no contacta ningún banco: valida el formulario como lo haría una real y decide el resultado con los datos de prueba que muestra la propia pantalla de pago.

**Tarjetas de prueba** (titular de 3 a 60 caracteres: letras, espacios, apóstrofo, punto o guion; vencimiento MM/AA vigente, como máximo a 10 años; CVV de 3 dígitos, 4 en American Express):

| Número | Marca | Resultado |
| --- | --- | --- |
| 4111 1111 1111 1111 | Visa | Aprobada |
| 5555 5555 5555 4444 | Mastercard | Aprobada |
| 4000 0000 0000 0002 | Visa | Rechazada: fondos insuficientes |
| 4000 0000 0000 0069 | Visa | Rechazada por el banco emisor |

Cualquier otro número con dígito verificador (Luhn) correcto de Visa, Mastercard o American Express también se aprueba.

**Yape:** celular de 9 dígitos que empiece con 9 y código de aprobación `123456`. Cualquier otro código de 6 dígitos se rechaza.

**Comprobante:** boleta con DNI de 8 dígitos (`B001-000001`) o factura con RUC de 11 dígitos que empiece con 10 o 20 y razón social (`F001-000001`). Los precios del catálogo ya incluyen el IGV de 18 %, que el comprobante desglosa en subtotal e IGV. Cada serie tiene su propio correlativo.

Un pago rechazado no descuenta stock ni crea pedido. Si se aprueba, con SQL Server el stock, el pedido y el pago se confirman en una sola transacción (si algo falla, se revierte todo).

**Seguridad de los datos de pago:** no se almacena el número completo de la tarjeta ni el CVV (tampoco el código de Yape). Del medio de pago solo se guardan la marca, los últimos 4 dígitos y el nombre del titular; el número, el CVV y el código Yape tampoco se devuelven al formulario tras un error. Además, todos los formularios POST llevan un token CSRF (`_csrf`) que valida `SesionFilter`, que también normaliza la ruta y rechaza con 400 las URI con traversal (`;`, `/..`, `\`, `%2e`). Cada checkout lleva un token de un solo uso (`tokenCheckout`) que evita el doble envío y comprueba que el precio no haya cambiado desde que se mostró el total.

## Despliegue en Azure

NEXORA se empaqueta como contenedor (`Dockerfile`, Tomcat 11 con Java 21) y se publica en **Azure Container Apps** con **Azure SQL Database** (oferta gratuita). La infraestructura es código (`infra/main.bicep` y scripts en `infra/`) y GitHub Actions (`.github/workflows/ci.yml`) compila, prueba, construye la imagen, la publica en GHCR y despliega. Guía paso a paso para Azure for Students (verificación local con `docker compose`, creación de recursos, inicialización de la base, OIDC, costos, registros, rotación de claves y eliminación): [`docs/DESPLIEGUE.md`](docs/DESPLIEGUE.md).

```
docker compose up -d --build      # pila real en local: http://localhost:8080 (copia antes .env.example a .env)
bash infra/crear-infra.sh         # crea la infraestructura en Azure (pide confirmación)
```

Variables de entorno de la aplicación (el contenedor las recibe en tiempo de ejecución; nunca se guardan en la imagen):

| Variable | Valores | Efecto |
| --- | --- | --- |
| `NEXORA_ENTORNO` | `desarrollo` (defecto) / `produccion` (imagen Docker) | En producción nunca se usa el respaldo en memoria: si SQL falla, la tienda responde 503 |
| `DB_URL` | cadena JDBC de SQL Server o Azure SQL | Obligatoria en producción |
| `DB_USER` | usuario SQL (`nexora_app` en Azure) | Obligatoria en producción |
| `DB_PASSWORD` | clave del usuario | Obligatoria en producción; en Azure es el secreto `db-password` |
| `NEXORA_DEMO` | `true` / `false` | Cinta "Entorno de demostración" y cuenta demo en el login |
| `NEXORA_VERSION` | sha corto del commit (defecto `dev`) | Versión que informa `/salud` y se usa en `?v=` de CSS y JS |
| `NEXORA_COOKIE_SEGURA` | `true` / `false` (defecto: `true` en producción) | Atributo Secure de la cookie de sesión; `false` solo para probar por `http://localhost` |
| `NEXORA_TOKEN_SALUD` | secreto (en Azure, `salud-token`) | En producción, `/salud?bd=1` exige la cabecera `X-Salud-Token` con este valor (sin la variable responde 403); en desarrollo sigue abierto |

`GET /salud` devuelve 200 con `{"estado","version","entorno"}` sin consultar la base (`/salud?bd=1` sí hace `SELECT 1`) y lo usan las sondas de Azure.
