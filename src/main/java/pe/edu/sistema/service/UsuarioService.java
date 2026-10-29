package pe.edu.sistema.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import pe.edu.sistema.model.Usuario;
import pe.edu.sistema.util.ConexionBD;
import pe.edu.sistema.util.ConfigApp;
import pe.edu.sistema.util.Seguridad;

/**
 * Servicio de acceso, registro de clientes, control de roles y administración de usuarios de NEXORA.
 * Consulta las tablas dbo.usuario y dbo.rol en Microsoft SQL Server / Azure SQL (BD_NEXORA).
 * Si el servidor de base de datos no está activo, en desarrollo utiliza el respaldo en memoria. En producción
 * ({@link ConfigApp#esProduccion()}) nunca lo usa ni lo modifica: sin base configurada o ante un fallo de SQL
 * lanza {@link pe.edu.sistema.util.DatosNoDisponiblesException} (una {@link IllegalStateException}).
 * Las contraseñas se guardan cifradas ({@link Seguridad}); las heredadas en claro o con menos iteraciones se recifran al ingresar.
 * Tras {@link #MAX_INTENTOS} fallos seguidos la cuenta se bloquea {@link #MINUTOS_BLOQUEO} minutos.
 */
public class UsuarioService {

    public static final List<String> ROLES = List.of(
            Usuario.ROL_ADMIN, Usuario.ROL_ALMACEN, Usuario.ROL_CLIENTE, Usuario.ROL_CONSULTA);

    /** Intentos fallidos seguidos que bloquean la cuenta. */
    public static final int MAX_INTENTOS = 5;
    /** Duración del bloqueo temporal. */
    public static final int MINUTOS_BLOQUEO = 15;

    // Posiciones de los datos de una cuenta en el respaldo en memoria
    private static final int I_ID = 0;
    private static final int I_CLAVE = 1;
    private static final int I_NOMBRE = 2;
    private static final int I_CORREO = 3;
    private static final int I_ROL = 4;
    private static final int I_ACTIVO = 5;
    private static final int I_DNI = 6;
    private static final int I_TELEFONO = 7;

    private static final String MSG_SIN_PERMISO = "Solo el Administrador puede gestionar usuarios.";
    private static final String MSG_NO_ENCONTRADO = "No se encontró el usuario solicitado.";
    private static final String MSG_ROL_INVALIDO = "Elige un rol válido.";
    private static final String MSG_ERROR_BD =
            "No se pudo completar la operación en BD_NEXORA. Intenta nuevamente en unos momentos.";
    private static final String MSG_ESPACIOS = "La contraseña no puede empezar ni terminar con espacios.";

    private static final Pattern PATRON_USUARIO = Pattern.compile("[a-z0-9._-]{3,40}");
    private static final Pattern PATRON_CORREO = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");

    /** Hash de una clave que no corresponde a ninguna cuenta: iguala el tiempo de respuesta si el usuario no existe. */
    private static final String HASH_FALSO = Seguridad.hashClave("clave-sin-cuenta-nexora");

    /**
     * Respaldo en memoria mutable (solo desarrollo): username -> {id, clave cifrada, nombreCompleto, correo, rol,
     * activo ("1"/"0"), dni, teléfono (los dos últimos pueden ser null)}.
     * Es estático y lo comparten todas las instancias del servicio, así que todo acceso
     * (lectura, escritura o iteración) debe hacerse dentro de {@code synchronized (USUARIOS_MEMORIA)}.
     * Los arreglos guardados no se modifican nunca: un cambio coloca una copia nueva.
     */
    private static final Map<String, String[]> USUARIOS_MEMORIA = new LinkedHashMap<>();

    static {
        USUARIOS_MEMORIA.put("admin",    semilla("1", "admin123",   "Alessandro",     "admin@nexora.pe",    Usuario.ROL_ADMIN));
        USUARIOS_MEMORIA.put("almacen",  semilla("2", "almacen123", "Carlos Vega",    "almacen@nexora.pe",  Usuario.ROL_ALMACEN));
        USUARIOS_MEMORIA.put("cliente",  semilla("3", "cliente123", "Andrea Paredes", "cliente@nexora.pe",  Usuario.ROL_CLIENTE,
                "70451236", "987654321"));
        USUARIOS_MEMORIA.put("invitado", semilla("4", "invitado",   "Lucía Torres",   "consulta@nexora.pe", Usuario.ROL_CONSULTA));
    }

    /** Fallos acumulados de un usuario y, si llegó al máximo, el instante (ms) en que termina el bloqueo. */
    private record Intentos(int fallos, long bloqueadoHasta) {
    }

    /** Contadores de intentos fallidos por usuario normalizado; compartido por todas las instancias. */
    private static final Map<String, Intentos> INTENTOS = new ConcurrentHashMap<>();
    /** Tope de usuarios distintos en {@link #INTENTOS}: pasado este valor se descartan los que no están bloqueados. */
    private static final int MAX_USUARIOS_RASTREADOS = 5000;

