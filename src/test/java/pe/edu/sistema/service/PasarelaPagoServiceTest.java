package pe.edu.sistema.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import pe.edu.sistema.model.Pago;
import pe.edu.sistema.model.SolicitudPago;

/**
 * Pruebas de la pasarela de pago simulada: algoritmo de Luhn, detección de marca, validación del
 * formulario y autorización según las tarjetas y el código Yape de prueba. No usan base de datos.
 */
class PasarelaPagoServiceTest {

    private static final String VISA = "4111111111111111";
    private static final String MASTERCARD = "5555555555554444";
    private static final String AMEX = "378282246310005";
    private static final String FONDOS_INSUFICIENTES = "4000000000000002";
    private static final String RECHAZO_BANCO = "4000000000000069";

    private static final DateTimeFormatter MM_AA = DateTimeFormatter.ofPattern("MM/yy");

    private final PasarelaPagoService pasarela = new PasarelaPagoService();

    // ---------------------------------------------------------------- auxiliares

    private static String vencimiento(YearMonth mes) {
        return mes.format(MM_AA);
    }

    /** Solicitud de tarjeta completa y válida: vencimiento a 2 años, CVV de 3 dígitos y boleta con DNI. */
    private static SolicitudPago tarjeta(String numero) {
        SolicitudPago s = new SolicitudPago();
        s.setMetodo(Pago.METODO_TARJETA);
        s.setNumeroTarjeta(numero);
        s.setTitular("Andrea Paredes");
        s.setVencimiento(vencimiento(YearMonth.now().plusYears(2)));
        s.setCvv("123");
        s.setTipoComprobante(Pago.BOLETA);
        s.setDocumento("70451236");
        return s;
    }

    /** Solicitud de Yape completa y válida, con boleta a DNI. */
    private static SolicitudPago yape(String codigo) {
        SolicitudPago s = new SolicitudPago();
        s.setMetodo(Pago.METODO_YAPE);
        s.setCelular("987654321");
        s.setCodigoYape(codigo);
        s.setTipoComprobante(Pago.BOLETA);
        s.setDocumento("70451236");
        return s;
    }

    private void assertError(Map<String, String> errores, String campo) {
        assertTrue(errores.containsKey(campo), "Se esperaba un error en '" + campo + "', pero hubo: " + errores);
    }

    // ---------------------------------------------------------------- Luhn

    @ParameterizedTest
    @ValueSource(strings = {VISA, MASTERCARD, AMEX, FONDOS_INSUFICIENTES, RECHAZO_BANCO, "4012888888881881"})
    @DisplayName("Luhn acepta números válidos")
    void luhnAceptaNumerosValidos(String numero) {
        assertTrue(PasarelaPagoService.luhnValido(numero), numero);
    }

