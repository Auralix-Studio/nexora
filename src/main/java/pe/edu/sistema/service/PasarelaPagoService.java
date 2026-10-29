package pe.edu.sistema.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import pe.edu.sistema.model.Pago;
import pe.edu.sistema.model.SolicitudPago;

/**
 * Pasarela de pago simulada de NEXORA. No contacta a ningún banco: valida los datos como lo
 * haría una pasarela real y decide la autorización según tarjetas y códigos de prueba.
 */
public class PasarelaPagoService {

    /** Tarjetas de prueba: {número, marca, resultado esperado}. Se muestran en el checkout. */
    public static final List<String[]> TARJETAS_PRUEBA = List.of(
            new String[]{"4111 1111 1111 1111", "Visa", "Aprobada"},
            new String[]{"5555 5555 5555 4444", "Mastercard", "Aprobada"},
            new String[]{"4000 0000 0000 0002", "Visa", "Rechazada: fondos insuficientes"},
            new String[]{"4000 0000 0000 0069", "Visa", "Rechazada por el banco emisor"});

    /** Código Yape que la pasarela simulada aprueba. Cualquier otro se rechaza. */
    public static final String CODIGO_YAPE_PRUEBA = "123456";

    private static final String TARJETA_FONDOS_INSUFICIENTES = "4000000000000002";
    private static final String TARJETA_RECHAZO_BANCO = "4000000000000069";

    private static final Pattern TITULAR = Pattern.compile("[\\p{L}\\p{M} '.\\-]{3,60}");
    private static final Pattern VENCIMIENTO = Pattern.compile("(0[1-9]|1[0-2])/(\\d{2})");
    private static final Pattern CELULAR = Pattern.compile("9\\d{8}");
    private static final Pattern CODIGO_YAPE = Pattern.compile("\\d{6}");
    private static final Pattern DNI = Pattern.compile("\\d{8}");
    private static final Pattern RUC = Pattern.compile("(10|20)\\d{9}");

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm");
    private static final DateTimeFormatter FORMATO_OPERACION = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final SecureRandom ALEATORIO = new SecureRandom();

    /**
     * Valida el formulario sin autorizar nada.
     *
     * @return campo -> mensaje (claves: metodo, numeroTarjeta, titular, vencimiento, cvv,
     *         celular, codigoYape, tipoComprobante, documento, razonSocial); vacío si es válido
     */
    public Map<String, String> validar(SolicitudPago solicitud) {
        Map<String, String> errores = new LinkedHashMap<>();
        SolicitudPago s = solicitud != null ? solicitud : new SolicitudPago();
        String metodo = texto(s.getMetodo());

        if (Pago.METODO_TARJETA.equals(metodo)) {
            validarTarjeta(s, errores);
        } else if (Pago.METODO_YAPE.equals(metodo)) {
            validarYape(s, errores);
        } else {
            errores.put("metodo", "Elige un método de pago: tarjeta o Yape");
        }
        validarComprobante(s, errores);
        return errores;
    }

