<%@ tag description="Insignia con el tipo de un movimiento de stock" pageEncoding="UTF-8" body-content="empty" %>
<%@ attribute name="tipo" required="true" description="Entrada, Salida, Ajuste o Devolución" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:choose>
    <c:when test="${tipo eq 'Entrada'}"><c:set var="clase" value="insignia--mov-entrada"/></c:when>
    <c:when test="${tipo eq 'Salida'}"><c:set var="clase" value="insignia--mov-salida"/></c:when>
    <c:when test="${tipo eq 'Ajuste'}"><c:set var="clase" value="insignia--mov-ajuste"/></c:when>
    <c:otherwise><c:set var="clase" value="insignia--mov-devolucion"/></c:otherwise>
</c:choose>
<span class="insignia ${clase}"><c:out value="${tipo}"/></span>
