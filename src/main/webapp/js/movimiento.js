/*
 * NEXORA — movimiento de la interfaz (sin librerías, compatible con CSP script-src 'self').
 * Se carga en <head> para marcar <html class="js"> antes del primer pintado; el resto espera al DOM.
 * Sin JavaScript todo el contenido sigue visible: los estados ocultos de estilos.css dependen de .js.
 * Con "reducir movimiento" no hay animaciones: solo se aplican los estados finales.
 */
(function () {
    "use strict";

    var raiz = document.documentElement;
    raiz.classList.add("js");

    var consulta = function (q) { return window.matchMedia ? window.matchMedia(q).matches : false; };
    var reducido = consulta("(prefers-reduced-motion: reduce)");
    var punteroFino = consulta("(hover: hover) and (pointer: fine)");
    var todos = function (sel, ctx) { return Array.prototype.slice.call((ctx || document).querySelectorAll(sel)); };

    function soles(n) {
        return "S/ " + n.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    // ------------------------------------------------------------ contadores (data-valor + data-formato)
    // El texto del servidor es el valor final. Si no hay data-valor se deduce de él (S/ 1,234.50 o un entero).
    function prepararContador(el) {
        var finalTexto = el.textContent;
        var fin = parseFloat(el.getAttribute("data-valor"));
        var formato = el.getAttribute("data-formato");
        if (isNaN(fin)) {
            var t = finalTexto.trim();
            if (/^S\/ [\d,]+\.\d{2}$/.test(t)) { fin = parseFloat(t.slice(3).replace(/,/g, "")); formato = "soles"; }
            else if (/^\d{1,9}$/.test(t)) { fin = parseInt(t, 10); formato = "entero"; }
            else { return null; }
        }
        var escribir = function (v) { el.textContent = formato === "soles" ? soles(v) : String(Math.round(v)); };
        el.setAttribute("data-final", finalTexto);
        escribir(0);
        return function () {
            var t0 = null;
            var paso = function (ahora) {
                if (t0 === null) { t0 = ahora; }
                var p = Math.min((ahora - t0) / 1300, 1);
                if (p >= 1) { el.textContent = finalTexto; return; }
                escribir(fin * (1 - Math.pow(2, -10 * p)));
                requestAnimationFrame(paso);
            };
            requestAnimationFrame(paso);
        };
    }

    // ------------------------------------------------------------ titular por palabras (data-dividir)
    function dividir(el) {
        var palabras = el.textContent.trim().split(/\s+/);
        el.textContent = "";
        palabras.forEach(function (palabra, i) {
            var fuera = document.createElement("span");
            var dentro = document.createElement("span");
            fuera.className = "pal";
            dentro.className = "pal__in";
            dentro.style.setProperty("--i", i);
            dentro.textContent = palabra;
            fuera.appendChild(dentro);
            el.appendChild(fuera);
            if (i < palabras.length - 1) { el.appendChild(document.createTextNode(" ")); }
        });
    }

    // ------------------------------------------------------------ aparición al hacer scroll (data-revelar)
    function iniciarRevelado() {
        var objetivos = todos("[data-revelar]");
        var contadores = [];
        if (!reducido) {
            todos(".indicador__valor").forEach(function (el) {
                var arrancar = prepararContador(el);
                if (arrancar) { contadores.push({ el: el, arrancar: arrancar }); }
            });
        }
        if (!("IntersectionObserver" in window) || reducido) {
            objetivos.forEach(function (el) { el.classList.add("revelado"); });
            contadores.forEach(function (c) { c.arrancar(); });
            return;
        }
        var io = new IntersectionObserver(function (entradas) {
            var visibles = entradas.filter(function (e) { return e.isIntersecting; });
            visibles.sort(function (a, b) {
                return (a.boundingClientRect.top - b.boundingClientRect.top) || (a.boundingClientRect.left - b.boundingClientRect.left);
            });
            visibles.forEach(function (e, i) {
                e.target.style.setProperty("--retardo", Math.min(i, 8) * 70 + "ms");
                e.target.classList.add("revelado");
                io.unobserve(e.target);
            });
        }, { threshold: 0.12, rootMargin: "0px 0px -6% 0px" });
        objetivos.forEach(function (el) { io.observe(el); });

        var ioContador = new IntersectionObserver(function (entradas) {
            entradas.forEach(function (e) {
                if (!e.isIntersecting) { return; }
                ioContador.unobserve(e.target);
                contadores.forEach(function (c) { if (c.el === e.target) { c.arrancar(); } });
            });
        }, { threshold: 0.4 });
        contadores.forEach(function (c) { ioContador.observe(c.el); });
    }

    // ------------------------------------------------------------ inclinación 3D y parallax con el puntero
    function seguirPuntero(zona, alMover, alSalir) {
        var cuadro = 0;
        var x = 0;
        var y = 0;
        var pintar = function () { cuadro = 0; alMover(x, y); };
        zona.addEventListener("pointermove", function (e) {
            if (e.pointerType !== "mouse") { return; }
            var r = zona.getBoundingClientRect();
            x = (e.clientX - r.left) / r.width - 0.5;
            y = (e.clientY - r.top) / r.height - 0.5;
            if (!cuadro) { cuadro = requestAnimationFrame(pintar); }
        });
        zona.addEventListener("pointerleave", function () {
            if (cuadro) { cancelAnimationFrame(cuadro); cuadro = 0; }
            alSalir();
        });
    }

    function iniciarPuntero() {
        if (reducido || !punteroFino) { return; }
        todos("[data-parallax]").forEach(function (zona) {
            seguirPuntero(zona, function (x, y) {
                zona.style.setProperty("--px", (x * 2).toFixed(3));
                zona.style.setProperty("--py", (y * 2).toFixed(3));
            }, function () {
                zona.style.setProperty("--px", "0");
                zona.style.setProperty("--py", "0");
            });
        });
        todos("[data-inclinar]").forEach(function (tarjeta) {
            seguirPuntero(tarjeta, function (x, y) {
                tarjeta.style.setProperty("--ry", (x * 9).toFixed(2) + "deg");
                tarjeta.style.setProperty("--rx", (-y * 7).toFixed(2) + "deg");
            }, function () {
                tarjeta.style.removeProperty("--rx");
                tarjeta.style.removeProperty("--ry");
            });
        });
    }

    // ------------------------------------------------------------ esqueleto de imágenes de producto
    function iniciarImagenes() {
        todos("img[data-imagen-producto]").forEach(function (img) {
            var caja = img.closest(".tarjeta-producto__media");
            if (!caja) { return; }
            var listo = function () { caja.classList.add("cargada"); };
            if (img.complete) { listo(); }
            else { img.addEventListener("load", listo); img.addEventListener("error", listo); }
        });
    }

    // ------------------------------------------------------------ avisos: salida animada y cierre automático
    function cerrarAviso(aviso) {
        if (!aviso.isConnected || aviso.classList.contains("saliendo")) { return; }
        if (reducido) { aviso.remove(); return; }
        var hecho = false;
        var quitar = function () {
            if (hecho || !aviso.isConnected) { return; }
            hecho = true;
            // La transición de vista desplaza el contenido hacia arriba en lugar de saltar
            if (document.startViewTransition) { document.startViewTransition(function () { aviso.remove(); }); }
            else { aviso.remove(); }
        };
        aviso.classList.add("saliendo");
        aviso.addEventListener("animationend", function (e) { if (e.target === aviso) { quitar(); } });
        setTimeout(quitar, 450);
    }

    function iniciarAvisos() {
        todos("[data-aviso]").forEach(function (aviso) {
            var cerrar = aviso.querySelector("[data-cerrar-aviso]");
            if (cerrar) { cerrar.addEventListener("click", function () { cerrarAviso(aviso); }); }
            if (!aviso.classList.contains("aviso--ok")) { return; }
            var restante = 6000;
            var inicio = 0;
            var reloj = 0;
            var arrancar = function () {
                inicio = Date.now();
                clearTimeout(reloj);
                reloj = setTimeout(function () { cerrarAviso(aviso); }, restante);
            };
            var pausar = function () {
                if (aviso.classList.contains("pausado")) { return; }
                clearTimeout(reloj);
                restante = Math.max(restante - (Date.now() - inicio), 600);
                aviso.classList.add("pausado");
            };
            var seguir = function () {
                if (!aviso.classList.contains("pausado")) { return; }
                aviso.classList.remove("pausado");
                arrancar();
            };
            aviso.addEventListener("mouseenter", pausar);
            aviso.addEventListener("mouseleave", seguir);
            aviso.addEventListener("focusin", pausar);
            aviso.addEventListener("focusout", seguir);
            arrancar();
        });
    }

    // ------------------------------------------------------------ modales: foco inicial y Tab atrapado
    function iniciarModal() {
        var capa = document.querySelector(".modal-capa[role='dialog']");
        if (!capa) { return; }
        var enfocables = function () {
            return todos('a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled])', capa);
        };
        var primero = capa.querySelector('input:not([type="hidden"])') || enfocables()[0];
        if (primero) { setTimeout(function () { primero.focus({ preventScroll: true }); }, 60); }
        capa.addEventListener("keydown", function (e) {
            if (e.key !== "Tab") { return; }
            var lista = enfocables();
            if (!lista.length) { return; }
            var a = lista[0];
            var z = lista[lista.length - 1];
            if (e.shiftKey && document.activeElement === a) { e.preventDefault(); z.focus(); }
            else if (!e.shiftKey && document.activeElement === z) { e.preventDefault(); a.focus(); }
        });
    }

    // ------------------------------------------------------------ arranque
    function arrancar() {
        try {
            todos("[data-dividir]").forEach(function (el) {
                if (!reducido) { dividir(el); }
                el.classList.add("dividido");
            });
            iniciarImagenes();
            iniciarRevelado();
            iniciarPuntero();
            iniciarAvisos();
            iniciarModal();
        } catch (e) {
            // Ante cualquier fallo nada debe quedar oculto
            todos("[data-revelar]").forEach(function (el) { el.classList.add("revelado"); });
            todos("[data-dividir]").forEach(function (el) { el.classList.add("dividido"); });
            todos(".tarjeta-producto__media").forEach(function (el) { el.classList.add("cargada"); });
            todos("[data-final]").forEach(function (el) { el.textContent = el.getAttribute("data-final"); });
        }
    }

    if (document.readyState === "loading") { document.addEventListener("DOMContentLoaded", arrancar); }
    else { arrancar(); }
})();
