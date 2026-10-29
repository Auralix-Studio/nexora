/* ============================================================================
 * UNIVERSIDAD PERUANA LOS ANDES - FACULTAD DE INGENIERÍA
 * Curso      : Desarrollo de Aplicaciones I (Semana 05 - Semana 06)
 * Docente    : Mg. Ing. Raúl Fernández Bejarano
 * Estudiante : Alessandro
 * Sistema    : NEXORA - Sistema Web de Control de Inventario y Roles
 * Base Datos : BD_NEXORA (Microsoft SQL Server / Azure SQL Database)
 * Descripción: Script del Modelo Físico (DDL + DML + Roles + Inicio de Sesión)
 * ============================================================================ */

-- 1. CREACIÓN DE LA BASE DE DATOS BD_NEXORA
IF DB_ID(N'BD_NEXORA') IS NULL
BEGIN
    CREATE DATABASE BD_NEXORA;
END
GO

USE BD_NEXORA;
GO

-- ============================================================================
-- 2. ELIMINACIÓN PREVIA DE OBJETOS (SI EXISTEN) PARA RE-EJECUCIÓN LIMPIA
-- ============================================================================
IF OBJECT_ID(N'dbo.sp_validar_usuario', N'P') IS NOT NULL DROP PROCEDURE dbo.sp_validar_usuario; -- heredado: comparaba claves en claro
IF OBJECT_ID(N'dbo.sp_listar_productos', N'P') IS NOT NULL DROP PROCEDURE dbo.sp_listar_productos;
IF OBJECT_ID(N'dbo.sp_registrar_producto', N'P') IS NOT NULL DROP PROCEDURE dbo.sp_registrar_producto;
IF OBJECT_ID(N'dbo.sp_actualizar_producto', N'P') IS NOT NULL DROP PROCEDURE dbo.sp_actualizar_producto;
IF OBJECT_ID(N'dbo.sp_eliminar_producto', N'P') IS NOT NULL DROP PROCEDURE dbo.sp_eliminar_producto;
IF OBJECT_ID(N'dbo.vw_productos_detalle', N'V') IS NOT NULL DROP VIEW dbo.vw_productos_detalle;
IF OBJECT_ID(N'dbo.vw_usuarios_roles', N'V') IS NOT NULL DROP VIEW dbo.vw_usuarios_roles;
IF OBJECT_ID(N'dbo.auditoria', N'U') IS NOT NULL DROP TABLE dbo.auditoria;
IF OBJECT_ID(N'dbo.movimiento_stock', N'U') IS NOT NULL DROP TABLE dbo.movimiento_stock;
IF OBJECT_ID(N'dbo.pago', N'U') IS NOT NULL DROP TABLE dbo.pago;
IF OBJECT_ID(N'dbo.entrega', N'U') IS NOT NULL DROP TABLE dbo.entrega;
IF OBJECT_ID(N'dbo.pedido', N'U') IS NOT NULL DROP TABLE dbo.pedido;
IF OBJECT_ID(N'dbo.producto', N'U') IS NOT NULL DROP TABLE dbo.producto;
IF OBJECT_ID(N'dbo.categoria', N'U') IS NOT NULL DROP TABLE dbo.categoria;
IF OBJECT_ID(N'dbo.sesion_acceso', N'U') IS NOT NULL DROP TABLE dbo.sesion_acceso;
IF OBJECT_ID(N'dbo.usuario', N'U') IS NOT NULL DROP TABLE dbo.usuario;
IF OBJECT_ID(N'dbo.rol', N'U') IS NOT NULL DROP TABLE dbo.rol;
GO

-- ============================================================================
-- 3. MODELO FÍSICO DE TABLAS (DDL - 3ra Forma Normal)
-- ============================================================================

-- Fechas: todas en hora de Perú (UTC-5, sin horario de verano). Azure SQL trabaja en UTC, por eso los
-- valores por defecto convierten SYSDATETIMEOFFSET() a N'SA Pacific Standard Time' (Lima, Bogotá, Quito)
-- en lugar de usar SYSDATETIME(), que depende de la zona del servidor.

