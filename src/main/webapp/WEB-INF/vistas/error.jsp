<%-- Vista: página de error independiente (403, 404 y errores inesperados). Registrada en web.xml. --%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" isErrorPage="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:set var="codigoError" value="${requestScope['jakarta.servlet.error.status_code']}"/>
<c:choose>
    <c:when test="${codigoError eq 403}">
        <c:set var="tituloError" value="Acceso denegado"/>
        <c:set var="textoError" value="Tu perfil no tiene permiso para ver esta página. Vuelve al inicio o ingresa con otra cuenta."/>
        <c:set var="iconoError" value="candado"/>
    </c:when>
    <c:when test="${codigoError eq 404}">
        <c:set var="tituloError" value="Página no encontrada"/>
        <c:set var="textoError" value="La dirección que buscas no existe o fue movida. Revisa el enlace o vuelve al inicio."/>
        <c:set var="iconoError" value="buscar"/>
    </c:when>
    <c:otherwise>
        <c:set var="tituloError" value="Ocurrió un error inesperado"/>
        <c:set var="textoError" value="No pudimos completar la operación. Inténtalo de nuevo en unos minutos."/>
        <c:set var="iconoError" value="alerta"/>
    </c:otherwise>
</c:choose>
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><c:out value="${tituloError}"/> · NEXORA</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:opsz,wght@12..96,600&family=IBM+Plex+Mono:wght@400;500&family=Instrument+Sans:wght@400;500;600&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/estilos.css">
</head>
<body class="pagina-acceso">
<main class="acceso-pantalla">
    <section class="acceso-formulario">
        <div class="tarjeta tarjeta-acceso pantalla-error">
            <a class="acceso-marca" href="${pageContext.request.contextPath}/inicio"><span class="marca__logo"><ui:icono nombre="capas" tam="22"/></span><strong>NEXORA</strong></a>
            <div class="pantalla-error__cuerpo">
                <span class="chip chip--grande ${codigoError eq 403 or codigoError eq 404 ? 'chip--aviso' : 'chip--error'}"><ui:icono nombre="${iconoError}" tam="22"/></span>
                <c:if test="${not empty codigoError}"><p class="mono pequeno suave">Error <c:out value="${codigoError}"/></p></c:if>
                <h1 class="tarjeta-acceso__titulo"><c:out value="${tituloError}"/></h1>
                <p class="nota"><c:out value="${textoError}"/></p>
            </div>
            <a class="boton boton--primario boton--bloque" href="${pageContext.request.contextPath}/inicio"><ui:icono nombre="inicio" tam="18"/> Volver al inicio</a>
        </div>
    </section>
</main>
</body>
</html>