    /**
     * Simula la autorización de una solicitud ya validada.
     *
     * @return pago con estado Aprobado (con código de autorización y operación) o Rechazado
     *         (con motivo); con marca y últimos 4 dígitos, nunca el número completo ni el CVV
     */
    public Pago autorizar(SolicitudPago solicitud, double monto) {
        SolicitudPago s = solicitud != null ? solicitud : new SolicitudPago();
        LocalDateTime ahora = LocalDateTime.now();
        String metodo = texto(s.getMetodo());

        Pago pago = new Pago();
        pago.setMetodo(metodo);
        pago.setMonto(monto);
        pago.setFecha(ahora.format(FORMATO_FECHA));
        pago.setTipoComprobante(texto(s.getTipoComprobante()));
        pago.setDocumentoCliente(soloDigitos(s.getDocumento()));
        pago.setRazonSocial(Pago.FACTURA.equals(pago.getTipoComprobante()) ? texto(s.getRazonSocial()) : null);

        String motivoRechazo;
        if (Pago.METODO_TARJETA.equals(metodo)) {
            String numero = soloDigitos(s.getNumeroTarjeta());
            pago.setMarca(detectarMarca(numero));
            pago.setUltimosDigitos(ultimos4(numero));
            pago.setTitular(texto(s.getTitular()));
            if (numero.equals(TARJETA_FONDOS_INSUFICIENTES)) {
                motivoRechazo = "Fondos insuficientes";
            } else if (numero.equals(TARJETA_RECHAZO_BANCO)) {
                motivoRechazo = "Transacción rechazada por el banco emisor";
            } else {
                motivoRechazo = null;
            }
        } else if (Pago.METODO_YAPE.equals(metodo)) {
            pago.setMarca("Yape");
            pago.setUltimosDigitos(ultimos4(soloDigitos(s.getCelular())));
            pago.setTitular(null);
            motivoRechazo = CODIGO_YAPE_PRUEBA.equals(texto(s.getCodigoYape()))
                    ? null
                    : "Código de aprobación Yape inválido o vencido";
        } else {
            motivoRechazo = "Método de pago no admitido";
        }

        if (motivoRechazo != null) {
            pago.setEstado(Pago.RECHAZADO);
            pago.setMotivoRechazo(motivoRechazo);
            return pago;
        }
        pago.setEstado(Pago.APROBADO);
        pago.setCodigoAutorizacion(String.format("%06d", ALEATORIO.nextInt(1_000_000)));
        pago.setCodigoOperacion("OP-" + ahora.format(FORMATO_OPERACION)
                + "-" + String.format("%04d", ALEATORIO.nextInt(10_000)));
        return pago;
    }

    /** Algoritmo de Luhn sobre una cadena de solo dígitos. */
    public static boolean luhnValido(String digitos) {
        if (digitos == null || digitos.isEmpty()) {
            return false;
        }
        int suma = 0;
        boolean duplicar = false;
        for (int i = digitos.length() - 1; i >= 0; i--) {
            char c = digitos.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            int d = c - '0';
            if (duplicar) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            suma += d;
            duplicar = !duplicar;
        }
        return suma % 10 == 0;
    }

