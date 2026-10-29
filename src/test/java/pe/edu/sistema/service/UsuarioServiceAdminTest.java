package pe.edu.sistema.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.edu.sistema.model.Usuario;
import pe.edu.sistema.util.ConexionBD;
import pe.edu.sistema.util.Seguridad;

/**
 * Pruebas de la administración de usuarios de {@link UsuarioService} con el respaldo en memoria.
 * El respaldo es estático y dura todo el JVM, así que cada prueba crea sus propias cuentas con nombres
 * que no usa ninguna otra; las semillas (admin, almacen, cliente, invitado) solo se consultan.
 */
class UsuarioServiceAdminTest {

    private static final String CLAVE = "Clave12345";
    private static final String IP = "127.0.0.1";

    private final UsuarioService usuarios = new UsuarioService();

    /** Evita escribir en una base real si alguien ejecuta las pruebas con SQL Server activo. */
    @BeforeAll
    static void soloRespaldoEnMemoria() {
        assumeFalse(ConexionBD.estaDisponible(),
                "Estas pruebas usan el respaldo en memoria; hay un SQL Server activo y no se modificará");
    }

    // ---------------------------------------------------------------- auxiliares

    /** Administrador que no existe en el respaldo: sirve de actor sin chocar con ninguna cuenta real. */
    private static Usuario actorAdmin() {
        return new Usuario(90_001, "actor_admin_prueba", "Actor Admin", "actor@prueba.pe", Usuario.ROL_ADMIN, true);
    }

    private static Usuario actorConRol(String rol) {
        return new Usuario(90_002, "actor_" + rol.toLowerCase(), "Actor " + rol, "", rol, true);
    }

    private static String correo(String username) {
        return username + "@prueba.nexora.pe";
    }

    /** Crea una cuenta con la clave estándar y falla con el mensaje si el servicio la rechaza. */
    private void crear(String username, String rol) {
        String error = usuarios.crearUsuario(actorAdmin(), username, "Nombre " + username, correo(username), rol, CLAVE);
        assertNull(error, "No se pudo crear " + username + ": " + error);
    }

    private Usuario buscar(String username) {
        return usuarios.listarUsuarios().stream()
                .filter(u -> u.getUsername().equals(username)).findFirst().orElse(null);
    }

    private int idDe(String username) {
        Usuario u = buscar(username);
        assertNotNull(u, "La cuenta " + username + " debía existir");
        return u.getIdUsuario();
    }

    private Usuario ingresar(String username, String clave) {
        return usuarios.autenticarUsuario(username, clave, null, IP);
    }

    /** Respaldo en memoria interno: username -> {id, clave cifrada, nombre, correo, rol, activo}. */
    @SuppressWarnings("unchecked")
    private static Map<String, String[]> respaldoInterno() throws ReflectiveOperationException {
        Field campo = UsuarioService.class.getDeclaredField("USUARIOS_MEMORIA");
        campo.setAccessible(true);
        return (Map<String, String[]>) campo.get(null);
    }

    // ---------------------------------------------------------------- crearUsuario: permisos y validaciones

    @Test
    @DisplayName("crearUsuario solo lo permite el Administrador y no crea nada en otro caso")
    void crearUsuarioSoloAdministrador() {
        String esperado = "Solo el Administrador puede gestionar usuarios.";
        for (Usuario actor : new Usuario[]{actorConRol(Usuario.ROL_ALMACEN), actorConRol(Usuario.ROL_CLIENTE),
                actorConRol(Usuario.ROL_CONSULTA), null}) {
            assertEquals(esperado, usuarios.crearUsuario(actor, "adm_sin_permiso", "Sin Permiso",
                    correo("adm_sin_permiso"), Usuario.ROL_CLIENTE, CLAVE));
        }
        assertNull(buscar("adm_sin_permiso"));
    }

