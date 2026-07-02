### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    { $decks ->
        [one] usado por { $decks } baralla
       *[other] usado por { $decks } barallas
    }
deck-config-default-name = Predeterminado
deck-config-title = Opcións da baralla

## Daily limits section

deck-config-daily-limits = Límites diarios
deck-config-new-cards-ignore-review-limit = As novas tarxetas ignoran o límite de repasos

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Perfil de estudo
deck-config-deck-only = Esta baralla
deck-config-today-only = Só hoxe

## New Cards section

deck-config-learning-steps = Etapas de aprendizaxe
deck-config-new-insertion-order = Orde de inserción
deck-config-new-insertion-order-sequential = Secuencial (tarxetas máis antigas primeiro)
deck-config-new-insertion-order-random = Ao chou

## Lapses section

deck-config-relearning-steps = Etapas de reaprendizaxe

## Burying section

deck-config-bury-title = Agochar
deck-config-bury-new-siblings = Agochar as tarxetas relacionadas novas
deck-config-bury-review-siblings = Agochar as tarxetas relacionadas por repasar
deck-config-bury-interday-learning-siblings = Agochar as tarxetas relacionadas entre días

## Gather order and sort order of cards

deck-config-ordering-title = Orde de visualización
deck-config-new-gather-priority = Recompilación das tarxetas novas
deck-config-new-card-sort-order = Clasificación das tarxetas novas
deck-config-new-review-priority = Orde das tarxetas novas ao repasar
deck-config-interday-step-priority = Orde das tarxetas en aprendizaxe ao repasar
deck-config-review-sort-order = Clasificación dos repasos

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = Baralla
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = Baralla, logo tarxetas ao chou
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = Posición ascendente
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = Posición descendente
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = Notas ao chou
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = Tarxetas ao chou
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = Tipo de tarxeta, logo ao chou
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = Nota ao chou, logo tipo de tarxeta
# Sort the cards randomly.
deck-config-sort-order-random = Ao chou
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = Tipo de tarxeta, logo orde de recompilación
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = Orde de recompilación
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = Mesturar cos repasos
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = Amosar despois dos repasos
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = Amosar antes dos repasos
# Sort the cards first by due date, in ascending order (oldest due date to newest), then randomly within the same due date.
deck-config-sort-order-due-date-then-random = Data de repaso, logo ao chou
# Sort the cards first by due date, in ascending order (oldest due date to newest), then by deck within the same due date.
deck-config-sort-order-due-date-then-deck = Data de repaso, logo baralla
# Sort the cards first by deck, then by due date in ascending order (oldest due date to newest) within the same deck.
deck-config-sort-order-deck-then-due-date = Baralla, logo data de repaso
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = Intervalos ascendentes
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = Intervalos decrecentes
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = Facilidade ascentente
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = Facilidade descendente
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = Tarxetas doadas primeiro
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = Tarxetas difíciles primeiro
# Sort the cards by retrievability percentage, in ascending order (0% to 100%, least retrievable to most easily retrievable).
deck-config-sort-order-retrievability-ascending = Recuperabilidade crecente
# Sort the cards by retrievability percentage, in descending order (100% to 0%, most easily retrievable to least retrievable).
deck-config-sort-order-retrievability-descending = Recuperabilidade decrecente

## Timer section


## Auto Advance section

deck-config-question-action-show-answer = Amosar resposta
deck-config-question-action-show-reminder = Amosar recordatorio

## Audio section

deck-config-audio-title = Son
deck-config-disable-autoplay = Non reproducir sons automaticamente
deck-config-skip-question-when-replaying = Omitir pregunta ao repetir a resposta

## Advanced section


## Easy Days section.

deck-config-easy-days-title = Días de descanso
deck-config-easy-days-monday = Luns
deck-config-easy-days-tuesday = Martes
deck-config-easy-days-wednesday = Mércores
deck-config-easy-days-thursday = Xoves
deck-config-easy-days-friday = Venres
deck-config-easy-days-saturday = Sábado
deck-config-easy-days-sunday = Domingo
deck-config-easy-days-normal = Normal
deck-config-easy-days-reduced = Reducido
deck-config-easy-days-minimum = Mínimo

## Adding/renaming

deck-config-add-group = Engadir perfil de estudo
deck-config-name-prompt = Nome
deck-config-rename-group = Renomear perfil de estudo
deck-config-clone-group = Clonar perfil de estudo

## Removing

deck-config-remove-group = Eliminar perfil de estudo
deck-config-confirm-remove-name = Queres eliminar { $name }?

## Other Buttons

deck-config-save-button = Gardar
deck-config-save-to-all-subdecks = Gardar para todas as barallas secundarias
deck-config-save-and-optimize = Optimizar todos os perfís de estudo

## These strings are shown via the Description button at the bottom of the
## overview screen.


## Warnings shown to the user


## Selecting a deck


## Messages related to the FSRS scheduler

deck-config-evaluate-button = Avaliar
deck-config-desired-retention = Retención desexada
deck-config-historical-retention = Retención histórica
deck-config-get-params = Obter parámetros
deck-config-complete = { $num }% completado.
deck-config-iterations = Iteración: { $count }...
deck-config-percent-of-reviews =
    { $reviews ->
        [one] { $pct }% de { $reviews } repaso
       *[other] { $pct }% de { $reviews } repasos
    }
deck-config-percent-input = { $pct }%
deck-config-wait-for-audio = Esperar polo son
deck-config-show-reminder = Amosar recordatorio
deck-config-simulate = Simular
deck-config-clear-last-simulate = Limpar última simulación
deck-config-fsrs-simulator-radio-count = Repasos
deck-config-advanced-settings = Configuración avanzada
deck-config-suspend-leeches = Samesugas suspensas
deck-config-save-options-to-preset = Gardar cambios no perfil de estudo
# $time here is pre-formatted e.g. "10 Seconds" 
deck-config-fsrs-simulator-ratio-tooltip = { $time } por tarxeta memorizada

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.


## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-unable-to-determine-desired-retention = Non se puido determinar unha retención mínima recomendada.
deck-config-predicted-minimum-recommended-retention = Retención mínima recomendada: { $num }
deck-config-compute-minimum-recommended-retention = Retención mínima recomendada
deck-config-predicted-optimal-retention = Retención mínima recomendada: { $num }
