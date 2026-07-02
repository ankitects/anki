### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    { $decks ->
        [one] usado por { $decks } mazo
       *[other] usado por { $decks } mazos
    }
deck-config-default-name = Predeterminado
deck-config-title = Opciones del mazo

## Daily limits section

deck-config-daily-limits = Límites diarios
deck-config-new-limit-tooltip =
    El número máximo de nuevas tarjetas para introducir en un día, si hay nuevas tarjetas disponibles.
    Debido a que el material nuevo aumentará tu carga de revisión a corto plazo, esta opción normalmente
    debería ser al menos 10 veces menor que tu límite de revisión.
deck-config-review-limit-tooltip =
    El número máximo de tarjetas de revisión para mostrar en un día, 
    si las tarjetas están listas para su revisión.
deck-config-limit-deck-v3 =
    Al estudiar un mazo que tiene submazos en su interior, los límites establecidos en cada
    submazo controlan el número máximo de tarjetas que serán obtenidas de ese mazo en particular.
    Los límites establecidos en el mazo seleccionado controlan el total de cartas que se mostrarán.
deck-config-limit-new-bound-by-reviews =
    El límite de revisión afecta el límite de nuevas tarjetas. Por ejemplo, si tu límite 
    de revisiones está definido en 200, y tienes 190 revisiones esperando, 
    un máximo de 10 tarjetas nuevas serán introducidas. Si tu límite de revisiones 
    fue alcanzado no se mostrarán nuevas tarjetas.
deck-config-limit-interday-bound-by-reviews =
    El límite de revisión también afecta a las tarjetas de aprendizaje entre días. Al aplicar
    el límite, primero se obtienen las tarjetas de aprendizaje entre días,  y luego las tarjetas de revisión.
deck-config-tab-description =
    - 'Configuración': el límite se comparte con todos los mazos que usan esta configuración.
    - 'Este mazo': El límite es específico para este mazo.
    - 'Solo hoy': Realiza un cambio temporal en el límite de este mazo.
deck-config-new-cards-ignore-review-limit = Las tarjetas nuevas ignoran el límite de revisión
deck-config-new-cards-ignore-review-limit-tooltip =
    Por defecto el límite de revisión también se aplica a las nuevas tarjetas, y no se mostrarán 
    nuevas tarjetas cuando se haya alcanzado el límite de revisión. Si esta opción está habilitada
    se mostrarán nuevas tarjetas independientemente del límite de revisión.
deck-config-apply-all-parent-limits = Límites empiezan desde arriba
deck-config-apply-all-parent-limits-tooltip =
    Por defecto los límites diarios de un mazo superior no aplican si estás estudiando desde su submazo.
    Si está opción está activada los límites empezarán 
    desde el mazo superior, lo que puede ser útil si deseas estudiar submazos individuales
    mientras respetas un límite total de tarjetas para el árbol de mazos.
deck-config-affects-entire-collection = Afecta a toda la colección.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Configuración
deck-config-deck-only = Este mazo
deck-config-today-only = Solo hoy

## New Cards section

deck-config-learning-steps = Pasos en la etapa de aprendizaje
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Los intervalos suelen ser minutos (por ejemplo, '1m') o días (por ejemplo, '2d'), pero también se admiten horas (por ejemplo, '1h') y segundos (por ejemplo, '30s').
deck-config-learning-steps-tooltip =
    Uno o más intervalos, separados por espacios. El primer intervalo, que por 
    defecto es de 1 minuto, será usado cuando usted presione el botón 'Otra vez' 
    en una nueva tarjeta. El botón 'Bien' avanzará al siguiente paso, que es de 
    10 minutos por defecto. Una vez superados todos los pasos, la tarjeta se 
    convertirá en una tarjeta de revisión, y aparecerá en un día diferente. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip =
    El número de días a esperar antes de volver a mostrar una tarjeta, después que el botón 
    'Bien' se presiona en el último paso de la etapa de aprendizaje.
deck-config-easy-interval-tooltip =
    El número de días a esperar antes de volver a mostrar una tarjeta, después de presionar el botón 'Fácil'
    para inmediatamente remover una tarjeta de la etapa de aprendizaje.