    @Test
    @DisplayName("crearUsuario valida usuario, nombre, correo, rol y clave con su mensaje")
    void crearUsuarioValidaDatos() {
        Usuario admin = actorAdmin();
        String correoOk = correo("adm_valida");

        // Usuario: 3-40 caracteres [a-z0-9._-]
        for (String malo : new String[]{"ab", "con espacio", "ñandu", "a@b", "x".repeat(41), "", null}) {
            String error = usuarios.crearUsuario(admin, malo, "Nombre", correoOk, Usuario.ROL_CLIENTE, CLAVE);
            assertNotNull(error, "Debía rechazar el usuario '" + malo + "'");
            assertTrue(error.startsWith("El usuario debe tener entre 3 y 40 caracteres"), error);
        }
        // Nombre
        assertEquals("Ingresa el nombre completo.",
                usuarios.crearUsuario(admin, "adm_valida", "   ", correoOk, Usuario.ROL_CLIENTE, CLAVE));
        assertEquals("Ingresa el nombre completo.",
                usuarios.crearUsuario(admin, "adm_valida", null, correoOk, Usuario.ROL_CLIENTE, CLAVE));
        assertEquals("El nombre completo no puede superar 100 caracteres.",
                usuarios.crearUsuario(admin, "adm_valida", "N".repeat(101), correoOk, Usuario.ROL_CLIENTE, CLAVE));
        // Correo
        for (String malo : new String[]{"sin-arroba", "a@b", "con espacio@x.pe", "", null, "a".repeat(96) + "@x.pe"}) {
            assertEquals("Ingresa un correo válido.",
                    usuarios.crearUsuario(admin, "adm_valida", "Nombre", malo, Usuario.ROL_CLIENTE, CLAVE),
                    "Debía rechazar el correo '" + malo + "'");
        }
        // Rol
        for (String malo : new String[]{"Superusuario", "", null, "Admin"}) {
            assertEquals("Elige un rol válido.",
                    usuarios.crearUsuario(admin, "adm_valida", "Nombre", correoOk, malo, CLAVE),
                    "Debía rechazar el rol '" + malo + "'");
        }
        // Clave
        assertEquals("Ingresa una contraseña.",
                usuarios.crearUsuario(admin, "adm_valida", "Nombre", correoOk, Usuario.ROL_CLIENTE, null));
        assertEquals("Ingresa una contraseña.",
                usuarios.crearUsuario(admin, "adm_valida", "Nombre", correoOk, Usuario.ROL_CLIENTE, ""));
        assertEquals("La contraseña no puede empezar ni terminar con espacios.",
                usuarios.crearUsuario(admin, "adm_valida", "Nombre", correoOk, Usuario.ROL_CLIENTE, " Clave12345"));
        assertEquals("La contraseña no puede empezar ni terminar con espacios.",
                usuarios.crearUsuario(admin, "adm_valida", "Nombre", correoOk, Usuario.ROL_CLIENTE, "Clave12345 "));
        assertEquals("La contraseña debe tener al menos 8 caracteres.",
                usuarios.crearUsuario(admin, "adm_valida", "Nombre", correoOk, Usuario.ROL_CLIENTE, "Abc1234"));
        assertEquals("La contraseña debe combinar letras y números.",
                usuarios.crearUsuario(admin, "adm_valida", "Nombre", correoOk, Usuario.ROL_CLIENTE, "soloLetrasAqui"));
        assertEquals("La contraseña debe combinar letras y números.",
                usuarios.crearUsuario(admin, "adm_valida", "Nombre", correoOk, Usuario.ROL_CLIENTE, "12345678"));
        assertEquals("La contraseña no puede superar 100 caracteres.",
                usuarios.crearUsuario(admin, "adm_valida", "Nombre", correoOk, Usuario.ROL_CLIENTE, "a1".repeat(51)));

        assertNull(buscar("adm_valida"), "Ningún intento inválido debe haber creado la cuenta");
        assertNull(usuarios.crearUsuario(admin, "adm_valida", "Nombre", correoOk, Usuario.ROL_CLIENTE, CLAVE));
        assertNotNull(buscar("adm_valida"));
    }

    @Test
    @DisplayName("crearUsuario normaliza usuario y correo, acepta el rol sin distinguir mayúsculas y la cuenta ingresa")
    void crearUsuarioNormalizaYPermiteIngresar() {
        assertNull(usuarios.crearUsuario(actorAdmin(), "  Adm.Mixto-1  ", "  Persona Mixta  ", "  Mixto.Uno@Prueba.PE ",
                "almacén", CLAVE));

        Usuario u = buscar("adm.mixto-1");
        assertNotNull(u, "El usuario se guarda en minúsculas y sin espacios");
        assertEquals("Persona Mixta", u.getNombreCompleto());
        assertEquals("mixto.uno@prueba.pe", u.getCorreo());
        assertEquals(Usuario.ROL_ALMACEN, u.getRol(), "El rol se guarda con su escritura oficial");
        assertTrue(u.isActivo());

        Usuario ingreso = usuarios.autenticarUsuario(" ADM.MIXTO-1 ", CLAVE, Usuario.ROL_ALMACEN, IP);
        assertNotNull(ingreso);
        assertEquals("adm.mixto-1", ingreso.getUsername());
        assertNull(usuarios.autenticarUsuario("adm.mixto-1", CLAVE, Usuario.ROL_ADMIN, IP),
                "Con otro rol elegido en la ventana de ingreso no entra");
    }

