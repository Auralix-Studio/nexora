<%@ tag description="Tabla de productos reutilizable" pageEncoding="UTF-8" %>
<%@ attribute name="productos" required="true" type="java.util.List" %>
<%@ attribute name="acciones" required="false" description="todas | editar | eliminar | ninguna" %>
<%@ attribute name="resaltar" required="false" description="código de la fila a resaltar" %>
<%@ attribute name="pieIzquierda" required="false" %>
<%@ attribute name="pieDerecha" required="false" %>
<%@ attribute name="vacio" required="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:set var="esAdmin" value="${sessionScope.rol eq 'Administrador'}"/>
<c:set var="esCliente" value="${sessionScope.rol eq 'Cliente'}"/>
<c:set var="estaAutenticado" value="${not empty sessionScope.usuario}"/>
<c:set var="puedeEditar" value="${esAdmin or sessionScope.rol eq 'Almacén'}"/>
<c:set var="accBase" value="${empty acciones ? (esAdmin ? 'todas' : (puedeEditar ? 'editar' : 'comprar')) : acciones}"/>
<c:set var="acc" value="${not estaAutenticado or esCliente ? 'comprar' : (not puedeEditar ? 'ninguna' : (accBase eq 'todas' and not esAdmin ? 'editar' : accBase))}"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<div class="tabla-contenedor">
    <table class="tabla">
        <thead>
        <tr>
            <th class="col-codigo">Código</th>
            <th>Producto</th>
            <th class="col-categoria">Categoría</th>
            <th class="num">Precio</th>
            <th class="num col-stock">Stock</th>
            <th class="col-estado">Estado</th>
            <c:if test="${acc ne 'ninguna'}">
                <th class="num col-acciones">Acciones<c:if test="${not empty sessionScope.rol and sessionScope.rol ne 'Cliente'}"> · <c:out value="${sessionScope.rol}"/></c:if></th>
            </c:if>
        </tr>
        </thead>
        <tbody>
        <c:forEach var="p" items="${productos}">
            <tr class="${p.codigo eq resaltar ? 'resaltada' : ''}">
                <td class="mono fuerte" data-titulo="Código"><c:out value="${p.codigo}"/></td>
                <td class="fuerte" data-titulo="Producto"><c:out value="${p.nombre}"/></td>
                <td class="suave" data-titulo="Categoría"><c:out value="${p.categoria}"/></td>
                <td class="num mono" data-titulo="Precio"><c:out value="${p.precioTexto}"/></td>
                <td class="num mono ${p.disponible ? '' : 'texto-error'}" data-titulo="Stock"><c:out value="${p.stock}"/></td>
                <td data-titulo="Estado">
                    <span class="insignia ${p.disponible ? 'insignia--ok' : 'insignia--error'}"><c:out value="${p.estado}"/></span>
                </td>
                <c:if test="${acc ne 'ninguna'}">
                    <td class="num" data-titulo="Acciones">
                        <div class="acciones-fila">
                            <c:if test="${acc eq 'comprar'}">
                                <c:choose>
                                    <c:when test="${p.disponible and p.stock gt 0}">
                                        <c:url var="urlComprar" value="/productos"><c:param name="accion" value="comprar"/><c:param name="codigo" value="${p.codigo}"/></c:url>
                                        <a class="accion" href="${urlComprar}"><ui:icono nombre="caja" tam="16"/> Comprar</a>
                                    </c:when>
                                    <c:otherwise>
                                        <span class="mono pequeno tenue">Sin stock</span>
                                    </c:otherwise>
                                </c:choose>
                            </c:if>
                            <c:if test="${acc eq 'todas' or acc eq 'editar'}">
                                <c:url var="urlEditar" value="/productos"><c:param name="accion" value="editar"/><c:param name="codigo" value="${p.codigo}"/></c:url>
                                <a class="accion" href="${urlEditar}"><ui:icono nombre="editar" tam="16"/> Editar</a>
                            </c:if>
                            <c:if test="${esAdmin and (acc eq 'todas' or acc eq 'eliminar')}">
                                <c:url var="urlEliminar" value="/productos"><c:param name="accion" value="eliminar"/><c:param name="codigo" value="${p.codigo}"/></c:url>
                                <a class="accion accion--peligro" href="${urlEliminar}"><ui:icono nombre="eliminar" tam="16"/> Eliminar</a>
                            </c:if>
                        </div>
                    </td>
                </c:if>
            </tr>
        </c:forEach>
        <c:if test="${empty productos}">
            <tr class="fila-vacia">
                <td colspan="${acc ne 'ninguna' ? 7 : 6}"><c:out value="${empty vacio ? 'No hay productos para mostrar.' : vacio}"/></td>
            </tr>
        </c:if>
        </tbody>
    </table>
    <c:if test="${not empty pieIzquierda}">
        <div class="tabla-pie">
            <span><c:out value="${pieIzquierda}"/></span>
            <span><c:out value="${pieDerecha}"/></span>
        </div>
    </c:if>
</div>
