<%--
  Vista: tabla de productos. Se usa en cuatro modos con productos:
    listar   -> Productos / Listar (acciones Editar y Eliminar)
    editar   -> selección del producto a editar
    eliminar -> selección del producto a eliminar + confirmación
    comprar  -> selección del producto a comprar (ventana de cantidad)
  En modo pedidos solo incluye el historial (pedidos.jsp) y no recibe productos.
--%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:choose>
    <c:when test="${modo eq 'editar'}">
        <c:set var="titulo" value="Editar producto" scope="request"/>
        <c:set var="ruta" value="Productos / Editar" scope="request"/>
        <c:set var="menu" value="editar" scope="request"/>
    </c:when>
    <c:when test="${modo eq 'eliminar'}">
        <c:set var="titulo" value="Eliminar producto" scope="request"/>
        <c:set var="ruta" value="Productos / Eliminar" scope="request"/>
        <c:set var="menu" value="eliminar" scope="request"/>
    </c:when>
    <c:when test="${modo eq 'comprar'}">
        <c:set var="titulo" value="Comprar producto" scope="request"/>
        <c:set var="ruta" value="Catálogo / Comprar" scope="request"/>
        <c:set var="menu" value="comprar" scope="request"/>
    </c:when>
    <c:when test="${modo eq 'pedidos'}">
        <c:set var="titulo" value="${sessionScope.rol eq 'Cliente' ? 'Mis compras' : 'Pedidos de clientes'}" scope="request"/>
        <c:set var="ruta" value="Pedidos / Historial" scope="request"/>
        <c:set var="menu" value="pedidos" scope="request"/>
    </c:when>
    <c:otherwise>
        <c:set var="titulo" value="${empty sessionScope.rol or sessionScope.rol eq 'Cliente' ? 'Catálogo de productos' : 'Listar productos'}" scope="request"/>
        <c:set var="ruta" value="Catálogo / Productos" scope="request"/>
        <c:set var="menu" value="listar" scope="request"/>
    </c:otherwise>
</c:choose>
<jsp:include page="/includes/header.jsp"/>

<c:choose>
    <c:when test="${modo eq 'pedidos'}">
        <jsp:include page="/WEB-INF/vistas/productos/pedidos.jsp"/>
    </c:when>
    <c:otherwise>

<div class="barra-herramientas">
    <div>
        <c:choose>
            <c:when test="${modo eq 'editar'}">
                <h2 class="subtitulo">Seleccione el producto a editar</h2>
                <p class="nota">Sus datos se cargarán en el formulario para modificarlos.</p>
            </c:when>
            <c:when test="${modo eq 'eliminar'}">
                <h2 class="subtitulo">Seleccione el producto a eliminar</h2>
                <p class="nota">Se pedirá confirmación antes de quitarlo de la colección.</p>
            </c:when>
            <c:when test="${modo eq 'comprar'}">
                <h2 class="subtitulo">Elige el producto que deseas comprar</h2>
                <p class="nota">Elige la cantidad y paga de forma segura con tarjeta o Yape.</p>
            </c:when>
            <c:otherwise>
                <h2 class="subtitulo">${fn:length(productos)} productos en el catálogo NEXORA</h2>
                <c:choose>
                    <c:when test="${not empty sessionScope.rol and sessionScope.rol ne 'Cliente'}">
                        <p class="nota mono">Gestión autorizada · Perfil: <c:out value="${sessionScope.rol}"/></p>
                    </c:when>
                    <c:otherwise>
                        <p class="nota">Consulta los precios y las existencias de cada producto.</p>
                    </c:otherwise>
                </c:choose>
            </c:otherwise>
        </c:choose>
    </div>
    <div class="barra-herramientas__acciones">
        <form class="buscador" action="${pageContext.request.contextPath}/productos" method="get">
            <input type="hidden" name="accion" value="buscar">
            <input type="hidden" name="criterio" value="nombre">
            <ui:icono nombre="buscar" tam="18"/>
            <input type="search" name="valor" placeholder="Buscar por nombre" aria-label="Buscar por nombre">
        </form>
        <c:if test="${sessionScope.rol eq 'Administrador' or sessionScope.rol eq 'Almacén'}">
            <a class="boton boton--primario" href="${pageContext.request.contextPath}/productos?accion=nuevo">
                <ui:icono nombre="mas" tam="18"/> Nuevo producto
            </a>
        </c:if>
    </div>
</div>

