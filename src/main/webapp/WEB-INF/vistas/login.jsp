<%-- Vista: Ventana de Ingreso · Sistema NEXORA (Semana 06) --%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Ventana de Ingreso · NEXORA</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:opsz,wght@12..96,600&family=IBM+Plex+Mono:wght@400;500&family=Instrument+Sans:wght@400;500;600&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/estilos.css">
</head>
<body class="pagina-acceso">
<main class="acceso-pantalla">
    <section class="acceso-formulario">
        <div class="tarjeta tarjeta-acceso">
            <a class="acceso-marca" href="${pageContext.request.contextPath}/inicio"><span class="marca__logo"><ui:icono nombre="capas" tam="22"/></span><strong>NEXORA</strong></a>
            <form method="post" action="${pageContext.request.contextPath}/login" data-formulario-login novalidate class="formulario-acceso">
                <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
                <div class="fila-espaciada">
                    <span class="chip chip--grande"><ui:icono nombre="candado" tam="22"/></span>
                    <a href="${pageContext.request.contextPath}/inicio" class="mono pequeno suave" style="text-decoration: underline;">
                        ← Volver a la tienda
                    </a>
                </div>
                <div>
                    <h1 class="tarjeta-acceso__titulo">Iniciar sesión</h1>
                    <p class="nota">Ingresa tu usuario y contraseña para continuar.</p>
                </div>
                <c:if test="${not empty sessionScope.flashMensaje}">
                    <div class="alerta alerta--${sessionScope.flashTipo eq 'ok' ? 'ok' : 'error'}">
                        <ui:icono nombre="${sessionScope.flashTipo eq 'ok' ? 'check' : 'alerta'}"/>
                        <div><strong><c:out value="${sessionScope.flashMensaje}"/></strong></div>
                    </div>
                    <c:remove var="flashMensaje" scope="session"/>
                    <c:remove var="flashTipo" scope="session"/>
                </c:if>
                <c:if test="${not empty error}">
                    <div class="alerta alerta--error"><ui:icono nombre="alerta"/><div><strong><c:out value="${error}"/></strong></div></div>
                </c:if>

                <div class="campo">
                    <label for="usuario">Usuario</label>
                    <input id="usuario" name="usuario" type="text" autocomplete="username" placeholder="Ingresa tu usuario" required autofocus
                           value="${fn:escapeXml(usuarioIngresado)}">
                    <p class="campo__error" data-error-de="usuario"><ui:icono nombre="alerta" tam="14"/><span></span></p>
                </div>
                <div class="campo">
                    <label for="clave">Contraseña</label>
                    <input id="clave" name="clave" type="password" autocomplete="current-password" placeholder="••••••••" required>
                    <p class="campo__error" data-error-de="clave"><ui:icono nombre="alerta" tam="14"/><span></span></p>
                </div>
                <button type="submit" class="boton boton--primario boton--bloque">Iniciar Sesión</button>
            </form>

            <hr class="divisor">

            <details ${mostrarRegistroCliente ? 'open' : ''} class="registro-cliente">
                <summary>
                    ¿No tienes cuenta? Regístrate gratis aquí
                </summary>
                <form method="post" action="${pageContext.request.contextPath}/login" class="formulario-acceso formulario-acceso--registro" data-formulario-registro>
                    <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
                    <input type="hidden" name="accion" value="registrarCliente">

                    <div class="campo">
                        <label for="nombreCompleto">Nombres y Apellidos completos *</label>
                        <input id="nombreCompleto" name="nombreCompleto" type="text" placeholder="Ej. Carlos Mendoza Vargas" required value="${fn:escapeXml(regNombre)}">
                        <p class="campo__error" data-error-de="nombreCompleto"><ui:icono nombre="alerta" tam="14"/><span></span></p>
                    </div>

                    <div class="rejilla-campos">
                        <div class="campo">
                            <label for="dni">DNI (8 dígitos) *</label>
                            <input id="dni" name="dni" type="text" inputmode="numeric" maxlength="8" placeholder="Ej. 74839201" required value="${fn:escapeXml(regDni)}">
                            <p class="campo__error" data-error-de="dni"><ui:icono nombre="alerta" tam="14"/><span></span></p>
                        </div>
                        <div class="campo">
                            <label for="telefono">Celular / WhatsApp *</label>
                            <input id="telefono" name="telefono" type="tel" inputmode="tel" maxlength="9" placeholder="Ej. 987654321" required value="${fn:escapeXml(regTelefono)}">
                            <p class="campo__error" data-error-de="telefono"><ui:icono nombre="alerta" tam="14"/><span></span></p>
                        </div>
                    </div>

                    <div class="campo">
                        <label for="correo">Correo electrónico *</label>
                        <input id="correo" name="correo" type="email" autocomplete="email" placeholder="ejemplo@correo.com" required value="${fn:escapeXml(regCorreo)}">
                        <p class="campo__error" data-error-de="correo"><ui:icono nombre="alerta" tam="14"/><span></span></p>
                    </div>

                    <div class="campo">
                        <label for="nuevoUsuario">Nombre de usuario *</label>
                        <input id="nuevoUsuario" name="nuevoUsuario" type="text" autocomplete="username" maxlength="40" placeholder="Ej. cmendoza" required value="${fn:escapeXml(regUsuario)}">
                        <p class="campo__error" data-error-de="nuevoUsuario"><ui:icono nombre="alerta" tam="14"/><span></span></p>
                    </div>

                    <div class="rejilla-campos">
                        <div class="campo">
                            <label for="nuevaClave">Contraseña *</label>
                            <input id="nuevaClave" name="nuevaClave" type="password" autocomplete="new-password" minlength="8" maxlength="100" placeholder="Mínimo 8 caracteres, letras y números" required>
                            <p class="campo__error" data-error-de="nuevaClave"><ui:icono nombre="alerta" tam="14"/><span></span></p>
                        </div>
                        <div class="campo">
                            <label for="confirmarClave">Confirmar contraseña *</label>
                            <input id="confirmarClave" name="confirmarClave" type="password" autocomplete="new-password" minlength="8" maxlength="100" placeholder="Repita contraseña" required>
                            <p class="campo__error" data-error-de="confirmarClave"><ui:icono nombre="alerta" tam="14"/><span></span></p>
                        </div>
                    </div>

                    <button type="submit" class="boton boton--secundario boton--bloque">Crear mi cuenta</button>
                    <p class="nota nota-pago" style="margin-top:0.5rem; text-align:center;"><ui:icono nombre="escudo" tam="14"/> Tus datos están protegidos y se utilizarán para gestionar tus compras y envíos.</p>
                </form>
            </details>

        </div>
    </section>
</main>
<script src="${pageContext.request.contextPath}/js/validaciones.js"></script>
</body>
</html>
