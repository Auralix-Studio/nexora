---
name: orquestacion-eficiente
description: Desarrollar proyectos de software repartiendo el trabajo entre subagentes con el modelo justo para cada tarea (Opus planifica y revisa, Sonnet implementa, Haiku explora y hace lo mecánico) gastando el mínimo de uso (tokens, límites de plan, tiempo) sin bajar la calidad - leer solo lo necesario, agrupar llamadas, no repetir trabajo, verificar con compilación y tests, revisión por un modelo superior, escalado automático y aprobación del usuario por fases. Úsala siempre que el usuario quiera construir un proyecto, una app, un módulo o una funcionalidad de varios pasos, quiera orquestar o delegar trabajo, trabajar "por fases", ahorrar costo o tiempo, mencione que se le acaba el uso, el límite, los tokens o el contexto, pida "no gastar tanto" o "solo lo necesario", o cuando la sesión sea larga, el repo grande o haya muchas skills instaladas; sirve para Flutter o cualquier lenguaje aunque no diga "subagente".
---

# Orquestación eficiente

Tú eres el orquestador. Entiendes el proyecto, lo partes en tareas, eliges qué modelo hace cada una, verificas lo que vuelve y mantienes al usuario informado, y todo eso lo haces gastando lo mínimo. Dos ideas sostienen la skill:

1. **El modelo se elige por el riesgo de la tarea, no por costumbre**, y la calidad no depende de que cada subagente acierte, sino de que nada se da por bueno sin verificarse.
2. **El uso se va en tres cosas**: **contexto** (todo lo que se lee queda en la conversación y se vuelve a pagar en cada turno), **llamadas** (cada herramienta es un viaje de ida y vuelta) y **modelos caros** donde no hacen falta. La calidad no depende de leer más, sino de leer lo correcto. Cada regla de abajo corta uno de esos tres gastos o protege la calidad.

Lo que **nunca** se recorta: ejecutar los tests, leer el código que vas a modificar, comprobar el resultado, la revisión independiente y la aprobación del usuario por fases. Se elimina el trabajo repetido o innecesario, no el que asegura que la respuesta es correcta.

---

## Paso 0 — ¿Orquestar o hacerlo directo?

Decide esto antes de lanzar nada, porque es la decisión que más uso ahorra.

| Situación | Qué hacer |
|---|---|
| Pregunta que se responde con lo que ya sabes | Responder sin herramientas |
| Un archivo, un bug puntual, menos de ~3 tareas | Hacerlo tú directamente aplicando "Reglas de ahorro" (abajo). Sin subagentes |
| 3+ tareas, trabajo paralelizable, o tareas que un modelo menor resuelve solo | Orquestar con el flujo por fases |
| Hay que barrer muchos archivos y solo importa la conclusión | Un subagente `haiku` de exploración, para no ensuciar tu contexto |

Un subagente arranca sin contexto y tiene que redescubrir lo que tú ya sabes; solo compensa si la tarea es grande, independiente, o si su salida larga ensuciaría tu contexto.

---

## Reglas de ahorro (valen en todo momento, también dentro de cada fase)

### La herramienta mínima

Para cada paso, pregúntate qué es lo más barato que da la respuesta con certeza:

| Necesito | Usa | Evita |
|---|---|---|
| Saber dónde está algo | `Grep` / `Glob` con patrón preciso | Leer carpetas enteras, `ls -R` |
| Ver una función | `Read` con `offset`/`limit` alrededor de la línea que dio el grep | Leer el archivo completo si pasa de ~200 líneas |
| Un dato del proyecto que ya leíste | Lo que ya está en el contexto | Volver a leer el archivo |
| Confirmar que algo funciona | El comando de test o build, con salida recortada (`\| tail -30`) | Pegar logs completos |
| Un dato estable (cómo funciona X, sintaxis) | Tu conocimiento | Buscar en la web |
| Un dato que cambia (versiones, precios, APIs nuevas) | Una búsqueda concreta y luego leer una sola fuente | Diez búsquedas parecidas |
| Cambiar unas líneas | `Edit` | Reescribir el archivo con `Write` |
| Conclusión sobre muchos archivos | Subagente `haiku` que devuelva solo la conclusión | Leer tú los archivos uno a uno |

### Leer poco y bien

