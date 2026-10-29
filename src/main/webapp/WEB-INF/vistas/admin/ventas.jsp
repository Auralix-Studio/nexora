<%--
  Vista: reporte de ventas por período (personal).
  Recibe: porDia, porCategoria, porMetodo, porProducto (List<FilaReporte>), totalVentasTexto, numeroVentas,
          ticketPromedioTexto, fDesde, fHasta (yyyy-MM-dd)
--%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:set var="titulo" value="Ventas" scope="request"/>
<c:set var="ruta" value="Administración / Ventas" scope="request"/>
<c:set var="menu" value="admin-ventas" scope="request"/>
<jsp:include page="/includes/header.jsp"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:url var="urlCsv" value="/admin">
    <c:param name="accion" value="ventas"/>
    <c:param name="formato" value="csv"/>
    <c:param name="desde" value="${fDesde}"/>
    <c:param name="hasta" value="${fHasta}"/>
</c:url>

<form class="tarjeta filtros" method="get" action="${ctx}/admin">
    <input type="hidden" name="accion" value="ventas">
    <div class="tarjeta__encabezado">
        <h2 class="tarjeta__titulo">Período del reporte</h2>
        <p class="nota">Solo cuentan las ventas con pago aprobado que no fueron anuladas.</p>
    </div>
    <div class="fila-busqueda">
        <div class="campo campo--fecha">
            <label for="v-desde">Desde</label>
            <input id="v-desde" type="date" name="desde" value="${fn:escapeXml(fDesde)}">
        </div>
        <div class="campo campo--fecha">
            <label for="v-hasta">Hasta</label>
            <input id="v-hasta" type="date" name="hasta" value="${fn:escapeXml(fHasta)}">
        </div>
        <div class="filtros__acciones">
            <button type="submit" class="boton boton--primario"><ui:icono nombre="filtro" tam="18"/> Aplicar</button>
            <a class="boton boton--secundario" href="${urlCsv}"><ui:icono nombre="descargar" tam="18"/> Exportar CSV</a>
        </div>
    </div>
</form>

<section class="indicadores indicadores--tres" aria-label="Totales del período">
    <div class="indicador" data-revelar>
        <div class="indicador__cabecera"><span class="etiqueta">Total de ventas</span><span class="chip"><ui:icono nombre="monedas" tam="18"/></span></div>
        <strong class="indicador__valor"><c:out value="${totalVentasTexto}"/></strong>
        <span class="nota">ingresos del período</span>
    </div>
    <div class="indicador" data-revelar>
        <div class="indicador__cabecera"><span class="etiqueta">Número de ventas</span><span class="chip chip--ok"><ui:icono nombre="comprobante" tam="18"/></span></div>
        <strong class="indicador__valor"><c:out value="${numeroVentas}"/></strong>
        <span class="nota">comprobantes emitidos</span>
    </div>
    <div class="indicador" data-revelar>
        <div class="indicador__cabecera"><span class="etiqueta">Ticket promedio</span><span class="chip chip--aviso"><ui:icono nombre="grafico" tam="18"/></span></div>
        <strong class="indicador__valor"><c:out value="${ticketPromedioTexto}"/></strong>
        <span class="nota">por venta</span>
    </div>
</section>

<section class="rejilla-bloques">
    <div class="tarjeta" data-revelar>
        <div class="tarjeta__encabezado">
            <h2 class="tarjeta__titulo">Ventas por día</h2>
            <p class="nota">Monto vendido cada día y número de pedidos.</p>
        </div>
        <ui:barrasReporte filas="${porDia}" unidad="pedidos"/>
    </div>
    <div class="tarjeta" data-revelar>
        <div class="tarjeta__encabezado">
            <h2 class="tarjeta__titulo">Ventas por categoría</h2>
            <p class="nota">Monto y unidades vendidas por categoría.</p>
        </div>
        <ui:barrasReporte filas="${porCategoria}" unidad="uds."/>
    </div>
    <div class="tarjeta" data-revelar>
        <div class="tarjeta__encabezado">
            <h2 class="tarjeta__titulo">Ventas por método de pago</h2>
            <p class="nota">Monto y número de pedidos según cómo pagó el cliente.</p>
        </div>
        <ui:barrasReporte filas="${porMetodo}" unidad="pedidos"/>
    </div>
    <div class="tarjeta" data-revelar>
        <div class="tarjeta__encabezado">
            <h2 class="tarjeta__titulo">Productos más vendidos</h2>
            <p class="nota">Los 10 con mayor monto vendido y sus unidades.</p>
        </div>
        <ui:barrasReporte filas="${porProducto}" unidad="uds."/>
    </div>
</section>

<jsp:include page="/includes/footer.jsp"/>
