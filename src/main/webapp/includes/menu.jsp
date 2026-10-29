<%-- Menú lateral. El ítem activo se toma de requestScope.menu --%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="esAdmin" value="${sessionScope.rol eq 'Administrador'}"/>
<c:set var="esCliente" value="${sessionScope.rol eq 'Cliente'}"/>
<c:set var="puedeOperar" value="${esAdmin or sessionScope.rol eq 'Almacén'}"/>
<aside class="lateral" id="menu-lateral">
    <a class="marca" href="${ctx}/inicio">
        <span class="marca__logo"><ui:icono nombre="capas" tam="22"/></span>
        <span>
            <strong class="marca__nombre">NEXORA</strong>
            <span class="marca__sub">Tecnología y Soluciones Digitales</span>
        </span>
    </a>

    <nav class="navegacion" aria-label="Menú principal">
        <c:choose>
            <c:when test="${empty sessionScope.usuario}">
                <p class="navegacion__grupo">Tienda Online</p>
                <a class="nav-item ${menu eq 'inicio' ? 'activo' : ''}" href="${ctx}/inicio">
                    <ui:icono nombre="inicio"/> Tienda Online
                </a>
                <a class="nav-item ${(menu eq 'listar' or menu eq 'editar' or menu eq 'eliminar' or menu eq 'comprar') ? 'activo' : ''}" href="${ctx}/productos?accion=listar">
                    <ui:icono nombre="lista"/> Catálogo de productos
                </a>
                <a class="nav-item ${menu eq 'buscar' ? 'activo' : ''}" href="${ctx}/productos?accion=buscar">
                    <ui:icono nombre="buscar"/> Buscar producto
                </a>

                <p class="navegacion__grupo">Información</p>
                <a class="nav-item ${menu eq 'informacion' ? 'activo' : ''}" href="${ctx}/informacion">
                    <ui:icono nombre="info"/> Información de la tienda
                </a>
            </c:when>
            <c:when test="${esCliente}">
                <p class="navegacion__grupo">Tienda Online</p>
                <a class="nav-item ${menu eq 'inicio' ? 'activo' : ''}" href="${ctx}/inicio">
                    <ui:icono nombre="inicio"/> Tienda Online
                </a>

                <p class="navegacion__grupo">Catálogo y Compras</p>
                <a class="nav-item ${menu eq 'listar' ? 'activo' : ''}" href="${ctx}/productos?accion=listar">
                    <ui:icono nombre="lista"/> Catálogo de productos
                </a>
                <a class="nav-item ${menu eq 'pedidos' ? 'activo' : ''}" href="${ctx}/productos?accion=pedidos">
                    <ui:icono nombre="monedas"/> Mis compras y pedidos
                </a>
                <a class="nav-item ${menu eq 'buscar' ? 'activo' : ''}" href="${ctx}/productos?accion=buscar">
                    <ui:icono nombre="buscar"/> Buscar producto
                </a>

                <p class="navegacion__grupo">Atención al Cliente</p>
                <a class="nav-item ${menu eq 'informacion' ? 'activo' : ''}" href="${ctx}/informacion">
                    <ui:icono nombre="info"/> Información de la tienda
                </a>
            </c:when>
            <c:otherwise>
                <p class="navegacion__grupo">Administración</p>
                <a class="nav-item ${menu eq 'admin-panel' ? 'activo' : ''}" href="${ctx}/admin">
                    <ui:icono nombre="panel"/> Panel
                </a>
                <a class="nav-item ${menu eq 'admin-pedidos' ? 'activo' : ''}" href="${ctx}/admin?accion=pedidos">
                    <ui:icono nombre="comprobante"/> Pedidos
                </a>
                <a class="nav-item ${menu eq 'admin-inventario' ? 'activo' : ''}" href="${ctx}/admin?accion=inventario">
                    <ui:icono nombre="caja"/> Inventario y kardex
                </a>
                <a class="nav-item ${menu eq 'admin-ventas' ? 'activo' : ''}" href="${ctx}/admin?accion=ventas">
                    <ui:icono nombre="monedas"/> Ventas
                </a>

                <p class="navegacion__grupo">Catálogo</p>
                <a class="nav-item ${(menu eq 'listar' or menu eq 'editar' or menu eq 'eliminar') ? 'activo' : ''}" href="${ctx}/productos?accion=listar">
                    <ui:icono nombre="lista"/> Productos
                </a>
                <c:if test="${puedeOperar}">
                    <a class="nav-item ${menu eq 'registrar' ? 'activo' : ''}" href="${ctx}/productos?accion=nuevo">
                        <ui:icono nombre="registrar"/> Registrar producto
                    </a>
                </c:if>
                <c:if test="${esAdmin}">
                    <a class="nav-item ${menu eq 'admin-categorias' ? 'activo' : ''}" href="${ctx}/admin?accion=categorias">
                        <ui:icono nombre="etiqueta"/> Categorías
                    </a>
                </c:if>
                <a class="nav-item ${menu eq 'buscar' ? 'activo' : ''}" href="${ctx}/productos?accion=buscar">
                    <ui:icono nombre="buscar"/> Buscar producto
                </a>

                <p class="navegacion__grupo">Reportes</p>
                <a class="nav-item ${menu eq 'disponibles' ? 'activo' : ''}" href="${ctx}/reportes?accion=disponibles">
                    <ui:icono nombre="check"/> Productos disponibles
                </a>
                <a class="nav-item ${menu eq 'resumen' ? 'activo' : ''}" href="${ctx}/reportes?accion=resumen">
                    <ui:icono nombre="grafico"/> Resumen de inventario
                </a>

                <p class="navegacion__grupo">Sistema</p>
                <c:if test="${esAdmin}">
                    <a class="nav-item ${menu eq 'admin-usuarios' ? 'activo' : ''}" href="${ctx}/admin?accion=usuarios">
                        <ui:icono nombre="usuarios"/> Usuarios
                    </a>
                    <a class="nav-item ${menu eq 'admin-auditoria' ? 'activo' : ''}" href="${ctx}/admin?accion=auditoria">
                        <ui:icono nombre="historial"/> Auditoría
                    </a>
                </c:if>
                <a class="nav-item ${menu eq 'informacion' ? 'activo' : ''}" href="${ctx}/informacion">
                    <ui:icono nombre="info"/> Información del sistema
                </a>
            </c:otherwise>
        </c:choose>
    </nav>

    <div class="lateral__pie">
        <c:choose>
            <c:when test="${not empty sessionScope.usuario}">
                <a class="nav-item" href="${ctx}/logout">
                    <ui:icono nombre="salir"/> Cerrar sesión
                </a>
            </c:when>
            <c:otherwise>
                <a class="nav-item" href="${ctx}/login">
                    <ui:icono nombre="candado"/> Iniciar sesión / Registro
                </a>
            </c:otherwise>
        </c:choose>
        <p class="lateral__version">NEXORA · Soluciones Digitales</p>
    </div>
</aside>
<div class="lateral-fondo" data-cerrar-menu></div>
