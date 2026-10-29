<%-- Vista: resumen de productos --%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:set var="titulo" value="Resumen de productos" scope="request"/>
<c:set var="ruta" value="Reportes / Resumen" scope="request"/>
<c:set var="menu" value="resumen" scope="request"/>
<jsp:include page="/includes/header.jsp"/>

<section class="indicadores">
    <div class="indicador" data-revelar>
        <div class="indicador__cabecera"><span class="etiqueta">Total de productos</span><span class="chip"><ui:icono nombre="caja" tam="18"/></span></div>
        <strong class="indicador__valor">${total}</strong>
        <span class="nota">Productos registrados</span>
    </div>
    <div class="indicador" data-revelar>
        <div class="indicador__cabecera"><span class="etiqueta">Productos disponibles</span><span class="chip chip--ok"><ui:icono nombre="check" tam="18"/></span></div>
        <strong class="indicador__valor">${disponibles}</strong>
        <span class="nota">${pctDisponibles}% del total</span>
    </div>
    <div class="indicador" data-revelar>
        <div class="indicador__cabecera"><span class="etiqueta">Productos agotados</span><span class="chip chip--error"><ui:icono nombre="alerta" tam="18"/></span></div>
        <strong class="indicador__valor">${agotados}</strong>
        <span class="nota">${pctAgotados}% del total</span>
    </div>
    <div class="indicador" data-revelar>
        <div class="indicador__cabecera"><span class="etiqueta">Stock total</span><span class="chip chip--aviso"><ui:icono nombre="capas" tam="18"/></span></div>
        <strong class="indicador__valor">${stockTotal}</strong>
        <span class="nota">unidades en inventario</span>
    </div>
</section>

<section class="dos-columnas">
    <div class="tarjeta tarjeta--amplia" data-revelar>
        <div class="tarjeta__encabezado">
            <h2 class="tarjeta__titulo">Valor por categoría</h2>
            <p class="nota">Precio × stock de cada producto, agrupado por categoría.</p>
        </div>
        <div>
            <strong class="cifra cifra--grande"><c:out value="${valorTotal}"/></strong>
            <p class="nota">Valor total del inventario</p>
        </div>
        <ul class="barras">
            <c:forEach var="cat" items="${porCategoria}" varStatus="st">
                <li>
                    <span class="barras__nombre"><c:out value="${cat.categoria}"/></span>
                    <span class="barras__pista"><span class="barras__valor ancho-pct-${fn:substringBefore(cat.anchoBarra, '.')} ${st.first ? 'barras__valor--principal' : ''}"></span></span>
                    <span class="barras__monto mono"><c:out value="${cat.valorTexto}"/> <span class="suave"><c:out value="${cat.porcentajeTexto}"/></span></span>
                </li>
            </c:forEach>
        </ul>
    </div>

    <div class="tarjeta ticket columna-lateral" data-revelar>
        <p class="etiqueta">Resumen del sistema</p>
        <hr class="divisor divisor--punteado">
        <dl class="ticket__datos">
            <div><dt>Total de productos</dt><dd>${total}</dd></div>
            <div><dt>Productos disponibles</dt><dd class="texto-ok">${disponibles}</dd></div>
            <div><dt>Productos agotados</dt><dd class="texto-error">${agotados}</dd></div>
        </dl>
        <hr class="divisor divisor--punteado">
        <div class="ticket__grande"><span>Valor total del inventario</span><strong><c:out value="${valorTotal}"/></strong></div>
        <div class="ticket__grande"><span>Stock total</span><strong>${stockTotal} unidades</strong></div>
        <hr class="divisor divisor--punteado">
        <span class="mono pequeno suave">Distribución por estado</span>
        <div class="barra-estado" role="img" aria-label="Disponibles ${pctDisponibles}%, agotados ${pctAgotados}%">
            <span class="barra-estado__ok ancho-pct-${pctDisponibles}"></span>
            <span class="barra-estado__error ancho-pct-${pctAgotados}"></span>
        </div>
        <div class="leyenda">
            <span><i class="punto punto--ok"></i>Disponibles ${pctDisponibles}%</span>
            <span><i class="punto punto--error"></i>Agotados ${pctAgotados}%</span>
        </div>
        <p class="ticket__generado mono">Generado: <c:out value="${generado}"/></p>
    </div>
</section>

<jsp:include page="/includes/footer.jsp"/>