-- 3.1 Tabla ROL: Perfiles y niveles de permiso en el sistema NEXORA
CREATE TABLE dbo.rol (
    id_rol       INT IDENTITY(1,1) NOT NULL,
    nombre       NVARCHAR(30)      NOT NULL,
    descripcion  NVARCHAR(150)     NOT NULL,
    nivel_acceso INT               NOT NULL CONSTRAINT DF_rol_nivel DEFAULT (4),
    CONSTRAINT PK_rol PRIMARY KEY CLUSTERED (id_rol),
    CONSTRAINT UQ_rol_nombre UNIQUE (nombre),
    CONSTRAINT CK_rol_nivel CHECK (nivel_acceso IN (1, 2, 3, 4))
);
GO

-- 3.2 Tabla USUARIO: Credenciales de la Ventana de Ingreso vinculadas a un Rol.
-- dni (8 dígitos) y telefono (celular de 9 dígitos que empieza con 9) son del cliente; pueden quedar vacíos.
CREATE TABLE dbo.usuario (
    id_usuario      INT IDENTITY(1,1) NOT NULL,
    username        NVARCHAR(40)      NOT NULL,
    clave           NVARCHAR(120)     NOT NULL,
    nombre_completo NVARCHAR(100)     NOT NULL,
    correo          NVARCHAR(100)     NOT NULL,
    dni             CHAR(8)           NULL,
    telefono        VARCHAR(9)        NULL,
    id_rol          INT               NOT NULL,
    activo          BIT               NOT NULL CONSTRAINT DF_usuario_activo DEFAULT (1),
    fecha_registro  DATETIME2(0)      NOT NULL CONSTRAINT DF_usuario_fecha DEFAULT (CAST(SYSDATETIMEOFFSET() AT TIME ZONE N'SA Pacific Standard Time' AS DATETIME2(0))),
    CONSTRAINT PK_usuario PRIMARY KEY CLUSTERED (id_usuario),
    CONSTRAINT UQ_usuario_username UNIQUE (username),
    CONSTRAINT UQ_usuario_correo UNIQUE (correo),
    CONSTRAINT FK_usuario_rol FOREIGN KEY (id_rol) REFERENCES dbo.rol (id_rol),
    CONSTRAINT CK_usuario_dni CHECK (dni IS NULL OR dni LIKE '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]'),
    CONSTRAINT CK_usuario_telefono CHECK (telefono IS NULL OR telefono LIKE '9[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]')
);
GO

-- 3.3 Tabla SESION_ACCESO: Bitácora de auditoría de inicios de sesión (Login)
CREATE TABLE dbo.sesion_acceso (
    id_sesion     INT IDENTITY(1,1) NOT NULL,
    id_usuario    INT               NOT NULL,
    fecha_ingreso DATETIME2(0)      NOT NULL CONSTRAINT DF_sesion_fecha DEFAULT (CAST(SYSDATETIMEOFFSET() AT TIME ZONE N'SA Pacific Standard Time' AS DATETIME2(0))),
    ip_cliente    VARCHAR(45)       NOT NULL CONSTRAINT DF_sesion_ip DEFAULT ('127.0.0.1'),
    exito         BIT               NOT NULL CONSTRAINT DF_sesion_exito DEFAULT (1),
    CONSTRAINT PK_sesion_acceso PRIMARY KEY CLUSTERED (id_sesion),
    CONSTRAINT FK_sesion_usuario FOREIGN KEY (id_usuario) REFERENCES dbo.usuario (id_usuario)
);
GO

-- 3.4 Tabla CATEGORIA: Clasificación del catálogo de productos NEXORA
CREATE TABLE dbo.categoria (
    id_categoria INT IDENTITY(1,1) NOT NULL,
    nombre       NVARCHAR(50)      NOT NULL,
    descripcion  NVARCHAR(150)     NULL,
    activo       BIT               NOT NULL CONSTRAINT DF_categoria_activo DEFAULT (1),
    CONSTRAINT PK_categoria PRIMARY KEY CLUSTERED (id_categoria),
    CONSTRAINT UQ_categoria_nombre UNIQUE (nombre)
);
GO

