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