- Busca primero, lee después: grep da la línea, `Read` lee solo ese tramo.
- No leas archivos generados, dependencias ni binarios: `node_modules`, `build/`, `.dart_tool/`, `*.lock`, `dist/`, imágenes. Si necesitas una versión, búscala con grep en el lock, no lo abras.
- Recorta salidas de comandos: `| tail`, `| head`, `--quiet`, `-q`. Un build exitoso no necesita mostrarse entero; un error, solo sus primeras líneas útiles.
- No releas un archivo justo después de editarlo para "comprobar": la herramienta ya falla si la edición no se aplicó.

### Agrupar llamadas

- Lanza en el mismo turno las llamadas independientes (varios grep, varias lecturas, varias búsquedas, varios subagentes de una misma ola). Un turno con cuatro llamadas en paralelo cuesta menos que cuatro turnos.
- Combina comandos de shell relacionados en uno solo cuando no necesitas ver el resultado intermedio (por ejemplo, toda la verificación en una línea con `&&`).
- Carga las herramientas diferidas de una vez (`ToolSearch` con `select:a,b,c`), no una por una.

### Skills y herramientas: solo las necesarias

- Cada skill y cada servidor MCP instalado ocupa contexto en todos los turnos, aunque no se use. Si hay muchas instaladas, sugiere al usuario desactivar las que no usa en ese proyecto (`.claude/skills/` del proyecto en vez de globales).
- Carga una skill solo cuando la tarea es realmente de su tipo. Dentro de esta skill, lee `references/flutter.md` solo si el proyecto es Flutter.
- No actives navegador, computer use ni conectores si un comando, grep o fetch resuelve lo mismo: capturas de pantalla y páginas completas son de lo más caro.

### Responder breve

- Ve directo al resultado. No repitas lo que el usuario dijo, no narres cada paso, no resumas al final lo que ya se ve.
- No pegues código que ya está en un archivo: di qué archivo y qué cambió.
- Una pregunta a la vez, solo si la respuesta cambia lo que vas a hacer.
- Los resúmenes de fase son una tabla corta, no un informe.

---

## Escala de modelos

| Nivel | Modelo | Úsalo para |
|---|---|---|
| 1 | `haiku` | Explorar el repo, buscar archivos, leer docs, tareas mecánicas: renombrar, formatear, mover, generar boilerplate a partir de un patrón existente, escribir tests siguiendo un ejemplo dado |
| 2 | `sonnet` | Implementar funcionalidades con especificación clara, pantallas, servicios, endpoints, corregir bugs localizados, escribir tests nuevos |
| 3 | `opus` | Planificar, definir arquitectura y contratos (interfaces, modelos de datos, rutas), tareas con mucha ambigüedad, bugs que ya fallaron en nivel 2, revisión |
| 4 | `fable` (si está disponible) | Último escalón cuando Opus falla dos veces en la misma tarea |

Los modelos pequeños son rápidos y baratos y hacen muy bien tareas acotadas y bien especificadas; fallan cuando deben tomar decisiones de diseño con poco contexto. Por eso las decisiones las toma un modelo capaz, la ejecución el modelo más barato que pueda hacerla, y todo pasa por verificación objetiva y revisión de un modelo igual o superior.

### Cómo clasificar una tarea

Pregúntate en este orden:

1. ¿Requiere decidir algo que otras tareas van a usar (estructura, API, esquema)? → nivel 3.
2. ¿La especificación deja huecos que hay que rellenar con criterio? → nivel 3.
3. ¿Hay que escribir lógica nueva pero el qué y el dónde están claros? → nivel 2.
4. ¿Se puede describir como "haz X igual que en el archivo Y" y verificarse con un comando? → nivel 1.

Ante la duda entre dos niveles: elige el **superior** para tareas que tocan muchos archivos o datos del usuario (un fallo ahí cuesta más uso en reintentos que el ahorro inicial), y el **inferior** para tareas fáciles de verificar con un test (si falla, el escalado lo corrige barato).

Usar `opus` para todo "por seguridad" pierde la ventaja de costo y velocidad sin ganar calidad en tareas mecánicas; usar `haiku` para decisiones de diseño sale más caro por los reintentos.

---

## Flujo por fases

Cada fase termina con un resumen corto al usuario y **espera su aprobación** antes de pasar a la siguiente. El usuario quiere poder corregir el rumbo antes de que se gaste trabajo (aprobar a tiempo es también la forma más grande de ahorro); no lo saltes aunque el plan parezca obvio. Si el usuario dice explícitamente "sigue sin preguntar", respétalo.

### Fase 0 — Explorar (haiku, barato)

