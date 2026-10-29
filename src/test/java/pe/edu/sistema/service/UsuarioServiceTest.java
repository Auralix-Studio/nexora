package pe.edu.sistema.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.edu.sistema.model.Usuario;
import pe.edu.sistema.util.ConexionBD;

/**
 * Pruebas de {@link UsuarioService} con el respaldo en memoria (sin SQL Server).
 * El respaldo es estático y dura todo el JVM, así que cada prueba usa nombres de usuario propios.
 */
class UsuarioServiceTest {

    private static final String MSG_ESPACIOS = "La contraseña no puede empezar ni terminar con espacios.";

    private final UsuarioService usuarios = new UsuarioService();

    /** Evita escribir en una base real si alguien ejecuta las pruebas con SQL Server activo. */
    @BeforeAll
    static void soloRespaldoEnMemoria() {
        assumeFalse(ConexionBD.estaDisponible(),
                "Estas pruebas usan el respaldo en memoria; hay un SQL Server activo y no se modificará");
    }

    @Test
    @DisplayName("Registrar con la clave empezando o terminando en espacios no lanza y devuelve el mensaje")
    void registroConClaveConEspaciosDaMensaje() {
        for (String clave : new String[]{" clave123", "clave123 ", "  clave123  ", "    "}) {
            String error = assertDoesNotThrow(
                    () -> usuarios.registrarCliente("Cliente Espacios", "cliente_espacios", clave),
                    "registrarCliente no debe lanzar con la clave '" + clave + "'");
            assertEquals(MSG_ESPACIOS, error);
        }
        // Ninguno de los intentos dejó la cuenta creada
        assertNull(assertDoesNotThrow(
                () -> usuarios.autenticarUsuario("cliente_espacios", " clave123", null, "127.0.0.1")));
        assertNull(usuarios.autenticarUsuario("cliente_espacios", "clave123", null, "127.0.0.1"));
    }

    @Test
    @DisplayName("La clave se guarda tal cual: el registro y el ingreso usan exactamente el mismo texto")
    void registroYIngresoSonCoherentes() {
        String clave = "mi clave  con espacios7";
        assertEquals("", usuarios.registrarCliente("Cliente Coherente", "cliente_coherente", clave));

        Usuario u = usuarios.autenticarUsuario(" CLIENTE_COHERENTE ", clave, Usuario.ROL_CLIENTE, "127.0.0.1");
        assertNotNull(u, "La cuenta recién creada debe poder ingresar");
        assertEquals("cliente_coherente", u.getUsername());
        assertTrue(u.isCliente());

        assertNull(usuarios.autenticarUsuario("cliente_coherente", clave + " ", null, "127.0.0.1"));
        assertNull(usuarios.autenticarUsuario("cliente_coherente", "mi clave con espacios7", null, "127.0.0.1"));
    }

    @Test
    @DisplayName("Registrar valida usuario, clave corta, nombre vacío y usuario repetido")
    void registroValidaDatos() {
        String errorUsuario = "El usuario debe tener entre 3 y 40 caracteres: letras minúsculas, números, punto, guion o guion bajo.";
        assertEquals(errorUsuario, usuarios.registrarCliente("Cliente Corto", "ab", "clave123"));
        assertEquals("La contraseña debe tener al menos 8 caracteres.",
                usuarios.registrarCliente("Cliente Corto", "cliente_clave_corta", "abc1234"));
        assertEquals("Ingrese el nombre completo del cliente.",
                usuarios.registrarCliente("   ", "cliente_sin_nombre", "clave123"));
        assertEquals("", usuarios.registrarCliente("Cliente Repetido", "cliente_repetido", "clave123"));
        assertTrue(usuarios.registrarCliente("Otro Nombre", "CLIENTE_REPETIDO", "otraClave99").contains("ya está registrado"));
    }

    @Test
    @DisplayName("Registrar aplica las mismas reglas de clave que crearUsuario: corta, sin números o sin letras falla")
    void registroConClaveDebilFalla() {
        assertEquals("La contraseña debe tener al menos 8 caracteres.",
                usuarios.registrarCliente("Cliente Debil", "cliente_debil", "abc"));
        assertEquals("La contraseña debe combinar letras y números.",
                usuarios.registrarCliente("Cliente Debil", "cliente_debil", "sololetras"));
        assertEquals("La contraseña debe combinar letras y números.",
                usuarios.registrarCliente("Cliente Debil", "cliente_debil", "12345678"));
        assertEquals("Ingresa una contraseña.", usuarios.registrarCliente("Cliente Debil", "cliente_debil", ""));
        assertEquals("Ingresa una contraseña.", usuarios.registrarCliente("Cliente Debil", "cliente_debil", null));
        assertEquals("La contraseña no puede superar 100 caracteres.",
                usuarios.registrarCliente("Cliente Debil", "cliente_debil", "a1".repeat(51)));
        assertNull(usuarios.autenticarUsuario("cliente_debil", "sololetras", null, "127.0.0.1"),
                "Ningún intento dejó la cuenta creada");
    }

    @Test
    @DisplayName("Registrar rechaza usuarios fuera de [a-z0-9._-]{3,40} (tras normalizar) sin crear la cuenta")
    void registroConUsuarioInvalidoFalla() {
        for (String malo : new String[]{"con espacio", "ñandu", "a@b", "x".repeat(41), "user\u200D", "cliente\u0000x", null}) {
            String error = usuarios.registrarCliente("Cliente Malo", malo, "clave123");
            assertNotNull(error, "Debía rechazar el usuario '" + malo + "'");
            assertTrue(error.startsWith("El usuario debe tener entre 3 y 40 caracteres"), error);
        }
        // El ancho completo y las mayúsculas se normalizan: son el mismo usuario que la forma ASCII en minúsculas
        assertEquals("", usuarios.registrarCliente("Cliente Normal", "\uFF23LIENTE_NORMAL", "clave123"));
        assertNotNull(usuarios.autenticarUsuario("cliente_normal", "clave123", Usuario.ROL_CLIENTE, "127.0.0.1"));
        assertTrue(usuarios.registrarCliente("Otro", "cliente_normal", "clave123").contains("ya está registrado"));
    }

    @Test
    @DisplayName("Registrar y listar desde distintas instancias a la vez no falla ni pierde cuentas")
    void registroYListadoConcurrentes() throws Exception {
        int hilos = 4;
        int porHilo = 10;
        ExecutorService pool = Executors.newFixedThreadPool(hilos + 1);
        AtomicBoolean escribiendo = new AtomicBoolean(true);
        try {
            List<Future<?>> escritores = new ArrayList<>();
            for (int h = 0; h < hilos; h++) {
                int hilo = h;
                escritores.add(pool.submit(() -> {
                    UsuarioService propio = new UsuarioService();
                    for (int i = 0; i < porHilo; i++) {
                        assertEquals("", propio.registrarCliente("Cliente Hilo", "cliente_hilo" + hilo + "_" + i, "clave123"));
                    }
                }));
            }
            Future<?> lector = pool.submit(() -> {
                UsuarioService propio = new UsuarioService();
                while (escribiendo.get()) {
                    propio.listarUsuarios();
                }
            });
            for (Future<?> f : escritores) {
                f.get(30, TimeUnit.SECONDS);
            }
            escribiendo.set(false);
            lector.get(30, TimeUnit.SECONDS);
        } finally {
            escribiendo.set(false);
            pool.shutdownNow();
        }

        long hilosRegistrados = usuarios.listarUsuarios().stream()
                .filter(u -> u.getUsername().startsWith("cliente_hilo")).count();
        assertEquals((long) hilos * porHilo, hilosRegistrados);
    }
}
