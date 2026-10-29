<%--
  Vista: registro de auditoría (solo Administrador).
  Recibe: registros (List<RegistroAuditoria>, hasta 300), acciones (List<String>), fUsuario, fAccion, fDesde, fHasta
--%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:set var="titulo" value="Auditoría" scope="request"/>
<c:set var="ruta" value="Sistema / Auditoría" scope="request"/>
<c:set var="menu" value="admin-auditoria" scope="request"/>
<jsp:include page="/includes/header.jsp"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="hayFiltros" value="${not empty fUsuario or not empty fAccion or not empty fDesde or not empty fHasta}"/>

<form class="tarjeta filtros" method="get" action="${ctx}/admin">
    <input type="hidden" name="accion" value="auditoria">
    <div class="tarjeta__encabezado">
        <h2 class="tarjeta__titulo">Filtrar registros</h2>
        <p class="nota">Quién hizo qué y cuándo. Se muestran como máximo los 300 eventos más recientes.</p>
    </div>
    <div class="fila-busqueda">
        <div class="campo campo--crece">
            <label for="a-usuario">Usuario</label>
            <input id="a-usuario" type="search" name="usuario" maxlength="40" autocomplete="off"
                   placeholder="Ej. admin" value="${fn:escapeXml(fUsuario)}">
        </div>
        <div class="campo campo--seleccion">
            <label for="a-tipo">Acción</label>
            <div class="selector">
                <select id="a-tipo" name="tipo">
                    <option value="">Todas</option>
                    <c:forEach var="ac" items="${acciones}">
                        <option value="${fn:escapeXml(ac)}" ${ac eq fAccion ? 'selected' : ''}><c:out value="${ac}"/></option>
                    </c:forEach>
                </select>
                <ui:icono nombre="flecha-abajo" tam="18"/>
            </div>
        </div>
        <div class="campo campo--fecha">
            <label for="a-desde">Desde</label>
            <input id="a-desde" type="date" name="desde" value="${fn:escapeXml(fDesde)}">
        </div>
        <div class="campo campo--fecha">
            <label for="a-hasta">Hasta</label>
            <input id="a-hasta" type="date" name="hasta" value="${fn:escapeXml(fHasta)}">
        </div>
        <div class="filtros__acciones">
            <button type="submit" class="boton boton--primario"><ui:icono nombre="filtro" tam="18"/> Filtrar</button>
            <c:if test="${hayFiltros}">
                <a class="boton boton--secundario" href="${ctx}/admin?accion=auditoria">Limpiar</a>
            </c:if>
        </div>
    </div>
</form>

<div class="tabla-contenedor">
    <table class="tabla">
        <thead>
        <tr>
            <th>Fecha</th>
            <th>Usuario</th>
            <th>Rol</th>
            <th>Acción</th>
            <th>Entidad</th>
            <th>Detalle</th>
            <th>IP</th>
        </tr>
        </thead>
        <tbody>
        <c:forEach var="reg" items="${registros}">
            <tr class="${reg.alerta ? 'fila-alerta' : ''}">
                <td data-titulo="Fecha" class="mono suave"><c:out value="${reg.fecha}"/></td>
                <td data-titulo="Usuario" class="mono fuerte"><c:out value="${reg.usuario}"/></td>
                <td data-titulo="Rol"><c:out value="${reg.rol}"/></td>
                <td data-titulo="Acción"><span class="insignia insignia--ajusta ${reg.alerta ? 'insignia--error' : 'insignia--neutro'}"><c:out value="${reg.accion}"/></span></td>
                <td data-titulo="Entidad" class="suave"><c:out value="${reg.entidad}"/></td>
                <td data-titulo="Detalle" class="celda-texto"><c:out value="${reg.detalle}"/></td>
                <td data-titulo="IP" class="mono pequeno suave"><c:out value="${reg.ip}"/></td>
            </tr>
        </c:forEach>
        <c:if test="${empty registros}">
            <tr class="fila-vacia">
                <td colspan="7">${hayFiltros ? 'Ningún registro coincide con los filtros aplicados.' : 'Aún no hay eventos registrados.'}</td>
            </tr>
        </c:if>
        </tbody>
    </table>
    <div class="tabla-pie">
        <span><c:out value="${fn:length(registros)}"/>&#32;${fn:length(registros) eq 1 ? 'registro' : 'registros'}</span>
        <span>Los eventos con alerta aparecen resaltados</span>
    </div>
</div>

<jsp:include page="/includes/footer.jsp"/>
