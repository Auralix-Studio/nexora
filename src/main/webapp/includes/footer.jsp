<%-- Pie común: cierra el contenido y carga el JavaScript --%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="verRecursos" value="${empty appVersion ? 'dev' : appVersion}"/>
        </main>
        <footer class="pie">
            <span><strong>NEXORA</strong> · Equipamiento Tecnológico y Soluciones Digitales</span>
            <span>Universidad Peruana Los Andes · 2026</span>
        </footer>
    </div>
</div>
<script src="${pageContext.request.contextPath}/js/validaciones.js?v=${fn:escapeXml(verRecursos)}"></script>
</body>
</html>
