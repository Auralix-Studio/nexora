# NEXORA (semana04)

Tienda en línea Java 17 / Jakarta EE (servlets + JSP + JSTL 3) para Tomcat 11, con pasarela de pago
simulada. SQL Server es opcional: sin conexión, los servicios usan un respaldo en memoria.
Estado detallado, contratos y pendientes: `ORQUESTACION.md`. Bitácora académica: `PROCESO_REESTRUCTURACION_TIENDA.md`.

## Comandos
- Build + pruebas (145 JUnit): `"/c/Program Files/NetBeans-25/netbeans/java/maven/bin/mvn" -q -o package 2>&1 | tail -30`
  (Maven no está en el PATH; usa el que trae NetBeans 25).
- Prueba de punta a punta (32 comprobaciones): `bash docs/pruebas/e2e.sh http://localhost:8090/Semana04`
  con Tomcat recién iniciado (los valores esperados suponen datos semilla).
- Maven no compila JSP: los errores de vistas solo aparecen en ejecución; pruébalas en Tomcat.

## Despliegue local
- Tomcat 11.0.26 en `C:\Users\aless\Downloads\apache-tomcat`; el contexto `conf/Catalina/localhost/Semana04.xml`
  apunta a `target/Semana04` (WAR desplegado como carpeta).
- El puerto 8080 de este equipo lo ocupa otra aplicación (Node). No la detengas. Para probar, usa una
  `CATALINA_BASE` aparte con `server.xml` en los puertos 8090 y 8095 (apagado), y solo `Semana04.xml`; arráncala con
  `catalina.sh run` en segundo plano y apágala con `catalina.sh stop` (misma CATALINA_BASE).
- SQL Server: ver "Pendiente: SQL Server" en `ORQUESTACION.md` (BD_NEXORA aún no creada, TCP 1433 deshabilitado).

## Convenciones
- Código y textos en español de Perú; javadoc breve; sin emojis.
- Controladores: `Controlador.vista(req, resp, "carpeta/vista")`, `Controlador.mensaje(req, "ok|error", texto)`,
  patrón POST-Redirect-GET.
- Todo formulario POST lleva `<input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">`;
  `SesionFilter` lo valida y decide permisos con la ruta normalizada (`getServletPath()` + `getPathInfo()`).
- El POST de `/pago` exige además `tokenCheckout` (un solo uso, emitido en el GET del checkout).
- Solo el rol Cliente compra. Nunca guardar ni mostrar el número completo de la tarjeta ni el CVV.
- Salida dinámica en JSP siempre con `c:out` o `fn:escapeXml`.
- `entregables/` (informes .docx) es el entregable académico: no se modifica.
- No es repositorio git: antes de borrar o sobrescribir, revisa el archivo; no hay forma de deshacer.

## Orquestación eficiente
Al trabajar con subagentes en este repo, sigue estas reglas (agentes en `.claude/agents/`):
- Decide primero si orquestar: con menos de unas 3 tareas o un bug puntual, hazlo directo.
- Modelo según el riesgo: `explorador` y `implementador-rapido` (haiku) para lo mecánico, `implementador` (sonnet)
  para lógica con especificación clara, `planificador` y `revisor` (opus) para diseño, contratos y revisión.
- Define los contratos (modelos, firmas, rutas, atributos de vista) antes de repartir; nada de dos agentes
  paralelos en los mismos archivos.
- Cada encargo debe ser autosuficiente y mínimo: objetivo, archivos permitidos, patrón a seguir,
  "terminado cuando", comando de verificación, y un informe breve sin código pegado.
- Verifica tú cada ola (build, pruebas, E2E en Tomcat): el informe del subagente no es prueba.
- Escalado: primer fallo → mismo nivel con el error exacto (SendMessage al mismo agente); segundo → nivel superior.
- Revisión independiente con `revisor` al cerrar cada ola relevante; los bloqueantes vuelven como tareas nuevas.
- Ahorro: Grep antes de Read, leer por tramos, no leer `target/`, imágenes ni `.docx`, recortar salidas,
  agrupar llamadas independientes, no releer tras editar, respuestas breves.
- Mantén `ORQUESTACION.md` al día (estado, modelos, escalados, decisiones) para retomar en otra sesión.
