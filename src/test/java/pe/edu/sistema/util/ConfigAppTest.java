package pe.edu.sistema.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pruebas de {@link ConfigApp}: la propiedad del sistema manda sobre la variable de entorno. */
class ConfigAppTest {

    private Map<String, String> guardadas;

    @BeforeEach
    void guardar() {
        guardadas = PropiedadesSistema.guardar();
        PropiedadesSistema.limpiarConfiguracion();
    }

    @AfterEach
    void restaurar() {
        PropiedadesSistema.restaurar(guardadas);
    }

    @Test
    @DisplayName("esProduccion solo es verdadero con NEXORA_ENTORNO=produccion (sin distinguir mayúsculas)")
    void produccionSegunEntorno() {
        assumeTrue(System.getenv(ConfigApp.ENTORNO) == null, "el entorno de la prueba define NEXORA_ENTORNO");
        assertFalse(ConfigApp.esProduccion());
        assertEquals("desarrollo", ConfigApp.entorno());

        System.setProperty(ConfigApp.ENTORNO, "produccion");
        assertTrue(ConfigApp.esProduccion());
        assertEquals("produccion", ConfigApp.entorno());

        System.setProperty(ConfigApp.ENTORNO, "PRODUCCION");
        assertTrue(ConfigApp.esProduccion());

        System.setProperty(ConfigApp.ENTORNO, "desarrollo");
        assertFalse(ConfigApp.esProduccion());

        System.setProperty(ConfigApp.ENTORNO, "otra-cosa");
        assertFalse(ConfigApp.esProduccion());
    }

    @Test
    @DisplayName("esDemo solo es verdadero con NEXORA_DEMO=true")
    void demoSegunVariable() {
        assumeTrue(System.getenv(ConfigApp.DEMO) == null, "el entorno de la prueba define NEXORA_DEMO");
        assertFalse(ConfigApp.esDemo());

        System.setProperty(ConfigApp.DEMO, "true");
        assertTrue(ConfigApp.esDemo());

        System.setProperty(ConfigApp.DEMO, "TRUE");
        assertTrue(ConfigApp.esDemo());

        System.setProperty(ConfigApp.DEMO, "false");
        assertFalse(ConfigApp.esDemo());

        System.setProperty(ConfigApp.DEMO, "1");
        assertFalse(ConfigApp.esDemo());
    }

    @Test
    @DisplayName("version es NEXORA_VERSION o dev; los espacios se recortan y un valor vacío cuenta como ausente")
    void versionConValorPorDefecto() {
        assumeTrue(System.getenv(ConfigApp.VERSION) == null, "el entorno de la prueba define NEXORA_VERSION");
        assertEquals("dev", ConfigApp.version());

        System.setProperty(ConfigApp.VERSION, "  a1b2c3d  ");
        assertEquals("a1b2c3d", ConfigApp.version());

        System.setProperty(ConfigApp.VERSION, "   ");
        assertEquals("dev", ConfigApp.version());
    }

    @Test
    @DisplayName("leer prefiere la propiedad del sistema y devuelve el valor por defecto si no hay nada")
    void leerPropiedadYPorDefecto() {
        assertEquals("por-defecto", ConfigApp.leer("NEXORA_CLAVE_INEXISTENTE_PRUEBA", "por-defecto"));
        System.setProperty("NEXORA_CLAVE_INEXISTENTE_PRUEBA", " valor ");
        try {
            assertEquals("valor", ConfigApp.leer("NEXORA_CLAVE_INEXISTENTE_PRUEBA", "por-defecto"));
        } finally {
            System.clearProperty("NEXORA_CLAVE_INEXISTENTE_PRUEBA");
        }
    }
}
