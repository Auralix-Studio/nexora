<%--
  Vista: inventario, alertas de stock y kardex (personal).
  Recibe: productos, movimientos (últimos 50), productoSel (Producto o null), tiposMovimiento (List<String>),
          umbralStock, form (valores previos del ajuste), errorFormulario
--%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:set var="titulo" value="Inventario y kardex" scope="request"/>
<c:set var="ruta" value="Administración / Inventario" scope="request"/>
<c:set var="menu" value="admin-inventario" scope="request"/>
<jsp:include page="/includes/header.jsp"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="puedeAjustar" value="${sessionScope.usuarioObj.puedeAjustarStock}"/>

<div class="dos-columnas">
    <div class="columna-principal">
        <div class="barra-herramientas">
            <div>
                <h2 class="subtitulo">Existencias por producto</h2>
                <p class="nota">Se marcan los productos con <c:out value="${umbralStock}"/> unidades o menos. Pulsa Kardex para ver sus movimientos.</p>
            </div>
        </div>
        <div class="tabla-contenedor">
            <table class="tabla">
                <thead>
                <tr>
                    <th class="col-codigo">Código</th>
                    <th>Producto</th>
                    <th class="num col-stock">Stock</th>
                    <th class="col-estado">Estado</th>
                    <th>Alerta</th>
                    <th class="num">Kardex</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="p" items="${productos}">
                    <c:url var="urlKardex" value="/admin">
                        <c:param name="accion" value="inventario"/>
                        <c:param name="codigo" value="${p.codigo}"/>
                    </c:url>
                    <tr class="${p.stock le umbralStock ? 'fila-alerta' : ''} ${not empty productoSel and productoSel.codigo eq p.codigo ? 'fila-seleccionada' : ''}">
                        <td data-titulo="Código" class="mono fuerte"><c:out value="${p.codigo}"/></td>
                        <td data-titulo="Producto" class="fuerte"><c:out value="${p.nombre}"/></td>
                        <td data-titulo="Stock" class="num mono ${p.stock le umbralStock ? 'texto-error' : ''}"><c:out value="${p.stock}"/></td>
                        <td data-titulo="Estado"><span class="insignia ${p.disponible ? 'insignia--ok' : 'insignia--error'}"><c:out value="${p.estado}"/></span></td>
                        <td data-titulo="Alerta">
                            <c:choose>
                                <c:when test="${p.stock eq 0}"><span class="insignia insignia--error">Sin stock</span></c:when>
                                <c:when test="${p.stock le umbralStock}"><span class="insignia insignia--aviso">Stock bajo</span></c:when>
                                <c:otherwise><span class="tenue">—</span></c:otherwise>
                            </c:choose>
                        </td>
                        <td data-titulo="Kardex" class="num"><a class="accion" href="${urlKardex}"><ui:icono nombre="historial" tam="16"/> Kardex</a></td>
                    </tr>
                </c:forEach>
                <c:if test="${empty productos}">
                    <tr class="fila-vacia"><td colspan="6">No hay productos registrados.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>

    <c:if test="${puedeAjustar}">
        <form data-un-envio class="tarjeta columna-lateral columna-lateral--formulario" method="post" action="${ctx}/admin" id="form-ajuste">
            <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
            <input type="hidden" name="accion" value="ajustarStock">

            <div class="tarjeta__encabezado">
                <h2 class="tarjeta__titulo">Registrar movimiento</h2>
                <p class="nota">Entradas de mercadería y ajustes de stock. Los campos con * son obligatorios.</p>
            </div>

            <c:if test="${not empty errorFormulario}">
                <div class="alerta alerta--error" role="alert">
                    <ui:icono nombre="alerta"/>
                    <div><strong>No se registró el movimiento</strong><p><c:out value="${errorFormulario}"/></p></div>
                </div>
            </c:if>

            <c:choose>
                <c:when test="${not empty productoSel}">
                    <input type="hidden" name="codigo" value="${fn:escapeXml(productoSel.codigo)}">
                    <div class="resumen-producto">
                        <span class="etiqueta">Producto</span>
                        <strong class="resumen-producto__nombre"><c:out value="${productoSel.nombre}"/></strong>
                        <span class="mono pequeno suave"><c:out value="${productoSel.codigo}"/> · stock actual <c:out value="${productoSel.stock}"/></span>
                    </div>
                </c:when>
                <c:otherwise>
                    <div class="campo">
                        <label for="aj-codigo">Producto *</label>
                        <div class="selector">
                            <select id="aj-codigo" name="codigo" required>
                                <option value="">Selecciona un producto</option>
                                <c:forEach var="p" items="${productos}">
                                    <option value="${fn:escapeXml(p.codigo)}" ${form.codigo eq p.codigo ? 'selected' : ''}><c:out value="${p.codigo}"/> · <c:out value="${p.nombre}"/> (<c:out value="${p.stock}"/>)</option>
                                </c:forEach>
                            </select>
                            <ui:icono nombre="flecha-abajo" tam="18"/>
                        </div>
                    </div>
                </c:otherwise>
            </c:choose>

            <div class="campo">
                <label for="aj-tipo">Tipo de movimiento *</label>
                <div class="selector">
                    <select id="aj-tipo" name="tipo" required>
                        <c:forEach var="t" items="${tiposMovimiento}">
                            <option value="${fn:escapeXml(t)}" ${form.tipo eq t ? 'selected' : ''}><c:out value="${t}"/></option>
                        </c:forEach>
                    </select>
                    <ui:icono nombre="flecha-abajo" tam="18"/>
                </div>
            </div>

            <div class="campo">
                <label for="aj-cantidad">Cantidad *</label>
                <input id="aj-cantidad" type="number" name="cantidad" step="1" required inputmode="numeric"
                       placeholder="Ej. 10" value="${fn:escapeXml(form.cantidad)}">
                <p class="campo__ayuda">Una entrada suma unidades. Un ajuste admite negativos para restar (el stock no puede quedar bajo cero).</p>
            </div>

            <div class="campo">
                <label for="aj-motivo">Motivo *</label>
                <input id="aj-motivo" type="text" name="motivo" maxlength="200" required autocomplete="off"
                       placeholder="Ej. Reposición del proveedor" value="${fn:escapeXml(form.motivo)}">
            </div>

            <button type="submit" class="boton boton--primario boton--bloque"><ui:icono nombre="registrar" tam="18"/> Registrar movimiento</button>
            <c:if test="${not empty productoSel}">
                <a class="boton boton--secundario boton--bloque" href="${ctx}/admin?accion=inventario">Elegir otro producto</a>
            </c:if>
        </form>
    </c:if>
