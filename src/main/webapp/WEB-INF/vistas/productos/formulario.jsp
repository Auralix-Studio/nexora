<%--
  Vista: formulario de producto. modo = registrar | editar
  Recibe: form (valores), errores (campo -> mensaje), categorias, estados
--%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:set var="editando" value="${modo eq 'editar'}"/>
<c:choose>
    <c:when test="${editando}">
        <c:set var="titulo" value="Editar producto" scope="request"/>
        <c:set var="ruta" value="Productos / Editar / ${form.codigo}" scope="request"/>
        <c:set var="menu" value="editar" scope="request"/>
    </c:when>
    <c:otherwise>
        <c:set var="titulo" value="Registrar producto" scope="request"/>
        <c:set var="ruta" value="Productos / Registrar" scope="request"/>
        <c:set var="menu" value="registrar" scope="request"/>
    </c:otherwise>
</c:choose>
<jsp:include page="/includes/header.jsp"/>

<div class="dos-columnas">
    <form class="tarjeta tarjeta--amplia formulario" method="post" novalidate
          action="${pageContext.request.contextPath}/productos" data-formulario-producto
          data-modo="${modo}">
        <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
        <input type="hidden" name="accion" value="${editando ? 'actualizar' : 'registrar'}">

        <div class="tarjeta__encabezado">
            <h2 class="tarjeta__titulo">${editando ? 'Editar ' : 'Datos del producto'}<c:if test="${editando}"><c:out value="${form.codigo}"/></c:if></h2>
            <p class="nota">
                <c:choose>
                    <c:when test="${editando}">Actualiza los datos del producto. El estado se calcula a partir del stock, que se ajusta en el inventario.</c:when>
                    <c:otherwise>Complete los campos. Los marcados con * son obligatorios.</c:otherwise>
                </c:choose>
            </p>
        </div>

        <div class="alerta alerta--error ${empty errores ? 'oculto' : ''}" data-alerta-errores>
            <ui:icono nombre="alerta"/>
            <div>
                <strong data-alerta-titulo>Revise ${errores.size()} ${errores.size() eq 1 ? 'campo' : 'campos'} antes de guardar</strong>
                <p>El formulario no se guarda hasta corregir los errores.</p>
            </div>
        </div>

        <div class="rejilla-campos">
            <div class="campo ${not empty errores.codigo ? 'campo--error' : ''}">
                <label for="codigo">Código ${editando ? '' : '*'}</label>
                <input id="codigo" name="codigo" type="text" maxlength="10" autocomplete="off"
                       placeholder="Ej. P013" value="${fn:escapeXml(form.codigo)}"
                       ${editando ? 'readonly class="solo-lectura"' : 'autofocus'}>
                <p class="campo__error" data-error-de="codigo"><ui:icono nombre="alerta" tam="14"/><span><c:out value="${errores.codigo}"/></span></p>
            </div>
            <div class="campo ${not empty errores.nombre ? 'campo--error' : ''}">
                <label for="nombre">Nombre *</label>
                <input id="nombre" name="nombre" type="text" maxlength="120" autocomplete="off"
                       placeholder="Ej. Mouse inalámbrico Logitech M185" value="${fn:escapeXml(form.nombre)}">
                <p class="campo__error" data-error-de="nombre"><ui:icono nombre="alerta" tam="14"/><span><c:out value="${errores.nombre}"/></span></p>
            </div>
            <div class="campo ${not empty errores.categoria ? 'campo--error' : ''}">
                <label for="categoria">Categoría *</label>
                <div class="selector">
                    <select id="categoria" name="categoria">
                        <option value="">Seleccione una opción</option>
                        <c:forEach var="cat" items="${categorias}">
                            <option value="${cat}" ${cat eq form.categoria ? 'selected' : ''}><c:out value="${cat}"/></option>
                        </c:forEach>
                    </select>
                    <ui:icono nombre="flecha-abajo" tam="18"/>
                </div>
                <p class="campo__error" data-error-de="categoria"><ui:icono nombre="alerta" tam="14"/><span><c:out value="${errores.categoria}"/></span></p>
            </div>
            <div class="campo">
                <label for="estado">Estado</label>
                <div class="selector">
                    <select id="estado" name="estado">
                        <c:forEach var="est" items="${estados}">
                            <option value="${est}" ${est eq form.estado ? 'selected' : ''}><c:out value="${est}"/></option>
                        </c:forEach>
                    </select>
                    <ui:icono nombre="flecha-abajo" tam="18"/>
                </div>
                <p class="campo__ayuda">Se ajusta según el stock: sin stock el producto queda Agotado.</p>
            </div>
            <div class="campo ${not empty errores.precio ? 'campo--error' : ''}">
                <label for="precio">Precio (S/) *</label>
                <input id="precio" name="precio" type="text" inputmode="decimal" autocomplete="off"
                       placeholder="0.00" value="${fn:escapeXml(form.precio)}">
                <p class="campo__error" data-error-de="precio"><ui:icono nombre="alerta" tam="14"/><span><c:out value="${errores.precio}"/></span></p>
            </div>
            <c:choose>
                <c:when test="${editando}">
                    <%-- Editar no cambia el stock: se muestra de solo lectura y se ajusta desde el inventario --%>
                    <div class="campo">
                        <label for="stock">Stock actual</label>
                        <input id="stock" type="text" readonly class="solo-lectura" value="${fn:escapeXml(form.stock)}">
                        <p class="campo__ayuda">
                            El stock no se edita aquí.
                            <c:if test="${sessionScope.usuarioObj.puedeAjustarStock}">
                                <c:url var="urlInventario" value="/admin">
                                    <c:param name="accion" value="inventario"/>
                                    <c:param name="codigo" value="${form.codigo}"/>
                                </c:url>
                                <a href="${urlInventario}">Ajustar en inventario</a>
                            </c:if>
                        </p>
                    </div>
                </c:when>
                <c:otherwise>
                    <div class="campo ${not empty errores.stock ? 'campo--error' : ''}">
                        <label for="stock">Stock inicial *</label>
                        <input id="stock" name="stock" type="text" inputmode="numeric" autocomplete="off"
                               placeholder="0" value="${fn:escapeXml(form.stock)}">
                        <p class="campo__error" data-error-de="stock"><ui:icono nombre="alerta" tam="14"/><span><c:out value="${errores.stock}"/></span></p>
                    </div>
                </c:otherwise>
            </c:choose>
        </div>

        <c:if test="${editando}">
            <p class="nota con-icono"><ui:icono nombre="candado" tam="14"/> El código identifica al producto y no puede modificarse.</p>
        </c:if>

        <hr class="divisor">

        <div class="fila-espaciada formulario__acciones">
            <span class="mono suave pequeno">POST /productos?accion=${editando ? 'actualizar' : 'registrar'}</span>
            <div class="grupo-botones">
                <a class="boton boton--secundario" href="${pageContext.request.contextPath}/productos?accion=listar">Cancelar</a>
                <button type="submit" class="boton boton--primario">
                    <ui:icono nombre="ok" tam="18"/> ${editando ? 'Actualizar producto' : 'Guardar producto'}
                </button>
            </div>
        </div>
    </form>

    <aside class="columna-lateral">
        <c:choose>
            <c:when test="${editando}">
                <div class="tarjeta vista-previa">
                    <p class="etiqueta">Vista previa</p>
                    <div class="fila-espaciada">
                        <span class="mono acento"><c:out value="${form.codigo}"/></span>
                        <span class="insignia ${form.stock eq '0' ? 'insignia--error' : 'insignia--ok'}" data-previa="estado"><c:out value="${form.estado}"/></span>
                    </div>
                    <h3 class="vista-previa__nombre" data-previa="nombre"><c:out value="${form.nombre}"/></h3>
                    <hr class="divisor">
                    <dl class="lista-datos">
                        <div><dt>Categoría</dt><dd data-previa="categoria"><c:out value="${form.categoria}"/></dd></div>
                        <div><dt>Precio unitario</dt><dd class="mono" data-previa="precio">S/ <c:out value="${form.precio}"/></dd></div>
                        <div><dt>Stock</dt><dd class="mono" data-previa="stock"><c:out value="${form.stock}"/> unidades</dd></div>
                    </dl>
                    <div class="caja-valor">
                        <span class="nota">Valor en inventario</span>
                        <strong class="cifra" data-previa="valor">—</strong>
                    </div>
                </div>
            </c:when>
            <c:otherwise>
                <c:set var="intentado" value="${not empty errores}"/>
                <div class="tarjeta">
                    <div class="tarjeta__encabezado">
                        <h2 class="tarjeta__titulo">Reglas de validación</h2>
                        <p class="nota">Se verifican en el navegador y en el servlet.</p>
                    </div>
                    <ul class="reglas" data-reglas>
                        <c:set var="errCod" value="${errores.codigo}"/>
                        <li data-regla="codigo-obligatorio" class="${intentado ? (fn:contains(errCod, 'obligatorio') ? 'mal' : 'bien') : ''}"><span class="reglas__icono"></span>Código obligatorio</li>
                        <li data-regla="codigo-unico" class="${intentado ? (fn:contains(errCod, 'registrado') ? 'mal' : 'bien') : ''}"><span class="reglas__icono"></span>Código no duplicado</li>
                        <li data-regla="nombre" class="${intentado ? (empty errores.nombre ? 'bien' : 'mal') : ''}"><span class="reglas__icono"></span>Nombre obligatorio</li>
                        <li data-regla="precio" class="${intentado ? (empty errores.precio ? 'bien' : 'mal') : ''}"><span class="reglas__icono"></span>Precio mayor que cero</li>
                        <li data-regla="stock" class="${intentado ? (empty errores.stock ? 'bien' : 'mal') : ''}"><span class="reglas__icono"></span>Stock mayor o igual a cero</li>
                        <li data-regla="categoria" class="${intentado ? (empty errores.categoria ? 'bien' : 'mal') : ''}"><span class="reglas__icono"></span>Categoría obligatoria</li>
                    </ul>
                </div>
                <div class="tarjeta">
                    <h2 class="tarjeta__titulo">Categorías</h2>
                    <div class="chips" data-chips-categoria>
                        <c:forEach var="cat" items="${categorias}">
                            <button type="button" class="chip-texto ${cat eq form.categoria ? 'activo' : ''}" data-valor="${cat}"><c:out value="${cat}"/></button>
                        </c:forEach>
                    </div>
                </div>
            </c:otherwise>
        </c:choose>
    </aside>
</div>

<jsp:include page="/includes/footer.jsp"/>
