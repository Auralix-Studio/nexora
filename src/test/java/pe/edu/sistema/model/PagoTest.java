package pe.edu.sistema.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del desglose del IGV incluido (18 %) de {@link Pago}: un único cálculo compartido por
 * el modelo, el checkout y el servicio.
 */
class PagoTest {

    @Test
    @DisplayName("Un total de S/ 70.00 se desglosa en S/ 59.32 de subtotal y S/ 10.68 de IGV")
    void desgloseDeSetentaSoles() {
        assertEquals(59.32, Pago.subtotalDe(70.00), 0.0001);
        assertEquals(10.68, Pago.igvDe(70.00), 0.0001);
    }

    @Test
    @DisplayName("getSubtotal y getIgvMonto usan los mismos métodos estáticos")
    void instanciaYEstaticosCoinciden() {
        for (double total : new double[]{0.01, 35.00, 70.00, 165.00, 799.00, 2890.00, 14450.00}) {
            Pago pago = new Pago();
            pago.setMonto(total);
            assertEquals(Pago.subtotalDe(total), pago.getSubtotal(), 0.0);
            assertEquals(Pago.igvDe(total), pago.getIgvMonto(), 0.0);
        }
    }

    @Test
    @DisplayName("Subtotal + IGV suma el total (a céntimos) y ambos importes vienen redondeados")
    void subtotalMasIgvEsElTotal() {
        for (int centimos = 1; centimos <= 100_000; centimos += 37) {
            double total = centimos / 100.0;
            assertEquals(total, Pago.subtotalDe(total) + Pago.igvDe(total), 0.0051, "total " + total);
            assertEquals(Pago.redondear(Pago.igvDe(total)), Pago.igvDe(total), 0.0, "IGV a céntimos");
        }
    }

    @Test
    @DisplayName("redondear lleva los importes a céntimos")
    void redondearACentimos() {
        assertEquals(12.35, Pago.redondear(12.346), 0.0001);
        assertEquals(0.0, Pago.redondear(0.004), 0.0);
        assertEquals(2500.00, Pago.redondear(2500.0 * 1), 0.0);
    }
}