    /** Visa (4), Mastercard (51-55, 2221-2720), American Express (34, 37); null si no se reconoce. */
    public static String detectarMarca(String digitos) {
        if (digitos == null || digitos.length() < 2) {
            return null;
        }
        if (digitos.charAt(0) == '4') {
            return "Visa";
        }
        String prefijo2 = digitos.substring(0, 2);
        if (prefijo2.equals("34") || prefijo2.equals("37")) {
            return "American Express";
        }
        if (prefijo2.compareTo("51") >= 0 && prefijo2.compareTo("55") <= 0) {
            return "Mastercard";
        }
        if (digitos.length() >= 4) {
            try {
                int prefijo4 = Integer.parseInt(digitos.substring(0, 4));
                if (prefijo4 >= 2221 && prefijo4 <= 2720) {
                    return "Mastercard";
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- validaciones

    private void validarTarjeta(SolicitudPago s, Map<String, String> errores) {
        String bruto = texto(s.getNumeroTarjeta()).replaceAll("[\\s-]", "");
        String marca = null;
        if (bruto.isEmpty()) {
            errores.put("numeroTarjeta", "Ingresa el número de tarjeta");
        } else if (!bruto.matches("\\d{13,19}")) {
            errores.put("numeroTarjeta", "El número de tarjeta debe tener entre 13 y 19 dígitos");
        } else {
            marca = detectarMarca(bruto);
            if (marca == null) {
                errores.put("numeroTarjeta", "Tarjeta no reconocida: usa Visa, Mastercard o American Express");
            } else if (!luhnValido(bruto)) {
                errores.put("numeroTarjeta", "Número de tarjeta inválido");
            }
        }

        String titular = texto(s.getTitular());
        if (titular.isEmpty()) {
            errores.put("titular", "Ingresa el nombre del titular tal como figura en la tarjeta");
        } else if (!TITULAR.matcher(titular).matches()) {
            errores.put("titular", "El titular debe tener entre 3 y 60 caracteres: letras, espacios, apóstrofo, punto o guion");
        }

        String vencimiento = texto(s.getVencimiento());
        if (vencimiento.isEmpty()) {
            errores.put("vencimiento", "Ingresa la fecha de vencimiento (MM/AA)");
        } else {
            var m = VENCIMIENTO.matcher(vencimiento);
            if (!m.matches()) {
                errores.put("vencimiento", "Vencimiento inválido: usa el formato MM/AA");
            } else {
                YearMonth vence = YearMonth.of(2000 + Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
                YearMonth actual = YearMonth.now();
                if (vence.isBefore(actual)) {
                    errores.put("vencimiento", "La tarjeta está vencida");
                } else if (vence.isAfter(actual.plusYears(10))) {
                    errores.put("vencimiento", "El vencimiento no puede superar los 10 años");
                }
            }
        }

        int largoCvv = "American Express".equals(marca) ? 4 : 3;
        String cvv = texto(s.getCvv());
        if (cvv.isEmpty()) {
            errores.put("cvv", "Ingresa el código de seguridad (CVV)");
        } else if (!cvv.matches("\\d{" + largoCvv + "}")) {
            errores.put("cvv", "El CVV debe tener " + largoCvv + " dígitos");
        }
    }

    private void validarYape(SolicitudPago s, Map<String, String> errores) {
        String celular = texto(s.getCelular()).replaceAll("[\\s-]", "");
        if (celular.isEmpty()) {
            errores.put("celular", "Ingresa el celular asociado a tu Yape");
        } else if (!CELULAR.matcher(celular).matches()) {
            errores.put("celular", "El celular debe tener 9 dígitos y empezar con 9");
        }

        String codigo = texto(s.getCodigoYape());
        if (codigo.isEmpty()) {
            errores.put("codigoYape", "Ingresa el código de aprobación de Yape");
        } else if (!CODIGO_YAPE.matcher(codigo).matches()) {
            errores.put("codigoYape", "El código de aprobación debe tener 6 dígitos");
        }
    }

    private void validarComprobante(SolicitudPago s, Map<String, String> errores) {
        String tipo = texto(s.getTipoComprobante());
        String documento = texto(s.getDocumento()).replaceAll("[\\s-]", "");

        if (Pago.BOLETA.equals(tipo)) {
            if (documento.isEmpty()) {
                errores.put("documento", "Ingresa tu DNI");
            } else if (!DNI.matcher(documento).matches()) {
                errores.put("documento", "El DNI debe tener 8 dígitos");
            }
        } else if (Pago.FACTURA.equals(tipo)) {
            if (documento.isEmpty()) {
                errores.put("documento", "Ingresa el RUC");
            } else if (!RUC.matcher(documento).matches()) {
                errores.put("documento", "El RUC debe tener 11 dígitos y empezar con 10 o 20");
            }
            String razon = texto(s.getRazonSocial());
            if (razon.isEmpty()) {
                errores.put("razonSocial", "Ingresa la razón social de la empresa");
            } else if (razon.length() > 150) {
                errores.put("razonSocial", "La razón social admite como máximo 150 caracteres");
            }
        } else {
            errores.put("tipoComprobante", "Elige el tipo de comprobante: Boleta o Factura");
        }
    }

    // ---------------------------------------------------------------- auxiliares

    private static String texto(String s) {
        return s == null ? "" : s.trim();
    }

    private static String soloDigitos(String s) {
        return texto(s).replaceAll("[^0-9]", "");
    }

    private static String ultimos4(String digitos) {
        return digitos.length() <= 4 ? digitos : digitos.substring(digitos.length() - 4);
    }
}
