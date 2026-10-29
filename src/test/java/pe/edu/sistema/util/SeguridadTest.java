package pe.edu.sistema.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pruebas del cifrado de contraseñas ({@link Seguridad}): PBKDF2 con sal aleatoria. */
class SeguridadTest {

    @Test
    @DisplayName("La misma clave produce hashes distintos porque cada uno lleva su propia sal")
    void mismaClaveDaHashesDistintos() {
        String uno = Seguridad.hashClave("Clave12345");
        String dos = Seguridad.hashClave("Clave12345");

        assertNotEquals(uno, dos);
        assertNotEquals(uno.split("\\$")[2], dos.split("\\$")[2], "Las sales deben diferir");
        assertTrue(Seguridad.verificarClave("Clave12345", uno));
        assertTrue(Seguridad.verificarClave("Clave12345", dos), "Ambos hashes validan la misma clave");
    }

    @Test
    @DisplayName("El hash tiene el formato pbkdf2_sha256$iteraciones$sal$hash y no contiene la clave")
    void formatoDelHash() {
        String hash = Seguridad.hashClave("MiClaveSecreta9");

        String[] partes = hash.split("\\$");
        assertEquals(4, partes.length, hash);
        assertEquals("pbkdf2_sha256", partes[0]);
        assertEquals("600000", partes[1]);
        assertFalse(partes[2].isEmpty());
        assertFalse(partes[3].isEmpty());
        assertFalse(hash.contains("MiClaveSecreta9"));
        assertTrue(Seguridad.esHash(hash));
    }

    @Test
    @DisplayName("verificarClave acepta la clave correcta y rechaza otra, también si solo cambian mayúsculas o espacios")
    void verificarCorrectoEIncorrecto() {
        String hash = Seguridad.hashClave("Clave12345");

        assertTrue(Seguridad.verificarClave("Clave12345", hash));
        assertFalse(Seguridad.verificarClave("clave12345", hash));
        assertFalse(Seguridad.verificarClave("Clave12345 ", hash));
        assertFalse(Seguridad.verificarClave("Clave1234", hash));
        assertFalse(Seguridad.verificarClave("", hash));
    }

    @Test
    @DisplayName("Funciona con la clave vacía y con caracteres no ASCII")
    void claveVaciaYUnicode() {
        String vacia = Seguridad.hashClave("");
        assertTrue(Seguridad.verificarClave("", vacia));
        assertFalse(Seguridad.verificarClave(" ", vacia));

        String unicode = Seguridad.hashClave("contraseña-ñandú-€-123");
        assertTrue(Seguridad.verificarClave("contraseña-ñandú-€-123", unicode));
        assertFalse(Seguridad.verificarClave("contrasena-nandu-€-123", unicode));
    }

    @Test
    @DisplayName("Valores nulos: hashClave(null) lanza IllegalArgumentException; verificarClave devuelve false")
    void valoresNulos() {
        assertThrows(IllegalArgumentException.class, () -> Seguridad.hashClave(null));

        String hash = Seguridad.hashClave("Clave12345");
        assertFalse(Seguridad.verificarClave(null, hash));
        assertFalse(Seguridad.verificarClave("Clave12345", null));
        assertFalse(Seguridad.verificarClave(null, null));
    }

    @Test
    @DisplayName("Un valor guardado con formato inválido nunca valida ni lanza excepciones")
    void formatoInvalidoNoValida() {
        String[] invalidos = {
            "",
            "   ",
            "Clave12345",                            // clave heredada en claro: no es un hash
            "pbkdf2_sha256",                         // sin separadores
            "pbkdf2_sha256$",                        // sin datos
            "pbkdf2_sha256$120000$AAAA",             // faltan partes
            "pbkdf2_sha256$120000$AAAA$BBBB$CCCC",   // sobran partes
            "pbkdf2_sha256$abc$AAAA$AAAA",           // iteraciones no numéricas
            "pbkdf2_sha256$0$AAAA$AAAA",             // iteraciones menores que 1
            "pbkdf2_sha256$-5$AAAA$AAAA",            // iteraciones negativas
            "pbkdf2_sha256$120000$%%%%$%%%%",        // Base64 inválido
            "pbkdf2_sha256$120000$$AAAA",            // sal vacía
            "pbkdf2_sha256$120000$AAAA$",            // hash vacío
            "sha256$120000$AAAA$AAAA"                // otro prefijo
        };
        for (String guardado : invalidos) {
            final String valor = guardado;
            assertFalse(Seguridad.verificarClave("Clave12345", valor), "Debía rechazar '" + valor + "'");
        }
    }