    @ParameterizedTest
    @ValueSource(strings = {"4111111111111112", "1234567890123456", "5555555555554445", "378282246310006"})
    @DisplayName("Luhn rechaza números con el dígito verificador incorrecto")
    void luhnRechazaNumerosInvalidos(String numero) {
        assertFalse(PasarelaPagoService.luhnValido(numero), numero);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"4111 1111 1111 1111", "4111-1111-1111-1111", "4111a11111111111", "abcdefghijklmnop", " "})
    @DisplayName("Luhn rechaza vacío, nulo y cadenas con caracteres que no son dígitos")
    void luhnRechazaNoDigitos(String texto) {
        assertFalse(PasarelaPagoService.luhnValido(texto));
    }

    // ---------------------------------------------------------------- marca

    @ParameterizedTest
    @ValueSource(strings = {VISA, "4000000000000002", "4999999999999999", "4222"})
    @DisplayName("Detecta Visa (prefijo 4)")
    void detectaVisa(String numero) {
        assertEquals("Visa", PasarelaPagoService.detectarMarca(numero));
    }

    @ParameterizedTest
    @ValueSource(strings = {MASTERCARD, "5105105105105100", "51", "55",
        "2221000000000009", "2223003122003222", "2500000000000000", "2720999999999999"})
    @DisplayName("Detecta Mastercard (rangos 51-55 y 2221-2720)")
    void detectaMastercard(String numero) {
        assertEquals("Mastercard", PasarelaPagoService.detectarMarca(numero));
    }

    @ParameterizedTest
    @ValueSource(strings = {AMEX, "341111111111111", "34", "37"})
    @DisplayName("Detecta American Express (prefijos 34 y 37)")
    void detectaAmex(String numero) {
        assertEquals("American Express", PasarelaPagoService.detectarMarca(numero));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"4", "50", "56", "2220000000000000", "2721000000000000", "35", "36",
        "6011111111111117", "9999999999999999", "22ab", "xx"})
    @DisplayName("Marca desconocida para prefijos fuera de los rangos o cadenas no numéricas")
    void marcaDesconocida(String numero) {
        assertNull(PasarelaPagoService.detectarMarca(numero));
    }

    // ---------------------------------------------------------------- validar: tarjeta

    @Test
    @DisplayName("validar acepta una tarjeta completa y correcta")
    void validarAceptaTarjetaCorrecta() {
        assertTrue(pasarela.validar(tarjeta(VISA)).isEmpty());
        assertTrue(pasarela.validar(tarjeta(MASTERCARD)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"4111 1111 1111 1111", "4111-1111-1111-1111", "  4111111111111111  "})
    @DisplayName("validar ignora espacios y guiones del número de tarjeta")
    void validarIgnoraSeparadores(String numero) {
        assertTrue(pasarela.validar(tarjeta(numero)).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"4111", "4111111111111112", "41111111111111a1", "6011111111111117", "41111111111111111111"})
    @DisplayName("validar rechaza números de tarjeta vacíos, cortos, con Luhn inválido o de marca no reconocida")
    void validarRechazaNumeroMalo(String numero) {
        assertError(pasarela.validar(tarjeta(numero)), "numeroTarjeta");
    }

    @Test
    @DisplayName("validar rechaza una tarjeta vencida y acepta la del mes en curso")
    void validarVencimiento() {
        YearMonth actual = YearMonth.now();
        SolicitudPago vencida = tarjeta(VISA);
        vencida.setVencimiento(vencimiento(actual.minusMonths(1)));
        Map<String, String> errores = pasarela.validar(vencida);
        assertError(errores, "vencimiento");
        assertTrue(errores.get("vencimiento").contains("vencida"));

        SolicitudPago esteMes = tarjeta(VISA);
        esteMes.setVencimiento(vencimiento(actual));
        assertTrue(pasarela.validar(esteMes).isEmpty(), "Una tarjeta vigente hasta fin de este mes es válida");

        SolicitudPago limite = tarjeta(VISA);
        limite.setVencimiento(vencimiento(actual.plusYears(10)));
        assertTrue(pasarela.validar(limite).isEmpty(), "10 años es el máximo permitido");

        SolicitudPago lejana = tarjeta(VISA);
        lejana.setVencimiento(vencimiento(actual.plusYears(10).plusMonths(1)));
        assertError(pasarela.validar(lejana), "vencimiento");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"13/30", "00/30", "1/30", "01/2030", "2030-01", "ab/cd"})
    @DisplayName("validar rechaza vencimientos vacíos o con formato distinto de MM/AA")
    void validarVencimientoFormato(String texto) {
        SolicitudPago s = tarjeta(VISA);
        s.setVencimiento(texto);
        assertError(pasarela.validar(s), "vencimiento");
    }

    @Test
    @DisplayName("validar exige CVV de 3 dígitos y de 4 solo para American Express")
    void validarCvvSegunMarca() {
        SolicitudPago visaCon4 = tarjeta(VISA);
        visaCon4.setCvv("1234");
        assertError(pasarela.validar(visaCon4), "cvv");

        SolicitudPago amexCon3 = tarjeta(AMEX);
        amexCon3.setCvv("123");
        assertError(pasarela.validar(amexCon3), "cvv");

        SolicitudPago amexCon4 = tarjeta(AMEX);
        amexCon4.setCvv("1234");
        assertTrue(pasarela.validar(amexCon4).isEmpty());

        for (String malo : new String[]{"", "12", "12a", "abc"}) {
            SolicitudPago s = tarjeta(VISA);
            s.setCvv(malo);
            assertError(pasarela.validar(s), "cvv");
        }
        SolicitudPago sinCvv = tarjeta(VISA);
        sinCvv.setCvv(null);
        assertError(pasarela.validar(sinCvv), "cvv");
    }

    @Test
    @DisplayName("validar exige el titular: obligatorio, solo letras, espacios y signos de nombre, de 3 a 60 caracteres")
    void validarTitular() {
        for (String malo : new String[]{"", "   ", "Ab", "Juan123", "Ana_Perez", "A".repeat(61)}) {
            SolicitudPago s = tarjeta(VISA);
            s.setTitular(malo);
            assertError(pasarela.validar(s), "titular");
        }
        for (String bueno : new String[]{"José Ñandú Quispe", "Ana", "A".repeat(60)}) {
            SolicitudPago s = tarjeta(VISA);
            s.setTitular(bueno);
            assertTrue(pasarela.validar(s).isEmpty(), bueno);
        }
    }

    @Test
    @DisplayName("validar admite en el titular apóstrofo, punto y guion (O'Connor, Ana-María, J. Pérez)")
    void validarTitularConApostrofoPuntoYGuion() {
        for (String bueno : new String[]{"O'Connor", "Ana-María Pérez", "J. Pérez", "D'Angelo-Smith Jr."}) {
            SolicitudPago s = tarjeta(VISA);
            s.setTitular(bueno);
            assertTrue(pasarela.validar(s).isEmpty(), bueno);
        }
        for (String malo : new String[]{"Ana@Perez", "Juan; DROP", "Ana/Perez", "Ana_Perez", "A'", "A".repeat(61)}) {
            SolicitudPago s = tarjeta(VISA);
            s.setTitular(malo);
            assertError(pasarela.validar(s), "titular");
        }
    }

    // ---------------------------------------------------------------- validar: Yape

    @Test
    @DisplayName("validar acepta Yape con celular y código de 6 dígitos, sin pedir datos de tarjeta")
    void validarYapeCorrecto() {
        assertTrue(pasarela.validar(yape("123456")).isEmpty());
        assertTrue(pasarela.validar(yape("000000")).isEmpty(), "Cualquier código de 6 dígitos es válido en formato");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"887654321", "98765432", "9876543210", "98765432a", "012345678"})
    @DisplayName("validar rechaza celulares Yape vacíos, que no empiecen con 9 o que no tengan 9 dígitos")
    void validarYapeCelular(String celular) {
        SolicitudPago s = yape("123456");
        s.setCelular(celular);
        assertError(pasarela.validar(s), "celular");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"12345", "1234567", "abcdef", "12345a"})
    @DisplayName("validar rechaza códigos Yape vacíos o que no tengan 6 dígitos")
    void validarYapeCodigo(String codigo) {
        assertError(pasarela.validar(yape(codigo)), "codigoYape");
    }

    // ---------------------------------------------------------------- validar: comprobante

    @Test
    @DisplayName("validar boleta: exige un DNI de 8 dígitos")
    void validarBoletaDni() {
        for (String malo : new String[]{"", "1234567", "123456789", "7045123a"}) {
            SolicitudPago s = tarjeta(VISA);
            s.setDocumento(malo);
            assertError(pasarela.validar(s), "documento");
        }
        SolicitudPago conGuion = tarjeta(VISA);
        conGuion.setDocumento("7045 1236");
        assertTrue(pasarela.validar(conGuion).isEmpty());
    }

    @Test
    @DisplayName("validar factura: exige RUC de 11 dígitos que empiece con 10 o 20 y razón social")
    void validarFacturaRucYRazonSocial() {
        SolicitudPago ruc20 = tarjeta(VISA);
        ruc20.setTipoComprobante(Pago.FACTURA);
        ruc20.setDocumento("20123456789");
        ruc20.setRazonSocial("NEXORA S.A.C.");
        assertTrue(pasarela.validar(ruc20).isEmpty());

        SolicitudPago ruc10 = tarjeta(VISA);
        ruc10.setTipoComprobante(Pago.FACTURA);
        ruc10.setDocumento("10704512361");
        ruc10.setRazonSocial("Andrea Paredes");
        assertTrue(pasarela.validar(ruc10).isEmpty());

        for (String malo : new String[]{"", "30123456789", "2012345678", "201234567890", "2012345678a", "70451236"}) {
            SolicitudPago s = tarjeta(VISA);
            s.setTipoComprobante(Pago.FACTURA);
            s.setDocumento(malo);
            s.setRazonSocial("NEXORA S.A.C.");
            assertError(pasarela.validar(s), "documento");
        }

        SolicitudPago sinRazon = tarjeta(VISA);
        sinRazon.setTipoComprobante(Pago.FACTURA);
        sinRazon.setDocumento("20123456789");
        sinRazon.setRazonSocial("  ");
        assertError(pasarela.validar(sinRazon), "razonSocial");

        SolicitudPago razonLarga = tarjeta(VISA);
        razonLarga.setTipoComprobante(Pago.FACTURA);
        razonLarga.setDocumento("20123456789");
        razonLarga.setRazonSocial("X".repeat(151));
        assertError(pasarela.validar(razonLarga), "razonSocial");
    }

    @Test
    @DisplayName("validar exige elegir Boleta o Factura")
    void validarTipoComprobante() {
        for (String malo : new String[]{null, "", "Recibo", "boleta"}) {
            SolicitudPago s = tarjeta(VISA);
            s.setTipoComprobante(malo);
            assertError(pasarela.validar(s), "tipoComprobante");
        }
    }

    // ---------------------------------------------------------------- validar: método

    @Test
    @DisplayName("validar rechaza un método de pago vacío, nulo o desconocido")
    void validarMetodo() {
        for (String malo : new String[]{null, "", "  ", "PAYPAL", "tarjeta"}) {
            SolicitudPago s = tarjeta(VISA);
            s.setMetodo(malo);
            Map<String, String> errores = pasarela.validar(s);
            assertError(errores, "metodo");
            assertFalse(errores.containsKey("numeroTarjeta"), "Sin método no se valida la tarjeta");
        }
    }

    @Test
    @DisplayName("validar tolera una solicitud nula y la trata como formulario vacío")
    void validarSolicitudNula() {
        Map<String, String> errores = pasarela.validar(null);
        assertError(errores, "metodo");
        assertError(errores, "tipoComprobante");
    }

    // ---------------------------------------------------------------- autorizar

    @Test
    @DisplayName("autorizar aprueba una tarjeta de prueba y genera códigos de autorización y operación")
    void autorizarTarjetaAprobada() {
        Pago pago = pasarela.autorizar(tarjeta("4111 1111 1111 1111"), 250.50);

        assertTrue(pago.isAprobado());
        assertEquals(Pago.APROBADO, pago.getEstado());
        assertNull(pago.getMotivoRechazo());
        assertEquals(Pago.METODO_TARJETA, pago.getMetodo());
        assertEquals("Visa", pago.getMarca());
        assertEquals("1111", pago.getUltimosDigitos());
        assertEquals("Andrea Paredes", pago.getTitular());
        assertEquals(250.50, pago.getMonto(), 0.0001);
        assertEquals(Pago.BOLETA, pago.getTipoComprobante());
        assertEquals("70451236", pago.getDocumentoCliente());
        assertNull(pago.getRazonSocial(), "La boleta no lleva razón social");
        assertNotNull(pago.getFecha());
        assertTrue(pago.getCodigoAutorizacion().matches("\\d{6}"), pago.getCodigoAutorizacion());
        assertTrue(pago.getCodigoOperacion().matches("OP-\\d{14}-\\d{4}"), pago.getCodigoOperacion());
        assertEquals("Visa •••• 1111", pago.getMetodoTexto());
    }

    @Test
    @DisplayName("autorizar detecta Mastercard y American Express")
    void autorizarOtrasMarcas() {
        assertEquals("Mastercard", pasarela.autorizar(tarjeta(MASTERCARD), 10).getMarca());
        SolicitudPago amex = tarjeta(AMEX);
        amex.setCvv("1234");
        Pago pago = pasarela.autorizar(amex, 10);
        assertTrue(pago.isAprobado());
        assertEquals("American Express", pago.getMarca());
        assertEquals("0005", pago.getUltimosDigitos());
    }

    @Test
    @DisplayName("autorizar rechaza 4000 0000 0000 0002 por fondos insuficientes")
    void autorizarFondosInsuficientes() {
        for (String numero : new String[]{FONDOS_INSUFICIENTES, "4000 0000 0000 0002"}) {
            Pago pago = pasarela.autorizar(tarjeta(numero), 100);
            assertFalse(pago.isAprobado());
            assertEquals(Pago.RECHAZADO, pago.getEstado());
            assertEquals("Fondos insuficientes", pago.getMotivoRechazo());
            assertNull(pago.getCodigoAutorizacion());
            assertNull(pago.getCodigoOperacion());
            assertEquals("0002", pago.getUltimosDigitos());
        }
    }

    @Test
    @DisplayName("autorizar rechaza 4000 0000 0000 0069 por el banco emisor")
    void autorizarRechazoBancoEmisor() {
        Pago pago = pasarela.autorizar(tarjeta(RECHAZO_BANCO), 100);
        assertFalse(pago.isAprobado());
        assertEquals(Pago.RECHAZADO, pago.getEstado());
        assertTrue(pago.getMotivoRechazo().contains("banco emisor"), pago.getMotivoRechazo());
        assertNull(pago.getCodigoAutorizacion());
    }

    @Test
    @DisplayName("autorizar aprueba Yape con el código de prueba y rechaza cualquier otro")
    void autorizarYape() {
        Pago aprobado = pasarela.autorizar(yape(PasarelaPagoService.CODIGO_YAPE_PRUEBA), 80);
        assertEquals("123456", PasarelaPagoService.CODIGO_YAPE_PRUEBA);
        assertTrue(aprobado.isAprobado());
        assertEquals(Pago.METODO_YAPE, aprobado.getMetodo());
        assertEquals("Yape", aprobado.getMarca());
        assertEquals("4321", aprobado.getUltimosDigitos());
        assertNull(aprobado.getTitular());
        assertTrue(aprobado.getCodigoAutorizacion().matches("\\d{6}"));
        assertTrue(aprobado.getCodigoOperacion().matches("OP-\\d{14}-\\d{4}"));
        assertEquals("Yape •••• 4321", aprobado.getMetodoTexto());

        for (String otro : new String[]{"654321", "000000", "123457"}) {
            Pago rechazado = pasarela.autorizar(yape(otro), 80);
            assertFalse(rechazado.isAprobado(), otro);
            assertEquals(Pago.RECHAZADO, rechazado.getEstado());
            assertTrue(rechazado.getMotivoRechazo().contains("Yape"), rechazado.getMotivoRechazo());
            assertNull(rechazado.getCodigoAutorizacion());
        }
    }

    @Test
    @DisplayName("autorizar conserva los datos de factura y los omite en la boleta")
    void autorizarFactura() {
        SolicitudPago s = tarjeta(VISA);
        s.setTipoComprobante(Pago.FACTURA);
        s.setDocumento("20123456789");
        s.setRazonSocial("NEXORA S.A.C.");
        Pago pago = pasarela.autorizar(s, 500);
        assertTrue(pago.isFactura());
        assertEquals("20123456789", pago.getDocumentoCliente());
        assertEquals("NEXORA S.A.C.", pago.getRazonSocial());

        SolicitudPago boleta = tarjeta(VISA);
        boleta.setRazonSocial("Se ignora en boleta");
        assertNull(pasarela.autorizar(boleta, 500).getRazonSocial());
    }

    @Test
    @DisplayName("autorizar rechaza un método no admitido y tolera una solicitud nula")
    void autorizarMetodoNoAdmitido() {
        SolicitudPago s = tarjeta(VISA);
        s.setMetodo("PAYPAL");
        assertEquals(Pago.RECHAZADO, pasarela.autorizar(s, 10).getEstado());
        assertEquals(Pago.RECHAZADO, pasarela.autorizar(null, 10).getEstado());
    }

    @Test
    @DisplayName("el Pago no guarda ni expone el número completo de la tarjeta, el CVV ni el celular completo")
    void pagoNuncaContieneDatosSensibles() throws IllegalAccessException {
        // El modelo no tiene campos para esos datos
        for (Field campo : Pago.class.getDeclaredFields()) {
            String nombre = campo.getName().toLowerCase();
            assertFalse(nombre.contains("cvv") || nombre.contains("numerotarjeta") || nombre.contains("celular"),
                    "Pago no debe tener el campo " + campo.getName());
        }

        // Ningún texto del pago, aprobado o rechazado, contiene el número completo
        String[] numeros = {VISA, MASTERCARD, FONDOS_INSUFICIENTES, RECHAZO_BANCO};
        for (String numero : numeros) {
            SolicitudPago s = tarjeta(numero);
            s.setCvv("987");
            verificarSinDatosSensibles(pasarela.autorizar(s, 100), numero);
        }
        verificarSinDatosSensibles(pasarela.autorizar(yape("123456"), 100), "987654321");
        verificarSinDatosSensibles(pasarela.autorizar(yape("999999"), 100), "987654321");
    }

    private static void verificarSinDatosSensibles(Pago pago, String secreto) throws IllegalAccessException {
        for (Field campo : Pago.class.getDeclaredFields()) {
            if (campo.getType() == String.class) {
                campo.setAccessible(true);
                Object valor = campo.get(pago);
                assertTrue(valor == null || !valor.toString().contains(secreto),
                        "El campo " + campo.getName() + " contiene el dato completo " + secreto);
            }
        }
        assertFalse(pago.getMetodoTexto().contains(secreto));
        assertTrue(pago.getUltimosDigitos().length() <= 4);
    }
}