-- 3.5 Tabla PRODUCTO: Entidad principal del inventario (Producto.java)
CREATE TABLE dbo.producto (
    codigo         VARCHAR(10)    NOT NULL,
    nombre         NVARCHAR(120)  NOT NULL,
    id_categoria   INT            NOT NULL,
    precio         DECIMAL(10,2)  NOT NULL,
    stock          INT            NOT NULL,
    estado         NVARCHAR(20)   NOT NULL,
    fecha_registro DATETIME2(0)   NOT NULL CONSTRAINT DF_producto_fecha DEFAULT (CAST(SYSDATETIMEOFFSET() AT TIME ZONE N'SA Pacific Standard Time' AS DATETIME2(0))),
    CONSTRAINT PK_producto PRIMARY KEY CLUSTERED (codigo),
    CONSTRAINT FK_producto_categoria FOREIGN KEY (id_categoria) REFERENCES dbo.categoria (id_categoria),
    CONSTRAINT CK_producto_precio CHECK (precio > 0),
    CONSTRAINT CK_producto_stock CHECK (stock >= 0),
    CONSTRAINT CK_producto_estado CHECK (estado IN (N'Disponible', N'Agotado'))
);
GO

-- 3.6 Tabla PEDIDO: Solicitudes y compras realizadas por usuarios con rol Cliente
CREATE TABLE dbo.pedido (
    id_pedido       INT IDENTITY(1,1) NOT NULL,
    id_usuario      INT               NOT NULL,
    codigo_producto VARCHAR(10)       NOT NULL,
    cantidad        INT               NOT NULL,
    precio_unitario DECIMAL(10,2)     NOT NULL,
    fecha_pedido    DATETIME2(0)      NOT NULL CONSTRAINT DF_pedido_fecha DEFAULT (CAST(SYSDATETIMEOFFSET() AT TIME ZONE N'SA Pacific Standard Time' AS DATETIME2(0))),
    estado          NVARCHAR(20)      NOT NULL CONSTRAINT DF_pedido_estado DEFAULT (N'Confirmado'),
    fecha_actualizacion DATETIME2(0)  NULL,
    CONSTRAINT PK_pedido PRIMARY KEY CLUSTERED (id_pedido),
    CONSTRAINT CK_pedido_estado CHECK (estado IN (N'Confirmado', N'En preparación', N'Enviado', N'Entregado', N'Anulado')),
    CONSTRAINT FK_pedido_usuario FOREIGN KEY (id_usuario) REFERENCES dbo.usuario (id_usuario),
    CONSTRAINT FK_pedido_producto FOREIGN KEY (codigo_producto) REFERENCES dbo.producto (codigo),
    CONSTRAINT CK_pedido_cantidad CHECK (cantidad > 0),
    CONSTRAINT CK_pedido_precio CHECK (precio_unitario > 0)
);
GO

-- 3.7 Tabla PAGO: pago autorizado por la pasarela simulada y comprobante emitido (uno por pedido)
-- Pago simulado: nunca se almacena el número completo de la tarjeta ni el CVV
-- (solo la marca y los últimos 4 dígitos de la tarjeta o del celular Yape).
CREATE TABLE dbo.pago (
    id_pago             INT IDENTITY(1,1) NOT NULL,
    id_pedido           INT               NOT NULL,
    metodo              VARCHAR(10)       NOT NULL,
    marca               NVARCHAR(20)      NOT NULL,
    ultimos_digitos     CHAR(4)           NOT NULL,
    titular             NVARCHAR(120)     NULL,
    monto               DECIMAL(10,2)     NOT NULL,
    estado              NVARCHAR(20)      NOT NULL,
    codigo_autorizacion VARCHAR(12)       NOT NULL,
    codigo_operacion    VARCHAR(30)       NOT NULL,
    tipo_comprobante    NVARCHAR(10)      NOT NULL,
    numero_comprobante  VARCHAR(15)       NOT NULL,
    documento_cliente   VARCHAR(11)       NOT NULL,
    razon_social        NVARCHAR(150)     NULL,
    fecha_pago          DATETIME2(0)      NOT NULL CONSTRAINT DF_pago_fecha DEFAULT (CAST(SYSDATETIMEOFFSET() AT TIME ZONE N'SA Pacific Standard Time' AS DATETIME2(0))),
    CONSTRAINT PK_pago PRIMARY KEY CLUSTERED (id_pago),
    CONSTRAINT UQ_pago_pedido UNIQUE (id_pedido),
    CONSTRAINT UQ_pago_comprobante UNIQUE (numero_comprobante),
    CONSTRAINT FK_pago_pedido FOREIGN KEY (id_pedido) REFERENCES dbo.pedido (id_pedido),
    CONSTRAINT CK_pago_metodo CHECK (metodo IN ('TARJETA', 'YAPE')),
    CONSTRAINT CK_pago_monto CHECK (monto > 0),
    CONSTRAINT CK_pago_estado CHECK (estado IN (N'Aprobado', N'Rechazado', N'Reembolsado')),
    CONSTRAINT CK_pago_comprobante CHECK (tipo_comprobante IN (N'Boleta', N'Factura'))
);
GO

