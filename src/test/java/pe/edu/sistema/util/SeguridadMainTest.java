package pe.edu.sistema.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prueba de {@link Seguridad#main}: imprime solo el hash (lo usa el script de inicialización de Azure), con la clave
 * por argumento o, sin argumentos, por la entrada estándar (así no aparece en la lista de procesos).
 */
class SeguridadMainTest {

    private static InputStream entrada(String texto) {
        return new ByteArrayInputStream(texto.getBytes(StandardCharsets.UTF_8));
    }

    /** Ejecuta la herramienta con los argumentos y la entrada dados; devuelve {código, salida, error}. */
    private static Object[] ejecutar(String[] args, String stdin) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        int codigo = Seguridad.ejecutar(args, entrada(stdin),
                new PrintStream(salida, true, StandardCharsets.UTF_8), new PrintStream(error, true, StandardCharsets.UTF_8));
        return new Object[]{codigo, salida.toString(StandardCharsets.UTF_8), error.toString(StandardCharsets.UTF_8)};
    }

    private static void verificarHash(String texto, String clave) {
        assertTrue(texto.endsWith("\n") && !texto.endsWith("\r\n"), "debe terminar en \\n, sin retorno de carro");
        String hash = texto.substring(0, texto.length() - 1);
        assertFalse(hash.contains("\n") || hash.contains(clave));
        assertTrue(Seguridad.esHash(hash));
        assertTrue(Seguridad.verificarClave(clave, hash));
        assertFalse(Seguridad.verificarClave("otra-clave", hash));
        assertEquals(1, texto.lines().count());
    }

    @Test
    @DisplayName("main con argumento imprime únicamente el hash de la clave, en una sola línea terminada en \\n")
    void imprimeSoloElHash() {
        PrintStream original = System.out;
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(salida, true, StandardCharsets.UTF_8));
            Seguridad.main(new String[]{"ClavePrivada2026"});
        } finally {
            System.setOut(original);
        }
        verificarHash(salida.toString(StandardCharsets.UTF_8), "ClavePrivada2026");
    }

    @Test
    @DisplayName("main sin argumentos lee la clave de la entrada estándar (sin salto de línea final)")
    void leeLaClaveDeLaEntradaEstandar() {
        InputStream original = System.in;
        PrintStream salidaOriginal = System.out;
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            System.setIn(entrada("ClavePrivada2026"));   // como printf '%s' "$CLAVE" | java ...
            System.setOut(new PrintStream(salida, true, StandardCharsets.UTF_8));
            Seguridad.main(new String[0]);
        } finally {
            System.setIn(original);
            System.setOut(salidaOriginal);
        }
        verificarHash(salida.toString(StandardCharsets.UTF_8), "ClavePrivada2026");
    }

    @Test
    @DisplayName("Por la entrada estándar solo cuenta la primera línea y se descarta el retorno de carro final")
    void usaSoloLaPrimeraLinea() {
        for (String texto : new String[]{"ClavePrivada2026\n", "ClavePrivada2026\r\n", "ClavePrivada2026\nsobrante\n"}) {
            Object[] r = ejecutar(null, texto);
            assertEquals(0, r[0]);
            verificarHash((String) r[1], "ClavePrivada2026");
        }
    }

    @Test
    @DisplayName("La clave con acentos y espacios llega íntegra por la entrada estándar (UTF-8)")
    void conservaEspaciosYAcentos() {
        Object[] r = ejecutar(new String[0], "Clave ñandú 2026");
        assertEquals(0, r[0]);
        verificarHash((String) r[1], "Clave ñandú 2026");
    }

    @Test
    @DisplayName("Sin argumento ni entrada, o con argumento vacío, termina con código 2 y no imprime nada")
    void sinClaveTerminaConCodigoDos() {
        for (Object[] r : new Object[][]{ejecutar(new String[0], ""), ejecutar(new String[0], "\n"),
            ejecutar(new String[]{""}, "ClavePrivada2026")}) {
            assertEquals(2, r[0]);
            assertEquals("", r[1], "la salida estándar debe quedar vacía");
            assertTrue(((String) r[2]).startsWith("Uso:"));
        }
    }
}
