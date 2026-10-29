<%--
  Vista: administración de categorías (solo Administrador).
  Recibe: categorias (List<Categoria>), editar (Categoria en edición o null), form (valores previos), errorFormulario
--%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:set var="titulo" value="Categorías" scope="request"/>
<c:set var="ruta" value="Catálogo / Categorías" scope="request"/>
<c:set var="menu" value="admin-categorias" scope="request"/>
<jsp:include page="/includes/header.jsp"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="editando" value="${not empty editar}"/>
<%-- Tras un error se muestran los valores enviados; si no, los de la categoría en edición --%>
<c:set var="vNombre" value="${not empty form ? form.nombre : editar.nombre}"/>
<c:set var="vDescripcion" value="${not empty form ? form.descripcion : editar.descripcion}"/>

<div class="dos-columnas">
    <div class="columna-principal">
        <div class="barra-herramientas">
            <div>
                <h2 class="subtitulo"><c:out value="${fn:length(categorias)}"/>&#32;${fn:length(categorias) eq 1 ? 'categoría' : 'categorías'}</h2>
                <p class="nota">Una categoría con productos no se puede eliminar; desactívala para ocultarla del catálogo.</p>
            </div>
        </div>
        <div class="tabla-contenedor">
            <table class="tabla">
                <thead>
                <tr>
                    <th>Nombre</th>
                    <th>Descripción</th>
                    <th class="num">Productos</th>
                    <th>Estado</th>
                    <th>Acciones</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="cat" items="${categorias}">
                    <c:url var="urlEditar" value="/admin">
                        <c:param name="accion" value="categorias"/>
                        <c:param name="editar" value="${cat.idCategoria}"/>
                    </c:url>
                    <tr class="${editando and editar.idCategoria eq cat.idCategoria ? 'fila-seleccionada' : ''}">
                        <td data-titulo="Nombre" class="fuerte"><c:out value="${cat.nombre}"/></td>
                        <td data-titulo="Descripción" class="celda-texto suave">
                            <c:choose>
                                <c:when test="${empty cat.descripcion}"><span class="tenue">—</span></c:when>
                                <c:otherwise><c:out value="${cat.descripcion}"/></c:otherwise>
                            </c:choose>
                        </td>
                        <td data-titulo="Productos" class="num mono"><c:out value="${cat.cantidadProductos}"/></td>
                        <td data-titulo="Estado"><span class="insignia ${cat.activo ? 'insignia--ok' : 'insignia--neutro'}">${cat.activo ? 'Activa' : 'Inactiva'}</span></td>
                        <td data-titulo="Acciones">
                            <div class="acciones-fila acciones-fila--ajusta">
                                <a class="accion" href="${urlEditar}"><ui:icono nombre="editar" tam="16"/> Editar</a>

                                <c:choose>
                                    <c:when test="${cat.activo}">
                                        <form data-un-envio class="form-linea" method="post" action="${ctx}/admin"
                                              data-confirmar="¿Desactivar la categoría ${fn:escapeXml(cat.nombre)}? Dejará de ofrecerse para nuevos productos y en los filtros de la tienda. Sus productos siguen a la venta.">
                                            <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
                                            <input type="hidden" name="accion" value="estadoCategoria">
                                            <input type="hidden" name="id" value="${cat.idCategoria}">
                                            <input type="hidden" name="activo" value="false">
                                            <button type="submit" class="accion">Desactivar</button>
                                        </form>
                                    </c:when>
                                    <c:otherwise>
                                        <form data-un-envio class="form-linea" method="post" action="${ctx}/admin">
                                            <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
                                            <input type="hidden" name="accion" value="estadoCategoria">
                                            <input type="hidden" name="id" value="${cat.idCategoria}">
                                            <input type="hidden" name="activo" value="true">
                                            <button type="submit" class="accion">Activar</button>
                                        </form>
                                    </c:otherwise>
                                </c:choose>

                                <c:if test="${cat.eliminable}">
                                    <form data-un-envio class="form-linea" method="post" action="${ctx}/admin"
                                          data-confirmar="¿Eliminar la categoría ${fn:escapeXml(cat.nombre)}? Esta acción no se puede deshacer.">
                                        <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
                                        <input type="hidden" name="accion" value="eliminarCategoria">
                                        <input type="hidden" name="id" value="${cat.idCategoria}">
                                        <button type="submit" class="accion accion--peligro"><ui:icono nombre="eliminar" tam="16"/> Eliminar</button>
                                    </form>
                                </c:if>
                            </div>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty categorias}">
                    <tr class="fila-vacia"><td colspan="5">Aún no hay categorías registradas.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>

    <form data-un-envio class="tarjeta columna-lateral columna-lateral--formulario" method="post" action="${ctx}/admin" id="form-categoria">
        <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
        <input type="hidden" name="accion" value="${editando ? 'editarCategoria' : 'crearCategoria'}">
        <c:if test="${editando}"><input type="hidden" name="id" value="${editar.idCategoria}"></c:if>

        <div class="tarjeta__encabezado">
            <h2 class="tarjeta__titulo">${editando ? 'Editar categoría' : 'Nueva categoría'}</h2>
            <p class="nota">${editando ? 'Los productos de la categoría conservan su relación al renombrarla.' : 'Las categorías activas se ofrecen en los filtros de la tienda y al registrar productos. Una categoría inactiva no afecta a los productos que ya tiene.'}</p>
        </div>

        <c:if test="${not empty errorFormulario}">
            <div class="alerta alerta--error" role="alert">
                <ui:icono nombre="alerta"/>
                <div><strong>No se guardó la categoría</strong><p><c:out value="${errorFormulario}"/></p></div>
            </div>
        </c:if>

        <div class="campo">
            <label for="cat-nombre">Nombre *</label>
            <input id="cat-nombre" type="text" name="nombre" required maxlength="50" autocomplete="off"
                   placeholder="Ej. Audio" value="${fn:escapeXml(vNombre)}">
        </div>
        <div class="campo">
            <label for="cat-descripcion">Descripción</label>
            <textarea id="cat-descripcion" name="descripcion" rows="3" maxlength="150"
                      placeholder="Breve descripción (opcional)"><c:out value="${vDescripcion}"/></textarea>
        </div>

        <button type="submit" class="boton boton--primario boton--bloque">
            <ui:icono nombre="${editando ? 'check' : 'registrar'}" tam="18"/> ${editando ? 'Guardar cambios' : 'Crear categoría'}
        </button>
        <c:if test="${editando}">
            <a class="boton boton--secundario boton--bloque" href="${ctx}/admin?accion=categorias">Cancelar edición</a>
        </c:if>
    </form>
</div>

<jsp:include page="/includes/footer.jsp"/>
