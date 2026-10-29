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
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><c:out value="${titulo}"/> · NEXORA</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:opsz,wght@12..96,600&family=IBM+Plex+Mono:wght@400;500&family=Instrument+Sans:wght@400;500;600&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/estilos.css">
</head>
<body>
<div class="app">
    <jsp:include page="/includes/menu.jsp"/>
    <div class="principal">
        <header class="barra-superior">
            <div class="barra-superior__titulos">
                <button type="button" class="boton-menu" data-abrir-menu aria-label="Abrir menú">
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
            <div class="aviso aviso--${sessionScope.flashTipo eq 'ok' ? 'ok' : 'error'}" role="status" data-aviso>
                <ui:icono nombre="${sessionScope.flashTipo eq 'ok' ? 'check' : 'alerta'}"/>
                <span><c:out value="${sessionScope.flashMensaje}"/></span>
                <button type="button" class="aviso__cerrar" data-cerrar-aviso aria-label="Cerrar">
                    <ui:icono nombre="cerrar" tam="16"/>
                </button>
            </div>
            <c:remove var="flashMensaje" scope="session"/>
            <c:remove var="flashTipo" scope="session"/>
        </c:if>

        <main class="contenido">