    @Test
    @DisplayName("crearUsuario rechaza usuario y correo duplicados sin distinguir mayúsculas, también los de las semillas")
    void crearUsuarioRechazaDuplicados() {
        crear("adm_duplicado", Usuario.ROL_CLIENTE);
        Usuario admin = actorAdmin();

        String usuarioRepetido = usuarios.crearUsuario(admin, "ADM_DUPLICADO", "Otro", correo("adm_otro_correo"),
                Usuario.ROL_CLIENTE, CLAVE);
        assertNotNull(usuarioRepetido);
        assertTrue(usuarioRepetido.contains("ya está registrado"), usuarioRepetido);
        assertTrue(usuarioRepetido.contains("usuario"), usuarioRepetido);

        String correoRepetido = usuarios.crearUsuario(admin, "adm_correo_nuevo", "Otro",
                correo("adm_duplicado").toUpperCase(), Usuario.ROL_CLIENTE, CLAVE);
        assertNotNull(correoRepetido);
        assertTrue(correoRepetido.contains("correo") && correoRepetido.contains("ya está registrado"), correoRepetido);
        assertNull(buscar("adm_correo_nuevo"));

        assertNotNull(usuarios.crearUsuario(admin, "admin", "Otro Admin", correo("adm_otro_admin"),
                Usuario.ROL_ADMIN, CLAVE), "'admin' es una cuenta semilla");
        assertNotNull(usuarios.crearUsuario(admin, "adm_correo_semilla", "Otro", "ADMIN@nexora.pe",
                Usuario.ROL_ADMIN, CLAVE), "El correo de la semilla ya está usado");
    }

    // ---------------------------------------------------------------- la clave nunca queda en claro

    @Test
    @DisplayName("La clave no se expone: Usuario no la tiene, el respaldo guarda solo hashes y autenticar funciona")
    void claveNuncaQuedaEnClaro() throws ReflectiveOperationException {
        crear("adm_secreto", Usuario.ROL_CLIENTE);
        assertNotNull(ingresar("adm_secreto", CLAVE));
        assertNull(ingresar("adm_secreto", "OtraClave99"));

        // El modelo que devuelve listarUsuarios no tiene ningún campo de contraseña
        for (Field campo : Usuario.class.getDeclaredFields()) {
            if (!Modifier.isStatic(campo.getModifiers())) {
                String nombre = campo.getName().toLowerCase();
                assertFalse(nombre.contains("clave") || nombre.contains("password") || nombre.contains("hash"),
                        "Usuario no debe guardar la contraseña: " + campo.getName());
            }
        }
        for (Usuario u : usuarios.listarUsuarios()) {
            assertFalse(String.valueOf(u.getNombreCompleto()).contains(CLAVE));
            assertFalse(u.toString().contains(CLAVE));
        }

        // Toda cuenta del respaldo (semillas y creadas) guarda un hash, nunca el texto escrito
        synchronized (respaldoInterno()) {
            for (Map.Entry<String, String[]> e : respaldoInterno().entrySet()) {
                String guardada = e.getValue()[1];
                assertTrue(Seguridad.esHash(guardada), "La clave de " + e.getKey() + " debe estar cifrada");
                assertNotEquals(CLAVE, guardada);
            }
            assertTrue(Seguridad.verificarClave(CLAVE, respaldoInterno().get("adm_secreto")[1]));
        }
    }

    @Test
    @DisplayName("Una clave heredada en claro entra una vez y se migra a hash; la clave sigue funcionando")
    void migraClaveHeredadaEnClaro() throws ReflectiveOperationException {
        Map<String, String[]> respaldo = respaldoInterno();
        synchronized (respaldo) {
            respaldo.put("adm_heredado", new String[]{"88001", "claveHeredada1", "Cuenta Heredada",
                    correo("adm_heredado"), Usuario.ROL_CLIENTE, "1"});
        }
        try {
            assertNull(ingresar("adm_heredado", "claveIncorrecta1"), "Una clave incorrecta no entra");
            assertFalse(Seguridad.esHash(respaldo.get("adm_heredado")[1]), "Un fallo no migra la clave");

            assertNotNull(ingresar("adm_heredado", "claveHeredada1"));
            String migrada = respaldo.get("adm_heredado")[1];
            assertTrue(Seguridad.esHash(migrada), "Tras ingresar la clave queda cifrada");
            assertFalse(migrada.contains("claveHeredada1"));

            assertNotNull(ingresar("adm_heredado", "claveHeredada1"), "Con el hash sigue entrando");
            assertNull(ingresar("adm_heredado", "claveIncorrecta1"));
        } finally {
            synchronized (respaldo) {
                respaldo.remove("adm_heredado");
            }
        }
    }

    // ---------------------------------------------------------------- bloqueo por intentos fallidos

