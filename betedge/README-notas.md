# Notas de implementación

Decisiones y hallazgos que no son obvios leyendo el código, recogidos durante el desarrollo.

## Ingesta de cuotas (OddsPapi)

### "/moneyline" no identifica un único mercado — hay que filtrar también por periodo

`bookmakerMarketId` codifica un path tipo `line/29/205451/{id}/{id}/{periodo}/moneyline`. En **el 100% de los
fixtures inspeccionados** (30/30, exploración real vía `GET /odds-by-tournaments`), cada bookmaker publica **dos**
mercados cuyo sufijo termina en `/moneyline`:

- `.../0/moneyline` → confirmado contra `GET /v4/markets`: marketId `101`, nombre oficial **"Full Time Result"**,
  `period: "fulltime"`.
- `.../1/moneyline` → marketId `10208`, nombre oficial **"First Half Result"**, `period: "p1"`.

Las cuotas son genuinamente distintas entre ambos (no es la misma línea duplicada). Filtrar solo por
`bookmakerMarketId.endsWith("/moneyline")` mezclaría resultado de partido completo con resultado de primer
tiempo en la misma fila de `Odds`, contaminando el cálculo de consenso más adelante.

**Decisión**: `OddsPapiMarketDto.isFullTimeMoneyline()` exige el sufijo completo `/0/moneyline`, no solo
`/moneyline`. Además, el `marketId` numérico (`101` vs `10208`, o `555`/`556` en otro fixture) **no es estable
entre fixtures** — confirmado viendo distintos fixtures con distinto numérico para el mismo mercado lógico. Por
eso el parseo es siempre por sufijo del path, nunca por el ID numérico externo.

### `GET /v4/participants` no filtra por `participantIds`

Se probó `sportId=10&participantIds=3,35,656,2885,2859` esperando una respuesta acotada a esos 5 IDs. La API
devolvió el **directorio completo** de participantes para ese deporte (~19.400 entradas), como objeto plano
`{"<id>": "<nombre>"}`. Los 5 IDs pedidos estaban presentes, pero también todos los demás.

Dado que no filtra nada, `OddsPapiClient.fetchParticipantNames` ya no envía `participantIds` — solo pide
`sportId`. Como se explica más abajo, esto ahora se llama una vez al día (no por ciclo de ingesta), así que no
hace falta recolectar IDs de los fixtures en absoluto: el directorio completo cacheado ya resuelve cualquier
nombre que se necesite.

### Casas de apuestas clonadas (`cloneOf`)

`GET /v4/bookmakers` trae ~300 casas; una fracción grande tiene `cloneOf` apuntando al slug de otra casa (p. ej.
`"1xBit"` → `cloneOf: "22bet"`), replicando su precio. Se excluyen por completo: nunca se guardan como
`Bookmaker`, y cualquier bloque de odds bajo esa clave en `bookmakerOdds` de un fixture se ignora aunque venga
con datos aparentemente válidos.

### Estructura de outcome confirmada

Cada outcome de moneyline anida un mapa `players` con una sola entrada (clave `"0"`) que trae
`bookmakerOutcomeId` (siempre literal `"home"`/`"draw"`/`"away"`, nunca IDs numéricos ni nombres de equipo) y
`price`. Siempre exactamente 3 outcomes por mercado moneyline en la muestra revisada. `selection` en `Odds` guarda
ese valor tal cual, sin traducir.

## Presupuesto de solicitudes: datos de referencia separados de la ingesta de cuotas

La primera versión de la migración hacía **3 llamadas por ciclo de ingesta** (bookmakers + odds-by-tournaments +
participants). Con el intervalo de 8h eso salía a ~270 solicitudes/mes, por encima de las 250 gratis. `Bookmaker`
y los nombres de equipo cambian con muy poca frecuencia comparado con las cuotas, así que no tenía sentido
refrescarlos en cada ciclo.

**Solución (original)**: `ReferenceDataSyncService` mantenía en memoria (`AtomicReference`, sin tabla nueva) el
set de `externalKey` de bookmakers válidos (no-clonados) y el mapa de nombres de participantes, poblados por
`ReferenceDataScheduler` cada 24h. `IngestionService` no llamaba a `fetchBookmakers()`/`fetchParticipantNames()`
directamente — solo leía del caché. **Esto cambió más adelante** — ver "Se eliminó la sincronización del
catálogo completo de bookmakers" más abajo: la mitad de bookmakers de este caché ya no existe.

