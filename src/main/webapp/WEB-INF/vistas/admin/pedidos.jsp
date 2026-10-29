<%--
  Vista: pedidos de clientes con filtros (personal).
  Recibe: pedidos (List<Pedido>), estados (List<String>), fEstado, fCliente, fDesde, fHasta (yyyy-MM-dd),
          totalFiltradoTexto
--%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:set var="titulo" value="Pedidos de clientes" scope="request"/>
<c:set var="ruta" value="Administración / Pedidos" scope="request"/>
<c:set var="menu" value="admin-pedidos" scope="request"/>
<jsp:include page="/includes/header.jsp"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="hayFiltros" value="${not empty fEstado or not empty fCliente or not empty fDesde or not empty fHasta}"/>

<form class="tarjeta filtros" method="get" action="${ctx}/admin">
    <input type="hidden" name="accion" value="pedidos">
    <div class="tarjeta__encabezado">
        <h2 class="tarjeta__titulo">Filtrar pedidos</h2>
        <p class="nota">Combina estado, cliente y rango de fechas. Déjalos vacíos para ver todos.</p>
    </div>
    <div class="fila-busqueda">
        <div class="campo campo--seleccion">
            <label for="f-estado">Estado</label>
            <div class="selector">
                <select id="f-estado" name="estado">
                    <option value="">Todos</option>
                    <c:forEach var="est" items="${estados}">
                        <option value="${fn:escapeXml(est)}" ${est eq fEstado ? 'selected' : ''}><c:out value="${est}"/></option>
                    </c:forEach>
                </select>
                <ui:icono nombre="flecha-abajo" tam="18"/>
            </div>
        </div>
        <div class="campo campo--crece">
            <label for="f-cliente">Cliente</label>
            <input id="f-cliente" type="search" name="cliente" maxlength="60" autocomplete="off"
                   placeholder="Usuario o nombre" value="${fn:escapeXml(fCliente)}">
        </div>
        <div class="campo campo--fecha">
            <label for="f-desde">Desde</label>
            <input id="f-desde" type="date" name="desde" value="${fn:escapeXml(fDesde)}">
        </div>
        <div class="campo campo--fecha">
            <label for="f-hasta">Hasta</label>
            <input id="f-hasta" type="date" name="hasta" value="${fn:escapeXml(fHasta)}">
        </div>
        <div class="filtros__acciones">
            <button type="submit" class="boton boton--primario"><ui:icono nombre="filtro" tam="18"/> Filtrar</button>
            <c:if test="${hayFiltros}">
                <a class="boton boton--secundario" href="${ctx}/admin?accion=pedidos">Limpiar</a>
            </c:if>
        </div>
    </div>
</form>

<div class="tabla-contenedor">
    <table class="tabla">
        <thead>
        <tr>
            <th>N.° Pedido</th>
            <th>Fecha</th>
            <th>Cliente</th>
            <th>Producto</th>
            <th class="num">Cant.</th>
            <th class="num">Total</th>
            <th>Estado</th>
            <th>Pago</th>
            <th>Comprobante</th>
            <th class="num">Detalle</th>
        </tr>
        </thead>
        <tbody>
        <c:forEach var="ped" items="${pedidos}">
            <c:url var="urlPedido" value="/admin">
                <c:param name="accion" value="pedido"/>
                <c:param name="id" value="${ped.idPedido}"/>
            </c:url>
            <tr>
                <td data-titulo="N.° Pedido" class="mono fuerte"><c:out value="${ped.codigoPedido}"/></td>
                <td data-titulo="Fecha" class="mono suave"><c:out value="${ped.fechaTexto}"/></td>
                <td data-titulo="Cliente"><c:out value="${ped.nombreCliente}"/> <span class="mono pequeno tenue">(<c:out value="${ped.usernameCliente}"/>)</span></td>
                <td data-titulo="Producto" class="fuerte"><c:out value="${ped.nombreProducto}"/></td>
                <td data-titulo="Cantidad" class="num mono"><c:out value="${ped.cantidad}"/></td>
                <td data-titulo="Total" class="num mono fuerte"><c:out value="${ped.totalTexto}"/></td>
                <td data-titulo="Estado"><ui:insigniaEstado estado="${ped.estado}"/></td>
                <td data-titulo="Pago">
                    <div class="celda-pago">
                        <span class="insignia ${ped.estadoPago eq 'Pagado' ? 'insignia--ok' : (ped.estadoPago eq 'Reembolsado' ? 'insignia--anulado' : 'insignia--aviso')}"><c:out value="${ped.estadoPago}"/></span>
                        <c:if test="${not empty ped.pago}"><span class="celda-pago__metodo mono"><c:out value="${ped.pago.metodoTexto}"/></span></c:if>
                    </div>
                </td>
                <td data-titulo="Comprobante">
                    <c:choose>
                        <c:when test="${not empty ped.pago}">
                            <c:url var="urlComprobante" value="/pago">
                                <c:param name="accion" value="comprobante"/>
                                <c:param name="pedido" value="${ped.idPedido}"/>
                            </c:url>
                            <a class="accion mono" href="${urlComprobante}"><c:out value="${ped.pago.numeroComprobante}"/></a>
                        </c:when>
                        <c:otherwise><span class="tenue">—</span></c:otherwise>
                    </c:choose>
                </td>
                <td data-titulo="Detalle" class="num"><a class="accion" href="${urlPedido}">Ver</a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty pedidos}">
            <tr class="fila-vacia">
                <td colspan="10">${hayFiltros ? 'Ningún pedido coincide con los filtros aplicados.' : 'Aún no hay pedidos registrados.'}</td>
            </tr>
        </c:if>
        </tbody>
    </table>
    <div class="tabla-pie">
        <span><c:out value="${fn:length(pedidos)}"/>&#32;${fn:length(pedidos) eq 1 ? 'pedido' : 'pedidos'}</span>
        <span>Total filtrado: <strong><c:out value="${totalFiltradoTexto}"/></strong></span>
    </div>
</div>

<jsp:include page="/includes/footer.jsp"/>
