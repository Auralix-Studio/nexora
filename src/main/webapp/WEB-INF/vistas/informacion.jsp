<%-- Vista: módulo Información del sistema NEXORA --%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:set var="titulo" value="${not empty sessionScope.usuario and sessionScope.rol ne 'Cliente' ? 'Información del sistema' : 'Acerca de NEXORA'}" scope="request"/>
<c:set var="ruta" value="Información" scope="request"/>
<c:set var="menu" value="informacion" scope="request"/>
<jsp:include page="/includes/header.jsp"/>

<c:choose>
<c:when test="${not empty sessionScope.usuario and sessionScope.rol ne 'Cliente'}">
<div class="dos-columnas">
    <div class="tarjeta tarjeta--amplia">
        <div class="tarjeta__encabezado">
            <h2 class="tarjeta__titulo">NEXORA · Equipamiento Tecnológico y Soluciones Digitales</h2>
            <p class="nota">Plataforma tecnológica orientada a la comercialización de equipamiento digital y administración centralizada de operaciones comerciales.</p>
        </div>
        <dl class="ficha">
            <div><dt>Nombre comercial</dt><dd><strong>NEXORA</strong> · Soluciones Digitales</dd></div>
            <div><dt>Persistencia de datos</dt><dd>Microsoft SQL Server · Base de datos centralizada</dd></div>
            <div><dt>Plataforma tecnológica</dt><dd>Java 17 · Jakarta EE · JDBC</dd></div>
            <div><dt>Diseño de interfaz</dt><dd>JSP · Hojas de estilo CSS3 · JavaScript</dd></div>
            <div><dt>Seguridad y perfiles</dt><dd>Acceso por credenciales con detección automática de perfil</dd></div>
            <div><dt>Perfiles del sistema</dt><dd>Cliente, Administrador, Almacén y Consulta</dd></div>
            <div><dt>Arquitectura de software</dt><dd>Modelo Vista Controlador con capa de persistencia desacoplada</dd></div>
            <div><dt>Servidor de aplicaciones</dt><dd>Apache Tomcat 11</dd></div>
            <div><dt>Estado del servicio</dt><dd><c:out value="${bdEstado}"/></dd></div>
            <div><dt>Sesión activa actual</dt><dd><strong><c:out value="${sessionScope.rol}"/></strong> · Usuario: <c:out value="${sessionScope.usuario}"/></dd></div>
        </dl>
        <div class="curso">
            <span class="chip chip--solido chip--grande"><ui:icono nombre="info" tam="22"/></span>
            <div>
                <strong>Desarrollo de Aplicaciones Web I</strong>
                <span>Universidad Peruana Los Andes · Facultad de Ingeniería</span>
                <span class="mono pequeno">2026</span>
            </div>
        </div>
    </div>

    <div class="tarjeta columna-lateral">
        <div class="tarjeta__encabezado">
            <h2 class="tarjeta__titulo">Arquitectura por Capas</h2>
            <p class="nota">Separación lógica de responsabilidades en la solución tecnológica.</p>
        </div>
        <div class="capas">
            <div class="capa">
                <div class="capa__cabecera"><span class="chip"><ui:icono nombre="capas" tam="18"/></span><div><strong>Capa de Presentación</strong><span>Vistas web y componentes dinámicos</span></div></div>
                <ul><li>Formulario de ingreso y registro de cliente</li><li>Catálogo interactivo y pedidos en línea</li><li>Paneles de control y reportes de existencias</li></ul>
            </div>
            <p class="capa__flecha"><ui:icono nombre="flecha-abajo" tam="16"/> Peticiones HTTP y filtros de autenticación</p>
            <div class="capa capa--destacada">
                <div class="capa__cabecera"><span class="chip chip--solido"><ui:icono nombre="codigo" tam="18"/></span><div><strong>Capa de Control</strong><span>Servlets y gestión de sesiones</span></div></div>
                <ul><li>Controlador de inicio de sesión y autenticación</li><li>Controlador de catálogo, productos y compras</li><li>Controlador de reportes e indicadores</li></ul>
            </div>
            <p class="capa__flecha"><ui:icono nombre="flecha-abajo" tam="16"/> Conexión JDBC y lógica de negocio</p>
            <div class="capa">
                <div class="capa__cabecera"><span class="chip"><ui:icono nombre="caja" tam="18"/></span><div><strong>Capa de Negocio y Datos</strong><span>Entidades y base de datos</span></div></div>
                <ul><li>Modelos de producto, pedido y usuario</li><li>Servicios de cálculo y reglas comerciales</li><li>Conexión SQL Server con respaldo resiliente</li></ul>
            </div>
        </div>
    </div>
</div>

<c:if test="${sessionScope.rol eq 'Administrador'}">
<div class="tarjeta">
    <div class="tarjeta__encabezado">
        <h2 class="tarjeta__titulo">Perfiles de Acceso y Funciones en NEXORA</h2>
        <p class="nota">Permisos configurados en la plataforma según la responsabilidad de cada perfil.</p>
    </div>
    <div class="tabla-contenedor">
        <table class="tabla">
            <thead>
            <tr>
                <th>Usuario</th>
                <th>Nombre Completo</th>
                <th>Correo Electrónico</th>
                <th>Perfil Asignado</th>
                <th>Funciones Principales</th>
            </tr>
            </thead>
            <tbody>
            <c:forEach var="u" items="${usuariosSistema}">
                <tr class="${u.username eq sessionScope.usuario ? 'resaltada' : ''}">
                    <td class="mono fuerte"><c:out value="${u.username}"/></td>
                    <td class="fuerte"><c:out value="${u.nombreCompleto}"/></td>
                    <td class="mono suave"><c:out value="${u.correo}"/></td>
                    <td><span class="insignia ${u.administrador ? 'insignia--ok' : 'insignia--rol'}"><c:out value="${u.rol}"/></span></td>
                    <td class="suave"><c:out value="${u.descripcionPermisos}"/></td>
                </tr>
            </c:forEach>
            </tbody>
        </table>
    </div>
</div>

</c:if>
</c:when>
<c:otherwise>
    <section class="tarjeta">
        <div class="tarjeta__encabezado"><h2 class="tarjeta__titulo">Tecnología para tus proyectos</h2><p class="nota">Encuentra computadoras, monitores, accesorios y equipos de conectividad en NEXORA.</p></div>
        <dl class="lista-datos">
            <div><dt>Catálogo</dt><dd>Consulta precios y stock disponible</dd></div>
            <div><dt>Tu cuenta</dt><dd>Inicia sesión para registrar pedidos</dd></div>
            <div><dt>Mis pedidos</dt><dd>Revisa los productos y cantidades solicitadas</dd></div>
        </dl>
        <div class="grupo-botones"><a class="boton boton--primario" href="${pageContext.request.contextPath}/productos?accion=listar">Explorar productos</a></div>
    </section>
</c:otherwise>
</c:choose>
<jsp:include page="/includes/footer.jsp"/>
