<%@ tag description="Insignia con el estado de un pedido (color según el estado)" pageEncoding="UTF-8" body-content="empty" %>
<%@ attribute name="estado" required="true" description="Confirmado, En preparación, Enviado, Entregado o Anulado" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:choose>
    <c:when test="${estado eq 'En preparación'}"><c:set var="clase" value="insignia--preparacion"/></c:when>
    <c:when test="${estado eq 'Enviado'}"><c:set var="clase" value="insignia--enviado"/></c:when>
    <c:when test="${estado eq 'Entregado'}"><c:set var="clase" value="insignia--entregado"/></c:when>
    <c:when test="${estado eq 'Anulado'}"><c:set var="clase" value="insignia--anulado"/></c:when>
    <c:otherwise><c:set var="clase" value="insignia--confirmado"/></c:otherwise>
</c:choose>
<span class="insignia ${clase}"><c:out value="${estado}"/></span>
