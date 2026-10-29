package pe.edu.sistema.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.edu.sistema.model.RegistroAuditoria;
import pe.edu.sistema.model.Usuario;
import pe.edu.sistema.util.ConexionBD;

/**
 * Pruebas de {@link AuditoriaService} con el respaldo en memoria. El servicio es un singleton compartido:
 * cada prueba audita con nombres de usuario propios y, donde hace falta contar, compara contra el total anterior.
 */
class AuditoriaServiceTest {

    private static final int SIN_LIMITE = 1000;

    private final AuditoriaService auditoria = AuditoriaService.getInstancia();

    /** Evita escribir en una base real si alguien ejecuta las pruebas con SQL Server activo. */
    @BeforeAll
    static void soloRespaldoEnMemoria() {
        assumeFalse(ConexionBD.estaDisponible(),
                "Estas pruebas usan el respaldo en memoria; hay un SQL Server activo y no se modificará");
    }

    private List<RegistroAuditoria> de(String usuario) {
        return auditoria.listar(usuario, null, null, null, SIN_LIMITE);
    }

    @Test
    @DisplayName("Es un singleton")
    void esSingleton() {
        assertSame(AuditoriaService.getInstancia(), AuditoriaService.getInstancia());
    }

    @Test
    @DisplayName("registrar con un usuario guarda todos los datos y listar los devuelve con fecha e identificador")
    void registrarYListar() {
        Usuario actor = new Usuario(1, "aud_admin", "Admin de Auditoría", "a@x.pe", Usuario.ROL_ADMIN, true);

        auditoria.registrar(actor, RegistroAuditoria.PEDIDO_ANULADO, "Pedido PED-0099", "Motivo: duplicado", "10.0.0.7");

        List<RegistroAuditoria> registros = de("aud_admin");
        assertEquals(1, registros.size());
        RegistroAuditoria r = registros.get(0);
        assertEquals("aud_admin", r.getUsuario());
        assertEquals(Usuario.ROL_ADMIN, r.getRol());
        assertEquals(RegistroAuditoria.PEDIDO_ANULADO, r.getAccion());
        assertEquals("Pedido PED-0099", r.getEntidad());
        assertEquals("Motivo: duplicado", r.getDetalle());
        assertEquals("10.0.0.7", r.getIp());
        assertTrue(r.getIdAuditoria() > 0);
        assertNotNull(r.getFecha());
        assertTrue(r.getFecha().matches("\\d{2}/\\d{2}/\\d{4} · \\d{2}:\\d{2}"), r.getFecha());
    }

    @Test
    @DisplayName("La variante con texto sirve para accesos sin usuario autenticado")
    void registrarSinUsuarioAutenticado() {
        auditoria.registrar("aud_intruso", null, RegistroAuditoria.ACCESO_FALLIDO, "Inicio de sesión",
                "Clave incorrecta", "203.0.113.9");

        List<RegistroAuditoria> registros = de("aud_intruso");
        assertEquals(1, registros.size());
        assertEquals("aud_intruso", registros.get(0).getUsuario());
        assertNull(registros.get(0).getRol());
        assertEquals(RegistroAuditoria.ACCESO_FALLIDO, registros.get(0).getAccion());
        assertTrue(registros.get(0).isAlerta());
    }

    @Test
    @DisplayName("Listar por usuario no distingue mayúsculas, exige el nombre completo y recorta espacios")
    void filtroPorUsuario() {
        auditoria.registrar("Aud_Mayus", "Almacén", RegistroAuditoria.COMPRA, "x", "y", "127.0.0.1");
        auditoria.registrar("aud_mayus2", "Almacén", RegistroAuditoria.COMPRA, "x", "y", "127.0.0.1");

        assertEquals(1, de("aud_mayus").size());
        assertEquals(1, de("AUD_MAYUS").size());
        assertEquals(1, de("  Aud_Mayus ").size());
        assertEquals(1, de("aud_mayus2").size());
        assertTrue(de("aud_may").isEmpty(), "El filtro es por nombre exacto, no parcial");
        assertEquals("Aud_Mayus", de("aud_mayus").get(0).getUsuario(), "Se guarda como llegó");
    }

