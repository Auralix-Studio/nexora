package pe.edu.sistema.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Utilidades de formato para mostrar montos y fechas en las vistas.
 */
public final class Formato {

    private static final Locale ES_PE = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter FECHA_LARGA =
            DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", ES_PE);

    private Formato() {
    }

    private static DecimalFormat patron(String p) {
        return new DecimalFormat(p, DecimalFormatSymbols.getInstance(Locale.US));
    }

    /** 2500.0 -> "S/ 2,500.00" */
    public static String soles(double monto) {
        return "S/ " + patron("#,##0.00").format(monto);
    }

    /** 2500.0 -> "2500.00" (para cargar en formularios) */
    public static String decimal(double monto) {
        return patron("0.00").format(monto);
    }

    /** "Domingo, 11 de octubre de 2026" */
    public static String fechaLarga(LocalDate fecha) {
        String texto = fecha.format(FECHA_LARGA);
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