    @Test
    @DisplayName("Tras MAX_INTENTOS fallos la cuenta se bloquea, ni la clave correcta entra y restablecerClave la desbloquea")
    void bloqueoTrasMaxIntentosYDesbloqueo() {
        crear("adm_bloqueo", Usuario.ROL_CLIENTE);
        assertFalse(usuarios.estaBloqueado("adm_bloqueo"));
        assertEquals(0, usuarios.minutosRestantesBloqueo("adm_bloqueo"));

        for (int i = 1; i < UsuarioService.MAX_INTENTOS; i++) {
            assertNull(ingresar("adm_bloqueo", "incorrecta" + i));
            assertFalse(usuarios.estaBloqueado("adm_bloqueo"), "Con " + i + " fallos aún no se bloquea");
        }
        assertNull(ingresar("adm_bloqueo", "incorrecta99"));
        assertTrue(usuarios.estaBloqueado("adm_bloqueo"), "El fallo número " + UsuarioService.MAX_INTENTOS + " bloquea");
        assertTrue(usuarios.estaBloqueado("  ADM_BLOQUEO "), "El bloqueo no distingue mayúsculas ni espacios");
        long minutos = usuarios.minutosRestantesBloqueo("adm_bloqueo");
        assertTrue(minutos >= 1 && minutos <= UsuarioService.MINUTOS_BLOQUEO, "Minutos restantes: " + minutos);

        assertNull(ingresar("adm_bloqueo", CLAVE), "Bloqueada: ni la clave correcta entra");

        assertNull(usuarios.restablecerClave(actorAdmin(), idDe("adm_bloqueo"), "NuevaClave987"));
        assertFalse(usuarios.estaBloqueado("adm_bloqueo"), "restablecerClave desbloquea");
        assertEquals(0, usuarios.minutosRestantesBloqueo("adm_bloqueo"));
        assertNull(ingresar("adm_bloqueo", CLAVE), "La clave anterior ya no sirve");
        assertNotNull(ingresar("adm_bloqueo", "NuevaClave987"));
    }

    @Test
    @DisplayName("Un ingreso correcto reinicia el contador de fallos")
    void ingresoCorrectoReiniciaContador() {
        crear("adm_reinicio", Usuario.ROL_CLIENTE);
        for (int ronda = 0; ronda < 2; ronda++) {
            for (int i = 0; i < UsuarioService.MAX_INTENTOS - 1; i++) {
                assertNull(ingresar("adm_reinicio", "mala" + i));
            }
            assertFalse(usuarios.estaBloqueado("adm_reinicio"));
            assertNotNull(ingresar("adm_reinicio", CLAVE), "Con la clave correcta entra y reinicia");
        }
        assertFalse(usuarios.estaBloqueado("adm_reinicio"));
    }

    @Test
    @DisplayName("Los fallos con un usuario inexistente también cuentan para el bloqueo")
    void usuarioInexistenteTambienSeBloquea() {
        for (int i = 0; i < UsuarioService.MAX_INTENTOS; i++) {
            assertNull(ingresar("adm_no_existe", "cualquiera" + i));
        }
        assertTrue(usuarios.estaBloqueado("adm_no_existe"));
        assertNull(buscar("adm_no_existe"));
    }

    @Test
    @DisplayName("Un usuario inactivo no entra aunque use la clave correcta, y vuelve a entrar al reactivarlo")
    void inactivoNoEntra() {
        crear("adm_inactivo", Usuario.ROL_CLIENTE);
        assertNotNull(ingresar("adm_inactivo", CLAVE));

        assertNull(usuarios.cambiarEstadoUsuario(actorAdmin(), idDe("adm_inactivo"), false));
        assertFalse(buscar("adm_inactivo").isActivo());
        assertNull(ingresar("adm_inactivo", CLAVE), "Inactivo: no entra");

        assertNull(usuarios.cambiarEstadoUsuario(actorAdmin(), idDe("adm_inactivo"), true));
        assertTrue(buscar("adm_inactivo").isActivo());
        assertNotNull(ingresar("adm_inactivo", CLAVE));
    }

    // ---------------------------------------------------------------- estado, rol y clave: reglas

    @Test
    @DisplayName("cambiarEstadoUsuario, cambiarRol y restablecerClave exigen Administrador")
    void cambiosExigenAdministrador() {
        crear("adm_objetivo", Usuario.ROL_CLIENTE);
        int id = idDe("adm_objetivo");
        String esperado = "Solo el Administrador puede gestionar usuarios.";

        for (Usuario actor : new Usuario[]{actorConRol(Usuario.ROL_ALMACEN), actorConRol(Usuario.ROL_CLIENTE),
                actorConRol(Usuario.ROL_CONSULTA), null}) {
            assertEquals(esperado, usuarios.cambiarEstadoUsuario(actor, id, false));
            assertEquals(esperado, usuarios.cambiarRol(actor, id, Usuario.ROL_ALMACEN));
            assertEquals(esperado, usuarios.restablecerClave(actor, id, "NuevaClave987"));
        }
        Usuario sinCambios = buscar("adm_objetivo");
        assertTrue(sinCambios.isActivo());
        assertEquals(Usuario.ROL_CLIENTE, sinCambios.getRol());
        assertNotNull(ingresar("adm_objetivo", CLAVE), "La clave tampoco cambió");
    }