    @Test
    @DisplayName("Un hash alterado en la sal, el hash o las iteraciones deja de validar")
    void hashAlteradoNoValida() {
        String clave = "Clave12345";
        String[] partes = Seguridad.hashClave(clave).split("\\$");

        String hashCambiado = String.join("$", partes[0], partes[1], partes[2], cambiarPrimerCaracter(partes[3]));
        String salCambiada = String.join("$", partes[0], partes[1], cambiarPrimerCaracter(partes[2]), partes[3]);
        String otrasIteraciones = String.join("$", partes[0], "599999", partes[2], partes[3]);

        assertFalse(Seguridad.verificarClave(clave, hashCambiado));
        assertFalse(Seguridad.verificarClave(clave, salCambiada));
        assertFalse(Seguridad.verificarClave(clave, otrasIteraciones));
        assertTrue(Seguridad.verificarClave(clave, String.join("$", partes)), "El original sigue validando");
    }

    @Test
    @DisplayName("esHash reconoce solo el formato propio, sin distinguir null ni claves heredadas")
    void esHash() {
        assertTrue(Seguridad.esHash(Seguridad.hashClave("Clave12345")));
        assertTrue(Seguridad.esHash("pbkdf2_sha256$120000$AAAA$AAAA"), "Solo mira el prefijo");

        assertFalse(Seguridad.esHash(null));
        assertFalse(Seguridad.esHash(""));
        assertFalse(Seguridad.esHash("admin123"));
        assertFalse(Seguridad.esHash("pbkdf2_sha256"));
        assertFalse(Seguridad.esHash("PBKDF2_SHA256$120000$AAAA$AAAA"), "El prefijo distingue mayúsculas");
        assertFalse(Seguridad.esHash(" pbkdf2_sha256$120000$AAAA$AAAA"));
    }

    @Test
    @DisplayName("necesitaRecifrar: true para claves en claro, formatos ilegibles o con menos iteraciones; false para el hash actual")
    void necesitaRecifrar() {
        assertFalse(Seguridad.necesitaRecifrar(Seguridad.hashClave("Clave12345")), "El hash recién hecho está al día");

        assertTrue(Seguridad.necesitaRecifrar(null));
        assertTrue(Seguridad.necesitaRecifrar("admin123"), "Clave heredada en claro");
        assertTrue(Seguridad.necesitaRecifrar("pbkdf2_sha256$120000$AAAA$AAAA"), "Menos iteraciones que las actuales");
        assertTrue(Seguridad.necesitaRecifrar("pbkdf2_sha256$599999$AAAA$AAAA"));
        assertTrue(Seguridad.necesitaRecifrar("pbkdf2_sha256$abc$AAAA$AAAA"), "Iteraciones ilegibles");
        assertTrue(Seguridad.necesitaRecifrar("pbkdf2_sha256$600000$AAAA"), "Faltan partes");
        assertFalse(Seguridad.necesitaRecifrar("pbkdf2_sha256$600000$AAAA$AAAA"), "Mismas iteraciones que las actuales");
        assertFalse(Seguridad.necesitaRecifrar("pbkdf2_sha256$900000$AAAA$AAAA"), "Más iteraciones que las actuales");
    }

    /** Cambia el primer carácter Base64 por otro válido, de modo que el valor siga decodificando. */
    private static String cambiarPrimerCaracter(String base64) {
        char nuevo = base64.charAt(0) == 'A' ? 'B' : 'A';
        return nuevo + base64.substring(1);
    }
}