-- 3.8 Tabla MOVIMIENTO_STOCK: kardex de entradas y salidas de cada producto.
-- cantidad con signo: positiva entra al almacén, negativa sale (ventas y ajustes a la baja).
CREATE TABLE dbo.movimiento_stock (
    id_movimiento    INT IDENTITY(1,1) NOT NULL,
    codigo_producto  VARCHAR(10)       NOT NULL,
    tipo             NVARCHAR(15)      NOT NULL,
    cantidad         INT               NOT NULL,
    stock_resultante INT               NOT NULL,
    motivo           NVARCHAR(200)     NOT NULL,
    id_usuario       INT               NULL,
    fecha            DATETIME2(0)      NOT NULL CONSTRAINT DF_movimiento_fecha DEFAULT (CAST(SYSDATETIMEOFFSET() AT TIME ZONE N'SA Pacific Standard Time' AS DATETIME2(0))),
    CONSTRAINT PK_movimiento_stock PRIMARY KEY CLUSTERED (id_movimiento),
    CONSTRAINT FK_movimiento_producto FOREIGN KEY (codigo_producto) REFERENCES dbo.producto (codigo),
    CONSTRAINT FK_movimiento_usuario FOREIGN KEY (id_usuario) REFERENCES dbo.usuario (id_usuario),
    CONSTRAINT CK_movimiento_tipo CHECK (tipo IN (N'Entrada', N'Salida', N'Ajuste', N'Devolución')),
    CONSTRAINT CK_movimiento_cantidad CHECK (cantidad <> 0),
    CONSTRAINT CK_movimiento_stock CHECK (stock_resultante >= 0)
);
GO

-- 3.9 Tabla AUDITORIA: acciones del personal y accesos (sin FK para registrar también intentos fallidos).
CREATE TABLE dbo.auditoria (
    id_auditoria INT IDENTITY(1,1) NOT NULL,
    username     NVARCHAR(40)      NOT NULL,
    rol          NVARCHAR(30)      NULL,
    accion       NVARCHAR(40)      NOT NULL,
    entidad      NVARCHAR(80)      NULL,
    detalle      NVARCHAR(400)     NULL,
    ip           VARCHAR(45)       NULL,
    fecha        DATETIME2(0)      NOT NULL CONSTRAINT DF_auditoria_fecha DEFAULT (CAST(SYSDATETIMEOFFSET() AT TIME ZONE N'SA Pacific Standard Time' AS DATETIME2(0))),
    CONSTRAINT PK_auditoria PRIMARY KEY CLUSTERED (id_auditoria)
);
GO

-- 3.10 Tabla ENTREGA: datos de entrega del pedido (1:1 con dbo.pedido). Se inserta en la misma transacción
-- de la compra. tipo_envio: DOMICILIO o RETIRO (en tienda; la dirección es entonces la del almacén).
-- Longitudes coherentes con las validaciones del checkout (PagoServlet).
CREATE TABLE dbo.entrega (
    id_pedido          INT           NOT NULL,
    tipo_envio         VARCHAR(10)   NOT NULL,
    departamento       NVARCHAR(50)  NOT NULL,
    distrito           NVARCHAR(80)  NOT NULL,
    direccion          NVARCHAR(200) NOT NULL,
    referencia         NVARCHAR(200) NULL,
    telefono           VARCHAR(20)   NULL,
    correo_comprobante NVARCHAR(100) NULL,
    notas              NVARCHAR(300) NULL,
    CONSTRAINT PK_entrega PRIMARY KEY CLUSTERED (id_pedido),
    CONSTRAINT FK_entrega_pedido FOREIGN KEY (id_pedido) REFERENCES dbo.pedido (id_pedido),
    CONSTRAINT CK_entrega_tipo CHECK (tipo_envio IN ('DOMICILIO', 'RETIRO'))
);
GO