</div>

<div class="barra-herramientas">
    <div>
        <c:choose>
            <c:when test="${not empty productoSel}">
                <h2 class="subtitulo">Kardex de <span class="mono"><c:out value="${productoSel.codigo}"/></span> · <c:out value="${productoSel.nombre}"/></h2>
                <p class="nota">Movimientos de este producto, del más reciente al más antiguo.</p>
            </c:when>
            <c:otherwise>
                <h2 class="subtitulo">Últimos movimientos de stock</h2>
                <p class="nota">Hasta 50 movimientos de todos los productos, del más reciente al más antiguo.</p>
            </c:otherwise>
        </c:choose>
    </div>
    <c:if test="${not empty productoSel}">
        <a class="accion" href="${ctx}/admin?accion=inventario">Ver todos los movimientos</a>
    </c:if>
</div>
<div class="tabla-contenedor">
    <table class="tabla">
        <thead>
        <tr>
            <th>Fecha</th>
            <th>Producto</th>
            <th>Tipo</th>
            <th class="num">Cantidad</th>
            <th class="num">Stock resultante</th>
            <th>Motivo</th>
            <th>Usuario</th>
        </tr>
        </thead>
        <tbody>
        <c:forEach var="mov" items="${movimientos}">
            <tr>
                <td data-titulo="Fecha" class="mono suave"><c:out value="${mov.fecha}"/></td>
                <td data-titulo="Producto"><span class="mono fuerte"><c:out value="${mov.codigoProducto}"/></span> <c:out value="${mov.nombreProducto}"/></td>
                <td data-titulo="Tipo"><ui:insigniaMovimiento tipo="${mov.tipo}"/></td>
                <td data-titulo="Cantidad" class="num mono fuerte ${mov.cantidad gt 0 ? 'texto-ok' : 'texto-error'}"><c:out value="${mov.cantidadTexto}"/></td>
                <td data-titulo="Stock resultante" class="num mono"><c:out value="${mov.stockResultante}"/></td>
                <td data-titulo="Motivo" class="celda-texto"><c:out value="${mov.motivo}"/></td>
                <td data-titulo="Usuario">
                    <c:choose>
                        <c:when test="${empty mov.usuario}"><span class="tenue">Sistema</span></c:when>
                        <c:otherwise><c:out value="${mov.usuario}"/></c:otherwise>
                    </c:choose>
                </td>
            </tr>
        </c:forEach>
        <c:if test="${empty movimientos}">
            <tr class="fila-vacia"><td colspan="7">Aún no hay movimientos de stock registrados.</td></tr>
        </c:if>
        </tbody>
    </table>
</div>

<jsp:include page="/includes/footer.jsp"/>