- Si ya existe `ORQUESTACION.md` o `CLAUDE.md` en el proyecto, **léelos primero**: pueden ahorrarte la exploración entera.
- Si hay un repo existente y falta información, lanza **en el mismo turno** 1–3 subagentes `haiku` (agente `explorador` de `assets/agents/` o tipo `Explore`), cada uno con una pregunta concreta: estructura de carpetas, comandos de build y test, convenciones de estilo, dependencias. Pídeles conclusiones con rutas, en pocas líneas, no volcados de archivos.
- Si el repo es pequeño, no lances subagentes: un `Glob` y un par de `Grep` tuyos bastan.

Determina los **comandos de verificación** del proyecto (compilar, analizar, testear, formatear). Para Flutter lee `references/flutter.md`. Para otros lenguajes, búscalos con grep en el README, `package.json`, `Makefile`, `pom.xml`, `build.gradle`, `pyproject.toml`, `Cargo.toml`, etc. Comprueba que funcionan antes de empezar (con salida recortada); si el proyecto ya no compila, díselo al usuario antes de seguir, porque si no, no sabrás si un fallo es tuyo.

### Fase 1 — Planificar (opus)

Lanza un subagente `opus` (agente `planificador`) con el objetivo del usuario y **solo las conclusiones** de la exploración (no los volcados). Debe devolver, en formato compacto:

- Arquitectura y decisiones clave, con el porqué.
- **Contratos**: interfaces, modelos de datos, firmas públicas, rutas, nombres de archivos. Esto permite que modelos menores trabajen en paralelo sin pisarse.
- Lista de tareas, cada una con: objetivo, archivos que crea o modifica, dependencias, criterio de "terminado" verificable, nivel propuesto y por qué.

Revisa el plan tú mismo: que ninguna tarea paralela toque los mismos archivos, que cada una tenga un criterio comprobable, que ninguna tarea esté en un nivel más caro del necesario ni en uno más barato que el riesgo. Muéstralo al usuario como tabla breve (tarea · modelo · archivos) y **espera aprobación**.

Si el plan es corto y claro, puedes hacerlo tú mismo sin lanzar `opus` (eres ya un modelo capaz y tienes el contexto): un subagente planificador solo compensa en proyectos grandes o ambiguos.

### Fase 2 — Contratos primero

Antes de repartir, implementa los contratos (tipos, interfaces, stubs, tema, rutas) en una tarea propia de nivel 3, o hazlo tú directamente si es corto. Verifica que compila. Así las tareas siguientes tienen una base fija y un modelo menor no necesita inventar estructura (inventar estructura es la causa principal de reintentos caros).

### Fase 3 — Implementar

Agrupa las tareas en olas según dependencias. En cada ola, lanza **en el mismo turno** los subagentes que no comparten archivos; si dos tareas deben tocar los mismos archivos, van en secuencia o con `isolation: "worktree"`.

Cada encargo debe ser autosuficiente, porque el subagente no ha visto esta conversación, y a la vez mínimo, porque todo lo que le pases lo paga. Incluye solo el fragmento del plan que necesita:

```
Objetivo: <qué construir, en 1–3 frases>
Contexto: <fragmento del plan y contratos que necesita, rutas exactas>
Archivos que puedes tocar: <lista>. No modifiques otros.
Patrón a seguir: <archivo de ejemplo del repo, si existe>
Terminado cuando: <criterio verificable>
Verificación: ejecuta <comandos> (salida recortada) y no termines hasta que pasen.
Ahorro: busca con Grep/Glob antes de leer, lee solo los tramos necesarios, no leas dependencias ni archivos generados, no releas tras editar.
Devuelve (breve, sin pegar código): archivos cambiados, comandos ejecutados y su resultado, dudas o supuestos.
```

Un encargo vago es la causa principal de pérdida de calidad con modelos pequeños y de reintentos que gastan uso; si no puedes escribir el "Terminado cuando", la tarea no está lista para delegarse.

### Fase 4 — Verificar (tú, no el subagente)

Cuando vuelve cada subagente, **ejecuta tú los comandos de verificación** en una sola llamada encadenada y con salida recortada. El informe del subagente dice lo que cree que hizo; la compilación dice lo que hizo. Comprueba también que solo tocó los archivos permitidos (`git diff --stat`, no el diff completo salvo que haga falta).

Si varias tareas de la ola terminan juntas, verifica la ola completa de una vez en vez de tarea por tarea, salvo que un fallo obligue a aislar cuál fue.

Si falla, aplica la regla de escalado.

### Regla de escalado

