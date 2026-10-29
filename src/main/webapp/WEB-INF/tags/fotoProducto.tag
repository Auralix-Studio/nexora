<%@ tag description="Fotografía verificada de un producto; solo las variantes llevan nota de imagen referencial" pageEncoding="UTF-8" %>
<%@ attribute name="producto" required="true" type="pe.edu.sistema.model.Producto" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="nombreFoto" value="${fn:toLowerCase(producto.nombre)}"/>
<c:set var="archivoFoto" value="sin-foto.svg"/>
<c:set var="notaFoto" value=""/>
<c:choose>
    <c:when test="${fn:contains(nombreFoto, 'lenovo') and fn:contains(nombreFoto, 'ideapad 3')}"><c:set var="archivoFoto" value="P001.jpg"/><c:set var="notaFoto" value="Imagen referencial"/></c:when>
    <c:when test="${fn:contains(nombreFoto, 'logitech') and fn:contains(nombreFoto, 'm170')}"><c:set var="archivoFoto" value="P002.jpg"/></c:when>
    <c:when test="${fn:contains(nombreFoto, 'redragon') and fn:contains(nombreFoto, 'kumara')}"><c:set var="archivoFoto" value="P003.jpg"/></c:when>
    <c:when test="${fn:contains(nombreFoto, 'lg') and fn:contains(nombreFoto, '24') and fn:contains(nombreFoto, 'ips')}"><c:set var="archivoFoto" value="P004.jpg"/><c:set var="notaFoto" value="Imagen referencial"/></c:when>
    <c:when test="${fn:contains(nombreFoto, 'epson') and fn:contains(nombreFoto, 'l3250')}"><c:set var="archivoFoto" value="P005.jpg"/></c:when>
    <c:when test="${fn:contains(nombreFoto, 'kingston') and fn:contains(nombreFoto, 'a400')}"><c:set var="archivoFoto" value="P006.png"/></c:when>
    <c:when test="${fn:contains(nombreFoto, 'sandisk') and fn:contains(nombreFoto, 'ultra')}"><c:set var="archivoFoto" value="P007.png"/></c:when>
    <c:when test="${fn:contains(nombreFoto, 'hyperx') and fn:contains(nombreFoto, 'cloud ii') and not fn:contains(nombreFoto, 'cloud iii')}"><c:set var="archivoFoto" value="P008.jpg"/></c:when>
    <c:when test="${fn:contains(nombreFoto, 'tp-link') and fn:contains(nombreFoto, 'archer c6')}"><c:set var="archivoFoto" value="P009.jpg"/></c:when>
    <c:when test="${fn:contains(nombreFoto, 'hp 15') and fn:contains(nombreFoto, 'ryzen 5')}"><c:set var="archivoFoto" value="P010.jpg"/><c:set var="notaFoto" value="Imagen referencial"/></c:when>
    <c:when test="${fn:contains(nombreFoto, 'logitech') and fn:contains(nombreFoto, 'c920')}"><c:set var="archivoFoto" value="P011.jpg"/></c:when>
    <c:when test="${fn:contains(nombreFoto, 'tp-link') and fn:contains(nombreFoto, 'switch') and fn:contains(nombreFoto, '8')}"><c:set var="archivoFoto" value="P012.jpg"/><c:set var="notaFoto" value="Imagen referencial"/></c:when>
</c:choose>
<c:url var="fotoProducto" value="/img/productos/${archivoFoto}"/>
<div class="tarjeta-producto__media">
    <span class="imagen-no-disponible" hidden>Fotografía no disponible</span>
    <img src="${fotoProducto}" alt="<c:out value='${producto.nombre}'/>" class="tarjeta-producto__img" width="600" height="400" loading="lazy" decoding="async" data-imagen-producto>
    <span class="tarjeta-producto__badge-stock"><span class="insignia ${producto.disponible ? 'insignia--ok' : 'insignia--error'}">${producto.disponible ? 'Disponible' : 'Agotado'}</span></span>
</div>
<c:if test="${not empty notaFoto}"><p class="tarjeta-producto__foto-nota"><c:out value="${notaFoto}"/></p></c:if>
