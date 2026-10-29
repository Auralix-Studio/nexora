<%--
  Vista: panel de administración (personal).
  Recibe: resumen (ResumenVentas), umbralStock (int)
--%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:set var="titulo" value="Panel de administración" scope="request"/>
<c:set var="ruta" value="Administración / Panel" scope="request"/>
<c:set var="menu" value="admin-panel" scope="request"/>
<jsp:include page="/includes/header.jsp"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>

<%-- Máximo de pedidos entre los estados, para escalar las barras --%>
<c:set var="maxEstado" value="0"/>
<c:forEach var="e" items="${resumen.pedidosPorEstado}">
    <c:if test="${e.value gt maxEstado}"><c:set var="maxEstado" value="${e.value}"/></c:if>
</c:forEach>

<section class="panel-bienvenida">
    <div>
        <h2>Resumen de la tienda</h2>
        <p class="nota">Ventas, pedidos y alertas de inventario al día de hoy.</p>
    </div>
    <div class="grupo-botones">
        <a class="boton boton--secundario" href="${ctx}/admin?accion=pedidos"><ui:icono nombre="comprobante" tam="18"/> Ver pedidos</a>
        <a class="boton boton--secundario" href="${ctx}/admin?accion=ventas"><ui:icono nombre="grafico" tam="18"/> Reporte de ventas</a>
        <c:if test="${sessionScope.usuarioObj.puedeRegistrar}">
            <a class="boton boton--primario" href="${ctx}/productos?accion=nuevo"><ui:icono nombre="mas" tam="18"/> Nuevo producto</a>
        </c:if>
    </div>
</section>

<section class="indicadores indicadores--tres" aria-label="Indicadores de ventas">
    <div class="indicador">
        <div class="indicador__cabecera"><span class="etiqueta">Ingresos de hoy</span><span class="chip"><ui:icono nombre="monedas" tam="18"/></span></div>
        <strong class="indicador__valor"><c:out value="${resumen.ingresosHoyTexto}"/></strong>
        <span class="nota"><c:out value="${resumen.pedidosHoy}"/>&#32;${resumen.pedidosHoy eq 1 ? 'venta' : 'ventas'} hoy</span>
    </div>
    <div class="indicador">
        <div class="indicador__cabecera"><span class="etiqueta">Ingresos del mes</span><span class="chip chip--ok"><ui:icono nombre="calendario" tam="18"/></span></div>
        <strong class="indicador__valor"><c:out value="${resumen.ingresosMesTexto}"/></strong>
        <span class="nota"><c:out value="${resumen.pedidosMes}"/>&#32;${resumen.pedidosMes eq 1 ? 'venta' : 'ventas'} en el mes</span>
    </div>
    <div class="indicador">
        <div class="indicador__cabecera"><span class="etiqueta">Ingresos totales</span><span class="chip chip--aviso"><ui:icono nombre="grafico" tam="18"/></span></div>
        <strong class="indicador__valor"><c:out value="${resumen.ingresosTotalTexto}"/></strong>
        <span class="nota"><c:out value="${resumen.pedidosTotal}"/>&#32;${resumen.pedidosTotal eq 1 ? 'venta' : 'ventas'} en total</span>
    </div>
    <div class="indicador">
        <div class="indicador__cabecera"><span class="etiqueta">Ticket promedio</span><span class="chip"><ui:icono nombre="comprobante" tam="18"/></span></div>
        <strong class="indicador__valor"><c:out value="${resumen.ticketPromedioTexto}"/></strong>
        <span class="nota">por venta</span>
    </div>
    <a class="indicador indicador--enlace" href="${ctx}/admin?accion=pedidos">
        <div class="indicador__cabecera"><span class="etiqueta">Pedidos pendientes</span><span class="chip chip--aviso"><ui:icono nombre="alerta" tam="18"/></span></div>
        <strong class="indicador__valor ${resumen.pedidosPendientes gt 0 ? 'texto-aviso' : ''}"><c:out value="${resumen.pedidosPendientes}"/></strong>
        <span class="nota">confirmados o en preparación</span>
    </a>
    <div class="indicador">
        <div class="indicador__cabecera"><span class="etiqueta">Unidades vendidas</span><span class="chip chip--ok"><ui:icono nombre="caja" tam="18"/></span></div>
        <strong class="indicador__valor"><c:out value="${resumen.unidadesVendidas}"/></strong>
        <span class="nota">en ventas válidas</span>
    </div>
</section>