    @Test
    @DisplayName("Nadie cambia su propio estado ni su propio rol")
    void nadieCambiaSuPropioEstadoNiRol() {
        crear("adm_propio", Usuario.ROL_ADMIN);
        Usuario yo = buscar("adm_propio");

        assertEquals("No puedes cambiar el estado de tu propia cuenta.",
                usuarios.cambiarEstadoUsuario(yo, yo.getIdUsuario(), false));
        assertEquals("No puedes cambiar tu propio rol.",
                usuarios.cambiarRol(yo, yo.getIdUsuario(), Usuario.ROL_CLIENTE));

        Usuario despues = buscar("adm_propio");
        assertTrue(despues.isActivo());
        assertEquals(Usuario.ROL_ADMIN, despues.getRol());
        assertFalse(UsuarioService.sesionRevocada("adm_propio", 0L), "Un intento rechazado no revoca la sesión");
        assertFalse(UsuarioService.sesionRevocada("adm_propio", null), "Sin revocaciones tampoco se revoca una sesión sin marca");
    }

    @Test
    @DisplayName("cambiarEstadoUsuario y cambiarRol informan de usuario inexistente, mismo valor y rol inválido")
    void cambiosInformanErrores() {
        crear("adm_errores", Usuario.ROL_CLIENTE);
        int id = idDe("adm_errores");
        Usuario admin = actorAdmin();

        assertEquals("No se encontró el usuario solicitado.", usuarios.cambiarEstadoUsuario(admin, 987_654, false));
        assertEquals("No se encontró el usuario solicitado.", usuarios.cambiarRol(admin, 987_654, Usuario.ROL_CLIENTE));
        assertEquals("No se encontró el usuario solicitado.", usuarios.restablecerClave(admin, 987_654, "NuevaClave987"));

        assertTrue(usuarios.cambiarEstadoUsuario(admin, id, true).contains("ya está activo"));
        assertTrue(usuarios.cambiarRol(admin, id, "cliente").contains("ya tiene el rol Cliente"));
        assertEquals("Elige un rol válido.", usuarios.cambiarRol(admin, id, "Gerente"));
        assertEquals("Elige un rol válido.", usuarios.cambiarRol(admin, id, null));

        assertNull(usuarios.cambiarEstadoUsuario(admin, id, false));
        assertTrue(usuarios.cambiarEstadoUsuario(admin, id, false).contains("ya está inactivo"));
    }

    @Test
    @DisplayName("restablecerClave aplica las reglas de clave fuerte y cambia el hash guardado")
    void restablecerClaveValidaYCambiaHash() throws ReflectiveOperationException {
        crear("adm_clave", Usuario.ROL_CLIENTE);
        int id = idDe("adm_clave");
        Usuario admin = actorAdmin();
        String hashAntes = respaldoInterno().get("adm_clave")[1];

        assertEquals("Ingresa una contraseña.", usuarios.restablecerClave(admin, id, ""));
        assertEquals("La contraseña debe tener al menos 8 caracteres.", usuarios.restablecerClave(admin, id, "Ab1"));
        assertEquals("La contraseña debe combinar letras y números.", usuarios.restablecerClave(admin, id, "sinnumeros"));
        assertEquals("La contraseña no puede empezar ni terminar con espacios.",
                usuarios.restablecerClave(admin, id, " Clave12345"));
        assertEquals(hashAntes, respaldoInterno().get("adm_clave")[1], "Los rechazos no tocan la clave");

        assertNull(usuarios.restablecerClave(admin, id, CLAVE), "Se puede repetir la misma clave");
        String hashDespues = respaldoInterno().get("adm_clave")[1];
        assertNotEquals(hashAntes, hashDespues, "Cada cifrado lleva una sal nueva");
        assertTrue(Seguridad.verificarClave(CLAVE, hashDespues));
    }

