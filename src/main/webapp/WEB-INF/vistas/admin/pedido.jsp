<%--
  Vista: detalle de un pedido, línea de tiempo y acciones de gestión (personal).
  Recibe: pedido (Pedido con pago), siguienteEstado (String; vacío si el flujo terminó o el pedido está anulado)
--%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:set var="titulo" value="Detalle del pedido" scope="request"/>
<c:set var="ruta" value="Administración / Pedidos / Detalle" scope="request"/>
<c:set var="menu" value="admin-pedidos" scope="request"/>
<jsp:include page="/includes/header.jsp"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="pago" value="${pedido.pago}"/>
<c:set var="puedeAvanzar" value="${not empty siguienteEstado and sessionScope.usuarioObj.puedeGestionarPedidos}"/>
<c:set var="puedeAnular" value="${pedido.anulable and sessionScope.usuarioObj.puedeAnularPedidos}"/>

<%-- Posición del estado actual dentro del flujo (el estado Anulado queda fuera) --%>
<c:set var="flujo" value="${fn:split('Confirmado,En preparación,Enviado,Entregado', ',')}"/>
<c:set var="idxActual" value="-1"/>
<c:forEach var="paso" items="${flujo}" varStatus="st">
    <c:if test="${paso eq pedido.estado}"><c:set var="idxActual" value="${st.index}"/></c:if>
</c:forEach>

<div class="barra-herramientas">
    <a class="accion" href="${ctx}/admin?accion=pedidos"><ui:icono nombre="flecha" tam="16"/> Volver a pedidos</a>
</div>