<section class="dos-columnas">
    <div class="columna-principal">
        <div class="tarjeta">
            <div class="tarjeta__encabezado">
                <h2 class="tarjeta__titulo">Pedidos por estado</h2>
                <p class="nota">Cantidad de pedidos en cada etapa del flujo de atención.</p>
            </div>
            <c:choose>
                <c:when test="${empty resumen.pedidosPorEstado}">
                    <p class="nota">Aún no hay pedidos registrados.</p>
                </c:when>
                <c:otherwise>
                    <ul class="barras barras--estados">
                        <c:forEach var="e" items="${resumen.pedidosPorEstado}">
                            <li>
                                <span class="barras__nombre"><ui:insigniaEstado estado="${e.key}"/></span>
                                <span class="barras__pista"><span class="barras__valor ${e.key eq 'Anulado' ? 'barras__valor--error' : 'barras__valor--principal'}" style="width: ${maxEstado gt 0 ? e.value * 100 / maxEstado : 0}%"></span></span>
                                <span class="barras__monto mono"><c:out value="${e.value}"/></span>
                            </li>
                        </c:forEach>
                    </ul>
                </c:otherwise>
            </c:choose>
        </div>

        <div class="tarjeta">
            <div class="tarjeta__encabezado">
                <h2 class="tarjeta__titulo">Productos más vendidos</h2>
                <p class="nota">Ordenados por monto vendido; se indican las unidades.</p>
            </div>
            <ui:barrasReporte filas="${resumen.topProductos}" unidad="uds." vacio="Aún no hay ventas registradas."/>
        </div>
    </div>

    <div class="columna-lateral">
        <div class="tarjeta">
            <div class="tarjeta__encabezado">
                <h2 class="tarjeta__titulo">Alertas de stock</h2>
                <p class="nota">Productos con <c:out value="${umbralStock}"/> unidades o menos.</p>
            </div>
            <c:choose>
                <c:when test="${empty resumen.stockBajo}">
                    <div class="banda banda--ok">
                        <ui:icono nombre="check" tam="20"/>
                        <div class="banda__texto"><strong>Inventario saludable</strong><span>Ningún producto está por debajo del umbral.</span></div>
                    </div>
                </c:when>
                <c:otherwise>
                    <ul class="lista-alertas">
                        <c:forEach var="p" items="${resumen.stockBajo}">
                            <c:url var="urlKardex" value="/admin">
                                <c:param name="accion" value="inventario"/>
                                <c:param name="codigo" value="${p.codigo}"/>
                            </c:url>
                            <li>
                                <div class="lista-alertas__nombre">
                                    <strong><c:out value="${p.nombre}"/></strong>
                                    <span class="mono pequeno suave"><c:out value="${p.codigo}"/> · <c:out value="${p.categoria}"/></span>
                                </div>
                                <div class="lista-alertas__estado">
                                    <span class="insignia ${p.stock eq 0 ? 'insignia--error' : 'insignia--aviso'}"><c:out value="${p.stock}"/>&#32;${p.stock eq 1 ? 'unidad' : 'unidades'}</span>
                                    <a class="accion" href="${urlKardex}">Kardex</a>
                                </div>
                            </li>
                        </c:forEach>
                    </ul>
                </c:otherwise>
            </c:choose>
            <a class="accion" href="${ctx}/admin?accion=inventario"><ui:icono nombre="caja" tam="16"/> Ir al inventario</a>
        </div>
    </div>
</section>

<div class="barra-herramientas">
    <div>
        <h2 class="subtitulo">Últimos pedidos</h2>
        <p class="nota">Los más recientes registrados en la tienda.</p>
    </div>
    <a class="accion" href="${ctx}/admin?accion=pedidos">Ver todos</a>
</div>
<div class="tabla-contenedor">
    <table class="tabla">
        <thead>
        <tr>
            <th>N.° Pedido</th>
            <th>Fecha</th>
            <th>Cliente</th>
            <th>Producto</th>
            <th class="num">Total</th>
            <th>Estado</th>
            <th class="num">Detalle</th>
        </tr>
        </thead>
        <tbody>
        <c:forEach var="ped" items="${resumen.ultimosPedidos}">
            <c:url var="urlPedido" value="/admin">
                <c:param name="accion" value="pedido"/>
                <c:param name="id" value="${ped.idPedido}"/>
            </c:url>
            <tr>
                <td data-titulo="N.° Pedido" class="mono fuerte"><c:out value="${ped.codigoPedido}"/></td>
                <td data-titulo="Fecha" class="mono suave"><c:out value="${ped.fechaTexto}"/></td>
                <td data-titulo="Cliente"><c:out value="${ped.nombreCliente}"/></td>
                <td data-titulo="Producto" class="fuerte"><c:out value="${ped.nombreProducto}"/> <span class="mono pequeno suave">× <c:out value="${ped.cantidad}"/></span></td>
                <td data-titulo="Total" class="num mono fuerte"><c:out value="${ped.totalTexto}"/></td>
                <td data-titulo="Estado"><ui:insigniaEstado estado="${ped.estado}"/></td>
                <td data-titulo="Detalle" class="num"><a class="accion" href="${urlPedido}">Ver</a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty resumen.ultimosPedidos}">
            <tr class="fila-vacia"><td colspan="7">Aún no hay pedidos registrados.</td></tr>
        </c:if>
        </tbody>
    </table>
</div>

<jsp:include page="/includes/footer.jsp"/>