**Arranque en frío**: si el caché todavía no se pobló (proceso recién iniciado, el scheduler de 24h aún no
disparó), el primer `get*()` detecta `populated == false` y ejecuta `syncNow()` de forma síncrona antes de
devolver el dato — así la primera ingesta nunca corre contra un caché vacío. `syncNow()` es `synchronized`,
así que una carrera entre el scheduler y este fallback en frío como mucho produce una sincronización extra y
redundante al arrancar (no un dato corrupto ni una excepción); no se le puso más lógica que eso porque es un
caso raro y de una sola vez.

**Descubrimiento posterior (verificación contra la API real, no solo lectura de código)**: `GET
/odds-by-tournaments` exige exactamente un parámetro `bookmaker` por llamada — omitirlo devuelve `400
INVALID_PARAMETER` ("Please provide exactly one bookmaker using the 'bookmaker' query parameter"), no
existe opción de "todas las casas en una sola llamada". El diseño de "1 llamada de fixtures por ciclo"
descrito arriba nunca funcionó contra la API real: producía silenciosamente 0 eventos en cada ingesta, con
la excepción atrapada y logueada en `IngestionService.fetchFixturesSafely` en vez de propagarse.

**Solución**: `odds-ingestion.bookmakers` fija una lista pequeña y configurable — `pinnacle` como casa
"sharp" de referencia para la probabilidad real, el resto casas retail reconocidas; confirmadas
no-clonadas contra el catálogo real, no solo contra `exploracion-api/bookmakers.json`).
`IngestionService.fetchFixturesSafely` llama a `/odds-by-tournaments` una vez por casa de esa lista y
fusiona los fixtures resultantes por `fixtureId` (`mergeBookmakerOdds`), ya que cada respuesta trae
`bookmakerOdds` con una sola entrada (la de esa casa). La lista y el costo real cambiaron dos veces desde
aquí — ver las dos secciones siguientes.

