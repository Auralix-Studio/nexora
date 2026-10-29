<%@ tag description="Catálogo de productos reutilizable" pageEncoding="UTF-8" %>
<%@ attribute name="productos" required="true" type="java.util.List" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<section class="rejilla-tienda">
    <c:forEach var="p" items="${productos}">
        <article class="tarjeta-producto" data-revelar data-inclinar>
            <ui:fotoProducto producto="${p}"/>
            <div class="tarjeta-producto__cuerpo">
                <div class="tarjeta-producto__meta">
                    <span><c:out value="${p.categoria}"/></span>
                    <span><c:out value="${p.codigo}"/></span>
                </div>
                <h3 class="tarjeta-producto__nombre" title="<c:out value='${p.nombre}'/>">
                    <c:out value="${p.nombre}"/>
                </h3>
                <div class="tarjeta-producto__precios">
                    <div>
                        <span class="tarjeta-producto__precio-actual"><c:out value="${p.precioTexto}"/></span>
                    </div>
                </div>
                <c:choose>
                    <c:when test="${p.disponible}">
                        <c:url var="pedidoUrl" value="/productos"><c:param name="accion" value="comprar"/><c:param name="codigo" value="${p.codigo}"/></c:url>
                        <a href="${pedidoUrl}" class="tarjeta-producto__boton-comprar tarjeta-producto__boton-comprar--activo">
                            <ui:icono nombre="caja" tam="18"/> Comprar ahora
                        </a>
                    </c:when>
                    <c:otherwise>
                        <button type="button" class="tarjeta-producto__boton-comprar tarjeta-producto__boton-comprar--agotado" disabled>
                            <ui:icono nombre="alerta" tam="18"/> Agotado
                        </button>
                    </c:otherwise>
                </c:choose>
            </div>
        </article>
    </c:forEach>
    <c:if test="${empty productos}">
        <p class="nota">No hay productos en exhibición en este momento.</p>
    </c:if>
</section>
