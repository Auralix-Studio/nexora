<%@ tag description="Lista de barras horizontales para filas de reporte (FilaReporte)" pageEncoding="UTF-8" body-content="empty" %>
<%@ attribute name="filas" required="true" type="java.util.List" description="List de FilaReporte" %>
<%@ attribute name="unidad" required="false" description="texto que acompaña a la cantidad (ej. pedidos, uds.)" %>
<%@ attribute name="vacio" required="false" description="mensaje cuando no hay filas" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:choose>
    <c:when test="${empty filas}">
        <p class="nota"><c:out value="${empty vacio ? 'Sin ventas en el período seleccionado.' : vacio}"/></p>
    </c:when>
    <c:otherwise>
        <ul class="barras barras--admin">
            <c:forEach var="fila" items="${filas}" varStatus="st">
                <li>
                    <span class="barras__nombre"><c:out value="${fila.etiqueta}"/></span>
                    <span class="barras__pista"><span class="barras__valor ${st.first ? 'barras__valor--principal' : ''}" style="width: ${fila.anchoBarra}%"></span></span>
                    <span class="barras__monto mono">
                        <span><c:out value="${fila.montoTexto}"/> <span class="suave"><c:out value="${fila.porcentajeTexto}"/></span></span>
                        <span class="barras__detalle"><c:out value="${fila.cantidad}"/>&#32;<c:out value="${unidad}"/></span>
                    </span>
                </li>
            </c:forEach>
        </ul>
    </c:otherwise>
</c:choose>
