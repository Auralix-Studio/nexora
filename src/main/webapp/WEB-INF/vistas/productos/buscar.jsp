<%--
  Vista: búsqueda de productos por código, nombre o categoría.
  Recibe: criterio, valor, categorias, resultados (solo si se buscó), total
--%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:set var="titulo" value="Buscar producto" scope="request"/>
<c:set var="ruta" value="Productos / Buscar" scope="request"/>
<c:set var="menu" value="buscar" scope="request"/>
<jsp:include page="/includes/header.jsp"/>
<c:set var="porCategoria" value="${criterio eq 'categoria'}"/>

<form class="tarjeta formulario-busqueda" method="get" action="${pageContext.request.contextPath}/productos" data-busqueda>
    <input type="hidden" name="accion" value="buscar">
    <div class="tarjeta__encabezado">
        <h2 class="tarjeta__titulo">Criterio de búsqueda</h2>
        <p class="nota">Elija cómo buscar y escriba o seleccione el valor.</p>
    </div>
    <div class="fila-busqueda">
        <fieldset class="campo">
            <legend>Buscar por</legend>
            <div class="segmentos">
                <label><input type="radio" name="criterio" value="codigo" ${criterio eq 'codigo' ? 'checked' : ''}><span>Código</span></label>
                <label><input type="radio" name="criterio" value="nombre" ${criterio eq 'nombre' ? 'checked' : ''}><span>Nombre</span></label>
                <label><input type="radio" name="criterio" value="categoria" ${porCategoria ? 'checked' : ''}><span>Categoría</span></label>
            </div>
        </fieldset>
        <div class="campo campo--crece">
            <label for="valor-texto" data-etiqueta-valor>${criterio eq 'codigo' ? 'Código' : (porCategoria ? 'Categoría' : 'Nombre')}</label>
            <input id="valor-texto" type="search" name="valor" autocomplete="off"
                   placeholder="${criterio eq 'codigo' ? 'Ej. P001' : 'Ej. Logitech'}"
                   value="${porCategoria ? '' : fn:escapeXml(valor)}"
                   ${porCategoria ? 'hidden disabled' : ''} data-valor-texto>
            <div class="selector" ${porCategoria ? '' : 'hidden'} data-valor-categoria>
                <select name="valor" aria-label="Categoría" ${porCategoria ? '' : 'disabled'}>
                    <c:forEach var="cat" items="${categorias}">
                        <option value="${cat}" ${porCategoria and cat eq valor ? 'selected' : ''}><c:out value="${cat}"/></option>
                    </c:forEach>
                </select>
                <ui:icono nombre="flecha-abajo" tam="18"/>
            </div>
        </div>
        <button type="submit" class="boton boton--primario"><ui:icono nombre="buscar" tam="18"/> Buscar</button>
    </div>
</form>

<c:choose>
    <c:when test="${not empty resultados or not empty valor}">
        <div class="barra-herramientas">
            <div>
                <h2 class="subtitulo">${fn:length(resultados)} ${fn:length(resultados) eq 1 ? 'resultado' : 'resultados'}</h2>
                <p class="nota mono"><c:out value="${criterio}"/> = "<c:out value="${valor}"/>"</p>
            </div>
            <a class="boton boton--fantasma" href="${pageContext.request.contextPath}/productos?accion=buscar">
                <ui:icono nombre="cerrar" tam="18"/> Limpiar búsqueda
            </a>
        </div>
        <c:choose>
            <c:when test="${empty sessionScope.rol or sessionScope.rol eq 'Cliente'}">
                <ui:catalogoProductos productos="${resultados}"/>
            </c:when>
            <c:otherwise>
                <ui:tablaProductos productos="${resultados}"
                                   pieIzquierda="${fn:length(resultados)} coincidencias de ${total} productos"
                                   pieDerecha="Resultados de búsqueda"
                                   vacio="No se encontraron productos con ese criterio."/>
            </c:otherwise>
        </c:choose>
    </c:when>
    <c:otherwise>
        <div class="ayudas">
            <div class="ayuda"><ui:icono nombre="codigo" tam="18"/><div><strong>Por código</strong><span>Coincidencia exacta, por ejemplo P001.</span></div></div>
            <div class="ayuda"><ui:icono nombre="buscar" tam="18"/><div><strong>Por nombre</strong><span>Contiene el texto, sin distinguir mayúsculas.</span></div></div>
            <div class="ayuda"><ui:icono nombre="filtro" tam="18"/><div><strong>Por categoría</strong><span>Seleccione una categoría de la lista.</span></div></div>
        </div>
    </c:otherwise>
</c:choose>

<jsp:include page="/includes/footer.jsp"/>