    /** Secuencia global de revocaciones: cada una toma el número siguiente; las sesiones guardan la marca con que nacieron. */
    private static final AtomicLong SECUENCIA = new AtomicLong();
    /** Usuario normalizado -> número de secuencia de su última revocación (desactivación, cambio de rol o de clave). */
    private static final Map<String, Long> REVOCADO_EN = new ConcurrentHashMap<>();

    /** Resultado de un intento de ingreso: la cuenta (null si falló) y si este fallo provocó el bloqueo. */
    public record ResultadoAcceso(Usuario usuario, boolean bloqueoNuevo) {
    }

    private static final ResultadoAcceso FALLO = new ResultadoAcceso(null, false);

    private static final String SQL_AUTENTICAR =
            "SELECT u.id_usuario, u.username, u.nombre_completo, u.correo, r.nombre AS rol, u.activo, u.clave, "
            + "u.dni, u.telefono "
            + "FROM dbo.usuario u "
            + "INNER JOIN dbo.rol r ON u.id_rol = r.id_rol "
            + "WHERE u.username = ? COLLATE Latin1_General_100_BIN2 AND u.activo = 1";

    private static final String SQL_USUARIOS =
            "SELECT u.id_usuario, u.username, u.nombre_completo, u.correo, r.nombre AS rol, u.activo, "
            + "u.dni, u.telefono "
            + "FROM dbo.usuario u "
            + "INNER JOIN dbo.rol r ON u.id_rol = r.id_rol ";

    private static final String SQL_LISTAR = SQL_USUARIOS + "ORDER BY r.nivel_acceso, u.username";

    private static final String SQL_BUSCAR_ID = SQL_USUARIOS + "WHERE u.id_usuario = ?";

    private static final String SQL_REGISTRAR_CLIENTE =
            "INSERT INTO dbo.usuario (username, clave, nombre_completo, correo, dni, telefono, id_rol, activo) "
            + "VALUES (?, ?, ?, ?, ?, ?, (SELECT id_rol FROM dbo.rol WHERE LOWER(nombre) = LOWER('Cliente')), 1)";

    private static final String SQL_CREAR_USUARIO =
            "INSERT INTO dbo.usuario (username, clave, nombre_completo, correo, id_rol, activo) "
            + "VALUES (?, ?, ?, ?, (SELECT id_rol FROM dbo.rol WHERE LOWER(nombre) = LOWER(?)), 1)";

    private static final String SQL_DUPLICADOS =
            "SELECT username, correo FROM dbo.usuario WHERE LOWER(username) = ? OR LOWER(correo) = ?";

    private static final String SQL_CONTAR_ADMINISTRADORES =
            "SELECT COUNT(*) FROM dbo.usuario u INNER JOIN dbo.rol r ON u.id_rol = r.id_rol "
            + "WHERE LOWER(r.nombre) = LOWER(?) AND u.activo = 1";

    private static final String SQL_ACTUALIZAR_ACTIVO = "UPDATE dbo.usuario SET activo = ? WHERE id_usuario = ?";

    private static final String SQL_ACTUALIZAR_ROL =
            "UPDATE dbo.usuario SET id_rol = (SELECT id_rol FROM dbo.rol WHERE LOWER(nombre) = LOWER(?)) "
            + "WHERE id_usuario = ?";

    private static final String SQL_ACTUALIZAR_CLAVE = "UPDATE dbo.usuario SET clave = ? WHERE id_usuario = ?";

    /** Recifra la clave solo si no cambió mientras tanto (por ejemplo, por un restablecimiento del administrador). */
    private static final String SQL_RECIFRAR_CLAVE =
            "UPDATE dbo.usuario SET clave = ? WHERE id_usuario = ? AND clave = ?";

    private static final String SQL_REGISTRAR_SESION =
            "INSERT INTO dbo.sesion_acceso (id_usuario, ip_cliente, exito) VALUES (?, ?, 1)";

    // ---------------------------------------------------------------- acceso

    /**
     * Valida las credenciales y el rol en la Ventana de Ingreso de NEXORA.
     * Devuelve null si los datos son incorrectos, la cuenta está inactiva o bloqueada, o el rol no coincide.
     * Cada fallo de credenciales suma al contador de bloqueo; un acceso correcto lo reinicia.
     *
     * @see #intentarAcceso(String, String, String, String)
     */
    public Usuario autenticarUsuario(String usuario, String clave, String rolRequerido, String ipCliente) {
        return intentarAcceso(usuario, clave, rolRequerido, ipCliente).usuario();
    }

