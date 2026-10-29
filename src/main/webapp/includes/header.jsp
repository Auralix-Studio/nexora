<%--
  Cabecera común: <head>, menú lateral y barra superior.
  Cada vista define antes, en ámbito request:
    titulo -> título grande de la barra superior
    ruta   -> migas de pan (ej. "Productos / Listar")
    menu   -> id del ítem activo del menú
--%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:set var="ctxRecursos" value="${pageContext.request.contextPath}"/>
<c:set var="verRecursos" value="${empty appVersion ? 'dev' : appVersion}"/>
<c:set var="descripcionPagina" value="${empty descripcion ? 'NEXORA: tienda en línea de equipamiento tecnológico. Laptops, monitores y accesorios con pago seguro por tarjeta o Yape y comprobante electrónico.' : descripcion}"/>
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><c:out value="${titulo}"/> · NEXORA</title>
    <meta name="description" content="<c:out value='${descripcionPagina}'/>">
    <meta name="theme-color" content="#14211B">
    <meta property="og:type" content="website">
    <meta property="og:site_name" content="NEXORA">
    <meta property="og:locale" content="es_PE">
    <meta property="og:title" content="<c:out value='${titulo}'/> · NEXORA">
    <meta property="og:description" content="<c:out value='${descripcionPagina}'/>">
    <link rel="icon" type="image/svg+xml" href="${ctxRecursos}/img/favicon.svg">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:opsz,wght@12..96,600&family=IBM+Plex+Mono:wght@400;500&family=Instrument+Sans:wght@400;500;600&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="${ctxRecursos}/css/estilos.css?v=${fn:escapeXml(verRecursos)}">
    <script src="${ctxRecursos}/js/movimiento.js?v=${fn:escapeXml(verRecursos)}"></script>
</head>
<body>
<a class="saltar" href="#contenido">Saltar al contenido</a>
<div class="app">
    <jsp:include page="/includes/menu.jsp"/>
    <div class="principal">
        <c:if test="${modoDemo}">
            <div class="cinta-demo" role="note">Entorno de demostración · pagos simulados, sin cargos reales</div>
        </c:if>
        <header class="barra-superior">
            <div class="barra-superior__titulos">
                <button type="button" class="boton-menu" data-abrir-menu aria-label="Abrir menú" aria-controls="menu-lateral" aria-expanded="false">
                    <ui:icono nombre="menu"/>
                </button>
                <div>
                    <p class="ruta">NEXORA / <c:out value="${ruta}"/></p>
                    <h1 class="titulo-pagina"><c:out value="${titulo}"/></h1>
                </div>
            </div>
            <div class="barra-superior__derecha">
                <span class="pastilla-fecha">
                    <ui:icono nombre="calendario" tam="16"/>
                    <c:out value="${fechaActual}"/>
                </span>
                <span class="divisor-vertical"></span>
                <c:choose>
                    <c:when test="${not empty sessionScope.usuario}">
                        <div class="usuario">
                            <span class="avatar"><c:out value="${fn:toUpperCase(fn:substring(sessionScope.usuario, 0, 2))}"/></span>
                            <div>
                                <strong class="usuario__nombre"><c:out value="${not empty sessionScope.nombreCompleto ? sessionScope.nombreCompleto : sessionScope.usuario}"/></strong>
                                <span class="usuario__rol">Rol: <strong><c:out value="${sessionScope.rol}"/></strong> · Sesión activa</span>
                            </div>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <a class="boton boton--primario" href="${pageContext.request.contextPath}/login">
                            <ui:icono nombre="candado" tam="16"/> Iniciar Sesión
                        </a>
                    </c:otherwise>
                </c:choose>
            </div>
        </header>

        <c:if test="${not empty sessionScope.flashMensaje}">
            <div class="aviso aviso--${sessionScope.flashTipo eq 'ok' ? 'ok' : 'error'}" role="status" aria-live="${sessionScope.flashTipo eq 'ok' ? 'polite' : 'assertive'}" aria-atomic="true" data-aviso>
                <ui:icono nombre="${sessionScope.flashTipo eq 'ok' ? 'check' : 'alerta'}"/>
                <span><c:out value="${sessionScope.flashMensaje}"/></span>
                <button type="button" class="aviso__cerrar" data-cerrar-aviso aria-label="Cerrar">
                    <ui:icono nombre="cerrar" tam="16"/>
                </button>
            </div>
            <c:remove var="flashMensaje" scope="session"/>
            <c:remove var="flashTipo" scope="session"/>
        </c:if>

        <main class="contenido" id="contenido" tabindex="-1">