CREATE NONCLUSTERED INDEX IX_producto_nombre ON dbo.producto (nombre);
CREATE NONCLUSTERED INDEX IX_producto_id_categoria ON dbo.producto (id_categoria);
CREATE NONCLUSTERED INDEX IX_usuario_id_rol ON dbo.usuario (id_rol);
CREATE NONCLUSTERED INDEX IX_pedido_id_usuario ON dbo.pedido (id_usuario);
CREATE NONCLUSTERED INDEX IX_pedido_estado_fecha ON dbo.pedido (estado, fecha_pedido);
CREATE NONCLUSTERED INDEX IX_movimiento_producto_fecha ON dbo.movimiento_stock (codigo_producto, fecha);
CREATE NONCLUSTERED INDEX IX_auditoria_fecha ON dbo.auditoria (fecha);
GO

-- ============================================================================
-- 4. CARGA DE DATOS INICIALES (DML - Roles, Usuarios, Productos y Pedidos)
-- ============================================================================

-- 4.1 Los 4 Roles del sistema NEXORA (incluyendo Cliente)
INSERT INTO dbo.rol (nombre, descripcion, nivel_acceso) VALUES
(N'Administrador', N'Control total: registrar, editar, eliminar productos y consultar reportes',          1),
(N'Almacén',       N'Operación de inventario: registrar, editar productos y consultar reportes',          2),
(N'Cliente',       N'Catálogo de cliente: explorar productos, realizar compras/pedidos y ver mis pedidos', 3),
(N'Consulta',      N'Solo lectura: listar, buscar productos y visualizar reportes',                        4);
GO

-- 4.2 Usuarios para la Ventana de Ingreso (UsuarioService.java)
-- Las contraseñas se guardan cifradas con PBKDF2-SHA256 (util/Seguridad.java), nunca en claro.
-- Contraseñas de prueba: admin123, almacen123, cliente123, invitado.
INSERT INTO dbo.usuario (username, clave, nombre_completo, correo, id_rol, activo) VALUES
(N'admin',    N'pbkdf2_sha256$120000$cTJBpayNR/eRN1bQ7pdNJA==$IUplO8GZpnLIKBLBC4qL68kmcXqaaxKrWsTPz4Xudfg=', N'Alessandro',     N'admin@nexora.pe',    1, 1),
(N'almacen',  N'pbkdf2_sha256$120000$0TpA4pqWwahOTyiUjAzQKg==$+6rnPaMOoGmZJXeKPLfs1hhn9WQb9rb43+gcofNykEE=', N'Carlos Vega',    N'almacen@nexora.pe',  2, 1),
(N'cliente',  N'pbkdf2_sha256$120000$2TvUVhdYzAsU9uZBmwj8tQ==$KEz2VRwEszhs3ruFw8YSGINXw3z44Vy6vvPnoZOH0iw=', N'Andrea Paredes', N'cliente@nexora.pe',  3, 1),
(N'invitado', N'pbkdf2_sha256$120000$rZkmGwRDouCfW40cDSyI5Q==$ak+IlCK5Zm/Ldx3y6EbFGWQlgFaBGbCkW7HGF4xbqhg=', N'Lucía Torres',   N'consulta@nexora.pe', 4, 1);
GO

-- DNI y celular del cliente de muestra (los mismos del respaldo en memoria y de los pagos iniciales)
UPDATE dbo.usuario SET dni = '70451236', telefono = '987654321' WHERE username = N'cliente';
GO

-- 4.3 Registro inicial en bitácora de inicios de sesión
INSERT INTO dbo.sesion_acceso (id_usuario, ip_cliente, exito) VALUES
(1, '127.0.0.1', 1),
(2, '127.0.0.1', 1),
(3, '127.0.0.1', 1);
GO

-- 4.4 Categorías oficiales del sistema NEXORA
INSERT INTO dbo.categoria (nombre, descripcion) VALUES
(N'Computadoras',   N'Laptops, equipos de escritorio y estaciones de trabajo'),
(N'Accesorios',     N'Periféricos: mouse, teclados, audífonos y webcams'),
(N'Monitores',      N'Pantallas LED, IPS y monitores profesionales'),
(N'Impresoras',     N'Impresoras multifuncionales y de tinta continua'),
(N'Almacenamiento', N'Unidades de estado sólido SSD, discos duros y memorias USB'),
(N'Redes',          N'Routers inalámbricos, switches y equipos de conectividad');
GO