    @Test
    @DisplayName("No se puede dejar el sistema sin administradores activos (desactivar ni quitar el rol)")
    void nuncaSeQuedaSinAdministradoresActivos() {
        crear("adm_ultimo", Usuario.ROL_ADMIN);
        Usuario actor = actorAdmin();
        List<Usuario> desactivados = new ArrayList<>();
        try {
            // Deja a adm_ultimo como único administrador activo (las demás cuentas se restauran al final)
            for (Usuario u : usuarios.listarUsuarios()) {
                if (u.isAdministrador() && u.isActivo() && !u.getUsername().equals("adm_ultimo")) {
                    assertNull(usuarios.cambiarEstadoUsuario(actor, u.getIdUsuario(), false),
                            "Mientras quede otro administrador activo se puede desactivar a " + u.getUsername());
                    desactivados.add(u);
                }
            }
            int id = idDe("adm_ultimo");

            assertEquals("No se puede desactivar al último administrador activo.",
                    usuarios.cambiarEstadoUsuario(actor, id, false));
            assertEquals("No se puede quitar el rol al último administrador activo.",
                    usuarios.cambiarRol(actor, id, Usuario.ROL_ALMACEN));

            Usuario sigue = buscar("adm_ultimo");
            assertTrue(sigue.isActivo());
            assertEquals(Usuario.ROL_ADMIN, sigue.getRol());
            assertFalse(UsuarioService.sesionRevocada("adm_ultimo", 0L), "Los rechazos no revocan la sesión");

            // Con otro administrador activo vuelve a ser posible
            Usuario otro = desactivados.get(0);
            assertNull(usuarios.cambiarEstadoUsuario(actor, otro.getIdUsuario(), true));
            assertNull(usuarios.cambiarRol(actor, id, Usuario.ROL_ALMACEN));
        } finally {
            for (Usuario u : desactivados) {
                usuarios.cambiarEstadoUsuario(actor, u.getIdUsuario(), true);
            }
        }
        assertTrue(buscar("admin").isActivo(), "La cuenta semilla debe quedar activa");
        assertEquals(Usuario.ROL_ADMIN, buscar("admin").getRol());
    }

    // ---------------------------------------------------------------- revocación de sesiones (por secuencia)

    @Test
    @DisplayName("Desactivar revoca la sesión vieja; reactivar o volver a ingresar no la rehabilita y la sesión nueva sí vale")
    void sesionRevocadaTrasDesactivar() {
        crear("adm_sesion_estado", Usuario.ROL_CLIENTE);
        long marcaVieja = UsuarioService.marcaActual();
        assertNotNull(ingresar("adm_sesion_estado", CLAVE));
        assertFalse(UsuarioService.sesionRevocada("adm_sesion_estado", marcaVieja));

        assertNull(usuarios.cambiarEstadoUsuario(actorAdmin(), idDe("adm_sesion_estado"), false));
        assertTrue(UsuarioService.sesionRevocada("adm_sesion_estado", marcaVieja));
        assertTrue(UsuarioService.sesionRevocada("  ADM_SESION_ESTADO ", marcaVieja), "No distingue mayúsculas ni espacios");

        assertNull(usuarios.cambiarEstadoUsuario(actorAdmin(), idDe("adm_sesion_estado"), true));
        assertTrue(UsuarioService.sesionRevocada("adm_sesion_estado", marcaVieja),
                "Reactivar no rehabilita la sesión vieja");

        // Un ingreso posterior no borra la revocación: la sesión vieja sigue revocada
        long marcaNueva = UsuarioService.marcaActual();
        assertNotNull(ingresar("adm_sesion_estado", CLAVE));
        assertTrue(UsuarioService.sesionRevocada("adm_sesion_estado", marcaVieja),
                "Volver a ingresar no rehabilita la sesión vieja");
        assertFalse(UsuarioService.sesionRevocada("adm_sesion_estado", marcaNueva),
                "La sesión nacida después de la revocación es válida");
    }

    @Test
    @DisplayName("Cambiar el rol revoca la sesión vieja y la sesión nueva trae el rol actualizado y es válida")
    void sesionRevocadaTrasCambiarRol() {
        crear("adm_sesion_rol", Usuario.ROL_ALMACEN);
        long marcaVieja = UsuarioService.marcaActual();
        assertNotNull(ingresar("adm_sesion_rol", CLAVE));
        assertFalse(UsuarioService.sesionRevocada("adm_sesion_rol", marcaVieja));

        assertNull(usuarios.cambiarRol(actorAdmin(), idDe("adm_sesion_rol"), Usuario.ROL_CONSULTA));
        assertTrue(UsuarioService.sesionRevocada("adm_sesion_rol", marcaVieja));
        assertEquals(Usuario.ROL_CONSULTA, buscar("adm_sesion_rol").getRol());

        long marcaNueva = UsuarioService.marcaActual();
        Usuario nuevo = ingresar("adm_sesion_rol", CLAVE);
        assertNotNull(nuevo);
        assertEquals(Usuario.ROL_CONSULTA, nuevo.getRol(), "El nuevo ingreso trae el rol actualizado");
        assertFalse(UsuarioService.sesionRevocada("adm_sesion_rol", marcaNueva));
        assertTrue(UsuarioService.sesionRevocada("adm_sesion_rol", marcaVieja));
    }

