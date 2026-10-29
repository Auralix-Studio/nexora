/*
 * Sistema Web de Gestión de Productos — JavaScript de la interfaz.
 * La validación se repite en el servidor (ProductoService.validar);
 * aquí solo se adelanta para dar respuesta inmediata al usuario.
 */
(function () {
    "use strict";

    // ------------------------------------------------------------ menú lateral (móvil)
    document.querySelectorAll("[data-abrir-menu]").forEach(function (b) {
        b.addEventListener("click", function () { document.body.classList.add("menu-abierto"); });
    });
    document.querySelectorAll("[data-cerrar-menu]").forEach(function (b) {
        b.addEventListener("click", function () { document.body.classList.remove("menu-abierto"); });
    });

    // Una imagen fallida no debe dejar un icono roto ni sustituirse por otro modelo.
    document.querySelectorAll('[data-imagen-producto]').forEach(function (img) {
        function mostrarAlternativa() { img.hidden = true; img.parentElement.querySelector('.imagen-no-disponible').hidden = false; }
        img.addEventListener('error', mostrarAlternativa);
        if (img.complete && img.naturalWidth === 0) mostrarAlternativa();
    });

    // ------------------------------------------------------------ avisos
    document.querySelectorAll("[data-cerrar-aviso]").forEach(function (b) {
        b.addEventListener("click", function () { b.closest("[data-aviso]").remove(); });
    });
    var aviso = document.querySelector(".aviso--ok");
    if (aviso) {
        setTimeout(function () { if (aviso.isConnected) { aviso.remove(); } }, 6000);
    }

    // ------------------------------------------------------------ modal de confirmación
    var cancelar = document.querySelector("[data-cancelar-modal]");
    if (cancelar) {
        document.addEventListener("keydown", function (e) {
            if (e.key === "Escape") { window.location.href = cancelar.href; }
        });
        var capa = document.querySelector(".modal-capa");
        capa.addEventListener("click", function (e) {
            if (e.target === capa) { window.location.href = cancelar.href; }
        });
    }

    // ------------------------------------------------------------ utilidades
    function mostrarError(form, campo, mensaje) {
        var p = form.querySelector('[data-error-de="' + campo + '"]');
        if (!p) { return; }
        p.querySelector("span").textContent = mensaje || "";
        p.closest(".campo").classList.toggle("campo--error", !!mensaje);
    }

    function leerDecimal(texto) {
        var t = (texto || "").trim();
        t = t.indexOf(".") >= 0 ? t.replace(/,/g, "") : t.replace(",", ".");
        return /^-?\d+(\.\d+)?$/.test(t) ? parseFloat(t) : NaN;
    }

    function leerEntero(texto) {
        var t = (texto || "").trim();
        return /^-?\d+$/.test(t) ? parseInt(t, 10) : NaN;
    }

    function soles(n) {
        return "S/ " + n.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    // ------------------------------------------------------------ formulario de producto
    var formProducto = document.querySelector("[data-formulario-producto]");
    if (formProducto) {
        var f = formProducto.elements;
        var esNuevo = formProducto.dataset.modo === "registrar";
        var tocado = false;

        var validar = function () {
            var errores = {};
            var codigo = f.codigo.value.trim();
            if (esNuevo) {
                if (!codigo) { errores.codigo = "El código es obligatorio"; }
                else if (codigo.length > 10) { errores.codigo = "El código admite como máximo 10 caracteres"; }
            }
            if (!f.nombre.value.trim()) { errores.nombre = "El nombre es obligatorio"; }
            if (!f.categoria.value) { errores.categoria = "Seleccione una categoría"; }

            var precio = leerDecimal(f.precio.value);
            if (!f.precio.value.trim()) { errores.precio = "El precio es obligatorio"; }
            else if (isNaN(precio)) { errores.precio = "Ingrese un precio válido, por ejemplo 80.00"; }
            else if (precio <= 0) { errores.precio = "El precio debe ser mayor que cero"; }

            // Al editar, el stock es de solo lectura (se ajusta en Inventario) y no se valida
            if (esNuevo) {
                var stock = leerEntero(f.stock.value);
                if (!f.stock.value.trim()) { errores.stock = "El stock es obligatorio"; }
                else if (isNaN(stock)) { errores.stock = "El stock debe ser un número entero"; }
                else if (stock < 0) { errores.stock = "El stock debe ser mayor o igual a cero"; }
            }
            return errores;
        };

        var pintar = function (errores) {
            ["codigo", "nombre", "categoria", "precio", "stock"].forEach(function (c) {
                if ((c === "codigo" || c === "stock") && !esNuevo) { return; }
                // No borra el error "código duplicado" que solo puede detectar el servidor
                var actual = formProducto.querySelector('[data-error-de="codigo"] span');
                if (c === "codigo" && !errores.codigo && actual && /registrado/.test(actual.textContent)
                        && f.codigo.dataset.original === f.codigo.value.trim().toUpperCase()) {
                    return;
                }
                mostrarError(formProducto, c, errores[c]);
            });

            var reglas = document.querySelector("[data-reglas]");
            if (reglas) {
                var marcar = function (regla, ok) {
                    var li = reglas.querySelector('[data-regla="' + regla + '"]');
                    li.classList.toggle("bien", ok);
                    li.classList.toggle("mal", !ok);
                };
                var errCodigo = formProducto.querySelector('[data-error-de="codigo"] span').textContent;
                marcar("codigo-obligatorio", !!f.codigo.value.trim());
                marcar("codigo-unico", !/registrado/.test(errCodigo));
                marcar("nombre", !errores.nombre);
                marcar("precio", !errores.precio);
                marcar("stock", !errores.stock);
                marcar("categoria", !errores.categoria);
            }

            var total = formProducto.querySelectorAll(".campo--error").length;
            var alerta = formProducto.querySelector("[data-alerta-errores]");
            alerta.classList.toggle("oculto", total === 0);
            alerta.querySelector("[data-alerta-titulo]").textContent =
                "Revise " + total + (total === 1 ? " campo" : " campos") + " antes de guardar";
        };

        // El estado debe ser coherente con el stock
        var sincronizarEstado = function () {
            var stock = leerEntero(f.stock.value);
            if (!isNaN(stock) && stock >= 0) {
                f.estado.value = stock > 0 ? "Disponible" : "Agotado";
            }
        };

        // Vista previa (solo en editar)
        var previa = function () {
            var set = function (k, v) {
                var el = document.querySelector('[data-previa="' + k + '"]');
                if (el) { el.textContent = v; }
            };
            var precio = leerDecimal(f.precio.value);
            var stock = leerEntero(f.stock.value);
            set("nombre", f.nombre.value.trim() || "—");
            set("categoria", f.categoria.value || "—");
            set("precio", isNaN(precio) ? "—" : soles(precio));
            set("stock", isNaN(stock) ? "—" : stock + " unidades");
            set("valor", isNaN(precio) || isNaN(stock) ? "—" : soles(precio * stock));
            var est = document.querySelector('[data-previa="estado"]');
            if (est) {
                est.textContent = f.estado.value;
                est.className = "insignia " + (f.estado.value === "Disponible" ? "insignia--ok" : "insignia--error");
            }
        };

        // Chips de categoría (solo en registrar)
        document.querySelectorAll("[data-chips-categoria] [data-valor]").forEach(function (chip) {
            chip.addEventListener("click", function () {
                f.categoria.value = chip.dataset.valor;
                f.categoria.dispatchEvent(new Event("change", { bubbles: true }));
            });
        });
        var marcarChip = function () {
            document.querySelectorAll("[data-chips-categoria] [data-valor]").forEach(function (chip) {
                chip.classList.toggle("activo", chip.dataset.valor === f.categoria.value);
            });
        };

        f.codigo.dataset.original = f.codigo.value.trim().toUpperCase();
        f.codigo.addEventListener("input", function () {
            f.codigo.value = f.codigo.value.toUpperCase();
        });

        var alCambiar = function (e) {
            if (e && e.target === f.stock) { sincronizarEstado(); }
            marcarChip();
            previa();
            if (tocado || formProducto.querySelector(".campo--error")) { pintar(validar()); }
        };
        formProducto.addEventListener("input", alCambiar);
        formProducto.addEventListener("change", alCambiar);

        formProducto.addEventListener("submit", function (e) {
            tocado = true;
            sincronizarEstado();
            var errores = validar();
            pintar(errores);
            if (Object.keys(errores).length > 0) {
                e.preventDefault();
                var primero = formProducto.querySelector(".campo--error input, .campo--error select");
                if (primero) { primero.focus(); }
            }
        });

        previa();
    }

    // ------------------------------------------------------------ búsqueda
    var formBusqueda = document.querySelector("[data-busqueda]");
    if (formBusqueda) {
        var texto = formBusqueda.querySelector("[data-valor-texto]");
        var caja = formBusqueda.querySelector("[data-valor-categoria]");
        var lista = caja.querySelector("select");
        var etiqueta = formBusqueda.querySelector("[data-etiqueta-valor]");
        var nombres = { codigo: "Código", nombre: "Nombre", categoria: "Categoría" };

        formBusqueda.querySelectorAll('input[name="criterio"]').forEach(function (radio) {
            radio.addEventListener("change", function () {
                var cat = radio.value === "categoria";
                texto.hidden = cat;
                texto.disabled = cat;
                caja.hidden = !cat;
                lista.disabled = !cat;
                etiqueta.textContent = nombres[radio.value];
                texto.placeholder = radio.value === "codigo" ? "Ej. P001" : "Ej. Logitech";
                (cat ? lista : texto).focus();
            });
        });

        formBusqueda.addEventListener("submit", function (e) {
            if (!texto.disabled && !texto.value.trim()) {
                e.preventDefault();
                texto.focus();
                texto.setAttribute("aria-invalid", "true");
            }
        });
    }

    // ------------------------------------------------------------ confirmación de acciones destructivas (administración)
    // data-confirmar="texto": en un formulario pregunta al enviarlo; en un botón o enlace, al pulsarlo.
    document.querySelectorAll("[data-confirmar]").forEach(function (el) {
        var evento = el.tagName === "FORM" ? "submit" : "click";
        el.addEventListener(evento, function (e) {
            if (!window.confirm(el.getAttribute("data-confirmar"))) { e.preventDefault(); }
        });
    });

    // ------------------------------------------------------------ doble envío (administración)
    // data-un-envio: al enviar el formulario deshabilita sus botones submit, salvo que se haya cancelado
    // (p. ej. con data-confirmar, cuyo listener se registró antes y ya llamó a preventDefault).
    document.querySelectorAll("form[data-un-envio]").forEach(function (form) {
        var botones = function () { return form.querySelectorAll('button[type="submit"], input[type="submit"]'); };
        form.addEventListener("submit", function (e) {
            if (e.defaultPrevented) { return; }
            // Después de que el navegador arme los datos: un botón deshabilitado no viajaría en el envío
            setTimeout(function () {
                botones().forEach(function (b) { b.disabled = true; });
            }, 0);
        });
        // Al volver con "atrás" desde la caché del navegador, los botones vuelven a estar disponibles
        window.addEventListener("pageshow", function (e) {
            if (e.persisted) { botones().forEach(function (b) { b.disabled = false; }); }
        });
    });

    // ------------------------------------------------------------ comprobante: imprimir
    document.querySelectorAll("[data-imprimir]").forEach(function (b) {
        b.addEventListener("click", function () { window.print(); });
    });

    // ------------------------------------------------------------ checkout (pasarela de pago simulada)
    // Las mismas reglas se aplican en el servidor (PasarelaPagoService.validar).
    var formPago = document.querySelector("[data-formulario-pago]");
    if (formPago) {
        var ep = formPago.elements;
        var camposPago = ["tipoEnvio", "distrito", "direccion", "telefonoContacto", "correoComprobante",
                          "metodo", "numeroTarjeta", "titular", "vencimiento", "cvv", "celular",
                          "codigoYape", "tipoComprobante", "documento", "razonSocial"];
        var botonPagar = formPago.querySelector("[data-boton-pagar]");
        var textoBoton = botonPagar.querySelector("[data-texto-boton]") || botonPagar;
        var textoBotonOriginal = textoBoton.textContent;
        var marcaTarjeta = formPago.querySelector("[data-marca-tarjeta]");
        var etiquetaDocumento = formPago.querySelector("[data-etiqueta-documento]");
        var campoRazon = formPago.querySelector("[data-campo-razon]");
        var intentado = false;
        var enviando = false;

        var soloDigitos = function (texto) { return (texto || "").replace(/\D/g, ""); };

        var detectarMarca = function (digitos) {
            if (/^4/.test(digitos)) { return "Visa"; }
            if (/^3[47]/.test(digitos)) { return "American Express"; }
            if (/^(5[1-5]|222[1-9]|22[3-9]\d|2[3-6]\d\d|27[01]\d|2720)/.test(digitos)) { return "Mastercard"; }
            return "";
        };

        var luhnValido = function (digitos) {
            var suma = 0;
            var duplicar = false;
            for (var i = digitos.length - 1; i >= 0; i--) {
                var d = digitos.charCodeAt(i) - 48;
                if (duplicar) { d *= 2; if (d > 9) { d -= 9; } }
                suma += d;
                duplicar = !duplicar;
            }
            return digitos.length > 0 && suma % 10 === 0;
        };

        // Reformatea un campo y conserva la posición del cursor cuando se edita a mitad del texto.
        var reformatear = function (campo, formato, borrando) {
            var anterior = campo.value;
            var pos = campo.selectionStart;
            var digitosAntes = anterior.slice(0, pos).replace(/\D/g, "").length;
            campo.value = formato(anterior, borrando);
            if (pos !== null && pos < anterior.length && document.activeElement === campo) {
                var cuenta = 0;
                var i = 0;
                while (i < campo.value.length && cuenta < digitosAntes) {
                    if (/\d/.test(campo.value.charAt(i))) { cuenta++; }
                    i++;
                }
                campo.setSelectionRange(i, i);
            }
        };

        var formatoTarjeta = function (valor) {
            return soloDigitos(valor).slice(0, 19).replace(/(\d{4})(?=\d)/g, "$1 ");
        };

        var formatoVencimiento = function (valor, borrando) {
            var d = soloDigitos(valor).slice(0, 4);
            if (d.length === 1 && d > "1" && !borrando) { d = "0" + d; }
            if (d.length > 2) { return d.slice(0, 2) + "/" + d.slice(2); }
            if (d.length === 2 && !borrando) { return d + "/"; }
            return d;
        };

        var metodoElegido = function () { return ep.metodo.value || ""; };
        var esFactura = function () { return ep.tipoComprobante.value === "Factura"; };

        var actualizarMarca = function () {
            var marca = detectarMarca(soloDigitos(ep.numeroTarjeta.value));
            marcaTarjeta.textContent = marca;
            marcaTarjeta.hidden = !marca;
            marcaTarjeta.dataset.marca = marca;
            // American Express usa 4 dígitos de CVV; Visa y Mastercard, 3.
            ep.cvv.maxLength = marca && marca !== "American Express" ? 3 : 4;
            if (ep.cvv.value.length > ep.cvv.maxLength) { ep.cvv.value = ep.cvv.value.slice(0, ep.cvv.maxLength); }
        };

        var actualizarMetodo = function () {
            var metodo = metodoElegido();
            formPago.querySelectorAll("[data-panel-metodo]").forEach(function (panel) {
                panel.hidden = panel.dataset.panelMetodo !== metodo;
            });
        };

        var actualizarComprobante = function () {
            var factura = esFactura();
            etiquetaDocumento.textContent = (factura ? "RUC" : "DNI") + " *";
            ep.documento.maxLength = factura ? 11 : 8;
            ep.documento.placeholder = factura ? "11 dígitos" : "8 dígitos";
            ep.documento.value = ep.documento.value.slice(0, ep.documento.maxLength);
            campoRazon.hidden = !factura;
        };

        var actualizarEnvio = function () {
            var esRetiro = ep.tipoEnvio && ep.tipoEnvio.value === "RETIRO";
            formPago.querySelectorAll("[data-panel-envio]").forEach(function (panel) {
                panel.hidden = panel.dataset.panelEnvio === "RETIRO" ? !esRetiro : esRetiro;
            });
        };

        var validarPago = function () {
            var errores = {};

            // Validación de logística y despacho
            var esRetiro = ep.tipoEnvio && ep.tipoEnvio.value === "RETIRO";
            if (!esRetiro) {
                var dir = ep.direccion ? ep.direccion.value.trim() : "";
                if (!dir || dir.length < 5) {
                    errores.direccion = "Ingresa la dirección exacta de entrega";
                }
                var dist = ep.distrito ? ep.distrito.value.trim() : "";
                if (!dist) {
                    errores.distrito = "Ingresa el distrito o localidad";
                }
            }
            var telContacto = ep.telefonoContacto ? soloDigitos(ep.telefonoContacto.value) : "";
            if (!telContacto) {
                errores.telefonoContacto = "Ingresa un celular de contacto para la entrega";
            } else if (!/^9\d{8}$/.test(telContacto)) {
                errores.telefonoContacto = "El celular debe tener 9 dígitos y empezar con 9";
            }
            var correoComp = ep.correoComprobante ? ep.correoComprobante.value.trim() : "";
            if (correoComp && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correoComp)) {
                errores.correoComprobante = "Ingresa un correo electrónico válido";
            }

            var metodo = metodoElegido();

            if (metodo === "TARJETA") {
                var digitos = soloDigitos(ep.numeroTarjeta.value);
                var marca = "";
                if (!digitos) { errores.numeroTarjeta = "Ingresa el número de tarjeta"; }
                else if (digitos.length < 13 || digitos.length > 19) {
                    errores.numeroTarjeta = "El número de tarjeta debe tener entre 13 y 19 dígitos";
                } else {
                    marca = detectarMarca(digitos);
                    if (!marca) { errores.numeroTarjeta = "Tarjeta no reconocida: usa Visa, Mastercard o American Express"; }
                    else if (!luhnValido(digitos)) { errores.numeroTarjeta = "Número de tarjeta inválido"; }
                }

                var titular = ep.titular.value.trim();
                if (!titular) { errores.titular = "Ingresa el nombre del titular tal como figura en la tarjeta"; }
                else if (!/^[\p{L}\p{M} '.\-]{3,60}$/u.test(titular)) { errores.titular = "El titular debe tener entre 3 y 60 caracteres: letras, espacios, apóstrofo, punto o guion"; }

                var vence = ep.vencimiento.value.trim();
                var partes = /^(0[1-9]|1[0-2])\/(\d{2})$/.exec(vence);
                if (!vence) { errores.vencimiento = "Ingresa la fecha de vencimiento (MM/AA)"; }
                else if (!partes) { errores.vencimiento = "Vencimiento inválido: usa el formato MM/AA"; }
                else {
                    var hoy = new Date();
                    var indiceActual = hoy.getFullYear() * 12 + hoy.getMonth();
                    var indiceVence = (2000 + parseInt(partes[2], 10)) * 12 + (parseInt(partes[1], 10) - 1);
                    if (indiceVence < indiceActual) { errores.vencimiento = "La tarjeta está vencida"; }
                    else if (indiceVence > indiceActual + 120) { errores.vencimiento = "El vencimiento no puede superar los 10 años"; }
                }

                var largoCvv = marca === "American Express" ? 4 : 3;
                var cvv = ep.cvv.value.trim();
                if (!cvv) { errores.cvv = "Ingresa el código de seguridad (CVV)"; }
                else if (!new RegExp("^\\d{" + largoCvv + "}$").test(cvv)) { errores.cvv = "El CVV debe tener " + largoCvv + " dígitos"; }
            } else if (metodo === "YAPE") {
                var celular = ep.celular.value.replace(/[\s-]/g, "");
                if (!celular) { errores.celular = "Ingresa el celular asociado a tu Yape"; }
                else if (!/^9\d{8}$/.test(celular)) { errores.celular = "El celular debe tener 9 dígitos y empezar con 9"; }

                var codigoYape = ep.codigoYape.value.trim();
                if (!codigoYape) { errores.codigoYape = "Ingresa el código de aprobación de Yape"; }
                else if (!/^\d{6}$/.test(codigoYape)) { errores.codigoYape = "El código de aprobación debe tener 6 dígitos"; }
            } else {
                errores.metodo = "Elige un método de pago: tarjeta o Yape";
            }

            var documento = ep.documento.value.replace(/[\s-]/g, "");
            if (esFactura()) {
                if (!documento) { errores.documento = "Ingresa el RUC"; }
                else if (!/^(10|20)\d{9}$/.test(documento)) { errores.documento = "El RUC debe tener 11 dígitos y empezar con 10 o 20"; }
                var razon = ep.razonSocial.value.trim();
                if (!razon) { errores.razonSocial = "Ingresa la razón social de la empresa"; }
                else if (razon.length > 150) { errores.razonSocial = "La razón social admite como máximo 150 caracteres"; }
            } else if (ep.tipoComprobante.value === "Boleta") {
                if (!documento) { errores.documento = "Ingresa tu DNI"; }
                else if (!/^\d{8}$/.test(documento)) { errores.documento = "El DNI debe tener 8 dígitos"; }
            } else {
                errores.tipoComprobante = "Elige el tipo de comprobante: Boleta o Factura";
            }
            return errores;
        };

        var actualizarAlertaPago = function () {
            var alerta = formPago.querySelector("[data-alerta-errores]");
            if (!alerta) { return; }
            var total = formPago.querySelectorAll(".campo--error").length;
            alerta.classList.toggle("oculto", total === 0);
            alerta.querySelector("[data-alerta-titulo]").textContent =
                "Revise " + total + (total === 1 ? " campo" : " campos") + " antes de pagar";
        };

        var pintarPago = function (campos, errores) {
            campos.forEach(function (c) { mostrarError(formPago, c, errores[c]); });
            actualizarAlertaPago();
        };

        var limpiarPago = function (campos) {
            campos.forEach(function (c) { mostrarError(formPago, c, ""); });
            actualizarAlertaPago();
        };

        // Solo se pinta el campo que cambió, salvo que ya se haya intentado pagar.
        var alEditarPago = function (e) {
            var campo = e.target.closest ? e.target.closest(".campo") : null;
            var conError = campo && campo.classList.contains("campo--error");
            if (!intentado && !conError) { return; }
            pintarPago(intentado ? camposPago : [e.target.name], validarPago());
        };

        // Formato y filtrado mientras se escribe
        ep.numeroTarjeta.addEventListener("input", function () {
            reformatear(ep.numeroTarjeta, formatoTarjeta);
            actualizarMarca();
        });
        ep.vencimiento.addEventListener("input", function (e) {
            reformatear(ep.vencimiento, formatoVencimiento, !!(e.inputType && e.inputType.indexOf("delete") === 0));
        });
        ["cvv", "celular", "codigoYape", "documento", "telefonoContacto"].forEach(function (nombre) {
            if (ep[nombre]) {
                ep[nombre].addEventListener("input", function () {
                    var limpio = soloDigitos(ep[nombre].value);
                    if (limpio !== ep[nombre].value) { ep[nombre].value = limpio; }
                });
            }
        });

        formPago.addEventListener("input", alEditarPago);
        formPago.addEventListener("change", alEditarPago);

        formPago.querySelectorAll('input[name="tipoEnvio"]').forEach(function (radio) {
            radio.addEventListener("change", function () {
                actualizarEnvio();
                if (intentado) { pintarPago(camposPago, validarPago()); }
                else { limpiarPago(["distrito", "direccion"]); }
            });
        });
        formPago.querySelectorAll('input[name="metodo"]').forEach(function (radio) {
            radio.addEventListener("change", function () {
                actualizarMetodo();
                if (intentado) { pintarPago(camposPago, validarPago()); }
                else { limpiarPago(["metodo", "numeroTarjeta", "titular", "vencimiento", "cvv", "celular", "codigoYape"]); }
            });
        });
        formPago.querySelectorAll('input[name="tipoComprobante"]').forEach(function (radio) {
            radio.addEventListener("change", function () {
                actualizarComprobante();
                if (intentado) { pintarPago(camposPago, validarPago()); }
                else { limpiarPago(["tipoComprobante", "documento", "razonSocial"]); }
            });
        });

        formPago.addEventListener("submit", function (e) {
            if (enviando) { e.preventDefault(); return; }
            intentado = true;
            var errores = validarPago();
            pintarPago(camposPago, errores);
            if (Object.keys(errores).length > 0) {
                e.preventDefault();
                var primero = null;
                formPago.querySelectorAll(".campo--error input").forEach(function (input) {
                    if (!primero && input.offsetParent !== null) { primero = input; }
                });
                if (primero) { primero.focus(); }
                return;
            }
            // Lo que no corresponde al método o comprobante elegido no se envía al servidor.
            formPago.querySelectorAll("[data-panel-metodo]").forEach(function (panel) {
                if (panel.hidden) {
                    panel.querySelectorAll("input").forEach(function (input) { input.value = ""; });
                }
            });
            if (!esFactura()) { ep.razonSocial.value = ""; }
            enviando = true;
            botonPagar.disabled = true;
            botonPagar.setAttribute("aria-busy", "true");
            textoBoton.textContent = "Procesando pago…";
        });

        // Al volver con el botón "Atrás" el formulario se restaura: se devuelve el botón a su estado inicial.
        window.addEventListener("pageshow", function (e) {
            if (e.persisted) {
                enviando = false;
                botonPagar.disabled = false;
                botonPagar.removeAttribute("aria-busy");
                textoBoton.textContent = textoBotonOriginal;
            }
        });

        actualizarEnvio();
        actualizarMetodo();
        actualizarComprobante();
        actualizarMarca();
        actualizarAlertaPago();
    }

    // ------------------------------------------------------------ inicio de sesión (Ventana de Ingreso NEXORA)
    var formLogin = document.querySelector("[data-formulario-login]");
    if (formLogin) {
        formLogin.addEventListener("submit", function (e) {
            var u = formLogin.elements.usuario.value.trim();
            var c = formLogin.elements.clave.value;
            mostrarError(formLogin, "usuario", u ? "" : "Ingrese su usuario");
            mostrarError(formLogin, "clave", c ? "" : "Ingrese su contraseña");
            if (!u || !c) { e.preventDefault(); }
        });
    }

    // ------------------------------------------------------------ registro de nuevo cliente
    var formRegistro = document.querySelector("[data-formulario-registro]");
    if (formRegistro) {
        var er = formRegistro.elements;
        ["dni", "telefono"].forEach(function (nombre) {
            if (er[nombre]) {
                er[nombre].addEventListener("input", function () {
                    var limpio = (er[nombre].value || "").replace(/\D/g, "");
                    if (limpio !== er[nombre].value) { er[nombre].value = limpio; }
                });
            }
        });

        formRegistro.addEventListener("submit", function (e) {
            var valido = true;
            var nom = er.nombreCompleto ? er.nombreCompleto.value.trim() : "";
            var dni = er.dni ? (er.dni.value || "").replace(/\D/g, "") : "";
            var tel = er.telefono ? (er.telefono.value || "").replace(/\D/g, "") : "";
            var cor = er.correo ? er.correo.value.trim() : "";
            var usr = er.nuevoUsuario ? er.nuevoUsuario.value.trim() : "";
            var c1 = er.nuevaClave ? er.nuevaClave.value : "";
            var c2 = er.confirmarClave ? er.confirmarClave.value : "";

            var errorNom = !nom ? "Ingrese sus nombres y apellidos" : (nom.length < 3 ? "Mínimo 3 caracteres" : "");
            var errorDni = !dni ? "Ingrese su DNI" : (!/^\d{8}$/.test(dni) ? "El DNI debe tener 8 dígitos" : "");
            var errorTel = !tel ? "Ingrese su celular" : (!/^9\d{8}$/.test(tel) ? "Debe tener 9 dígitos y empezar con 9" : "");
            var errorCor = !cor ? "Ingrese su correo" : (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(cor) ? "Correo no válido" : "");
            var errorUsr = !usr ? "Ingrese un nombre de usuario"
                : (!/^[a-z0-9._-]{3,40}$/.test(usr.toLowerCase()) ? "De 3 a 40 caracteres: letras, números, punto, guion o guion bajo" : "");
            var errorC1 = !c1 ? "Ingrese una contraseña"
                : (c1 !== c1.trim() ? "No puede empezar ni terminar con espacios"
                : (c1.length < 8 ? "Mínimo 8 caracteres"
                : (c1.length > 100 ? "Máximo 100 caracteres"
                : (!/\p{L}/u.test(c1) || !/\p{Nd}/u.test(c1) ? "Debe combinar letras y números" : ""))));
            var errorC2 = !c2 ? "Confirme la contraseña" : (c1 !== c2 ? "Las contraseñas no coinciden" : "");

            mostrarError(formRegistro, "nombreCompleto", errorNom);
            mostrarError(formRegistro, "dni", errorDni);
            mostrarError(formRegistro, "telefono", errorTel);
            mostrarError(formRegistro, "correo", errorCor);
            mostrarError(formRegistro, "nuevoUsuario", errorUsr);
            mostrarError(formRegistro, "nuevaClave", errorC1);
            mostrarError(formRegistro, "confirmarClave", errorC2);

            if (errorNom || errorDni || errorTel || errorCor || errorUsr || errorC1 || errorC2) {
                e.preventDefault();
                var primero = formRegistro.querySelector(".campo--error input");
                if (primero) { primero.focus(); }
            }
        });
    }
})();
