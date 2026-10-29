<%--
  Vista: administración de usuarios (solo Administrador).
  Recibe: usuarios (List<Usuario>), roles (List<String>), form (valores previos del alta), errorFormulario
--%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:set var="titulo" value="Usuarios" scope="request"/>
<c:set var="ruta" value="Sistema / Usuarios" scope="request"/>
<c:set var="menu" value="admin-usuarios" scope="request"/>
<jsp:include page="/includes/header.jsp"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>

<details class="plegable" id="nuevo-usuario" ${not empty errorFormulario ? 'open' : ''}>
    <summary><ui:icono nombre="mas" tam="18"/> Nuevo usuario</summary>
    <form data-un-envio class="plegable__cuerpo" method="post" action="${ctx}/admin">
        <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
        <input type="hidden" name="accion" value="crearUsuario">

        <p class="nota">Crea una cuenta de personal o de cliente. La contraseña se guarda cifrada. Los campos con * son obligatorios.</p>

        <c:if test="${not empty errorFormulario}">
            <div class="alerta alerta--error" role="alert">
                <ui:icono nombre="alerta"/>
                <div><strong>No se creó el usuario</strong><p><c:out value="${errorFormulario}"/></p></div>
            </div>
        </c:if>

        <div class="rejilla-campos">
            <div class="campo">
                <label for="nu-username">Usuario *</label>
                <input id="nu-username" type="text" name="username" required minlength="3" maxlength="40"
                       pattern="[a-z0-9._\-]{3,40}" title="De 3 a 40 caracteres: letras minúsculas, números, punto, guion y guion bajo"
                       autocomplete="off" autocapitalize="none" placeholder="ej. maria.lopez" value="${fn:escapeXml(form.username)}">
            </div>
            <div class="campo">
                <label for="nu-nombre">Nombre completo *</label>
                <input id="nu-nombre" type="text" name="nombreCompleto" required maxlength="100"
                       autocomplete="off" placeholder="Nombres y apellidos" value="${fn:escapeXml(form.nombreCompleto)}">
            </div>
            <div class="campo">
                <label for="nu-correo">Correo electrónico *</label>
                <input id="nu-correo" type="email" name="correo" required maxlength="100"
                       autocomplete="off" placeholder="nombre@correo.com" value="${fn:escapeXml(form.correo)}">
            </div>
            <div class="campo">
                <label for="nu-rol">Rol *</label>
                <div class="selector">
                    <select id="nu-rol" name="rol" required>
                        <c:forEach var="r" items="${roles}">
                            <option value="${fn:escapeXml(r)}" ${form.rol eq r ? 'selected' : ''}><c:out value="${r}"/></option>
                        </c:forEach>
                    </select>
                    <ui:icono nombre="flecha-abajo" tam="18"/>
                </div>
            </div>
            <div class="campo campo--completo">
                <label for="nu-clave">Contraseña inicial *</label>
                <input id="nu-clave" type="password" name="clave" required minlength="8" maxlength="100"
                       autocomplete="new-password" placeholder="Mínimo 8 caracteres">
                <p class="campo__ayuda">Al menos 8 caracteres, con letras y números, sin espacios al inicio ni al final.</p>
            </div>
        </div>

        <div>
            <button type="submit" class="boton boton--primario"><ui:icono nombre="registrar" tam="18"/> Crear usuario</button>
        </div>
    </form>
</details>

<div class="barra-herramientas">
    <div>
        <h2 class="subtitulo"><c:out value="${fn:length(usuarios)}"/>&#32;${fn:length(usuarios) eq 1 ? 'usuario registrado' : 'usuarios registrados'}</h2>
        <p class="nota">No puedes desactivar tu propia cuenta ni cambiar tu rol. El sistema siempre conserva un administrador activo.</p>
    </div>
</div>
<div class="tabla-contenedor">
    <table class="tabla">
        <thead>
        <tr>
            <th>Usuario</th>
            <th>Nombre</th>
            <th>Correo</th>
            <th>Rol</th>
            <th>Estado</th>
            <th>Acciones</th>
        </tr>
        </thead>
        <tbody>
        <c:forEach var="u" items="${usuarios}">
            <c:set var="esYo" value="${u.username eq sessionScope.usuario}"/>
            <tr>
                <td data-titulo="Usuario" class="mono fuerte">
                    <c:out value="${u.username}"/>
                    <c:if test="${esYo}"><span class="insignia insignia--neutro">Tú</span></c:if>
                </td>
                <td data-titulo="Nombre"><c:out value="${u.nombreCompleto}"/></td>
                <td data-titulo="Correo" class="celda-texto suave"><c:out value="${u.correo}"/></td>
                <td data-titulo="Rol"><span class="insignia insignia--neutro"><c:out value="${u.rol}"/></span></td>
                <td data-titulo="Estado"><span class="insignia ${u.activo ? 'insignia--ok' : 'insignia--error'}">${u.activo ? 'Activo' : 'Inactivo'}</span></td>
                <td data-titulo="Acciones">
                    <div class="acciones-admin">
                        <c:if test="${not esYo}">
                            <c:choose>
                                <c:when test="${u.activo}">
                                    <form data-un-envio class="form-linea" method="post" action="${ctx}/admin"
                                          data-confirmar="¿Desactivar la cuenta de ${fn:escapeXml(u.username)}? No podrá iniciar sesión hasta que se vuelva a activar.">
                                        <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
                                        <input type="hidden" name="accion" value="estadoUsuario">
                                        <input type="hidden" name="id" value="${u.idUsuario}">
                                        <input type="hidden" name="activo" value="false">
                                        <button type="submit" class="accion accion--peligro">Desactivar</button>
                                    </form>
                                </c:when>
                                <c:otherwise>
                                    <form data-un-envio class="form-linea" method="post" action="${ctx}/admin">
                                        <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
                                        <input type="hidden" name="accion" value="estadoUsuario">
                                        <input type="hidden" name="id" value="${u.idUsuario}">
                                        <input type="hidden" name="activo" value="true">
                                        <button type="submit" class="accion">Activar</button>
                                    </form>
                                </c:otherwise>
                            </c:choose>

                            <form data-un-envio class="form-linea" method="post" action="${ctx}/admin">
                                <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
                                <input type="hidden" name="accion" value="rolUsuario">
                                <input type="hidden" name="id" value="${u.idUsuario}">
                                <select name="rol" aria-label="Rol de ${fn:escapeXml(u.username)}">
                                    <c:forEach var="r" items="${roles}">
                                        <option value="${fn:escapeXml(r)}" ${r eq u.rol ? 'selected' : ''}><c:out value="${r}"/></option>
                                    </c:forEach>
                                </select>
                                <button type="submit" class="accion">Cambiar rol</button>
                            </form>
                        </c:if>

                        <form data-un-envio class="form-linea" method="post" action="${ctx}/admin">
                            <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
                            <input type="hidden" name="accion" value="claveUsuario">
                            <input type="hidden" name="id" value="${u.idUsuario}">
                            <input type="password" name="clave" required minlength="8" maxlength="100" autocomplete="new-password"
                                   placeholder="Nueva contraseña" aria-label="Nueva contraseña de ${fn:escapeXml(u.username)}">
                            <button type="submit" class="accion">Restablecer</button>
                        </form>
                    </div>
                </td>
            </tr>
        </c:forEach>
        <c:if test="${empty usuarios}">
            <tr class="fila-vacia"><td colspan="6">No hay usuarios registrados.</td></tr>
        </c:if>
        </tbody>
    </table>
</div>

<jsp:include page="/includes/footer.jsp"/>