    @Test
    @DisplayName("Listar por acción filtra por la acción exacta y devuelve del más reciente al más antiguo")
    void filtroPorAccionYOrden() {
        auditoria.registrar("aud_acciones", "Administrador", RegistroAuditoria.USUARIO_CREADO, "e1", "primero", "127.0.0.1");
        auditoria.registrar("aud_acciones", "Administrador", RegistroAuditoria.CATEGORIA, "e2", "segundo", "127.0.0.1");
        auditoria.registrar("aud_acciones", "Administrador", RegistroAuditoria.USUARIO_CREADO, "e3", "tercero", "127.0.0.1");

        List<RegistroAuditoria> todas = de("aud_acciones");
        assertEquals(3, todas.size());
        assertEquals("tercero", todas.get(0).getDetalle(), "El más reciente va primero");
        assertEquals("segundo", todas.get(1).getDetalle());
        assertEquals("primero", todas.get(2).getDetalle());
        assertTrue(todas.get(0).getIdAuditoria() > todas.get(1).getIdAuditoria());

        List<RegistroAuditoria> soloCreados = auditoria.listar("aud_acciones", RegistroAuditoria.USUARIO_CREADO,
                null, null, SIN_LIMITE);
        assertEquals(2, soloCreados.size());
        for (RegistroAuditoria r : soloCreados) {
            assertEquals(RegistroAuditoria.USUARIO_CREADO, r.getAccion());
        }
        assertEquals(1, auditoria.listar("aud_acciones", "  " + RegistroAuditoria.CATEGORIA + " ", null, null,
                SIN_LIMITE).size(), "Recorta espacios");
        assertTrue(auditoria.listar("aud_acciones", RegistroAuditoria.COMPRA, null, null, SIN_LIMITE).isEmpty());
        assertTrue(auditoria.listar("aud_acciones", "usuario creado", null, null, SIN_LIMITE).isEmpty(),
                "La acción se compara exacta");
    }

    @Test
    @DisplayName("Listar por fechas usa límites inclusivos y acepta uno solo de los extremos")
    void filtroPorFechas() {
        auditoria.registrar("aud_fechas", "Almacén", RegistroAuditoria.STOCK_AJUSTE, "AP001", "+5", "127.0.0.1");
        LocalDate hoy = LocalDate.now();

        assertEquals(1, auditoria.listar("aud_fechas", null, hoy, hoy, SIN_LIMITE).size());
        assertEquals(1, auditoria.listar("aud_fechas", null, hoy.minusDays(1), hoy.plusDays(1), SIN_LIMITE).size());
        assertEquals(1, auditoria.listar("aud_fechas", null, hoy, null, SIN_LIMITE).size());
        assertEquals(1, auditoria.listar("aud_fechas", null, null, hoy, SIN_LIMITE).size());
        assertTrue(auditoria.listar("aud_fechas", null, hoy.plusDays(1), null, SIN_LIMITE).isEmpty());
        assertTrue(auditoria.listar("aud_fechas", null, null, hoy.minusDays(1), SIN_LIMITE).isEmpty());
        assertTrue(auditoria.listar("aud_fechas", null, hoy.plusDays(1), hoy.plusDays(3), SIN_LIMITE).isEmpty());
    }

    @Test
    @DisplayName("El límite recorta el resultado a los más recientes; un límite menor que 1 devuelve una lista vacía")
    void limite() {
        for (int i = 1; i <= 3; i++) {
            auditoria.registrar("aud_limite", "Almacén", RegistroAuditoria.COMPRA, "e", "registro " + i, "127.0.0.1");
        }
        List<RegistroAuditoria> dos = auditoria.listar("aud_limite", null, null, null, 2);
        assertEquals(2, dos.size());
        assertEquals("registro 3", dos.get(0).getDetalle());
        assertEquals("registro 2", dos.get(1).getDetalle());

        assertTrue(auditoria.listar("aud_limite", null, null, null, 0).isEmpty());
        assertTrue(auditoria.listar("aud_limite", null, null, null, -1).isEmpty());
    }

    @Test
    @DisplayName("Los textos largos se recortan al largo de cada columna")
    void recortaTextosLargos() {
        String usuarioLargo = "aud_recorte_" + "u".repeat(60);   // 72 caracteres
        auditoria.registrar(usuarioLargo, "r".repeat(50), "a".repeat(60), "e".repeat(100), "d".repeat(500),
                "i".repeat(60));

        List<RegistroAuditoria> registros = de(usuarioLargo.substring(0, 40));
        assertEquals(1, registros.size(), "El usuario guardado son sus primeros 40 caracteres");
        RegistroAuditoria r = registros.get(0);
        assertEquals(40, r.getUsuario().length());
        assertEquals(30, r.getRol().length());
        assertEquals(40, r.getAccion().length());
        assertEquals(80, r.getEntidad().length());
        assertEquals(400, r.getDetalle().length());
        assertEquals(45, r.getIp().length());
        assertEquals("d".repeat(400), r.getDetalle());
    }