    @Test
    @DisplayName("restablecerClave también revoca las sesiones abiertas del usuario")
    void restablecerClaveRevocaSesiones() {
        crear("adm_sesion_clave", Usuario.ROL_CLIENTE);
        long marcaVieja = UsuarioService.marcaActual();
        assertNotNull(ingresar("adm_sesion_clave", CLAVE));
        assertFalse(UsuarioService.sesionRevocada("adm_sesion_clave", marcaVieja));

        assertNull(usuarios.restablecerClave(actorAdmin(), idDe("adm_sesion_clave"), "NuevaClave987"));
        assertTrue(UsuarioService.sesionRevocada("adm_sesion_clave", marcaVieja));

        long marcaNueva = UsuarioService.marcaActual();
        assertNotNull(ingresar("adm_sesion_clave", "NuevaClave987"));
        assertFalse(UsuarioService.sesionRevocada("adm_sesion_clave", marcaNueva));
    }

    @Test
    @DisplayName("Una sesión sin marca (anterior al esquema) se revoca solo si hay una revocación para ese usuario")
    void sesionSinMarcaSeRevocaSoloSiHayRevocacion() {
        crear("adm_sesion_sinmarca", Usuario.ROL_CLIENTE);
        assertFalse(UsuarioService.sesionRevocada("adm_sesion_sinmarca", null));
        assertNull(usuarios.cambiarEstadoUsuario(actorAdmin(), idDe("adm_sesion_sinmarca"), false));
        assertTrue(UsuarioService.sesionRevocada("adm_sesion_sinmarca", null));
    }

    @Test
    @DisplayName("sesionRevocada es false para null, para cuentas sin cambios y para nombres desconocidos")
    void sesionRevocadaSinCambios() {
        assertFalse(UsuarioService.sesionRevocada(null, null));
        assertFalse(UsuarioService.sesionRevocada("", 0L));
        assertFalse(UsuarioService.sesionRevocada("adm_nunca_existio", 0L));
        crear("adm_sin_cambios", Usuario.ROL_CLIENTE);
        assertFalse(UsuarioService.sesionRevocada("adm_sin_cambios", 0L));
    }

    // ---------------------------------------------------------------- usuario normalizado (NFKC) y variantes invisibles

    @Test
    @DisplayName("Variantes Unicode del usuario (ancho completo, U+200D, NUL) no evaden el bloqueo ni crean contadores")
    void variantesUnicodeNoEvadenElBloqueo() {
        crear("adm_unicode", Usuario.ROL_CLIENTE);
        // Ancho completo: NFKC lo lleva a "adm_unicode" y cuenta contra el mismo usuario
        String anchoCompleto = "\uFF41\uFF44\uFF4D_unicode";
        for (int i = 0; i < UsuarioService.MAX_INTENTOS; i++) {
            assertNull(ingresar(anchoCompleto, "incorrecta" + i));
        }
        assertTrue(usuarios.estaBloqueado("adm_unicode"), "El ancho completo cuenta contra el usuario real");
        assertTrue(usuarios.estaBloqueado(anchoCompleto));
        assertNull(ingresar("adm_unicode", CLAVE), "Bloqueada: ni la clave correcta entra");
    }

    @Test
    @DisplayName("Los usuarios con caracteres invisibles fallan sin consultar la BD y sin crear contadores nuevos")
    void usuariosInvalidosNoCreanContadores() throws ReflectiveOperationException {
        crear("adm_invisible", Usuario.ROL_CLIENTE);
        Field campo = UsuarioService.class.getDeclaredField("INTENTOS");
        campo.setAccessible(true);
        Map<?, ?> intentos = (Map<?, ?>) campo.get(null);
        int antes = intentos.size();

        // Con el carácter invisible al final, SQL Server lo trataría como el mismo usuario: se repite más que el máximo
        for (int i = 0; i < UsuarioService.MAX_INTENTOS + 1; i++) {
            assertNull(ingresar("adm_invisible\u200D", CLAVE), "Debe fallar aunque la clave sea la correcta");
        }
        for (String variante : new String[]{"adm\u200D_invisible", "adm_invisible\u0000x", "adm invisible", "", "x".repeat(60)}) {
            assertNull(ingresar(variante, CLAVE), "Debe fallar: '" + variante + "'");
        }
        assertEquals(antes, intentos.size(), "Las cadenas inválidas no deben crear entradas en el mapa de intentos");
        assertFalse(usuarios.estaBloqueado("adm_invisible"), "Los fallos inválidos no bloquean al usuario real");
        assertNotNull(ingresar("adm_invisible", CLAVE), "El usuario real sigue entrando con su clave");
    }

    // ---------------------------------------------------------------- bloqueo: aviso único