<c:choose>
    <c:when test="${(empty sessionScope.rol or sessionScope.rol eq 'Cliente') and (modo eq 'listar' or modo eq 'comprar')}">
        <%-- Cuadrícula de Tienda para Clientes y Visitantes --%>
        <ui:catalogoProductos productos="${productos}"/>
    </c:when>
    <c:otherwise>
        <ui:tablaProductos productos="${productos}"
                           acciones="${modo eq 'listar' ? 'todas' : modo}"
                           resaltar="${not empty productoEliminar ? productoEliminar.codigo : productoComprar.codigo}"
                           pieIzquierda="Mostrando ${fn:length(productos)} productos"
                           pieDerecha="Disponibles ${disponibles} · Agotados ${agotados}"
                           vacio="No hay productos registrados."/>
    </c:otherwise>
</c:choose>

</c:otherwise>
</c:choose>

<c:if test="${not empty productoComprar}">
    <%-- Modal de compra para el rol Cliente: elige la cantidad y continúa al checkout (GET /pago) --%>
    <div class="modal-capa" role="dialog" aria-modal="true" aria-labelledby="titulo-modal-compra">
        <div class="modal">
            <span class="chip chip--ok chip--grande"><ui:icono nombre="caja" tam="24"/></span>
            <div>
                <h2 class="modal__titulo" id="titulo-modal-compra">Comprar producto</h2>
                <p class="modal__texto">Elige la cantidad y continúa al pago seguro.</p>
            </div>
            <div class="resumen-producto">
                <div class="fila-espaciada">
                    <span class="mono suave"><c:out value="${productoComprar.codigo}"/></span>
                    <span class="insignia insignia--ok">Stock disponible: <c:out value="${productoComprar.stock}"/></span>
                </div>
                <strong class="resumen-producto__nombre"><c:out value="${productoComprar.nombre}"/></strong>
                <span class="mono suave pequeno"><c:out value="${productoComprar.categoria}"/> · Precio unitario: <c:out value="${productoComprar.precioTexto}"/></span>
            </div>
            <form action="${pageContext.request.contextPath}/pago" method="get" style="display: grid; gap: 14px;">
                <input type="hidden" name="codigo" value="${fn:escapeXml(productoComprar.codigo)}">
                <div class="campo">
                    <label for="cantidad">Cantidad (máx. ${productoComprar.stock} unidades)</label>
                    <input id="cantidad" name="cantidad" type="number" min="1" max="${productoComprar.stock}" value="1" required>
                </div>
                <div class="modal__acciones">
                    <a class="boton boton--secundario" href="${pageContext.request.contextPath}/productos?accion=listar" data-cancelar-modal>Cancelar</a>
                    <button type="submit" class="boton boton--primario"><ui:icono nombre="tarjeta" tam="18"/> Continuar al pago</button>
                </div>
            </form>
        </div>
    </div>
</c:if>

<c:if test="${not empty productoEliminar}">
    <%-- Confirmación de eliminación (funciona sin JavaScript) --%>
    <div class="modal-capa" role="dialog" aria-modal="true" aria-labelledby="titulo-modal">
        <div class="modal">
            <span class="chip chip--error chip--grande"><ui:icono nombre="eliminar" tam="24"/></span>
            <div>
                <h2 class="modal__titulo" id="titulo-modal">¿Eliminar este producto?</h2>
                <p class="modal__texto">Esta acción eliminará el producto y no se puede deshacer.</p>
            </div>
            <div class="resumen-producto">
                <div class="fila-espaciada">
                    <span class="mono suave"><c:out value="${productoEliminar.codigo}"/></span>
                    <span class="insignia ${productoEliminar.disponible ? 'insignia--ok' : 'insignia--error'}"><c:out value="${productoEliminar.estado}"/></span>
                </div>
                <strong class="resumen-producto__nombre"><c:out value="${productoEliminar.nombre}"/></strong>
                <span class="mono suave pequeno"><c:out value="${productoEliminar.categoria}"/> · <c:out value="${productoEliminar.precioTexto}"/> · Stock <c:out value="${productoEliminar.stock}"/></span>
            </div>
            <form class="modal__acciones" action="${pageContext.request.contextPath}/productos" method="post">
                <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
                <input type="hidden" name="accion" value="eliminar">
                <input type="hidden" name="codigo" value="${fn:escapeXml(productoEliminar.codigo)}">
                <a class="boton boton--secundario" href="${pageContext.request.contextPath}/productos?accion=eliminar" data-cancelar-modal>Cancelar</a>
                <button type="submit" class="boton boton--peligro"><ui:icono nombre="eliminar" tam="18"/> Sí, eliminar</button>
            </form>
        </div>
    </div>
</c:if>

<jsp:include page="/includes/footer.jsp"/>