**Bug real encontrado verificando esto contra la API real (no en un test)**: con 5 casas, las llamadas a
`/odds-by-tournaments` salían en ráfaga, sin ninguna pausa. OddsPapi responde `429 RATE_LIMITED` ("wait
0.73 seconds before making another request") a partir de la segunda llamada — en la práctica, solo la
primera casa de la lista (`pinnacle`) llegaba a guardar datos en cada corrida; las demás fallaban
silenciosamente (atrapadas y logueadas, no propagadas) sin que el resumen de la corrida lo reflejara como
error. Esto es particularmente grave porque `ValueBetCalculationService` exige ≥3 bookmakers por match
para siquiera evaluar un edge — con solo 1 casa con datos, **nunca** se iba a detectar un value bet, pase
lo que pase con las cuotas reales. **Solución**: `IngestionService.fetchFixturesSafely` ahora espera
`CALL_DELAY_MS` (1500ms, margen sobre el 0.73s observado) entre cada llamada a la API — no solo entre
casas distintas, ver la sección de batching más abajo, ya que el límite es por endpoint.

- **Se eliminó el tracking de cuota restante** (`x-requests-remaining` / umbral de corte) que existía en la
  integración anterior con The Odds API. OddsPapi no expone un mecanismo de cuota confirmado en esta sesión.
- **Errores HTTP** en `participants` o en la llamada a `odds-by-tournaments` de **cualquier (casa, lote de
  torneos) individual** se loguean como error y ese ciclo/sync continúa con datos vacíos o parciales (el
  resto sí se procesa) en vez de abortar — mismo criterio de resiliencia que ya se usaba para el caso "sin
  partidos".

### Segundo descubrimiento real: máximo 5 tournamentIds por llamada

Al sembrar las 7 ligas (Paso A del plan multi-proveedor — ver la sección de `Competition.externalKeys` más
abajo), la ingesta real dejó de traer eventos por completo. Verificado contra la API real:
`GET /odds-by-tournaments` con 7 `tournamentIds` devuelve `400 INVALID_PARAMETER`, "Please provide a
maximum of 5 tournament IDs". Con solo 3 ligas esto nunca se disparó; con 7, cada llamada fallaba entera
(las 3 casas configuradas, todas). **Solución**: `IngestionService.fetchFixturesSafely` agrupa los
`tournamentIds` en lotes de máximo `MAX_TOURNAMENT_IDS_PER_CALL` (5) — con 7 ligas, 2 lotes (5+2) — y hace
una llamada por cada combinación (casa, lote), no solo por casa. El delay de `CALL_DELAY_MS` aplica entre
**cada** llamada de la doble iteración, no solo entre casas distintas.

### Lista de casas reducida de 5 a 3 (para caber en presupuesto con el batching)

Agregar el batching duplicó el costo por casa (2 lotes en vez de 1), así que hubo que recortar la lista de
casas para mantenerse bajo las 250 llamadas/mes. Se mantuvo `pinnacle` (la referencia "sharp", no
negociable) y, de las 4 retail probadas (`bet365`, `unibet`, `betway`, `betmgm`), se descartaron
**`betway` y `betmgm`**: `betmgm` es un libro enfocado casi exclusivamente en el mercado regulado de EE.UU.
(Michigan, New Jersey, etc.), con relevancia real baja para las 7 ligas que rastreamos (todas europeas o
brasileña, ninguna estadounidense); `betway`, aunque de alcance más global, no mostró ninguna ventaja clara
de cobertura sobre `unibet` para fútbol europeo top-tier en los datos verificados esta sesión. `bet365` y
`unibet` quedan como las 2 retail: `bet365` por ser el libro más grande y con mejor cobertura global del
grupo, `unibet` por su enfoque específicamente fuerte en fútbol europeo (coincide con 5 de las 7 ligas
rastreadas).

**Costo real ahora**: `odds-ingestion.bookmakers` (3 casas) × 2 lotes de torneos × 1 ciclo/día × 30 días =
180 llamadas/mes, **más** `ReferenceDataScheduler` (ver sección siguiente — ahora 1 sola llamada: solo
participants) × 1 vez/día × 30 días = 30/mes → **210/mes entre ambos schedulers**, dejando **40/mes de
margen (~6 triggers manuales)** de las 250 gratis. Recalcular ambos schedulers juntos (no por separado) si
cambia la lista de casas, el número de ligas rastreadas, o cualquiera de los dos intervalos.

### Se eliminó la sincronización diaria del catálogo completo de bookmakers

`ReferenceDataSyncService` sincronizaba diariamente **todo** el catálogo de OddsPapi (~230 bookmakers, vía
`GET /bookmakers`) solo para mantener un set `validBookmakerKeys` — usado en `IngestionService.ingestFixture`
para descartar bookmakers clonados que pudieran colarse. Con `odds-ingestion.bookmakers` ahora fija en 3
casas, cada una confirmada no-clonada a mano, esa llamada diaria ya no protegía contra nada real: las
respuestas de `/odds-by-tournaments` solo pueden traer datos de la casa que pedimos explícitamente por
parámetro, así que `bookmakerOdds` nunca contiene una clave que no hayamos verificado nosotros mismos de
antemano. **Se eliminó** `syncBookmakers()`/`upsertBookmaker()`/`getValidBookmakerKeys()` de
`ReferenceDataSyncService` y `fetchBookmakers()`/`OddsPapiBookmakerDto` de `OddsPapiClient` (quedaban sin
uso). Las 3 filas de `Bookmaker` que la lista fija necesita ahora se siembran una sola vez en
`V17__seed_fixed_bookmakers.sql` (mismo nombre/slug que devolvió la última sincronización real), en vez de
refrescarse a diario. `ReferenceDataSyncService` ahora solo sincroniza nombres de participantes.

## Value Bets y Surebets

Ambos se calculan a partir del **último snapshot de cuota por (bookmaker, selección)** de cada match
(`OddsRepository.findLatestOddsByMatch`, un `DISTINCT ON (bookmaker_id, selection) ... ORDER BY ... timestamp DESC`
nativo de Postgres — no todo el histórico), para matches con `status` `SCHEDULED` o `LIVE`. Son conceptos
distintos que responden preguntas distintas:

- **Value Bet**: ¿esta casa concreta está pagando de más respecto a lo que el mercado en conjunto cree que va a
  pasar? Es una apuesta a una única selección, con riesgo (puede perderse).
- **Surebet**: ¿puedo cubrir las 3 selecciones en distintas casas y ganar seguro, sin importar el resultado?
  Combina varias casas a la vez; si existe, el "riesgo" es solo de ejecución (que las cuotas cambien antes de
  poder colocar las 3 apuestas).

### Value Bet — fórmulas exactas (`ValueBetCalculationService`)

Para un match con cuotas de ≥3 bookmakers distintos (si hay menos, se salta el match por completo):

1. Por cada bookmaker, probabilidad implícita cruda de cada selección: `implied = 1 / oddValue`.
2. Se suman las 3 implícitas de ese bookmaker (`sum`) — ese exceso sobre 1.0 es su margen/vig.
3. Se normaliza cada una: `normalized = implied / sum`. Esto reparte el margen proporcionalmente y dejar las 3
   normalizadas de esa casa sumando exactamente 1.0.
4. Por selección, `estimatedTrueProbability` = promedio de las `normalized` de todos los bookmakers que cotizan
   esa selección (consenso de mercado).
5. Por cada (bookmaker, selección): `impliedProbability` = la implícita cruda de esa casa (paso 1, sin
   normalizar — es la probabilidad "tal como la vende" esa casa concreta). `edgePercentage =
   (estimatedTrueProbability × oddValue − 1) × 100`.
6. Si `edgePercentage >= 3.0`: se guarda un `ValueBet`. `bookmakerProbabilities` guarda las `normalized` (paso 3)
   de cada bookmaker para esa selección — el detalle de auditoría de cómo se llegó al consenso.

Nota: el consenso usado para juzgar el edge de una casa **incluye la probabilidad normalizada de esa misma
casa** (no se excluye al evaluado del promedio) — así lo especifica el cálculo, sin ajuste adicional.

Puede haber más de un `ValueBet` para el mismo (match, selección) si varias casas superan el umbral
simultáneamente — no hay deduplicación a ese nivel; cada fila representa una casa concreta ofreciendo valor.

### Surebet — fórmulas exactas (`SurebetCalculationService`)

Para un match con la mejor cuota disponible (la más alta, de cualquier casa, sin mínimo de bookmakers) en las 3
selecciones:

1. `totalImpliedProbability` = suma de `1 / mejorCuota` de las 3 selecciones.
2. Si `totalImpliedProbability >= 1.0`: no hay arbitraje, no se guarda nada.
3. Si `< 1.0`: `profitPercentage = (1 / totalImpliedProbability − 1) × 100`.
4. Si `profitPercentage >= 0.5`: se guarda un `Surebet`. `legs` trae, por selección, la casa que dio la mejor
   cuota, esa cuota, y `stakePercentage = (1 / oddValue_de_esa_pata) / totalImpliedProbability × 100` — el % del
   bankroll a repartir en esa pata para que las 3 ganancias potenciales se igualen (las 3 `stakePercentage` suman
   100% exacto).

### Bug real encontrado durante la verificación: `LazyInitializationException`

Ambos servicios de cálculo llaman a `odds.getBookmaker().getExternalKey()` para identificar cada casa. Sin
`@Transactional` en el método público (`calculateForMatches`), la sesión de Hibernate que abrió
`findLatestOddsByMatch` se cerraba antes de esa llamada (el proyecto tiene `open-in-view: false`), y
`getExternalKey()` sobre el proxy lazy de `Bookmaker` lanzaba `LazyInitializationException: no session`. Esto
**no era un problema del test** — habría roto también la ejecución real vía `IngestionService`, ya que tampoco
está anotado `@Transactional`. Se agregó `@Transactional` a `calculateForMatches` en ambos servicios (así la
sesión permanece abierta durante todo el cálculo de un match), y quedó cubierto por el test de integración que
corre el flujo completo desde `IngestionService.runIngestion()`.

### "Activas" vs histórico

`GET /value-bets` y `/surebets` no devuelven todo lo que hay en la tabla — un registro se considera "activo"
solo si (a) el `Match` sigue en `SCHEDULED`/`LIVE` (join contra `match`, se excluye `FINISHED`) y (b) su
`detectedAt` está dentro de `OPPORTUNITY_STALENESS_HOURS` (default 48h, el doble del intervalo de ingesta de
24h — ver "Costo real ahora" más arriba —, para tolerar que un ciclo falle sin vaciar el listado). Ambas
condiciones viven en el `WHERE` de
`findActive()` en el repositorio, no en Java, para que el filtrado ocurra en la misma query que el
`DISTINCT ON`. `/value-bets/active` y `/surebets/active` (detalle de partido) aplican la misma definición de
"activo" que `findActive()`, pero colapsando por `matchId` en vez de globalmente: ValueBet por
`(bookmaker_id, selection)` (cada casa+selección se muestra si tiene edge vigente) y Surebet por `match_id`
solo (el profit ya es del partido completo, no de una pata individual).

## Variables de entorno

`.env` (no versionado, ver `.gitignore`) vive junto al `pom.xml`. `ODDSPAPI_API_KEY` y los demás secretos ya
tienen valores de desarrollo cargados ahí. `.env.example` documenta el formato sin valores reales.

Nuevas desde el split de caché: `REFERENCE_DATA_SYNC_INTERVAL` (default 24h) y
`REFERENCE_DATA_SYNC_SCHEDULING_ENABLED` (default `true`), independientes de `ODDS_INGESTION_INTERVAL`/
`ODDS_INGESTION_SCHEDULING_ENABLED`.