    @Test
    @DisplayName("intentarAcceso informa el bloqueo solo en el fallo que lo crea (para auditarlo una vez)")
    void intentarAccesoInformaElBloqueoUnaVez() {
        crear("adm_aviso", Usuario.ROL_CLIENTE);
        for (int i = 1; i < UsuarioService.MAX_INTENTOS; i++) {
            UsuarioService.ResultadoAcceso r = usuarios.intentarAcceso("adm_aviso", "mala" + i, null, IP);
            assertNull(r.usuario());
            assertFalse(r.bloqueoNuevo(), "Con " + i + " fallos aún no hay bloqueo");
        }
        UsuarioService.ResultadoAcceso quintoFallo = usuarios.intentarAcceso("adm_aviso", "mala99", null, IP);
        assertNull(quintoFallo.usuario());
        assertTrue(quintoFallo.bloqueoNuevo(), "El fallo número " + UsuarioService.MAX_INTENTOS + " crea el bloqueo");

        UsuarioService.ResultadoAcceso sexto = usuarios.intentarAcceso("adm_aviso", "mala100", null, IP);
        assertNull(sexto.usuario());
        assertFalse(sexto.bloqueoNuevo(), "Los intentos contra una cuenta ya bloqueada no son un bloqueo nuevo");
    }

    // ---------------------------------------------------------------- recifrado de claves con menos iteraciones

    @Test
    @DisplayName("Una clave cifrada con menos iteraciones entra una vez y se recifra con las iteraciones actuales")
    void recifraClavesConMenosIteraciones() throws ReflectiveOperationException {
        Map<String, String[]> respaldo = respaldoInterno();
        String viejo = hashConIteraciones("claveAntigua1", 10_000);
        assertTrue(Seguridad.necesitaRecifrar(viejo));
        synchronized (respaldo) {
            respaldo.put("adm_recifrar", new String[]{"88002", viejo, "Cuenta Antigua",
                    correo("adm_recifrar"), Usuario.ROL_CLIENTE, "1"});
        }
        try {
            assertNull(ingresar("adm_recifrar", "claveIncorrecta1"));
            assertEquals(viejo, respaldo.get("adm_recifrar")[1], "Un fallo no recifra");

            assertNotNull(ingresar("adm_recifrar", "claveAntigua1"));
            String nuevo = respaldo.get("adm_recifrar")[1];
            assertNotEquals(viejo, nuevo, "Tras ingresar la clave se guarda de nuevo");
            assertFalse(Seguridad.necesitaRecifrar(nuevo), "El nuevo hash usa las iteraciones actuales");
            assertTrue(Seguridad.verificarClave("claveAntigua1", nuevo));

            assertNotNull(ingresar("adm_recifrar", "claveAntigua1"), "Con el hash nuevo sigue entrando");
        } finally {
            synchronized (respaldo) {
                respaldo.remove("adm_recifrar");
            }
        }
    }

    /** Hash con el formato de {@link Seguridad} pero con un número de iteraciones distinto (cuentas antiguas). */
    private static String hashConIteraciones(String clave, int iteraciones) {
        try {
            byte[] sal = new byte[16];
            new java.security.SecureRandom().nextBytes(sal);
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(clave.toCharArray(), sal, iteraciones, 256);
            byte[] hash = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            java.util.Base64.Encoder b64 = java.util.Base64.getEncoder();
            return "pbkdf2_sha256$" + iteraciones + "$" + b64.encodeToString(sal) + "$" + b64.encodeToString(hash);
        } catch (java.security.GeneralSecurityException e) {
            throw new AssertionError(e);
        }
    }

    // ---------------------------------------------------------------- listados

    @Test
    @DisplayName("listarUsuarios incluye inactivos, ordena por rol y buscarPorId encuentra o devuelve null")
    void listarYBuscarPorId() {
        crear("adm_lista_cli", Usuario.ROL_CLIENTE);
        crear("adm_lista_adm", Usuario.ROL_ADMIN);
        assertNull(usuarios.cambiarEstadoUsuario(actorAdmin(), idDe("adm_lista_cli"), false));

        List<Usuario> lista = usuarios.listarUsuarios();
        Usuario inactivo = buscar("adm_lista_cli");
        assertNotNull(inactivo);
        assertFalse(inactivo.isActivo(), "La lista incluye las cuentas inactivas");

        int posAdmin = indiceDe(lista, "adm_lista_adm");
        int posCliente = indiceDe(lista, "adm_lista_cli");
        assertTrue(posAdmin < posCliente, "El Administrador va antes que el Cliente");

        Usuario porId = usuarios.buscarPorId(inactivo.getIdUsuario());
        assertNotNull(porId);
        assertEquals("adm_lista_cli", porId.getUsername());
        assertNull(usuarios.buscarPorId(987_654));
    }

    private static int indiceDe(List<Usuario> lista, String username) {
        for (int i = 0; i < lista.size(); i++) {
            if (lista.get(i).getUsername().equals(username)) {
                return i;
            }
        }
        throw new AssertionError("No está en la lista: " + username);
    }
}