-- 4.5 Los 12 productos iniciales del inventario NEXORA
INSERT INTO dbo.producto (codigo, nombre, id_categoria, precio, stock, estado) VALUES
('P001', N'Laptop Lenovo IdeaPad 3',   1, 2500.00, 10, N'Disponible'),
('P002', N'Mouse Logitech M170',       2,   80.00, 25, N'Disponible'),
('P003', N'Teclado Redragon Kumara',   2,  150.00,  0, N'Agotado'),
('P004', N'Monitor LG 24" IPS',        3,  650.00,  8, N'Disponible'),
('P005', N'Impresora Epson L3250',     4,  799.00,  4, N'Disponible'),
('P006', N'SSD Kingston A400 480GB',   5,  165.00, 30, N'Disponible'),
('P007', N'USB SanDisk Ultra 64GB',    5,   35.00, 60, N'Disponible'),
('P008', N'Audífonos HyperX Cloud II', 2,  280.00,  0, N'Agotado'),
('P009', N'Router TP-Link Archer C6',  6,  189.00, 12, N'Disponible'),
('P010', N'Laptop HP 15 Ryzen 5',      1, 2890.00,  5, N'Disponible'),
('P011', N'Webcam Logitech C920',      2,  320.00,  7, N'Disponible'),
('P012', N'Switch TP-Link 8 puertos',  6,   95.00,  0, N'Agotado');
GO

-- 4.6 Pedidos iniciales del rol Cliente (dbo.pedido)
INSERT INTO dbo.pedido (id_usuario, codigo_producto, cantidad, precio_unitario, fecha_pedido, estado, fecha_actualizacion) VALUES
(3, 'P002', 2,  80.00, '2026-10-18T18:30:00', N'Entregado', '2026-10-20T11:00:00'),
(3, 'P006', 1, 165.00, '2026-10-18T19:15:00', N'Enviado',   '2026-10-19T09:30:00');
GO

-- 4.7 Entregas de los pedidos iniciales (a domicilio, como en el respaldo en memoria)
INSERT INTO dbo.entrega (id_pedido, tipo_envio, departamento, distrito, direccion, referencia, telefono,
                         correo_comprobante, notas) VALUES
(1, 'DOMICILIO', N'Lima', N'Miraflores', N'Av. Larco 345, Miraflores', N'Frente al parque Kennedy', '987654321',
    N'cliente@nexora.pe', NULL),
(2, 'DOMICILIO', N'Lima', N'Miraflores', N'Av. Larco 345, Miraflores', N'Frente al parque Kennedy', '987654321',
    N'cliente@nexora.pe', NULL);
GO

-- 4.8 Pagos aprobados de los pedidos iniciales (Visa 1111, boleta a DNI 70451236)
-- Pago simulado: nunca se almacena el número completo ni el CVV
INSERT INTO dbo.pago (id_pedido, metodo, marca, ultimos_digitos, titular, monto, estado,
                      codigo_autorizacion, codigo_operacion, tipo_comprobante, numero_comprobante,
                      documento_cliente, razon_social, fecha_pago) VALUES
(1, 'TARJETA', N'Visa', '1111', N'Andrea Paredes', 160.00, N'Aprobado',
    '482915', 'OP-20261018183012-4821', N'Boleta', 'B001-000001', '70451236', NULL, '2026-10-18T18:30:00'),
(2, 'TARJETA', N'Visa', '1111', N'Andrea Paredes', 165.00, N'Aprobado',
    '730264', 'OP-20261018191547-7305', N'Boleta', 'B001-000002', '70451236', NULL, '2026-10-18T19:15:00');
GO

-- 4.9 Kardex: salidas por venta de los pedidos iniciales (el stock de 4.5 ya las descuenta)
INSERT INTO dbo.movimiento_stock (codigo_producto, tipo, cantidad, stock_resultante, motivo, id_usuario, fecha) VALUES
('P002', N'Salida', -2, 25, N'Venta PED-0001', 3, '2026-10-18T18:30:00'),
('P006', N'Salida', -1, 30, N'Venta PED-0002', 3, '2026-10-18T19:15:00');
GO

-- ============================================================================
-- 5. VISTAS Y PROCEDIMIENTOS ALMACENADOS (Login + Roles + CRUD Productos)
-- ============================================================================

