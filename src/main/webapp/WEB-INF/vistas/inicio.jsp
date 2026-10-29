<%--
  Vista: Portada Tienda Online NEXORA (visitantes y clientes; el personal va a /admin).
  Recibe: productosTienda, totalProductos, categoriasTienda (List<String>)
--%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:set var="titulo" value="Tienda Online" scope="request"/>
<c:set var="ruta" value="Tienda Tecnológica" scope="request"/>
<c:set var="menu" value="inicio" scope="request"/>
<jsp:include page="/includes/header.jsp"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>

<%-- Banner Hero Principal de la Tienda --%>
<section class="tienda-hero" data-parallax>
    <div class="tienda-hero__contenido">
        <div class="tienda-hero__badge">
            <ui:icono nombre="monedas" tam="14"/> NEXORA · TECNOLOGÍA A TU ALCANCE
        </div>
        <h2 class="tienda-hero__titulo" data-dividir>Tecnología para tu próximo proyecto.</h2>
        <p class="tienda-hero__subtitulo">
            Laptops, monitores y accesorios. Encuentra el equipo que necesitas en un solo lugar.
        </p>
        <div class="tienda-hero__acciones">
            <a href="#catalogo" class="boton boton--primario boton--brillo">
                <ui:icono nombre="caja" tam="18"/> Explorar catálogo
            </a>
            <a href="${ctx}/productos?accion=buscar" class="boton boton--contorno-claro">
                <ui:icono nombre="buscar" tam="18"/> Buscar un producto
            </a>
        </div>
    </div>
    <div class="tienda-hero__visual" aria-hidden="true"><img src="${ctx}/img/productos/P001.jpg" width="420" height="300" alt="" fetchpriority="high"><span>Tu espacio. Tu próximo equipo.</span></div>
</section>

<section class="barra-confianza" aria-label="Cómo funciona la tienda">
    <div class="confianza-item" data-revelar><span class="confianza-item__icono"><ui:icono nombre="filtro"/></span><div class="confianza-item__titulo">Explora por categoría</div></div>
    <div class="confianza-item" data-revelar><span class="confianza-item__icono"><ui:icono nombre="caja"/></span><div class="confianza-item__titulo">Stock en tiempo real</div></div>
    <div class="confianza-item" data-revelar><span class="confianza-item__icono"><ui:icono nombre="tarjeta"/></span><div class="confianza-item__titulo">Paga con tarjeta o Yape</div></div>
    <div class="confianza-item" data-revelar><span class="confianza-item__icono"><ui:icono nombre="comprobante"/></span><div class="confianza-item__titulo">Boleta o factura electrónica</div></div>
</section>

<%-- Encabezado de la Sección de Productos --%>
<div class="tienda-seccion-titulo" id="catalogo">
    <div>
        <h2>Encuentra tu próximo equipo</h2>
        <p>Explora por categoría · Precios en soles</p>
    </div>
    <div class="nota mono">
        Mostrando <strong class="texto-tinta">${fn:length(productosTienda)}</strong> productos
    </div>
</div>

<%-- Píldoras de Categorías (las entrega el servidor desde las categorías activas) --%>
<nav class="tienda-categorias" aria-label="Categorías del catálogo">
    <a href="${ctx}/inicio#catalogo" class="tienda-categoria-pill activa">
        <ui:icono nombre="caja" tam="16"/> Todo el Catálogo (<c:out value="${totalProductos}"/>)
    </a>
    <c:forEach var="cat" items="${categoriasTienda}">
        <c:url var="urlCategoria" value="/productos">
            <c:param name="accion" value="buscar"/>
            <c:param name="criterio" value="categoria"/>
            <c:param name="valor" value="${cat}"/>
        </c:url>
        <a href="${urlCategoria}" class="tienda-categoria-pill"><c:out value="${cat}"/></a>
    </c:forEach>
</nav>

<%-- Cuadrícula de Tarjetas de Producto eCommerce --%>
<ui:catalogoProductos productos="${productosTienda}"/>

<jsp:include page="/includes/footer.jsp"/>
