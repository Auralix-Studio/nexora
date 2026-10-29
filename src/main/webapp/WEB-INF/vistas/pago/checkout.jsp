<%--
  Vista: checkout de la pasarela de pago SIMULADA (solo rol Cliente).
  Recibe: producto, cantidad, subtotalTexto, igvTexto, totalTexto,
          form (valores previos, sin numeroTarjeta/cvv/codigoYape), errores (campo -> mensaje),
          errorPago (rechazo de la pasarela), tarjetasPrueba (List<String[]> {número, marca, resultado}),
          codigoYapePrueba, tokenCheckout (token de un solo uso del formulario; evita el doble envío)
--%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:set var="titulo" value="Finalizar compra" scope="request"/>
<c:set var="ruta" value="Catálogo / Pago seguro" scope="request"/>
<c:set var="menu" value="listar" scope="request"/>
<jsp:include page="/includes/header.jsp"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="metodo" value="${form.metodo eq 'YAPE' ? 'YAPE' : 'TARJETA'}"/>
<c:set var="esFactura" value="${form.tipoComprobante eq 'Factura'}"/>
<c:set var="esRetiro" value="${form.tipoEnvio eq 'RETIRO'}"/>

<div class="dos-columnas dos-columnas--pago">
    <form class="tarjeta tarjeta--amplia formulario-pago" method="post" novalidate
          action="${ctx}/pago" data-formulario-pago>
        <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
        <input type="hidden" name="tokenCheckout" value="${tokenCheckout}">
        <input type="hidden" name="codigo" value="${fn:escapeXml(producto.codigo)}">
        <input type="hidden" name="cantidad" value="${cantidad}">

        <div class="tarjeta__encabezado">
            <h2 class="tarjeta__titulo">Finalizar compra y entrega</h2>
            <p class="nota">Completa los datos de envío, comprobante y pago para procesar tu pedido. Los campos marcados con * son obligatorios.</p>
        </div>

        <c:if test="${not empty errorPago}">
            <div class="alerta alerta--error" role="alert">
                <ui:icono nombre="alerta"/>
                <div>
                    <strong>No se pudo completar el pago</strong>
                    <p><c:out value="${errorPago}"/></p>
                </div>
            </div>
        </c:if>

        <div class="alerta alerta--error ${empty errores ? 'oculto' : ''}" data-alerta-errores role="alert">
            <ui:icono nombre="alerta"/>
            <div>
                <strong data-alerta-titulo>Revise ${errores.size()} ${errores.size() eq 1 ? 'campo' : 'campos'} antes de pagar</strong>
                <p>El pedido no se procesa hasta corregir los campos señalados.</p>
            </div>
        </div>

        <%-- 1. Datos de Entrega y Envío --%>
        <section class="seccion-pago" aria-labelledby="titulo-entrega">
            <h3 class="seccion-pago__titulo" id="titulo-entrega"><span class="seccion-pago__numero">1</span> Modalidad de Entrega</h3>

            <fieldset class="campo">
                <legend>Elige cómo recibir tu compra</legend>
                <div class="segmentos segmentos--pago">
                    <label>
                        <input type="radio" name="tipoEnvio" value="DOMICILIO" ${esRetiro ? '' : 'checked'} data-radio-envio>
                        <span><ui:icono nombre="caja" tam="16"/> Envío a Domicilio Express</span>
                    </label>
                    <label>
                        <input type="radio" name="tipoEnvio" value="RETIRO" ${esRetiro ? 'checked' : ''} data-radio-envio>
                        <span><ui:icono nombre="capas" tam="16"/> Retiro en Tienda (Gratis)</span>
                    </label>
                </div>
            </fieldset>

            <%-- Panel Domicilio --%>
            <div class="panel-entrega" data-panel-envio="DOMICILIO" ${esRetiro ? 'hidden' : ''}>
                <div class="rejilla-campos">
                    <div class="campo ${not empty errores.departamento ? 'campo--error' : ''}">
                        <label for="departamento">Departamento / Región *</label>
                        <select id="departamento" name="departamento">
                            <c:set var="deptSel" value="${empty form.departamento ? 'Lima' : form.departamento}"/>
                            <option value="Lima" ${deptSel eq 'Lima' ? 'selected' : ''}>Lima</option>
                            <option value="Callao" ${deptSel eq 'Callao' ? 'selected' : ''}>Callao</option>
                            <option value="Arequipa" ${deptSel eq 'Arequipa' ? 'selected' : ''}>Arequipa</option>
                            <option value="Cusco" ${deptSel eq 'Cusco' ? 'selected' : ''}>Cusco</option>
                            <option value="La Libertad" ${deptSel eq 'La Libertad' ? 'selected' : ''}>La Libertad</option>
                            <option value="Lambayeque" ${deptSel eq 'Lambayeque' ? 'selected' : ''}>Lambayeque</option>
                            <option value="Piura" ${deptSel eq 'Piura' ? 'selected' : ''}>Piura</option>
                            <option value="Junín" ${deptSel eq 'Junín' ? 'selected' : ''}>Junín</option>
                            <option value="Ancash" ${deptSel eq 'Ancash' ? 'selected' : ''}>Ancash</option>
                            <option value="Ica" ${deptSel eq 'Ica' ? 'selected' : ''}>Ica</option>
                        </select>
                        <p class="campo__error" data-error-de="departamento"><ui:icono nombre="alerta" tam="14"/><span><c:out value="${errores.departamento}"/></span></p>
                    </div>

                    <div class="campo ${not empty errores.distrito ? 'campo--error' : ''}">
                        <label for="distrito">Distrito / Localidad *</label>
                        <input id="distrito" name="distrito" type="text" placeholder="Ej. Miraflores, San Isidro, Surco"
                               value="${fn:escapeXml(form.distrito)}">
                        <p class="campo__error" data-error-de="distrito"><ui:icono nombre="alerta" tam="14"/><span><c:out value="${errores.distrito}"/></span></p>
                    </div>

                    <div class="campo campo--completo ${not empty errores.direccion ? 'campo--error' : ''}">
                        <label for="direccion">Dirección exacta de entrega *</label>
                        <input id="direccion" name="direccion" type="text" placeholder="Av., Calle, Jirón, Número y Dpto/Interior"
                               value="${fn:escapeXml(form.direccion)}">
                        <p class="campo__error" data-error-de="direccion"><ui:icono nombre="alerta" tam="14"/><span><c:out value="${errores.direccion}"/></span></p>
                    </div>

                    <div class="campo campo--completo">
                        <label for="referencia">Referencia de ubicación (opcional)</label>
                        <input id="referencia" name="referencia" type="text" placeholder="Ej. Frente al parque Kennedy, portón negro"
                               value="${fn:escapeXml(form.referencia)}">
                    </div>
                </div>
            </div>

            <%-- Panel Retiro en Tienda --%>
            <div class="panel-entrega" data-panel-envio="RETIRO" ${esRetiro ? '' : 'hidden'}>
                <div class="alerta alerta--info" style="margin-top:0.5rem;">
                    <ui:icono nombre="info"/>
                    <div>
                        <strong>Punto de entrega oficial NEXORA</strong>
                        <p><strong>Almacén Central San Isidro:</strong> Av. República de Panamá 3545, San Isidro, Lima.</p>
                        <p class="nota">Horario de retiro: Lunes a Sábado de 09:00 a 19:00 hrs. Recuerda traer tu documento de identidad para el retiro.</p>
                    </div>
                </div>
            </div>

            <%-- Datos de Contacto para entrega --%>
            <div class="rejilla-campos" style="margin-top:0.75rem;">
                <div class="campo ${not empty errores.telefonoContacto ? 'campo--error' : ''}">
                    <label for="telefonoContacto">Celular de contacto / WhatsApp *</label>
                    <input id="telefonoContacto" name="telefonoContacto" type="tel" inputmode="tel" maxlength="9"
                           placeholder="Ej. 987654321" value="${fn:escapeXml(form.telefonoContacto)}">
                    <p class="campo__ayuda">El repartidor llamará a este número al llegar.</p>
                    <p class="campo__error" data-error-de="telefonoContacto"><ui:icono nombre="alerta" tam="14"/><span><c:out value="${errores.telefonoContacto}"/></span></p>
                </div>

                <div class="campo ${not empty errores.correoComprobante ? 'campo--error' : ''}">
                    <label for="correoComprobante">Correo para seguimiento digital *</label>
                    <input id="correoComprobante" name="correoComprobante" type="email" autocomplete="email"
                           placeholder="cliente@ejemplo.com" value="${fn:escapeXml(form.correoComprobante)}">
                    <p class="campo__ayuda">Te enviaremos el comprobante y el tracking.</p>
                    <p class="campo__error" data-error-de="correoComprobante"><ui:icono nombre="alerta" tam="14"/><span><c:out value="${errores.correoComprobante}"/></span></p>
                </div>

                <div class="campo campo--completo">
                    <label for="notasPedido">Indicaciones para el despacho (opcional)</label>
                    <input id="notasPedido" name="notasPedido" type="text" maxlength="200"
                           placeholder="Ej. Timbre 401 averiado, dejar en conserjería si no responde"
                           value="${fn:escapeXml(form.notasPedido)}">
                </div>
            </div>
        </section>

        <hr class="divisor">

        <%-- 2. Comprobante de Pago --%>
        <section class="seccion-pago" aria-labelledby="titulo-comprobante">
            <h3 class="seccion-pago__titulo" id="titulo-comprobante"><span class="seccion-pago__numero">2</span> Comprobante de Pago</h3>

            <fieldset class="campo ${not empty errores.tipoComprobante ? 'campo--error' : ''}">
                <legend>Tipo de comprobante</legend>
                <div class="segmentos segmentos--pago">
                    <label><input type="radio" name="tipoComprobante" value="Boleta" ${esFactura ? '' : 'checked'}><span>Boleta de Venta</span></label>
                    <label><input type="radio" name="tipoComprobante" value="Factura" ${esFactura ? 'checked' : ''}><span>Factura Comercial</span></label>
                </div>
                <p class="campo__error" data-error-de="tipoComprobante"><ui:icono nombre="alerta" tam="14"/><span><c:out value="${errores.tipoComprobante}"/></span></p>
            </fieldset>

            <div class="rejilla-campos">
                <div class="campo ${not empty errores.documento ? 'campo--error' : ''}">
                    <label for="documento" data-etiqueta-documento>${esFactura ? 'RUC' : 'DNI'} *</label>
                    <input id="documento" name="documento" type="text" inputmode="numeric" autocomplete="off"
                           maxlength="${esFactura ? 11 : 8}" placeholder="${esFactura ? '11 dígitos' : '8 dígitos'}"
                           value="${fn:escapeXml(form.documento)}">
                    <p class="campo__error" data-error-de="documento"><ui:icono nombre="alerta" tam="14"/><span><c:out value="${errores.documento}"/></span></p>
                </div>
                <div class="campo ${not empty errores.razonSocial ? 'campo--error' : ''}" data-campo-razon>
                    <label for="razonSocial">Razón social *</label>
                    <input id="razonSocial" name="razonSocial" type="text" autocomplete="organization" maxlength="150"
                           placeholder="Nombre de la empresa" value="${fn:escapeXml(form.razonSocial)}">
                    <p class="campo__ayuda">Obligatoria solo si eliges factura.</p>
                    <p class="campo__error" data-error-de="razonSocial"><ui:icono nombre="alerta" tam="14"/><span><c:out value="${errores.razonSocial}"/></span></p>
                </div>
            </div>
        </section>

        <hr class="divisor">

        <%-- 3. Método de pago --%>
        <section class="seccion-pago" aria-labelledby="titulo-metodo">
            <h3 class="seccion-pago__titulo" id="titulo-metodo"><span class="seccion-pago__numero">3</span> Método de Pago Seguro</h3>

            <fieldset class="campo ${not empty errores.metodo ? 'campo--error' : ''}">
                <legend>Elige cómo quieres pagar</legend>
                <div class="segmentos segmentos--pago">
                    <label><input type="radio" name="metodo" value="TARJETA" ${metodo eq 'TARJETA' ? 'checked' : ''}><span><ui:icono nombre="tarjeta" tam="16"/> Tarjeta de Débito / Crédito</span></label>
                    <label><input type="radio" name="metodo" value="YAPE" ${metodo eq 'YAPE' ? 'checked' : ''}><span><ui:icono nombre="celular" tam="16"/> Yape / Plin</span></label>
                </div>
                <p class="campo__error" data-error-de="metodo"><ui:icono nombre="alerta" tam="14"/><span><c:out value="${errores.metodo}"/></span></p>
            </fieldset>

            <div class="panel-pago" data-panel-metodo="TARJETA">
                <div class="rejilla-campos">
                    <div class="campo campo--completo ${not empty errores.numeroTarjeta ? 'campo--error' : ''}">
                        <div class="campo__fila">
                            <label for="numeroTarjeta">Número de tarjeta *</label>
                            <span class="marca-detectada" data-marca-tarjeta aria-live="polite"></span>
                        </div>
                        <input id="numeroTarjeta" name="numeroTarjeta" type="text" inputmode="numeric"
                               autocomplete="cc-number" maxlength="23" placeholder="0000 0000 0000 0000">
                        <p class="campo__error" data-error-de="numeroTarjeta"><ui:icono nombre="alerta" tam="14"/><span><c:out value="${errores.numeroTarjeta}"/></span></p>
                    </div>
                    <div class="campo campo--completo ${not empty errores.titular ? 'campo--error' : ''}">
                        <label for="titular">Titular de la tarjeta *</label>
                        <input id="titular" name="titular" type="text" autocomplete="cc-name" maxlength="60"
                               placeholder="Como figura en el plástico" value="${fn:escapeXml(form.titular)}">
                        <p class="campo__error" data-error-de="titular"><ui:icono nombre="alerta" tam="14"/><span><c:out value="${errores.titular}"/></span></p>
                    </div>
                    <div class="campo ${not empty errores.vencimiento ? 'campo--error' : ''}">
                        <label for="vencimiento">Vencimiento *</label>
                        <input id="vencimiento" name="vencimiento" type="text" inputmode="numeric"
                               autocomplete="cc-exp" maxlength="5" placeholder="MM/AA" value="${fn:escapeXml(form.vencimiento)}">
                        <p class="campo__error" data-error-de="vencimiento"><ui:icono nombre="alerta" tam="14"/><span><c:out value="${errores.vencimiento}"/></span></p>
                    </div>
                    <div class="campo ${not empty errores.cvv ? 'campo--error' : ''}">
                        <label for="cvv">CVV *</label>
                        <input id="cvv" name="cvv" type="text" inputmode="numeric"
                               autocomplete="cc-csc" maxlength="4" placeholder="123">
                        <p class="campo__error" data-error-de="cvv"><ui:icono nombre="alerta" tam="14"/><span><c:out value="${errores.cvv}"/></span></p>
                    </div>
                </div>
            </div>

            <div class="panel-pago" data-panel-metodo="YAPE">
                <div class="rejilla-campos">
                    <div class="campo ${not empty errores.celular ? 'campo--error' : ''}">
                        <label for="celular">Celular Yape / Plin *</label>
                        <input id="celular" name="celular" type="text" inputmode="numeric"
                               autocomplete="tel-national" maxlength="9" placeholder="9XXXXXXXX" value="${fn:escapeXml(form.celular)}">
                        <p class="campo__error" data-error-de="celular"><ui:icono nombre="alerta" tam="14"/><span><c:out value="${errores.celular}"/></span></p>
                    </div>
                    <div class="campo ${not empty errores.codigoYape ? 'campo--error' : ''}">
                        <label for="codigoYape">Código de aprobación *</label>
                        <input id="codigoYape" name="codigoYape" type="text" inputmode="numeric"
                               autocomplete="off" maxlength="6" placeholder="6 dígitos">
                        <p class="campo__error" data-error-de="codigoYape"><ui:icono nombre="alerta" tam="14"/><span><c:out value="${errores.codigoYape}"/></span></p>
                    </div>
                </div>
                <p class="campo__ayuda">Ingresa el código dinámico de 6 dígitos que muestra tu app bancaria.</p>
            </div>
        </section>

        <hr class="divisor">

        <div class="pago-accion">
            <button type="submit" class="boton boton--primario boton--bloque boton--pagar" data-boton-pagar>
                <ui:icono nombre="candado" tam="18"/> <span data-texto-boton>Confirmar y pagar <c:out value="${totalTexto}"/></span>
            </button>
            <p class="nota nota-pago"><ui:icono nombre="escudo" tam="16"/> <span>Transacción cifrada y protegida por NEXORA SecurePay. No guardamos el número completo de tarjeta ni el CVV.</span></p>
        </div>
    </form>

    <aside class="columna-lateral columna-lateral--pago">
        <div class="tarjeta resumen-pedido">
            <div class="tarjeta__encabezado">
                <h2 class="tarjeta__titulo">Resumen del pedido</h2>
            </div>
            <div class="resumen-pedido__foto">
                <ui:fotoProducto producto="${producto}"/>
            </div>
            <div class="resumen-pedido__producto">
                <h3 class="resumen-pedido__nombre"><c:out value="${producto.nombre}"/></h3>
                <p class="nota mono"><c:out value="${producto.codigo}"/> · <c:out value="${producto.categoria}"/></p>
            </div>
            <dl class="lista-datos">
                <div><dt>Cantidad</dt><dd class="mono"><c:out value="${cantidad}"/></dd></div>
                <div><dt>Precio unitario</dt><dd class="mono"><c:out value="${producto.precioTexto}"/></dd></div>
                <div><dt>Subtotal sin IGV</dt><dd class="mono"><c:out value="${subtotalTexto}"/></dd></div>
                <div><dt>IGV 18 %</dt><dd class="mono"><c:out value="${igvTexto}"/></dd></div>
                <div><dt>Despacho</dt><dd><span style="color:var(--color-exito, #10b981); font-weight:600;">Gratis</span> (Promoción Web)</dd></div>
                <div><dt>Tiempo estimado</dt><dd>24 a 48 hrs hábiles</dd></div>
            </dl>
            <div class="resumen-pedido__total">
                <span>Total</span>
                <strong class="mono"><c:out value="${totalTexto}"/></strong>
            </div>
            <c:url var="urlCambiar" value="/productos"><c:param name="accion" value="comprar"/><c:param name="codigo" value="${producto.codigo}"/></c:url>
            <a class="boton boton--secundario boton--pequeno boton--bloque" href="${urlCambiar}">Cambiar cantidad</a>
        </div>

        <div class="tarjeta caja-prueba">
            <div class="tarjeta__encabezado">
                <span class="etiqueta-prueba"><ui:icono nombre="info" tam="14"/> Modo de prueba</span>
                <p class="nota">Usa estos datos para simular el pago. Ninguna tarjeta es real.</p>
            </div>
            <div class="tabla-contenedor">
                <table class="tabla-prueba">
                    <thead>
                    <tr>
                        <th>Número</th>
                        <th>Marca</th>
                        <th>Resultado</th>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach var="t" items="${tarjetasPrueba}">
                        <tr>
                            <td class="mono"><c:out value="${t[0]}"/></td>
                            <td><c:out value="${t[1]}"/></td>
                            <td><c:out value="${t[2]}"/></td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </div>
            <p class="nota">Código Yape de prueba: <strong class="mono"><c:out value="${codigoYapePrueba}"/></strong>. Celular: cualquier número de 9 dígitos que empiece con 9.</p>
            <p class="nota">Usa cualquier fecha futura y un CVV de 3 dígitos (4 en Amex).</p>
        </div>
    </aside>
</div>

<jsp:include page="/includes/footer.jsp"/>