-- 5.1 Vista de usuarios con su rol y nivel de acceso
CREATE VIEW dbo.vw_usuarios_roles
AS
SELECT
    u.id_usuario,
    u.username,
    u.nombre_completo,
    u.correo,
    r.id_rol,
    r.nombre AS rol,
    r.nivel_acceso,
    r.descripcion AS permisos,
    u.activo,
    u.fecha_registro
FROM dbo.usuario u
INNER JOIN dbo.rol r ON u.id_rol = r.id_rol;
GO

-- 5.2 Vista detallada de productos con categoría y valor de inventario
CREATE VIEW dbo.vw_productos_detalle
AS
SELECT
    p.codigo,
    p.nombre,
    c.nombre AS categoria,
    p.precio,
    p.stock,
    p.estado,
    CAST(p.precio * p.stock AS DECIMAL(12,2)) AS valor_inventario,
    p.fecha_registro
FROM dbo.producto p
INNER JOIN dbo.categoria c ON p.id_categoria = c.id_categoria;
GO

-- 5.3 Procedimiento para listar todos los productos
CREATE PROCEDURE dbo.sp_listar_productos
AS
BEGIN
    SET NOCOUNT ON;
    SELECT codigo, nombre, categoria, precio, stock, estado
    FROM dbo.vw_productos_detalle
    ORDER BY codigo ASC;
END
GO

-- 5.4 Procedimiento para registrar un nuevo producto
CREATE PROCEDURE dbo.sp_registrar_producto
    @codigo    VARCHAR(10),
    @nombre    NVARCHAR(120),
    @categoria NVARCHAR(50),
    @precio    DECIMAL(10,2),
    @stock     INT
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @id_cat INT;
    SELECT @id_cat = id_categoria FROM dbo.categoria WHERE LOWER(nombre) = LOWER(@categoria);

    IF @id_cat IS NULL
        THROW 50001, 'La categoría especificada no existe en BD_NEXORA.', 1;

    DECLARE @estado NVARCHAR(20) = CASE WHEN @stock > 0 THEN N'Disponible' ELSE N'Agotado' END;

    INSERT INTO dbo.producto (codigo, nombre, id_categoria, precio, stock, estado)
    VALUES (UPPER(LTRIM(RTRIM(@codigo))), LTRIM(RTRIM(@nombre)), @id_cat, @precio, @stock, @estado);
END
GO

-- 5.5 Procedimiento para actualizar un producto existente
CREATE PROCEDURE dbo.sp_actualizar_producto
    @codigo    VARCHAR(10),
    @nombre    NVARCHAR(120),
    @categoria NVARCHAR(50),
    @precio    DECIMAL(10,2),
    @stock     INT
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @id_cat INT;
    SELECT @id_cat = id_categoria FROM dbo.categoria WHERE LOWER(nombre) = LOWER(@categoria);

    IF @id_cat IS NULL
        THROW 50001, 'La categoría especificada no existe en BD_NEXORA.', 1;

    DECLARE @estado NVARCHAR(20) = CASE WHEN @stock > 0 THEN N'Disponible' ELSE N'Agotado' END;

    UPDATE dbo.producto
    SET nombre       = LTRIM(RTRIM(@nombre)),
        id_categoria = @id_cat,
        precio       = @precio,
        stock        = @stock,
        estado       = @estado
    WHERE codigo = UPPER(LTRIM(RTRIM(@codigo)));
END
GO

-- 5.6 Procedimiento para eliminar un producto por código
CREATE PROCEDURE dbo.sp_eliminar_producto
    @codigo VARCHAR(10)
AS
BEGIN
    SET NOCOUNT ON;
    DELETE FROM dbo.producto
    WHERE codigo = UPPER(LTRIM(RTRIM(@codigo)));
END
GO

-- ============================================================================
-- 6. CONSULTAS DE VERIFICACIÓN DEL SISTEMA NEXORA
-- ============================================================================
SELECT * FROM dbo.rol;
SELECT * FROM dbo.vw_usuarios_roles;
SELECT * FROM dbo.sesion_acceso;
SELECT * FROM dbo.categoria;
SELECT * FROM dbo.vw_productos_detalle ORDER BY codigo;
SELECT * FROM dbo.pedido;
SELECT * FROM dbo.entrega;
SELECT * FROM dbo.pago;
SELECT * FROM dbo.movimiento_stock;
SELECT * FROM dbo.auditoria;
GO
