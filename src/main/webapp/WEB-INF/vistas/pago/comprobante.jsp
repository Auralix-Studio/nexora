<%--
  Vista: comprobante de pago (boleta o factura electrónica de demostración).
  Recibe: pedido (Pedido con pedido.pago)
--%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:set var="titulo" value="Comprobante de pago" scope="request"/>
<c:set var="ruta" value="Pedidos / Comprobante" scope="request"/>
<c:set var="menu" value="${sessionScope.rol eq 'Cliente' ? 'pedidos' : 'admin-pedidos'}" scope="request"/>
<jsp:include page="/includes/header.jsp"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="pago" value="${pedido.pago}"/>

<c:choose>
    <c:when test="${empty pago}">
        <div class="tarjeta">
            <div class="alerta alerta--error" role="alert">
                <ui:icono nombre="alerta"/>
                <div>
                    <strong>Este pedido no tiene un pago registrado</strong>
                    <p>Todavía no hay un comprobante para <c:out value="${pedido.codigoPedido}"/>.</p>
                </div>
            </div>
        </div>
    </c:when>
    <c:otherwise>
        <article class="recibo" aria-labelledby="titulo-recibo">
            <header class="recibo__cabecera">
                <div class="recibo__marca">
                    <span class="marca__logo"><ui:icono nombre="capas" tam="22"/></span>
                    <div>
                        <strong class="recibo__marca-nombre">NEXORA</strong>
                        <span class="recibo__marca-sub">Tecnología y Soluciones Digitales</span>
                    </div>
                </div>
                <div class="recibo__tipo">
                    <h2 class="recibo__documento" id="titulo-recibo">${pago.factura ? 'FACTURA ELECTRÓNICA' : 'BOLETA DE VENTA ELECTRÓNICA'}</h2>
                    <p class="recibo__numero mono"><c:out value="${pago.numeroComprobante}"/></p>
                </div>
            </header>

            <c:choose>
                <c:when test="${pedido.anulado}">
                    <div class="recibo__estado recibo__estado--error recibo__estado--anulado">
                        <ui:icono nombre="alerta" tam="20"/>
                        <strong>Pedido anulado · pago reembolsado</strong>
                        <span class="mono"><c:out value="${pago.montoTexto}"/></span>
                    </div>
                </c:when>
                <c:otherwise>
                    <div class="recibo__estado ${pago.aprobado ? '' : 'recibo__estado--error'}">
                        <ui:icono nombre="${pago.aprobado ? 'check' : 'alerta'}" tam="20"/>
                        <strong>${pago.aprobado ? 'Pago aprobado' : 'Pago rechazado'}</strong>
                        <span class="mono"><c:out value="${pago.montoTexto}"/></span>
                    </div>
                </c:otherwise>
            </c:choose>

            <div class="recibo__cuerpo">
                <dl class="recibo__datos">
                    <div><dt>N.° de pedido</dt><dd class="mono"><c:out value="${pedido.codigoPedido}"/></dd></div>
                    <div><dt>Fecha</dt><dd class="mono"><c:out value="${pedido.fechaTexto}"/></dd></div>
                    <div><dt>Cliente</dt><dd><c:out value="${pedido.nombreCliente}"/></dd></div>
                    <div><dt>${pago.factura ? 'RUC' : 'DNI'}</dt><dd class="mono"><c:out value="${pago.documentoCliente}"/></dd></div>
                    <c:if test="${pago.factura}">
                        <div class="recibo__dato-ancho"><dt>Razón social</dt><dd><c:out value="${pago.razonSocial}"/></dd></div>
                    </c:if>
                    <div><dt>Método de pago</dt><dd><c:out value="${pago.metodoTexto}"/></dd></div>
                    <div><dt>Código de autorización</dt><dd class="mono"><c:out value="${pago.codigoAutorizacion}"/></dd></div>
                    <div><dt>Código de operación</dt><dd class="mono"><c:out value="${pago.codigoOperacion}"/></dd></div>
                </dl>

                <%-- Datos de Despacho y Entrega --%>
                <div class="recibo__seccion-envio" style="margin: 1.25rem 0; padding: 1rem; background: var(--fondo-suave, #f8fafc); border-radius: var(--radio-md, 8px); border: 1px solid var(--borde, #e2e8f0);">
                    <div style="display:flex; align-items:center; justify-content:space-between; margin-bottom:0.75rem; flex-wrap:wrap; gap:0.5rem;">
                        <strong style="font-size:0.95rem; display:flex; align-items:center; gap:0.5rem;">
                            <ui:icono nombre="caja" tam="18"/> Logística y Despacho del Pedido
                        </strong>
                        <c:if test="${not empty pedido.codigoSeguimiento}">
                            <span class="mono" style="font-size:0.8rem; background: var(--fondo-destacado, #e0f2fe); color: var(--color-primario, #0284c7); padding: 0.2rem 0.6rem; border-radius: 4px; font-weight:600;">
                                Tracking: <c:out value="${pedido.codigoSeguimiento}"/>
                            </span>
                        </c:if>
                    </div>
                    <dl class="recibo__datos" style="margin:0;">
                        <div><dt>Modalidad</dt><dd><c:out value="${pedido.tipoEnvio}"/></dd></div>
                        <div><dt>Estado de entrega</dt><dd><strong><c:out value="${pedido.estado}"/></strong></dd></div>
                        <c:choose>
                            <c:when test="${pedido.retiroEnTienda}">
                                <div class="recibo__dato-ancho"><dt>Punto de Retiro</dt><dd><c:out value="${pedido.direccionEnvio}"/></dd></div>
                            </c:when>
                            <c:otherwise>
                                <div><dt>Destino</dt><dd><c:out value="${pedido.distrito}"/>, <c:out value="${pedido.departamento}"/></dd></div>
                                <div><dt>Dirección</dt><dd><c:out value="${pedido.direccionEnvio}"/></dd></div>
                                <c:if test="${not empty pedido.referenciaEnvio}">
                                    <div class="recibo__dato-ancho"><dt>Referencia</dt><dd><c:out value="${pedido.referenciaEnvio}"/></dd></div>
                                </c:if>
                            </c:otherwise>
                        </c:choose>
                        <c:if test="${not empty pedido.telefonoContacto}">
                            <div><dt>Celular de contacto</dt><dd class="mono"><c:out value="${pedido.telefonoContacto}"/></dd></div>
                        </c:if>
                        <c:if test="${not empty pedido.correoContacto}">
                            <div><dt>Correo tracking</dt><dd><c:out value="${pedido.correoContacto}"/></dd></div>
                        </c:if>
                        <c:if test="${not empty pedido.notasPedido}">
                            <div class="recibo__dato-ancho"><dt>Indicaciones</dt><dd><c:out value="${pedido.notasPedido}"/></dd></div>
                        </c:if>
                    </dl>
                </div>

                <div class="tabla-contenedor">
                    <table class="tabla-recibo">
                        <thead>
                        <tr>
                            <th>Código</th>
                            <th>Producto</th>
                            <th class="num">Cant.</th>
                            <th class="num">P. unitario</th>
                            <th class="num">Importe</th>
                        </tr>
                        </thead>
                        <tbody>
                        <tr>
                            <td data-titulo="Código" class="mono"><c:out value="${pedido.codigoProducto}"/></td>
                            <td data-titulo="Producto" class="fuerte"><c:out value="${pedido.nombreProducto}"/></td>
                            <td data-titulo="Cantidad" class="num mono"><c:out value="${pedido.cantidad}"/></td>
                            <td data-titulo="P. unitario" class="num mono"><c:out value="${pedido.precioUnitarioTexto}"/></td>
                            <td data-titulo="Importe" class="num mono fuerte"><c:out value="${pedido.totalTexto}"/></td>
                        </tr>
                        </tbody>
                    </table>
                </div>

                <dl class="recibo__totales">
                    <div><dt>Op. gravada</dt><dd class="mono"><c:out value="${pago.subtotalTexto}"/></dd></div>
                    <div><dt>IGV 18 %</dt><dd class="mono"><c:out value="${pago.igvTexto}"/></dd></div>
                    <div><dt>Despacho / Envío</dt><dd class="mono">Gratis</dd></div>
                    <div class="recibo__total"><dt>Importe total</dt><dd class="mono"><c:out value="${pago.montoTexto}"/></dd></div>
                </dl>
            </div>

            <footer class="recibo__pie">
                <p class="nota con-icono"><ui:icono nombre="info" tam="14"/> Documento de demostración sin validez tributaria.</p>
            </footer>
        </article>
    </c:otherwise>
</c:choose>

<div class="recibo-acciones">
    <c:if test="${not empty pago}">
        <button type="button" class="boton boton--primario" data-imprimir><ui:icono nombre="imprimir" tam="18"/> Imprimir</button>
    </c:if>
    <c:choose>
        <c:when test="${sessionScope.rol eq 'Cliente'}">
            <a class="boton boton--secundario" href="${ctx}/productos?accion=pedidos"><ui:icono nombre="monedas" tam="18"/> Ver mis pedidos</a>
            <a class="boton boton--secundario" href="${ctx}/productos?accion=listar"><ui:icono nombre="lista" tam="18"/> Seguir comprando</a>
        </c:when>
        <c:otherwise>
            <a class="boton boton--secundario" href="${ctx}/admin?accion=pedido&amp;id=${pedido.idPedido}"><ui:icono nombre="flecha" tam="18"/> Volver al pedido</a>
            <a class="boton boton--secundario" href="${ctx}/admin?accion=pedidos"><ui:icono nombre="comprobante" tam="18"/> Ver pedidos</a>
        </c:otherwise>
    </c:choose>
</div>

<jsp:include page="/includes/footer.jsp"/>