<div class="dos-columnas">
    <div class="columna-principal">
        <div class="tarjeta">
            <div class="fila-espaciada fila-espaciada--cabecera">
                <div class="tarjeta__encabezado">
                    <h2 class="tarjeta__titulo mono">Pedido <c:out value="${pedido.codigoPedido}"/></h2>
                    <p class="nota">Registrado el <c:out value="${pedido.fechaTexto}"/></p>
                </div>
                <ui:insigniaEstado estado="${pedido.estado}"/>
            </div>

            <c:choose>
                <c:when test="${pedido.anulado}">
                    <div class="alerta alerta--error" role="status">
                        <ui:icono nombre="alerta"/>
                        <div>
                            <strong>Pedido anulado</strong>
                            <p>El pago fue reembolsado y el stock se devolvió al inventario.</p>
                        </div>
                    </div>
                </c:when>
                <c:otherwise>
                    <ol class="linea-tiempo" aria-label="Progreso del pedido">
                        <c:forEach var="paso" items="${flujo}" varStatus="st">
                            <c:set var="situacion" value="${st.index lt idxActual or (st.index eq idxActual and pedido.estado eq 'Entregado') ? 'hecho' : (st.index eq idxActual ? 'actual' : 'pendiente')}"/>
                            <li class="linea-tiempo__paso linea-tiempo__paso--${situacion}" ${situacion eq 'actual' ? 'aria-current="step"' : ''}>
                                <span class="linea-tiempo__punto">
                                    <c:choose>
                                        <c:when test="${situacion eq 'hecho'}"><ui:icono nombre="ok" tam="16"/></c:when>
                                        <c:otherwise><c:out value="${st.count}"/></c:otherwise>
                                    </c:choose>
                                </span>
                                <span class="linea-tiempo__texto"><c:out value="${paso}"/></span>
                            </li>
                        </c:forEach>
                    </ol>
                </c:otherwise>
            </c:choose>

            <hr class="divisor">

            <dl class="recibo__datos">
                <div><dt>Cliente</dt><dd><c:out value="${pedido.nombreCliente}"/></dd></div>
                <div><dt>Usuario</dt><dd class="mono"><c:out value="${pedido.usernameCliente}"/></dd></div>
                <div class="recibo__dato-ancho"><dt>Producto</dt><dd><span class="mono"><c:out value="${pedido.codigoProducto}"/></span> · <c:out value="${pedido.nombreProducto}"/></dd></div>
                <div><dt>Cantidad</dt><dd class="mono"><c:out value="${pedido.cantidad}"/></dd></div>
                <div><dt>Precio unitario</dt><dd class="mono"><c:out value="${pedido.precioUnitarioTexto}"/></dd></div>
                <div><dt>Total del pedido</dt><dd class="mono fuerte"><c:out value="${pedido.totalTexto}"/></dd></div>
                <div><dt>Estado del pago</dt><dd><span class="insignia ${pedido.estadoPago eq 'Pagado' ? 'insignia--ok' : (pedido.estadoPago eq 'Reembolsado' ? 'insignia--anulado' : 'insignia--aviso')}"><c:out value="${pedido.estadoPago}"/></span></dd></div>
                <div><dt>Fecha del pedido</dt><dd class="mono"><c:out value="${pedido.fechaTexto}"/></dd></div>
                <div>
                    <dt>Última actualización</dt>
                    <dd class="mono">
                        <c:choose>
                            <c:when test="${empty pedido.fechaActualizacion}">—</c:when>
                            <c:otherwise><c:out value="${pedido.fechaActualizacion}"/></c:otherwise>
                        </c:choose>
                    </dd>
                </div>
            </dl>
        </div>

        <c:if test="${puedeAvanzar or puedeAnular}">
            <div class="tarjeta">
                <div class="tarjeta__encabezado">
                    <h2 class="tarjeta__titulo">Gestión del pedido</h2>
                    <p class="nota">Los cambios quedan registrados en la auditoría.</p>
                </div>

                <c:if test="${puedeAvanzar}">
                    <form data-un-envio class="form-accion" method="post" action="${ctx}/admin">
                        <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
                        <input type="hidden" name="accion" value="estadoPedido">
                        <input type="hidden" name="id" value="${pedido.idPedido}">
                        <input type="hidden" name="estado" value="${fn:escapeXml(siguienteEstado)}">
                        <p class="nota">Estado actual: <strong><c:out value="${pedido.estado}"/></strong>. El pedido avanza un paso en el flujo de atención.</p>
                        <button type="submit" class="boton boton--primario">
                            <ui:icono nombre="check" tam="18"/> Marcar como <c:out value="${siguienteEstado}"/>
                        </button>
                    </form>
                </c:if>

                <c:if test="${puedeAnular}">
                    <form data-un-envio class="zona-peligro" method="post" action="${ctx}/admin"
                          data-confirmar="¿Anular el pedido ${fn:escapeXml(pedido.codigoPedido)}? Se reembolsará el pago y se devolverá el stock. Esta acción no se puede deshacer.">
                        <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
                        <input type="hidden" name="accion" value="anularPedido">
                        <input type="hidden" name="id" value="${pedido.idPedido}">
                        <div class="campo">
                            <label for="motivo-anulacion">Motivo de la anulación *</label>
                            <textarea id="motivo-anulacion" name="motivo" rows="3" maxlength="200" required
                                      placeholder="Explica por qué se anula el pedido"></textarea>
                            <p class="campo__ayuda">Obligatorio, hasta 200 caracteres. Solo se anulan pedidos confirmados o en preparación.</p>
                        </div>
                        <button type="submit" class="boton boton--peligro">
                            <ui:icono nombre="cerrar" tam="18"/> Anular pedido
                        </button>
                    </form>
                </c:if>
            </div>
        </c:if>
    </div>

    <div class="columna-lateral">
        <div class="tarjeta ticket">
            <p class="etiqueta">Pago y comprobante</p>
            <hr class="divisor divisor--punteado">
            <c:choose>
                <c:when test="${not empty pago}">
                    <c:url var="urlComprobante" value="/pago">
                        <c:param name="accion" value="comprobante"/>
                        <c:param name="pedido" value="${pedido.idPedido}"/>
                    </c:url>
                    <dl class="ticket__datos">
                        <div><dt>Estado</dt><dd><span class="insignia ${pago.aprobado ? 'insignia--ok' : (pago.reembolsado ? 'insignia--anulado' : 'insignia--error')}"><c:out value="${pago.estado}"/></span></dd></div>
                        <div><dt>Método</dt><dd><c:out value="${pago.metodoTexto}"/></dd></div>
                        <div><dt>Comprobante</dt><dd><c:out value="${pago.tipoComprobante}"/></dd></div>
                        <div><dt>N.°</dt><dd><a class="acento" href="${urlComprobante}"><c:out value="${pago.numeroComprobante}"/></a></dd></div>
                        <div><dt>Autorización</dt><dd><c:out value="${pago.codigoAutorizacion}"/></dd></div>
                        <div><dt>Operación</dt><dd><c:out value="${pago.codigoOperacion}"/></dd></div>
                    </dl>
                    <hr class="divisor divisor--punteado">
                    <div class="ticket__grande"><span>Importe pagado</span><strong><c:out value="${pago.montoTexto}"/></strong></div>
                    <a class="boton boton--secundario boton--bloque" href="${urlComprobante}"><ui:icono nombre="comprobante" tam="18"/> Ver comprobante</a>
                </c:when>
                <c:otherwise>
                    <p class="nota">Este pedido no tiene un pago registrado.</p>
                </c:otherwise>
            </c:choose>
        </div>
    </div>
</div>

<jsp:include page="/includes/footer.jsp"/>
