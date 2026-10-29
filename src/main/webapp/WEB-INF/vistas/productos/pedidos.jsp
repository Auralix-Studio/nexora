<%-- Fragmento del historial, incluido únicamente en el modo pedidos. --%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
    <div class="tarjeta">
        <div class="tarjeta__encabezado">
            <h2 class="tarjeta__titulo">${sessionScope.rol eq 'Cliente' ? 'Mis compras' : 'Registro General de Pedidos de Clientes'}</h2>
            <p class="nota">${sessionScope.rol eq 'Cliente' ? 'Consulta el estado de pago y el comprobante de cada compra.' : 'Consulta el detalle, el pago y el comprobante de los pedidos registrados.'}</p>
        </div>
        <c:choose><c:when test="${not empty errorPedidos}"><p class="alerta alerta--error" role="alert"><c:out value="${errorPedidos}"/></p></c:when><c:otherwise>
        <div class="tabla-contenedor">
            <table class="tabla">
                <thead>
                <tr>
                    <th>N.° Pedido</th>
                    <th>Fecha y Hora</th>
                    <th>Cliente</th>
                    <th>Código Prod.</th>
                    <th>Producto</th>
                    <th class="num">Cant.</th>
                    <th class="num">Precio Unit.</th>
                    <th class="num">Total</th>
                    <th>Estado</th>
                    <th>Pago</th>
                    <th>Comprobante</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="ped" items="${pedidos}">
                    <tr>
                        <td data-titulo="N.° Pedido" class="mono fuerte"><c:out value="${ped.codigoPedido}"/></td>
                        <td data-titulo="Fecha" class="mono suave"><c:out value="${ped.fechaTexto}"/></td>
                        <td data-titulo="Cliente"><c:out value="${ped.nombreCliente}"/> <span class="mono pequeno tenue">(<c:out value="${ped.usernameCliente}"/>)</span></td>
                        <td data-titulo="Código" class="mono"><c:out value="${ped.codigoProducto}"/></td>
                        <td data-titulo="Producto" class="fuerte"><c:out value="${ped.nombreProducto}"/></td>
                        <td data-titulo="Cantidad" class="num mono"><c:out value="${ped.cantidad}"/></td>
                        <td data-titulo="Precio unitario" class="num mono"><c:out value="${ped.precioUnitarioTexto}"/></td>
                        <td data-titulo="Total" class="num mono fuerte"><c:out value="${ped.totalTexto}"/></td>
                        <td data-titulo="Estado"><ui:insigniaEstado estado="${ped.estado}"/></td>
                        <td data-titulo="Pago">
                            <div class="celda-pago">
                                <span class="insignia ${ped.estadoPago eq 'Pagado' ? 'insignia--ok' : 'insignia--aviso'}"><c:out value="${ped.estadoPago}"/></span>
                                <c:if test="${not empty ped.pago}"><span class="celda-pago__metodo mono"><c:out value="${ped.pago.metodoTexto}"/></span></c:if>
                            </div>
                        </td>
                        <td data-titulo="Comprobante">
                            <c:choose>
                                <c:when test="${not empty ped.pago}">
                                    <c:url var="urlComprobante" value="/pago"><c:param name="accion" value="comprobante"/><c:param name="pedido" value="${ped.idPedido}"/></c:url>
                                    <a class="accion mono" href="${urlComprobante}"><c:out value="${ped.pago.numeroComprobante}"/></a>
                                </c:when>
                                <c:otherwise><span class="tenue">—</span></c:otherwise>
                            </c:choose>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty pedidos}">
                    <tr class="fila-vacia">
                        <td colspan="11">${sessionScope.rol eq 'Cliente' ? 'Aún no tienes compras. Elige un producto disponible y pulsa Comprar ahora.' : 'Aún no hay compras registradas por los clientes.'}</td>
                    </tr>
                </c:if>
                </tbody>
            </table>
        </div>
        </c:otherwise></c:choose>
    </div>
