package pe.edu.sistema.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Cifrado de contraseñas con PBKDF2 (HMAC-SHA256) y sal aleatoria por usuario.
 * Formato almacenado: {@code pbkdf2_sha256$iteraciones$sal$hash} (sal y hash en Base64).
 */
public final class Seguridad {

    private static final String PREFIJO = "pbkdf2_sha256";
    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final int ITERACIONES = 600_000;
    private static final int BYTES_SAL = 16;
    private static final int BITS_HASH = 256;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private Seguridad() {
    }

    /** Devuelve el hash de la contraseña listo para guardar; nunca la contraseña en claro. */
    public static String hashClave(String clave) {
        if (clave == null) {
            throw new IllegalArgumentException("La contraseña no puede ser nula");
        }
        byte[] sal = new byte[BYTES_SAL];
        ALEATORIO.nextBytes(sal);
        byte[] hash = derivar(clave, sal, ITERACIONES);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIJO + "$" + ITERACIONES + "$" + b64.encodeToString(sal) + "$" + b64.encodeToString(hash);
    }

    /** Compara en tiempo constante la contraseña con un hash guardado por {@link #hashClave}. */
    public static boolean verificarClave(String clave, String almacenado) {
        if (clave == null || !esHash(almacenado)) {
            return false;
        }
        String[] partes = almacenado.split("\\$");
        if (partes.length != 4) {
            return false;
        }
        try {
            int iteraciones = Integer.parseInt(partes[1]);
            byte[] sal = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            if (iteraciones < 1 || sal.length == 0 || esperado.length == 0) {
                return false;
            }
            return MessageDigest.isEqual(esperado, derivar(clave, sal, iteraciones));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * true si lo guardado conviene cifrar de nuevo tras un ingreso correcto: una clave heredada en claro,
     * un hash que no se puede leer o uno hecho con menos iteraciones que las actuales.
     */
    public static boolean necesitaRecifrar(String almacenado) {
        if (!esHash(almacenado)) {
            return true;
        }
        String[] partes = almacenado.split("\\$");
        if (partes.length != 4) {
            return true;
        }
        try {
            return Integer.parseInt(partes[1]) < ITERACIONES;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /** true si el valor tiene el formato de hash de esta clase (y no es una contraseña heredada en claro). */
    public static boolean esHash(String valor) {
        return valor != null && valor.startsWith(PREFIJO + "$");
    }

    /** Máximo de bytes que se leen de la entrada estándar (una clave nunca se acerca a este tamaño). */
    private static final int MAX_BYTES_ENTRADA = 4096;

    /**
     * Herramienta de línea de comandos: imprime solo el hash de la clave (y nada más en la salida estándar).
     * La usa el script de inicialización de la base para guardar las claves privadas ya cifradas.
     *
     * <p>Sin argumentos lee la clave de la entrada estándar (primera línea, sin retorno de carro final), de modo
     * que no aparezca en la lista de procesos; con un argumento usa {@code args[0]}. Sin clave, termina con
     * código 2 y un mensaje en la salida de error.</p>
     */
    public static void main(String[] args) {
        int codigo = ejecutar(args, System.in, System.out, System.err);
        if (codigo != 0) {
            System.exit(codigo);
        }
    }

    /** Lógica de {@link #main} sin terminar la JVM. @return 0 si imprimió el hash; 2 si no hay clave que cifrar */
    static int ejecutar(String[] args, InputStream entrada, PrintStream salida, PrintStream error) {
        String clave;
        if (args != null && args.length >= 1) {
            clave = args[0];
        } else {
            clave = leerLinea(entrada);
        }
        if (clave == null || clave.isEmpty()) {
            error.println("Uso: printf '%s' \"$CLAVE\" | java -cp ... pe.edu.sistema.util.Seguridad   (o con la clave como argumento)");
            return 2;
        }
        // Salto de línea fijo (no el del sistema): el script de inicialización lee la línea sin retorno de carro
        salida.print(hashClave(clave) + "\n");
        salida.flush();
        return 0;
    }

    /** Primera línea de la entrada (UTF-8), sin salto de línea ni retorno de carro final; null si no se pudo leer. */
    private static String leerLinea(InputStream entrada) {
        try {
            String texto = new String(entrada.readNBytes(MAX_BYTES_ENTRADA), StandardCharsets.UTF_8);
            int salto = texto.indexOf('\n');
            if (salto >= 0) {
                texto = texto.substring(0, salto);
            }
            return texto.endsWith("\r") ? texto.substring(0, texto.length() - 1) : texto;
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] derivar(String clave, byte[] sal, int iteraciones) {
        PBEKeySpec spec = new PBEKeySpec(clave.toCharArray(), sal, iteraciones, BITS_HASH);
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 no está disponible en esta JVM", e);
        } finally {
            spec.clearPassword();
        }
    }
}
