<%-- Vista: reporte de productos disponibles (stock > 0) --%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:set var="titulo" value="Productos disponibles" scope="request"/>
<c:set var="ruta" value="Reportes / Productos disponibles" scope="request"/>
<c:set var="menu" value="disponibles" scope="request"/>
<jsp:include page="/includes/header.jsp"/>

<div class="banda banda--ok">
    <span class="chip chip--blanco chip--grande"><ui:icono nombre="check" tam="22"/></span>
    <div class="banda__texto">
        <strong>${disponibles} de ${total} productos tienen stock disponible</strong>
        <span>Filtro aplicado: stock &gt; 0. Los productos agotados no se incluyen en este reporte.</span>
    </div>
    <a class="boton boton--secundario" href="${pageContext.request.contextPath}/reportes?accion=resumen">
        <ui:icono nombre="grafico" tam="18"/> Ver resumen
    </a>
</div>

<ui:tablaProductos productos="${productos}" acciones="ninguna"
                   pieIzquierda="${disponibles} productos disponibles"
                   pieDerecha="Stock total: ${stockTotal} unidades · Valor: ${valorTotal}"
                   vacio="No hay productos con stock disponible."/>

<jsp:include page="/includes/footer.jsp"/>
