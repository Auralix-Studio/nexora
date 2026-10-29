package pe.edu.sistema.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Prueba de {@link Seguridad#main}: imprime solo el hash (lo usa el script de inicialización de Azure). */
class SeguridadMainTest {

    @Test
    @DisplayName("main imprime únicamente el hash de la clave, en una sola línea terminada en \\n")
    void imprimeSoloElHash() {
        PrintStream original = System.out;
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(salida, true, StandardCharsets.UTF_8));
            Seguridad.main(new String[]{"ClavePrivada2026"});
        } finally {
            System.setOut(original);
        }
        String texto = salida.toString(StandardCharsets.UTF_8);

        assertTrue(texto.endsWith("\n") && !texto.endsWith("\r\n"), "debe terminar en \\n, sin retorno de carro");
        String hash = texto.substring(0, texto.length() - 1);
        assertFalse(hash.contains("\n") || hash.contains("ClavePrivada2026"));
        assertTrue(Seguridad.esHash(hash));
        assertTrue(Seguridad.verificarClave("ClavePrivada2026", hash));
        assertFalse(Seguridad.verificarClave("otra-clave", hash));
        assertEquals(1, texto.lines().count());
    }
}