    @Test
    @DisplayName("Registrar nunca lanza excepciones, ni con todo nulo ni con textos vacíos")
    void nuncaLanzaConNulos() {
        int antes = de("anónimo").size();

        assertDoesNotThrow(() -> auditoria.registrar((Usuario) null, null, null, null, null));
        assertDoesNotThrow(() -> auditoria.registrar((String) null, null, null, null, null, null));
        assertDoesNotThrow(() -> auditoria.registrar("   ", "  ", "  ", "  ", "  ", "  "));
        assertDoesNotThrow(() -> auditoria.registrar(new Usuario(), null, null, null, null));

        List<RegistroAuditoria> anonimos = de("anónimo");
        assertEquals(antes + 4, anonimos.size(), "Cada llamada dejó un registro con el usuario 'anónimo'");
        RegistroAuditoria r = anonimos.get(0);
        assertEquals("anónimo", r.getUsuario());
        assertEquals("Sin acción", r.getAccion());
        assertNull(r.getRol());
        assertNull(r.getEntidad());
        assertNull(r.getDetalle());
        assertNull(r.getIp());
    }

    @Test
    @DisplayName("listar acepta filtros nulos o vacíos sin lanzar excepciones")
    void listarConFiltrosNulos() {
        auditoria.registrar("aud_filtros_vacios", "Almacén", RegistroAuditoria.COMPRA, "e", "d", "127.0.0.1");

        assertDoesNotThrow(() -> auditoria.listar(null, null, null, null, 5));
        assertDoesNotThrow(() -> auditoria.listar("", "", null, null, 5));
        assertFalse(auditoria.listar(null, null, null, null, 5).isEmpty());
        assertFalse(auditoria.listar("   ", "   ", null, null, 5).isEmpty());
        assertTrue(auditoria.listar(null, null, null, null, 5).size() <= 5);
    }

    @Test
    @DisplayName("Las fechas extremas no lanzan excepciones: fuera de rango el límite no filtra o no coincide con nada")
    void listarConFechasExtremasNoLanza() {
        auditoria.registrar("aud_fechas_extremas", "Almacén", RegistroAuditoria.COMPRA, "e", "d", "127.0.0.1");
        LocalDate[] extremas = {LocalDate.of(10000, 1, 1), LocalDate.of(-1, 1, 1), LocalDate.MIN, LocalDate.MAX,
                LocalDate.of(999_999_999, 12, 31), LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31)};
        for (LocalDate extrema : extremas) {
            assertDoesNotThrow(() -> auditoria.listar("aud_fechas_extremas", null, extrema, null, 10), "desde " + extrema);
            assertDoesNotThrow(() -> auditoria.listar("aud_fechas_extremas", null, null, extrema, 10), "hasta " + extrema);
            assertDoesNotThrow(() -> auditoria.listar("aud_fechas_extremas", null, extrema, extrema, 10), "ambos " + extrema);
        }
        assertEquals(1, auditoria.listar("aud_fechas_extremas", null, LocalDate.of(-1, 1, 1), LocalDate.MAX, 10).size(),
                "Límites fuera de rango dejan de restringir");
        assertTrue(auditoria.listar("aud_fechas_extremas", null, LocalDate.MAX, null, 10).isEmpty(),
                "Un 'desde' en el futuro lejano no coincide con ningún registro");
        assertTrue(auditoria.listar("aud_fechas_extremas", null, null, LocalDate.MIN, 10).isEmpty(),
                "Un 'hasta' en el pasado remoto no coincide con ningún registro");
    }

    @Test
    @DisplayName("Los registros devueltos son copias: modificarlos no altera la auditoría guardada")
    void listarDevuelveCopias() {
        auditoria.registrar("aud_copias", "Almacén", RegistroAuditoria.COMPRA, "e", "original", "127.0.0.1");

        RegistroAuditoria copia = de("aud_copias").get(0);
        copia.setDetalle("alterado");
        copia.setUsuario("otro");

        RegistroAuditoria guardado = de("aud_copias").get(0);
        assertEquals("original", guardado.getDetalle());
        assertEquals("aud_copias", guardado.getUsuario());
    }

    @Test
    @DisplayName("Solo las acciones de seguridad y destructivas se marcan como alerta")
    void alertas() {
        RegistroAuditoria r = new RegistroAuditoria();
        for (String accion : new String[]{RegistroAuditoria.ACCESO_FALLIDO, RegistroAuditoria.CUENTA_BLOQUEADA,
                RegistroAuditoria.PEDIDO_ANULADO, RegistroAuditoria.PRODUCTO_ELIMINADO}) {
            r.setAccion(accion);
            assertTrue(r.isAlerta(), accion);
        }
        for (String accion : new String[]{RegistroAuditoria.INICIO_SESION, RegistroAuditoria.COMPRA,
                RegistroAuditoria.USUARIO_CREADO, RegistroAuditoria.CATEGORIA, null}) {
            r.setAccion(accion);
            assertFalse(r.isAlerta(), String.valueOf(accion));
        }
        assertTrue(RegistroAuditoria.ACCIONES.containsAll(List.of(RegistroAuditoria.INICIO_SESION,
                RegistroAuditoria.PEDIDO_ANULADO, RegistroAuditoria.CATEGORIA)));
    }
}