deck-config-new-insertion-order = Orden de inserción
deck-config-new-insertion-order-tooltip =
    Controla la posición (revisión #) que se asignan a las nuevas tarjetas cuando usted agrega nuevas tarjetas.
    Las tarjetas con un número de revisión más bajo se mostrarán primero al estudiar.
    Cambiar esta opción actualizará automáticamente la posición de las nuevas tarjetas ya existentes.
deck-config-new-insertion-order-sequential = Secuencial (las tarjetas más antiguas primero)
deck-config-new-insertion-order-random = Aleatorio
deck-config-new-insertion-order-random-with-v3 =
    Cuando el planificador v3 está habilitado, es mejor mantener seleccionado
    la opción "Secuencial" y en su lugar, ajustar el orden de visualización.

## Lapses section

deck-config-relearning-steps = Pasos de reaprendizaje
deck-config-relearning-steps-tooltip =
    Cero o más intervalos, separados por espacios. Por defecto al presionar el 
    botón “Otra vez” en una tarjeta de revisión, esta se mostrará nuevamente 10 minutos después.
    Si no se proporciona ningún intervalo, se cambiará el intervalo de la tarjeta, sin entrar en reaprendizaje. { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip =
    La cantidad de veces que se debe presionar "Otra vez" en una tarjeta de revisión 
    antes de que se marque como sanguijuela. Las sanguijuelas son cartas que consumen 
    mucho de tu tiempo, y cuando una carta está marcada como sanguijuela, es una buena
    idea reescribirla, borrarla o pensar en una mnemotécnica para ayudarte a recordarla.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    `Solo etiqueta`: agrega una etiqueta de "sanguijuela" a la nota y muestre una ventana emergente.
    
    `Suspender tarjeta`: agrega una etiqueta de "sanguijuela" y oculta la tarjeta hasta deshacerse manualmente la suspensión.

## Burying section

deck-config-bury-title = Enterrar
deck-config-bury-new-siblings = Enterrar tarjetas hermanas nuevas (de la nota) hasta el día siguiente.
deck-config-bury-review-siblings = Enterrar tarjetas hermanas (de la nota).
deck-config-bury-interday-learning-siblings = Enterrar a las tarjetas hermanas (de la nota) de aprendizaje entre días.
deck-config-bury-new-tooltip =
    Si hay otras tarjetas 'nuevas' dentro de la misma nota (p. ej. tarjetas invertidas, respuestas anidadas adyacentes)
    se retrasarán hasta el día siguiente.
deck-config-bury-review-tooltip = Si otras tarjetas de "revisión" de la misma nota, estas se retrasarán hasta el día siguiente.
deck-config-bury-interday-learning-tooltip =
    Si hay otras tarjetas de `aprendizaje` de la misma nota con intervalo mayor a 1 día
    se retrasará hasta el día siguiente.
deck-config-bury-priority-tooltip =
    Cuando Anki recopila tarjetas, primero reúne las tarjetas de aprendizaje intradía, 
    luego las tarjetas de aprendizaje entre días, luego las revisiones y finalmente las
    nuevas tarjetas. Esto afecta cómo funciona el enterramiento:
    - Si tienes todas las opciones de enterramiento habilitadas, se mostrará la tarjeta 
    hermana (de la nota) que aparece primero en esa lista. Por ejemplo, se mostrará 
    una tarjeta de revisión en preferencia a una nueva tarjeta.
    - Las tarjetas hermanas (de la nota) que aparecen más tarde en la lista no pueden 
    enterrar los tipos de tarjetas anteriores. Por ejemplo, si desactivas el enterramiento 
    de las nuevas tarjetas (de la nota), y estudias una nueva tarjeta, no enterrará 
    ninguna tarjeta de aprendizaje entre días o de revisión, y puedes ver tanto un 
    hermano de revisión como un hermano nuevo en la misma sesión

## Gather order and sort order of cards

deck-config-ordering-title = Orden de visualización
deck-config-new-gather-priority = Nuevo orden de recolección de tarjetas
deck-config-new-gather-priority-tooltip-2 =
    `Mazo`: reúne las cartas de cada mazo en orden, empezando por los que están en la parte superior. 
    Las tarjetas de cada mazo se recompilan en posición ascendente. Si se alcanza el límite diario del mazo 
    seleccionado, la recolección puede detenerse antes de que se hayan verificado todos los mazos. Este
    orden es más rápido en colecciones grandes y le permite priorizar los submazos que están más cerca 
    de la parte superior.
    
    `Posición ascendente`: ​​reúne cartas por posición ascendente (revisión #), que suele ser la tarjeta más
    antigua primero.
    
    `Posición descendente`: ​​reúne cartas por posición descendente (debido a #), que suele ser la tarjeta 
    más joven primero.
    
    `Notas aleatorias`: reúne tarjetas de notas seleccionadas al azar. Cuando el enterramiento de 
    hermanos está deshabilitado, esto permite que se vean todas las tarjetas de una nota en una 
    sesión (por ejemplo, una tarjeta anverso->reverso y reverso->anverso)
    
    `Tarjetas aleatorias`: reúne tarjetas de forma completamente aleatoria.
deck-config-new-card-sort-order = Nuevo orden de clasificación de tarjetas
deck-config-new-card-sort-order-tooltip-2 =
    `Plantilla de tarjeta`: muestra las cartas en orden de la plantilla de tarjeta. Si tiene deshabilitado 
    el entierro de hermanos, esto asegurará que todas las tarjetas anverso→reverso se vean antes 
    que las tarjetas reverso→anverso.
    
    `Orden de recompilación`: Muestra las cartas exactamente como fueron reunidas. Si el entierro 
    de hermanos está deshabilitado, esto generalmente dará como resultado que todas las tarjetas 
    de una nota se vean una tras otra.
    
    `Plantilla de tarjeta, luego aleatoria`: Igual que `Plantilla de cartas`, pero mezcla las cartas de 
    cada plantilla. Cuando se combina con un orden de recolección de posición ascendente, esto se 
    puede usar para mostrar las cartas más antiguas en un orden aleatorio, por ejemplo.
    
    `Nota aleatoria, luego plantilla de tarjeta`: recoge notas al azar y luego muestra todas sus 
    hermanas en orden.
    
    `Random`: mezcla completamente las cartas recompiladas.
deck-config-new-review-priority = Nuevos/revisiones (orden de estudio)
deck-config-new-review-priority-tooltip = Cuándo mostrar nuevas tarjetas en relación con las tarjetas de revisión.
deck-config-interday-step-priority = Aprendizaje entre días/revisiones (orden de estudio)
deck-config-interday-step-priority-tooltip =
    Cuándo mostrar tarjetas de (re)aprendizaje que cruzan un límite de día.
    
    El límite de revisión siempre se aplica primero a las tarjetas de aprendizaje entre días 
    y luego a las revisiones. Esta opción controlará el orden en que se muestran las tarjetas 
    recopiladas, pero las tarjetas de aprendizaje entre días siempre se recopilarán primero.
deck-config-review-sort-order = Revisar orden de clasificación
deck-config-review-sort-order-tooltip =
    El orden predeterminado prioriza las tarjetas que llevan más tiempo esperando, 
    de modo que si tiene una acumulación de revisiones, las que llevan más tiempo 
    esperando aparecerán primero. Si tiene una gran acumulación de tarjetas que 
    tardará más de unos pocos días en salir de la cola, o si desea ver las tarjetas en el 
    orden de los submazos, es posible que usted prefiera los órdenes de clasificación 
    alternativos.
deck-config-display-order-will-use-current-deck =
    Anki usará el orden de visualización del mazo seleccionado
    para estudiar, y no los submazos que pueda tener.

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = Mazo
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = Mazo, luego notas aleatorias
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = Posición ascendente
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = Posición descendente
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = Notas aleatorias
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = Tarjeta aleatoria
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = Plantilla de tarjeta, luego aleatoria
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = Nota aleatoria, luego plantilla de tarjeta
# Sort the cards randomly.
deck-config-sort-order-random = Aleatorio
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = Plantilla de tarjeta
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = Orden de recolección
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = Mezclar con las tarjetas para revisar
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = Mostrar después de las tarjetas para revisar
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = Mostrar antes de las tarjetas para revisar
# Sort the cards first by due date, in ascending order (oldest due date to newest), then randomly within the same due date.
deck-config-sort-order-due-date-then-random = Fecha de revisión, luego aleatorio
# Sort the cards first by due date, in ascending order (oldest due date to newest), then by deck within the same due date.
deck-config-sort-order-due-date-then-deck = Fecha de revisión, luego mazo
# Sort the cards first by deck, then by due date in ascending order (oldest due date to newest) within the same deck.
deck-config-sort-order-deck-then-due-date = Mazo, luego fecha de revisión
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = Intervalos ascendentes
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = Intervalos descendentes
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = Facilidad ascendente
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = Facilidad descendente
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = Tarjetas fáciles primero
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = Tarjetas difíciles primero
# Sort the cards by retrievability percentage, in ascending order (0% to 100%, least retrievable to most easily retrievable).
deck-config-sort-order-retrievability-ascending = Recordabilidad ascendente
# Sort the cards by retrievability percentage, in descending order (100% to 0%, most easily retrievable to least retrievable).
deck-config-sort-order-retrievability-descending = Recordabilidad descendente

## Timer section

deck-config-timer-title = Temporizador
deck-config-maximum-answer-secs = Tiempo máximo de respuesta en segundos
deck-config-maximum-answer-secs-tooltip =
    El número máximo de segundos registrados para una sola revisión. Si la respuesta excede
    este tiempo (por ejemplo cuando sales de la pantalla), el tiempo empleado en la tarjeta se 
    registrará hasta el límite que establezca.
deck-config-show-answer-timer-tooltip =
    Mostrar un cronómetro en la pantalla de estudio, que muestra el número de segundos que
    te demoras en contestar una tarjeta.
deck-config-stop-timer-on-answer = Detener temporizador al responder
deck-config-stop-timer-on-answer-tooltip =
    Determina si el temporizador se detiene cuando se muestre la respuesta.
    Esta opción no afecta las estatísticas.

## Auto Advance section

deck-config-seconds-to-show-question = La cantidad de segundos que se mostrará la pregunta
deck-config-seconds-to-show-question-tooltip-3 = Cuando avanzar automáticamente esté activado, el number de segundos a esperar antes de aplicar la acción de pregunta. Establece la opción a 0 para deshabilitar.
deck-config-seconds-to-show-answer = La cantidad de segundos que se mostrará la pregunta
deck-config-seconds-to-show-answer-tooltip-2 = Cuando avanzar automáticamente esté activado, el number de segundos a esperar antes de aplicar la acción de respuesta. Establece la opción a 0 para deshabilitar.
deck-config-question-action-show-answer = Mostrar respuesta
deck-config-question-action-show-reminder = Mostrar recordatorio
deck-config-question-action = Acción de pregunta
deck-config-question-action-tool-tip = La acción a realizar después de que se muestre la pregunta y haya transcurrido el tiempo.
deck-config-answer-action = Acción de respuesta
deck-config-answer-action-tooltip-2 = La acción a realizar tras mostrar la respuesta, y que el tiempo halla pasado.
deck-config-wait-for-audio-tooltip-2 = Espera a que el audio termine de reproducirse antes de aplicar la acción de pregunta o la acción de respuesta automáticamente.

## Audio section

deck-config-audio-title = Audio
deck-config-disable-autoplay = No reproducir audio automáticamente
deck-config-disable-autoplay-tooltip =
    Cuando está habilitado, Anki no reproducirá audio automáticamente.
    Se puede reproducir manualmente haciendo clic/tocando en un ícono de audio, o usando la acción de reproducción de audio.
deck-config-skip-question-when-replaying = Saltar la pregunta al repetir la respuesta
deck-config-always-include-question-audio-tooltip = Si el audio de la pregunta debe incluirse cuando se usa la acción Reproducir mientras se mira el lado de la respuesta de una tarjeta.

## Advanced section

deck-config-advanced-title = Avanzado
deck-config-maximum-interval-tooltip =
    El número máximo de días que esperará una tarjeta de revisión. Cuando las revisiones
    hayan alcanzado el límite, `Difícil`, `Buena` y `Fácil` darán el mismo retraso. Cuanto 
    más corto establezca esto, mayor será su carga de trabajo.
deck-config-starting-ease-tooltip =
    El multiplicador de facilidad con el que comienzan las nuevas tarjetas. De forma 
    predeterminada, el botón 'Bien' en una tarjeta recién aprendida retrasará la 
    próxima revisión en 2,5 veces la demora anterior.
deck-config-easy-bonus-tooltip = Un multiplicador adicional que se aplica al intervalo de revisión de una tarjeta al responder `Fácil`.
deck-config-interval-modifier-tooltip =
    Este multiplicador se aplica a todas las revisiones y se pueden usar ajustes menores
    para hacer que Anki sea más conservador o agresivo en su planificación. Por favor, 
    consulte el manual antes de cambiar esta opción.
deck-config-hard-interval-tooltip = El multiplicador aplicado a un intervalo de revisión al responder 'Difícil'.
deck-config-new-interval-tooltip = El multiplicador aplicado a un intervalo de revisión al responder `Otra vez`.
deck-config-minimum-interval-tooltip = El intervalo mínimo dado a una tarjeta de revisión después de responder `Otra vez`.
deck-config-custom-scheduling = Planificación personalizada
deck-config-custom-scheduling-tooltip = Afecta a toda la colección. ¡Úselo bajo su propio riesgo!

## Easy Days section.

deck-config-easy-days-title = Dias fáciles
deck-config-easy-days-monday = Lunes
deck-config-easy-days-tuesday = Martes
deck-config-easy-days-wednesday = Miércoles
deck-config-easy-days-thursday = Jueves
deck-config-easy-days-friday = Viernes
deck-config-easy-days-saturday = Sábado
deck-config-easy-days-sunday = Domingo
deck-config-easy-days-normal = Normal
deck-config-easy-days-reduced = Reducido
deck-config-easy-days-minimum = Mínimo
deck-config-easy-days-no-normal-days = Al menos un día debería establecerse como  '{ deck-config-easy-days-normal }'.
deck-config-easy-days-change = Los repasos actuales no serán reprogramados a menos que  '{ deck-config-reschedule-cards-on-change }' esté habilitado en los ajustes de FSRS.

## Adding/renaming

deck-config-add-group = Agregar configuración
deck-config-name-prompt = Nombre
deck-config-rename-group = Renombrar configuración
deck-config-clone-group = Clonar configuración

## Removing

deck-config-remove-group = Remover configuración
deck-config-will-require-full-sync =
    El cambio solicitado requerirá una sincronización unidireccional. Si ha realizado cambios 
    en otro dispositivo, y aún no los sincronizó con este dispositivo, hágalo antes de continuar.
deck-config-confirm-remove-name = ¿Remover { $name }?

## Other Buttons

deck-config-save-button = Guardar
deck-config-save-to-all-subdecks = Guardar para todos los submazos
deck-config-save-and-optimize = Optimizar todos los preajustes
deck-config-revert-button-tooltip = Restaure esta configuración a su valor predeterminado.

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Manejo de Anki 2.1.41+
deck-config-description-new-handling-hint =
    Trata la entrada como Markdown y limpia la entrada HTML. Cuando está habilitado, 
    la descripción también se mostrará en la pantalla de felicitaciones. Markdown 
    aparecerá como texto en Anki 2.1.40 y anteriores.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    Un mazo principal tiene un límite de { $cards ->
        [one] { $cards } tarjeta
       *[other] { $cards } tarjetas.
    }, que sobrescribirá este límite.
deck-config-reviews-too-low =
    Agregando{ $cards ->
        [one] { $cards } una nueva tarjeta cada día
       *[other] { $cards } nuevas tarjetas cada día
    }, su límite de revisión debe ser por lo menos { $expected }.
deck-config-learning-step-above-graduating-interval = El intervalo de graduación debe ser al menos tan largo como el último paso de la etapa de aprendizaje.
deck-config-good-above-easy = El intervalo fácil debe ser al menos tan largo como el intervalo de graduación.
deck-config-relearning-steps-above-minimum-interval = El intervalo mínimo debería ser al menos tan largo como el último paso de la etapa de reaprendizaje.
deck-config-maximum-answer-secs-above-recommended = Hacer preguntas breves (cortas, pequeñas) permite que Anki pueda programar sus revisiones de manera más eficiente.
deck-config-too-short-maximum-interval = In intervalo máximo de menos de 6 meses no es recomendable.
deck-config-ignore-before-info = (Aproximádamente) { $included }/{ $totalCards } tarjetas serán usadas para optimizar los parámetros FSRS.

## Selecting a deck

deck-config-which-deck = ¿Qué mazo quieres?

## Messages related to the FSRS scheduler

deck-config-updating-cards = Actualización de tarjetas en curso: { $current_cards_count }/{ $total_cards_count }...
deck-config-invalid-parameters = Los parametros FSRS introducidos son inválidos. Deja los campos vacios para usar los parametros por defecto.
deck-config-not-enough-history = No hay suficientes repasos en el historial para ejecutar esta operación.
deck-config-must-have-400-reviews =
    { $count ->
        [one] Sólo se han encontrado { $count } opiniones. Debe tener al menos 400 opiniones para esta operación.
       *[other] { "" }
    }
# Numbers that control how aggressively the FSRS algorithm schedules cards
deck-config-weights = Parámetros FSRS
deck-config-compute-optimal-weights = Optimizar los parámetros FSRS
deck-config-optimize-button = Optimizar
# Indicates that a given function or label, provided via the "text" variable, operates slowly.
deck-config-slow-suffix = { $text } (lento)
deck-config-compute-button = Calcular
deck-config-ignore-before = Ignorar tarjetas repasadas antes de
deck-config-time-to-optimize = Ha pasado un buen tiempo - usar el botón para optimizar todo es recomendado
deck-config-evaluate-button = Evaluar
deck-config-desired-retention = Retención deseada
deck-config-historical-retention = Retención histórica
deck-config-smaller-is-better = Los números más pequeños indican un mejor ajuste a su historial de revisiones.
deck-config-steps-too-large-for-fsrs = Cuando el FSRS está activado, no se recomiendan pasos de 1 día o más.
deck-config-get-params = Obtener parámetros
deck-config-complete = { $num }% completo.
deck-config-iterations = Iteración: { $count }...
deck-config-reschedule-cards-on-change = Reprogramar tarjetas tras el cambio
deck-config-fsrs-tooltip =
    Afecta a toda la colección.
    
    FSRS (Free Spaced Repetition Scheduler), o en español "Programador de repetición espaciada libre" es una alternativa al viejo programador, SM-2 (SuperMemo 2) de Anki.
    Determinando más precisamente la probailidad de olvidarse una tarjeta, te puede ayudar a recordar más material en la misma cantidad de tiempo. Este ajuste se comparte con todos los preajustes.
deck-config-desired-retention-tooltip = Por defecto, Anki programa las tarjetas de modo que tengas una probabilidad del 90% de recordarlas cuando aparezcan para repasarlas. Si aumentas este valor, Anki mostrará las tarjetas más frequentemente para aumentar la probabilidad de que te acuerdes de ellas. Si reduces el valor, Anki mostrará las tarjetas menos frequentemente, y te olvidarás más de ellas. Ten prudencia cuando ajustes el valor - valores más altos aumentarán tu carga de trabajo, y valores más bajos pueden desmotivarte, dado que te olvidas de mucha información.
deck-config-desired-retention-tooltip2 = Los valores de carga de trabajo que han sido proporcionados por la caja de información son estimaciones aproximadas. Para aumentar la precisión, usa el simulador.
deck-config-historical-retention-tooltip =
    Cuando falte parte de tu historial de repasos, FSRS necesita llenar los huecos. Por defecto, asumirá que cuando hiciste previos repasos, recordaste 90% de la información. Si tu vieja retención era mucho más o mucho menos que 90%, ajustar esta opción permitirá a FSRS a aproximar de una manera mejor los repasos que faltan.
    
    Tu historal de repasos puede estar incompleto por una de estas dos razones:
    1. Porque estás usando la opción 'ignorar tarjetas repasadas previamente'.
    2. Porque eliminaste el historial de repaso para liberar espacio, o importaste información de una aplicación de programación de repetición espaciada (SRS) distinta.
    
    El segundo caso es bastante inusual, asi que a menos que esté utilizando la primera opción, probablemente no necesite ajustar esta opción.
deck-config-weights-tooltip2 = Los parámetros FSRS afectan como se programan las tarjetas. Anki comenzará con los parámetros por defecto. Puedes usar la siguiente opción para optimizar los parámetros para que se adapten a tu rendimiento en mazos usando este preajuste.
deck-config-reschedule-cards-on-change-tooltip =
    Afecta a toda la colección y no se guarda.
    
    Esta opción determina si las fechas de repaso de las tarjetas serán reprogramadas cuando habilites FSRS, u optimices los parámetros. La opción por defecto es no reprogramar las tarjetas: los repasos futuros de las tarjetas usarán la nueva programación, pero no habrá un cambio inmediato a tu carga de trabajo. Si se activa la reprogramación, las fechas de repasos de las tarjetas serán cambiadas.
deck-config-reschedule-cards-warning =
    Dependiendo en tu retención deseada, esto puede resultar en una gran cantidad de tarjetas que tendrás que repasar, asi que no se recomienda si es la primera vez que usas FSRS.
    Usa esta opción con prudencia, ya que añadirá una entrada de repaso a tus tarjetas, y aumentará el tamaño de tu colección.
deck-config-ignore-before-tooltip-2 =
    Si la opción es habilitada, tarjetas repasadas antes de la fecha proporcionada serán excluidas cuando optimices los parámetros FSRS.
    Esta opción puede ser útil si importaste los datos de programación de otra persona, o si cambiaste la manera en la que usas los botones de respuesta.
deck-config-compute-optimal-weights-tooltip2 =
    Cuando presiones el butón "Optimizar", FSRS analizará tu historial de repasos, y generará parámetros que sean optimos para tu memoria y el contenido que estés estudiando. Si tus mazos varian mucho en dificultad, se recomienda asignarles diferentes preajustes, ya que los parámetros para mazos fáciles y para mazos difíciles serán diferentes.
    No necesitas optimizar tus parámetros frequentemente - hacerlo una vez cada pocos meses es suficiente.
    Por defecto, los parámetros serán calculados usando el historial de repasos de todos los mazos usando el preajuste seleccionado actualmente. Puedes alterar opcionalmente que tarjetas son utilizadas para calcular los parámetros, si ajustas la búsqueda antes de calcular los parámetros.
deck-config-please-save-your-changes-first = Por favor, guarde sus cambios primero.
deck-config-workload-factor-change =
    Carga de trabajo aproximada: { $factor }x
    (comparado a { $previousDR }% de retención deseada)
deck-config-workload-factor-unchanged = Cuanto más alto sea este valor, mayor será la frecuencia con la que se te mostrarán las tarjetas.
deck-config-desired-retention-too-low = Tu retención deseada es muy baja, lo que puede llevar a intervalos muy largos.
deck-config-desired-retention-too-high = Tu retención deseada es muy alta, lo que puede llevar a intervalos muy cortos.
deck-config-percent-of-reviews =
    { $reviews ->
        [one] { "" }
       *[other] { $pct }% de { $reviews } repasos
    }
deck-config-percent-input = { $pct }%
# This message appears during FSRS parameter optimization.
deck-config-checking-for-improvement = Comprobando si ha mejorado...
deck-config-optimizing-preset = Optimizando preajustes { $current_count }/{ $total_count }...
deck-config-fsrs-must-be-enabled = Primero, hay que activar FSRS.
deck-config-fsrs-params-optimal = Parece que los parámetros FSRS son óptimos.
deck-config-fsrs-params-no-reviews = No se encontraron repasos. Por favor, comprueba que este preajuste sea asignado a los mazos que quieras optimizar (incluyendo sub-mazos) e intentalo de nuevo.
deck-config-wait-for-audio = Esperar al audio
deck-config-show-reminder = Mostrar recordatorio
deck-config-answer-again = Otra Vez
deck-config-answer-hard = Difícil
deck-config-answer-good = Bien
deck-config-days-to-simulate = Días de simulación
deck-config-desired-retention-below-optimal = Tu retención deseada está por debajo de la óptima. Es recomendado aumentarla.
# Description of the y axis in the FSRS simulation
# diagram (Deck options -> FSRS) showing the total number of
# cards that can be recalled or retrieved on a specific date.
deck-config-fsrs-simulator-experimental = Simulador FSRS (experimental)
deck-config-fsrs-simulate-save-preset = Después de optimizar, guarda la configuración de tu mazo antes de usar el simulador.
deck-config-fsrs-desired-retention-help-me-decide-experimental = Ayúdame a decidir (Experimental)
deck-config-additional-new-cards-to-simulate = Tarjetas adicionales a simular
deck-config-simulate = Simular
deck-config-clear-last-simulate = Borrar la última simulación
deck-config-fsrs-simulator-radio-count = Repasos
deck-config-advanced-settings = Ajustes avanzados
deck-config-suspend-leeches = Suspender sanguijuelas
deck-config-save-options-to-preset = Guardar Cambios a el Preajuste
# Radio button in the FSRS simulation diagram (Deck options -> FSRS) selecting
# to show the total number of cards that can be recalled or retrieved on a
# specific date.
deck-config-fsrs-simulator-radio-memorized = Memorizado
deck-config-fsrs-simulator-radio-ratio = Proporción de Tiempo / Tarjetas memorizadas
# $time here is pre-formatted e.g. "10 Seconds" 
deck-config-fsrs-simulator-ratio-tooltip = { $time } por carta memorizada

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.


## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-unable-to-determine-desired-retention = No se ha podido determinar una retención minima recomendada.
deck-config-predicted-minimum-recommended-retention = Retención minima recomendada
deck-config-compute-minimum-recommended-retention = Retención mínima recomendada
deck-config-compute-optimal-retention-tooltip4 =
    Esta herramienta intentará determinar el valor de la retención que resultará en la mayor cantidad de material aprendido, en el menor tiempo posible.
    El número calculado puede servir como referencia cuando decidas el valor de retención que deseas poner.
    Puede que quieras elegir un valor de retención más alto si deseas invertir más tiempo de estudio para conseguirlo.
    Poner el valor de retención más bajo que el valor recomendado no se recomienda, ya que resultará en una carga de trabajo más alta, por la tasa de olvido más alta.
deck-config-a-100-day-interval =
    { $days ->
        [one] Un intervalo de 100 diás se volverá en { $days } día.
       *[other] Un intervalo de 100 días se volverá en { $days } días.
    }
deck-config-fsrs-simulator-y-axis-title-time = Hora/Día de repaso
deck-config-fsrs-simulator-y-axis-title-memorized = Tarjetas memorizadas
deck-config-bury-siblings = Enterrar tarjetas hermanas
deck-config-do-not-bury = No enterrar tarjetas hermanas
deck-config-bury-if-new = Enterrar si es nueva
deck-config-bury-if-new-or-review = Enterrar si es nueva o es revisión
deck-config-bury-if-new-review-or-interday = Enterrar si es nuevo, revisión o aprendizaje entre días
deck-config-bury-tooltip =
    Los hermanos son otras tarjetas de la misma nota (por ejemplo, tarjetas de adelante/atrás,
    u otras eliminaciones de cloze del mismo texto).
    
    Cuando esta opción está desactivada, es posible que se vean varias tarjetas de la misma nota 
    en el mismo día. Cuando está activada, Anki automáticamente enterrará a los hermanos, 
    ocultándolos hasta el próximo día. Esta opción te permite elegir qué tipos de tarjetas pueden
    ser enterradas cuando respondes a uno de sus hermanos.
    
    Cuando se utiliza el programador V3, las tarjetas de aprendizaje interdía también pueden ser
    enterradas. Las tarjetas de aprendizaje interdía son tarjetas con un paso de aprendizaje actual
    de uno o más días.
deck-config-health-check-tooltip1 = Esto mostrará un aviso si a FSRS le cuesta adaptarse a tu memoria.
deck-config-compute-optimal-retention = Calcular la retención mínima recomendada
deck-config-predicted-optimal-retention = Minimum recommended retention: { $num }
deck-config-weights-tooltip = Los parámetros FSRS afectan como se programan las tarjetas. Anki empezará con los parámetros por defecto. Una vez que acumules más de 1000 repasos, podrás usar la siguiente opción para optimizar los parámetos para que se adapten lo mejor posible a tu rendimiento en mazos usando este preajuste.
deck-config-fsrs-on-all-clients =
    Asegúrese de tener Anki(Mobile) 23.10+, o AnkiDroid 2.17+.
    FSRS no funcionará correctamente con versiones inferiores.