    /**
     * Igual que {@link #autenticarUsuario} pero informa además si este fallo fue el que bloqueó la cuenta
     * (para auditar el bloqueo una sola vez). El usuario escrito se normaliza (NFKC, sin espacios, en minúsculas);
     * si no cumple el formato de usuario se trata como un fallo más sin consultar la base de datos ni
     * crear contadores nuevos.
     */
    public ResultadoAcceso intentarAcceso(String usuario, String clave, String rolRequerido, String ipCliente) {
        if (usuario == null || clave == null) {
            return FALLO;
        }
        String userNorm = normalizar(usuario);
        if (!PATRON_USUARIO.matcher(userNorm).matches()) {
            // Variantes con caracteres invisibles o formatos imposibles: mismo tiempo de respuesta, sin tocar la BD
            gastarTiempo(clave);
            return FALLO;
        }
        String llave = llaveIntentos(userNorm);
        if (milisRestantes(llave) > 0) {
            return FALLO;
        }
        Usuario encontrado = null;

        // 1. Intentar autenticación contra MS SQL Server / Azure SQL Database (BD_NEXORA).
        //    En producción usarSql() lanza si no hay base configurada y un fallo de SQL no cae a la memoria.
        if (ConexionBD.usarSql()) {
            try (Connection cn = ConexionBD.obtenerConexion();
                 PreparedStatement ps = cn.prepareStatement(SQL_AUTENTICAR)) {
                ps.setString(1, userNorm);
                Usuario candidato = null;
                String guardada = null;
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        candidato = mapear(rs);
                        guardada = rs.getString("clave");
                    }
                }
                if (candidato == null) {
                    gastarTiempo(clave);
                } else if (claveCoincide(clave, guardada)) {
                    encontrado = candidato;
                    if (Seguridad.necesitaRecifrar(guardada)) {
                        recifrarClaveSql(cn, candidato.getIdUsuario(), clave, guardada);
                    }
                    registrarBitacoraSesion(cn, candidato.getIdUsuario(), ipCliente);
                }
            } catch (SQLException ex) {
                ConexionBD.alFallarSinRespaldo(ex);
            }
        }

        // 2. Respaldo en memoria si SQL Server no está activo (solo desarrollo)
        if (encontrado == null && !ConexionBD.estaDisponible()) {
            String[] datos;
            synchronized (USUARIOS_MEMORIA) {
                datos = USUARIOS_MEMORIA.get(userNorm);
            }
            // Los arreglos guardados no se modifican nunca: basta con leerlos fuera del candado
            if (datos != null && "1".equals(datos[I_ACTIVO])) {
                if (claveCoincide(clave, datos[I_CLAVE])) {
                    encontrado = aUsuario(userNorm, datos);
                    if (Seguridad.necesitaRecifrar(datos[I_CLAVE])) {
                        recifrarClaveMemoria(userNorm, datos, clave);
                    }
                }
            } else {
                gastarTiempo(clave);
            }
        }

        if (encontrado == null) {
            return new ResultadoAcceso(null, registrarFallo(llave));
        }

        // 3. Verificar coincidencia de rol si el usuario seleccionó uno específico en la Ventana de Ingreso
        if (rolRequerido != null && !rolRequerido.isBlank() && !"todos".equalsIgnoreCase(rolRequerido)
                && !encontrado.getRol().equalsIgnoreCase(rolRequerido.trim())) {
            return FALLO;
        }

        INTENTOS.remove(llave);
        return new ResultadoAcceso(encontrado, false);
    }

    /**
     * Marca actual de la secuencia de revocaciones. El controlador la toma ANTES de autenticar y la guarda
     * en la sesión nueva ({@code marcaSesion}): una revocación posterior tendrá un número mayor.
     */
    public static long marcaActual() {
        return SECUENCIA.get();
    }

    /**
     * true si la cuenta se desactivó, cambió de rol o de clave después de que naciera la sesión: el filtro
     * cierra esa sesión. Un ingreso posterior no la rehabilita (su marca es mayor que la revocación).
     * Una sesión sin marca (anterior a este esquema) se revoca si hay alguna revocación para el usuario.
     */
    public static boolean sesionRevocada(String username, Long marcaSesion) {
        if (username == null) {
            return false;
        }
        Long revocadoEn = REVOCADO_EN.get(normalizar(username));
        if (revocadoEn == null) {
            return false;
        }
        return marcaSesion == null || marcaSesion < revocadoEn;
    }

    /** Anota que las sesiones abiertas del usuario hasta este instante dejan de valer. */
    private static void revocarSesiones(String username) {
        REVOCADO_EN.put(normalizar(username), SECUENCIA.incrementAndGet());
    }

    /**
     * Registra una nueva cuenta con el rol Cliente desde la Ventana de Ingreso.
     *
     * @return mensaje de error si los datos son inválidos o el usuario ya existe; cadena vacía si fue exitoso
     */
    public String registrarCliente(String nombreCompleto, String username, String clave) {
        return registrarCliente(username, clave, nombreCompleto, null);
    }

    public String registrarCliente(String username, String clave, String nombreCompleto, String correo) {
        return registrarCliente(username, clave, nombreCompleto, correo, null, null);
    }

    /**
     * Registra una cuenta de Cliente con DNI y celular (opcionales: null o vacío = no se guardan).
     * El DNI debe tener 8 dígitos y el celular 9 dígitos empezando con 9. En producción solo opera sobre SQL
     * (sin respaldo en memoria): duplicados y alta se resuelven en dbo.usuario.
     *
     * @return mensaje de error si los datos son inválidos o el usuario ya existe; cadena vacía si fue exitoso
     */
    public String registrarCliente(String username, String clave, String nombreCompleto, String correo,
            String dni, String telefono) {
        String u = normalizar(username);
        String n = nombreCompleto == null ? "" : nombreCompleto.trim();
        String m = correo == null || correo.isBlank() ? (u + "@cliente.nexora.pe") : normalizar(correo);
        String documento = dni == null || dni.isBlank() ? null : dni.trim();
        String celular = telefono == null || telefono.isBlank() ? null : telefono.trim();

        if (!PATRON_USUARIO.matcher(u).matches()) {
            return "El usuario debe tener entre 3 y 40 caracteres: letras minúsculas, números, punto, guion o guion bajo.";
        }
        // La clave se guarda tal cual la escribió el usuario (cifrada), con las mismas reglas que crearUsuario
        String c = clave == null ? "" : clave;
        String errorClave = validarClave(c);
        if (errorClave != null) {
            return errorClave;
        }
        if (n.isEmpty()) {
            return "Ingrese el nombre completo del cliente.";
        }
        if (n.length() > 100) {
            return "El nombre completo no puede superar 100 caracteres.";
        }
        if (m.length() > 100 || !PATRON_CORREO.matcher(m).matches()) {
            return "Ingresa un correo válido.";
        }
        if (documento != null && !documento.matches("\\d{8}")) {
            return "El DNI debe contener exactamente 8 dígitos numéricos.";
        }
        if (celular != null && !celular.matches("9\\d{8}")) {
            return "El celular debe contener 9 dígitos y empezar con 9.";
        }

        // El cifrado es lento a propósito: se hace antes de tomar el candado
        String hash = Seguridad.hashClave(c);

        if (ConfigApp.esProduccion()) {
            // Sin respaldo en memoria: usarSql() lanza si no hay base configurada
            ConexionBD.usarSql();
            try {
                String duplicado = duplicado(true, u, m);
                if (duplicado != null) {
                    return duplicado;
                }
                insertarClienteSql(u, hash, n, m, documento, celular);
            } catch (SQLException ex) {
                ConexionBD.alFallar(ex);
                return "No se pudo registrar el cliente en BD_NEXORA (verifique que el usuario o correo no esté duplicado).";
            }
            INTENTOS.remove(llaveIntentos(u));
            return "";
        }

        // Comprobar, insertar y registrar en memoria es una sola operación frente a otros registros
        synchronized (USUARIOS_MEMORIA) {
            if (USUARIOS_MEMORIA.containsKey(u)) {
                return "El nombre de usuario '" + u + "' ya está registrado.";
            }
            if (correoEnMemoria(m)) {
                return "El correo '" + m + "' ya está registrado.";
            }

            if (ConexionBD.estaDisponible()) {
                try {
                    insertarClienteSql(u, hash, n, m, documento, celular);
                } catch (SQLException ex) {
                    return "No se pudo registrar el cliente en BD_NEXORA (verifique que el usuario o correo no esté duplicado).";
                }
            }

            USUARIOS_MEMORIA.put(u, new String[]{siguienteIdMemoria(), hash, n, m, Usuario.ROL_CLIENTE, "1",
                documento, celular});
            // Una cuenta nueva empieza sin fallos aunque alguien haya probado antes ese nombre
            INTENTOS.remove(llaveIntentos(u));
            return "";
        }
    }

    private static void insertarClienteSql(String username, String hash, String nombre, String correo, String dni,
            String telefono) throws SQLException {
        try (Connection cn = ConexionBD.obtenerConexion();
             PreparedStatement ps = cn.prepareStatement(SQL_REGISTRAR_CLIENTE)) {
            ps.setString(1, username);
            ps.setString(2, hash);
            ps.setString(3, nombre);
            ps.setString(4, correo);
            ps.setString(5, dni);
            ps.setString(6, telefono);
            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- bloqueo por intentos fallidos

    /** true si la cuenta acumuló {@link #MAX_INTENTOS} fallos y el bloqueo sigue vigente. */
    public boolean estaBloqueado(String username) {
        return milisRestantes(llaveIntentos(normalizar(username))) > 0;
    }

    /** Minutos que faltan para desbloquear (redondeado hacia arriba); 0 si no está bloqueada. */
    public long minutosRestantesBloqueo(String username) {
        long ms = milisRestantes(llaveIntentos(normalizar(username)));
        return (ms + 59_999L) / 60_000L;
    }

    private static long milisRestantes(String llave) {
        Intentos i = INTENTOS.get(llave);
        if (i == null || i.bloqueadoHasta() == 0) {
            return 0;
        }
        return Math.max(i.bloqueadoHasta() - System.currentTimeMillis(), 0);
    }

    /**
     * Suma un fallo; al llegar a {@link #MAX_INTENTOS} bloquea la cuenta {@link #MINUTOS_BLOQUEO} minutos.
     *
     * @return true si este fallo fue el que creó el bloqueo
     */
    private static boolean registrarFallo(String llave) {
        long ahora = System.currentTimeMillis();
        if (INTENTOS.size() > MAX_USUARIOS_RASTREADOS) {
            // Evita que probar nombres al azar haga crecer el mapa sin límite
            INTENTOS.values().removeIf(i -> i.bloqueadoHasta() <= ahora);
        }
        boolean[] bloqueoNuevo = {false};
        INTENTOS.compute(llave, (k, previo) -> {
            if (previo != null && previo.bloqueadoHasta() > ahora) {
                return previo;
            }
            // Un bloqueo vencido empieza de cero
            int fallos = previo == null || previo.bloqueadoHasta() != 0 ? 1 : previo.fallos() + 1;
            long hasta = fallos >= MAX_INTENTOS ? ahora + MINUTOS_BLOQUEO * 60_000L : 0;
            bloqueoNuevo[0] = hasta != 0;
            return new Intentos(fallos, hasta);
        });
        return bloqueoNuevo[0];
    }

    private static String llaveIntentos(String userNorm) {
        return userNorm.length() > 100 ? userNorm.substring(0, 100) : userNorm;
    }

    // ---------------------------------------------------------------- administración de usuarios
    // Convención de los métodos que modifican: devuelven null si tuvo éxito o el mensaje de error.

    /** Todos los usuarios (activos e inactivos), ordenados por rol y usuario. */
    public List<Usuario> listarUsuarios() {
        if (ConexionBD.usarSql()) {
            try (Connection cn = ConexionBD.obtenerConexion();
                 PreparedStatement ps = cn.prepareStatement(SQL_LISTAR);
                 ResultSet rs = ps.executeQuery()) {
                List<Usuario> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
                return lista;
            } catch (SQLException ex) {
                ConexionBD.alFallarSinRespaldo(ex);
            }
        }
        if (ConexionBD.estaDisponible()) {
            return new ArrayList<>();   // fallo puntual de SQL Server: no se mezclan datos del respaldo
        }
        List<Usuario> lista = new ArrayList<>();
        synchronized (USUARIOS_MEMORIA) {
            for (Map.Entry<String, String[]> e : USUARIOS_MEMORIA.entrySet()) {
                lista.add(aUsuario(e.getKey(), e.getValue()));
            }
        }
        lista.sort(Comparator.comparingInt((Usuario u) -> ROLES.indexOf(u.getRol()))
                .thenComparing(Usuario::getUsername));
        return lista;
    }

    /** Busca una cuenta por su identificador (activa o no); null si no existe. */
    public Usuario buscarPorId(int idUsuario) {
        boolean sql = ConexionBD.usarSql();
        try {
            return obtener(sql, idUsuario);
        } catch (SQLException ex) {
            ConexionBD.alFallar(ex);
            return null;
        }
    }

    /**
     * Crea una cuenta de personal o de cliente. Solo Administrador. Usuario 3-40 caracteres [a-z0-9._-],
     * único; nombre obligatorio; correo válido y único; rol de {@link #ROLES}; contraseña de al menos
     * 8 caracteres con letras y números, sin espacios al inicio ni al final. Se guarda cifrada.
     */
    public String crearUsuario(Usuario actor, String username, String nombreCompleto, String correo,
            String rol, String clave) {
        String error = exigirAdministrador(actor);
        if (error != null) {
            return error;
        }
        String u = normalizar(username);
        String n = nombreCompleto == null ? "" : nombreCompleto.trim();
        String m = normalizar(correo);
        String rolCanonico = rolCanonico(rol);

        if (!PATRON_USUARIO.matcher(u).matches()) {
            return "El usuario debe tener entre 3 y 40 caracteres: letras minúsculas, números, punto, guion o guion bajo.";
        }
        if (n.isEmpty()) {
            return "Ingresa el nombre completo.";
        }
        if (n.length() > 100) {
            return "El nombre completo no puede superar 100 caracteres.";
        }
        if (m.length() > 100 || !PATRON_CORREO.matcher(m).matches()) {
            return "Ingresa un correo válido.";
        }
        if (rolCanonico == null) {
            return MSG_ROL_INVALIDO;
        }
        error = validarClave(clave);
        if (error != null) {
            return error;
        }

        String hash = Seguridad.hashClave(clave);
        synchronized (USUARIOS_MEMORIA) {
            boolean sql = ConexionBD.usarSql();
            try {
                error = duplicado(sql, u, m);
                if (error != null) {
                    return error;
                }
                if (sql) {
                    try (Connection cn = ConexionBD.obtenerConexion();
                         PreparedStatement ps = cn.prepareStatement(SQL_CREAR_USUARIO)) {
                        ps.setString(1, u);
                        ps.setString(2, hash);
                        ps.setString(3, n);
                        ps.setString(4, m);
                        ps.setString(5, rolCanonico);
                        ps.executeUpdate();
                    }
                } else {
                    USUARIOS_MEMORIA.put(u, new String[]{siguienteIdMemoria(), hash, n, m, rolCanonico, "1", null, null});
                }
                INTENTOS.remove(llaveIntentos(u));
                return null;
            } catch (SQLException ex) {
                ConexionBD.alFallar(ex);
                return MSG_ERROR_BD;
            }
        }
    }

    /**
     * Activa o desactiva. Nadie cambia el estado de su propia cuenta ni deja el sistema sin
     * administradores activos. Pedir el estado que la cuenta ya tiene devuelve un mensaje.
     */
    public String cambiarEstadoUsuario(Usuario actor, int idUsuario, boolean activo) {
        String error = exigirAdministrador(actor);
        if (error != null) {
            return error;
        }
        if (actor.getIdUsuario() == idUsuario) {
            return "No puedes cambiar el estado de tu propia cuenta.";
        }
        synchronized (USUARIOS_MEMORIA) {
            boolean sql = ConexionBD.usarSql();
            try {
                Usuario objetivo = obtener(sql, idUsuario);
                if (objetivo == null) {
                    return MSG_NO_ENCONTRADO;
                }
                if (objetivo.isActivo() == activo) {
                    return "El usuario " + objetivo.getUsername() + " ya está " + (activo ? "activo." : "inactivo.");
                }
                if (!activo && objetivo.isAdministrador() && contarAdministradoresActivos(sql) <= 1) {
                    return "No se puede desactivar al último administrador activo.";
                }
                actualizarActivo(sql, idUsuario, activo);
                if (!activo) {
                    revocarSesiones(objetivo.getUsername());
                }
                return null;
            } catch (SQLException ex) {
                ConexionBD.alFallar(ex);
                return MSG_ERROR_BD;
            }
        }
    }

    /** Cambia el rol. Nadie cambia el suyo ni deja el sistema sin administradores activos. */
    public String cambiarRol(Usuario actor, int idUsuario, String rol) {
        String error = exigirAdministrador(actor);
        if (error != null) {
            return error;
        }
        String nuevoRol = rolCanonico(rol);
        if (nuevoRol == null) {
            return MSG_ROL_INVALIDO;
        }
        if (actor.getIdUsuario() == idUsuario) {
            return "No puedes cambiar tu propio rol.";
        }
        synchronized (USUARIOS_MEMORIA) {
            boolean sql = ConexionBD.usarSql();
            try {
                Usuario objetivo = obtener(sql, idUsuario);
                if (objetivo == null) {
                    return MSG_NO_ENCONTRADO;
                }
                if (nuevoRol.equalsIgnoreCase(objetivo.getRol())) {
                    return "El usuario " + objetivo.getUsername() + " ya tiene el rol " + nuevoRol + ".";
                }
                if (objetivo.isAdministrador() && objetivo.isActivo() && !Usuario.ROL_ADMIN.equals(nuevoRol)
                        && contarAdministradoresActivos(sql) <= 1) {
                    return "No se puede quitar el rol al último administrador activo.";
                }
                actualizarRol(sql, idUsuario, nuevoRol);
                revocarSesiones(objetivo.getUsername());
                return null;
            } catch (SQLException ex) {
                ConexionBD.alFallar(ex);
                return MSG_ERROR_BD;
            }
        }
    }

    /** Asigna una contraseña nueva (mismas reglas que crearUsuario), desbloquea la cuenta y cierra sus sesiones abiertas. */
    public String restablecerClave(Usuario actor, int idUsuario, String nuevaClave) {
        String error = exigirAdministrador(actor);
        if (error != null) {
            return error;
        }
        error = validarClave(nuevaClave);
        if (error != null) {
            return error;
        }
        String hash = Seguridad.hashClave(nuevaClave);
        synchronized (USUARIOS_MEMORIA) {
            boolean sql = ConexionBD.usarSql();
            try {
                Usuario objetivo = obtener(sql, idUsuario);
                if (objetivo == null) {
                    return MSG_NO_ENCONTRADO;
                }
                actualizarClave(sql, idUsuario, hash);
                INTENTOS.remove(llaveIntentos(normalizar(objetivo.getUsername())));
                revocarSesiones(objetivo.getUsername());
                return null;
            } catch (SQLException ex) {
                ConexionBD.alFallar(ex);
                return MSG_ERROR_BD;
            }
        }
    }

    // ---------------------------------------------------------------- validaciones

    private static String exigirAdministrador(Usuario actor) {
        return actor != null && actor.isPuedeAdministrarSistema() ? null : MSG_SIN_PERMISO;
    }

    /** Rol de {@link #ROLES} con su escritura oficial, sin distinguir mayúsculas; null si no existe. */
    private static String rolCanonico(String rol) {
        if (rol == null) {
            return null;
        }
        String buscado = rol.trim();
        for (String r : ROLES) {
            if (r.equalsIgnoreCase(buscado)) {
                return r;
            }
        }
        return null;
    }

    /** Reglas de contraseña de las cuentas creadas por el administrador; null si es válida. */
    private static String validarClave(String clave) {
        if (clave == null || clave.isEmpty()) {
            return "Ingresa una contraseña.";
        }
        if (!clave.equals(clave.strip())) {
            return MSG_ESPACIOS;
        }
        if (clave.length() < 8) {
            return "La contraseña debe tener al menos 8 caracteres.";
        }
        if (clave.length() > 100) {
            return "La contraseña no puede superar 100 caracteres.";
        }
        boolean letra = clave.chars().anyMatch(Character::isLetter);
        boolean numero = clave.chars().anyMatch(Character::isDigit);
        if (!letra || !numero) {
            return "La contraseña debe combinar letras y números.";
        }
        return null;
    }

    // ---------------------------------------------------------------- acceso a datos (SQL o memoria)

    /** Mensaje de error si el usuario o el correo ya existen; null si están libres. */
    private String duplicado(boolean sql, String username, String correo) throws SQLException {
        if (sql) {
            try (Connection cn = ConexionBD.obtenerConexion();
                 PreparedStatement ps = cn.prepareStatement(SQL_DUPLICADOS)) {
                ps.setString(1, username);
                ps.setString(2, correo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        if (username.equalsIgnoreCase(rs.getString("username"))) {
                            return "El usuario '" + username + "' ya está registrado.";
                        }
                        return "El correo '" + correo + "' ya está registrado.";
                    }
                }
            }
            return null;
        }
        synchronized (USUARIOS_MEMORIA) {
            if (USUARIOS_MEMORIA.containsKey(username)) {
                return "El usuario '" + username + "' ya está registrado.";
            }
            if (correoEnMemoria(correo)) {
                return "El correo '" + correo + "' ya está registrado.";
            }
        }
        return null;
    }

    private Usuario obtener(boolean sql, int idUsuario) throws SQLException {
        if (sql) {
            try (Connection cn = ConexionBD.obtenerConexion();
                 PreparedStatement ps = cn.prepareStatement(SQL_BUSCAR_ID)) {
                ps.setInt(1, idUsuario);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? mapear(rs) : null;
                }
            }
        }
        synchronized (USUARIOS_MEMORIA) {
            for (Map.Entry<String, String[]> e : USUARIOS_MEMORIA.entrySet()) {
                if (String.valueOf(idUsuario).equals(e.getValue()[I_ID])) {
                    return aUsuario(e.getKey(), e.getValue());
                }
            }
        }
        return null;
    }

    private int contarAdministradoresActivos(boolean sql) throws SQLException {
        if (sql) {
            try (Connection cn = ConexionBD.obtenerConexion();
                 PreparedStatement ps = cn.prepareStatement(SQL_CONTAR_ADMINISTRADORES)) {
                ps.setString(1, Usuario.ROL_ADMIN);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        }
        int total = 0;
        synchronized (USUARIOS_MEMORIA) {
            for (String[] d : USUARIOS_MEMORIA.values()) {
                if (Usuario.ROL_ADMIN.equalsIgnoreCase(d[I_ROL]) && "1".equals(d[I_ACTIVO])) {
                    total++;
                }
            }
        }
        return total;
    }

    private void actualizarActivo(boolean sql, int idUsuario, boolean activo) throws SQLException {
        if (sql) {
            try (Connection cn = ConexionBD.obtenerConexion();
                 PreparedStatement ps = cn.prepareStatement(SQL_ACTUALIZAR_ACTIVO)) {
                ps.setBoolean(1, activo);
                ps.setInt(2, idUsuario);
                ps.executeUpdate();
            }
            return;
        }
        cambiarCampoMemoria(idUsuario, I_ACTIVO, activo ? "1" : "0");
    }

    private void actualizarRol(boolean sql, int idUsuario, String rol) throws SQLException {
        if (sql) {
            try (Connection cn = ConexionBD.obtenerConexion();
                 PreparedStatement ps = cn.prepareStatement(SQL_ACTUALIZAR_ROL)) {
                ps.setString(1, rol);
                ps.setInt(2, idUsuario);
                ps.executeUpdate();
            }
            return;
        }
        cambiarCampoMemoria(idUsuario, I_ROL, rol);
    }

    private void actualizarClave(boolean sql, int idUsuario, String hash) throws SQLException {
        if (sql) {
            try (Connection cn = ConexionBD.obtenerConexion();
                 PreparedStatement ps = cn.prepareStatement(SQL_ACTUALIZAR_CLAVE)) {
                ps.setString(1, hash);
                ps.setInt(2, idUsuario);
                ps.executeUpdate();
            }
            return;
        }
        cambiarCampoMemoria(idUsuario, I_CLAVE, hash);
    }

    /** Reemplaza una fila del respaldo en memoria por una copia con un campo cambiado (conserva el orden). */
    private static void cambiarCampoMemoria(int idUsuario, int campo, String valor) {
        synchronized (USUARIOS_MEMORIA) {
            for (Map.Entry<String, String[]> e : USUARIOS_MEMORIA.entrySet()) {
                if (String.valueOf(idUsuario).equals(e.getValue()[I_ID])) {
                    String[] copia = e.getValue().clone();
                    copia[campo] = valor;
                    e.setValue(copia);
                    return;
                }
            }
        }
    }

    /**
     * Guarda de nuevo la clave con el cifrado actual (era una clave heredada en claro o tenía menos iteraciones)
     * tras un ingreso correcto; si falla, se reintenta en el siguiente. No pisa una clave cambiada entretanto.
     */
    private static void recifrarClaveSql(Connection cn, int idUsuario, String clave, String guardada) {
        try (PreparedStatement ps = cn.prepareStatement(SQL_RECIFRAR_CLAVE)) {
            ps.setString(1, Seguridad.hashClave(clave));
            ps.setInt(2, idUsuario);
            ps.setString(3, guardada);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private static void recifrarClaveMemoria(String username, String[] original, String clave) {
        String hash = Seguridad.hashClave(clave);
        synchronized (USUARIOS_MEMORIA) {
            if (USUARIOS_MEMORIA.get(username) == original) {
                String[] copia = original.clone();
                copia[I_CLAVE] = hash;
                USUARIOS_MEMORIA.put(username, copia);
            }
        }
    }

    // ---------------------------------------------------------------- auxiliares

    private static String[] semilla(String id, String claveClaro, String nombre, String correo, String rol) {
        return semilla(id, claveClaro, nombre, correo, rol, null, null);
    }

    private static String[] semilla(String id, String claveClaro, String nombre, String correo, String rol,
            String dni, String telefono) {
        return new String[]{id, Seguridad.hashClave(claveClaro), nombre, correo, rol, "1", dni, telefono};
    }

    /**
     * Forma canónica de un usuario o correo: NFKC (el ancho completo pasa a ASCII), sin espacios en los
     * extremos y en minúsculas. Los caracteres invisibles no se eliminan: el patrón de usuario los rechaza.
     */
    private static String normalizar(String texto) {
        return texto == null ? "" : Normalizer.normalize(texto, Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Compara la clave escrita con la guardada. Si lo guardado es un hash, lo verifica; si es una clave
     * heredada en claro, compara en tiempo constante.
     */
    private static boolean claveCoincide(String clave, String guardada) {
        if (guardada == null) {
            return false;
        }
        if (Seguridad.esHash(guardada)) {
            return Seguridad.verificarClave(clave, guardada);
        }
        return MessageDigest.isEqual(guardada.getBytes(StandardCharsets.UTF_8), clave.getBytes(StandardCharsets.UTF_8));
    }

    /** Hace el mismo trabajo que una verificación real para que el tiempo no delate si el usuario existe. */
    private static void gastarTiempo(String clave) {
        Seguridad.verificarClave(clave, HASH_FALSO);
    }

    private static Usuario mapear(ResultSet rs) throws SQLException {
        return new Usuario(
                rs.getInt("id_usuario"),
                rs.getString("username"),
                rs.getString("nombre_completo"),
                rs.getString("correo"),
                rs.getString("rol"),
                rs.getBoolean("activo"),
                rs.getString("dni"),
                rs.getString("telefono"));
    }

    private static Usuario aUsuario(String username, String[] d) {
        return new Usuario(Integer.parseInt(d[I_ID]), username, d[I_NOMBRE], d[I_CORREO], d[I_ROL],
                "1".equals(d[I_ACTIVO]), d.length > I_DNI ? d[I_DNI] : null, d.length > I_TELEFONO ? d[I_TELEFONO] : null);
    }

    /** Siguiente identificador libre del respaldo en memoria. Llamar con el candado tomado. */
    private static String siguienteIdMemoria() {
        int max = 0;
        synchronized (USUARIOS_MEMORIA) {
            for (String[] d : USUARIOS_MEMORIA.values()) {
                max = Math.max(max, Integer.parseInt(d[I_ID]));
            }
        }
        return String.valueOf(max + 1);
    }

    /** true si algún usuario del respaldo en memoria ya usa ese correo. */
    private static boolean correoEnMemoria(String correo) {
        synchronized (USUARIOS_MEMORIA) {
            for (String[] d : USUARIOS_MEMORIA.values()) {
                if (correo.equalsIgnoreCase(d[I_CORREO])) {
                    return true;
                }
            }
        }
        return false;
    }

    private void registrarBitacoraSesion(Connection cn, int idUsuario, String ip) {
        try (PreparedStatement ps = cn.prepareStatement(SQL_REGISTRAR_SESION)) {
            ps.setInt(1, idUsuario);
            ps.setString(2, ip != null ? ip : "127.0.0.1");
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }
}