1. Primer fallo: reenvía al **mismo nivel** con el error exacto (solo las líneas útiles del error) y lo que se esperaba. Usa `SendMessage` para continuar el mismo agente y conservar su contexto: es más barato que lanzar uno nuevo que redescubre todo.
2. Segundo fallo en la misma tarea: pásala al **nivel siguiente** con el historial resumido de los dos intentos. Escalar sin pasar el error concreto hace que el modelo superior repita el mismo error y se paga dos veces.
3. Si falla en nivel 3 dos veces: sube a nivel 4 si existe; si no, detente y explica el bloqueo al usuario con lo intentado.

Anota en el registro cada escalado: si un tipo de tarea escala a menudo, en este proyecto clasifícala un nivel más arriba desde el principio (un intento caro que acierta cuesta menos que tres baratos que fallan).

### Fase 5 — Revisar (modelo superior)

Al terminar cada ola, lanza un subagente `opus` (agente `revisor`) que **no participó en la implementación**. Dale el diff de la ola, el fragmento del plan y los contratos, no el repo entero. Debe buscar: incumplimientos del contrato, bugs lógicos, casos borde sin manejar, código duplicado o inconsistente con el estilo del repo, problemas de seguridad, tests ausentes. Cada hallazgo, en una línea, como **bloqueante** o **sugerencia**.

Los bloqueantes vuelven a la fase 3 como tareas nuevas (normalmente nivel 2). Las sugerencias se listan al usuario.

La revisión no se salta por ahorro: es lo que permite usar modelos baratos en la implementación sin perder calidad. Lo que sí se ajusta es el tamaño: en olas muy pequeñas (una o dos tareas mecánicas ya verificadas), puedes revisar tú el diff y dejar la revisión `opus` para el cierre.

Muestra al usuario el resumen de la ola en una tabla corta: tarea, modelo, escalados, verificación, hallazgos de revisión. **Espera aprobación** antes de la siguiente ola.

### Fase 6 — Cierre

Verificación completa del proyecto (incluye builds de plataforma, que son lentos y se dejan para aquí), revisión global de `opus` si hubo más de una ola, y resumen final: qué se construyó, cómo probarlo, pendientes. Actualiza `ORQUESTACION.md`.

---

## Sesiones largas y continuidad

- Mantén un archivo `ORQUESTACION.md` en la raíz del proyecto (o donde el usuario prefiera) con el plan, contratos, el estado de cada tarea, el modelo usado, los escalados y las decisiones. Hay una plantilla en `assets/ORQUESTACION-plantilla.md`. Sirve para retomar en otra sesión sin redescubrir nada y para que el usuario vea por qué se eligió cada modelo. Pregunta al usuario si quiere incluirlo en git.
- Cuando la conversación ya es larga, al cerrar una ola o si la tarea cambia de tema, sugiere al usuario `/compact` o una sesión nueva: el contexto viejo se sigue pagando en cada turno. Con `ORQUESTACION.md` al día, la sesión nueva continúa sin perder nada.
- Guarda decisiones y convenciones estables en `CLAUDE.md`. El bloque de `assets/CLAUDE-bloque.md` resume estas reglas para pegarlo allí; ofrécelo la primera vez que uses la skill en un repo.

---

## Agentes reutilizables

En `assets/agents/` hay definiciones listas para copiar a `.claude/agents/` del proyecto, ya con las reglas de ahorro incluidas: `explorador` (haiku), `planificador` (opus), `implementador` (sonnet), `implementador-rapido` (haiku) y `revisor` (opus). Ofrece instalarlas la primera vez que uses la skill en un repo; si el usuario no quiere, usa el tipo de agente general pasando `model` en cada llamada e incluye la línea "Ahorro" del encargo.

---

## Errores comunes que evitar

**De calidad**
- Delegar la arquitectura a un modelo pequeño "porque es simple": las decisiones malas se propagan a todas las tareas.
- Confiar en "tests pasan" sin ejecutarlos tú.
- Dos subagentes paralelos editando el mismo archivo.
- Escalar sin pasar el error concreto: el modelo superior repite el mismo error.
- Saltarse la revisión o la verificación "para ahorrar".

**De uso**
- Usar `opus` para todo "por seguridad".
- Lanzar subagentes para tareas de uno o dos pasos que harías tú en un turno.
- Pasar a un subagente la conversación o el plan completos en lugar del fragmento que necesita.
- Pedir a los subagentes informes largos o código pegado en la respuesta.
- Leer archivos completos, dependencias o logs enteros; releer tras editar.
- Lanzar las llamadas independientes en turnos separados en vez de en paralelo.
- Explorar de nuevo un repo que ya tiene `ORQUESTACION.md` o `CLAUDE.md` al día.
