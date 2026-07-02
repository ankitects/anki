## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount } s
scheduling-answer-button-time-minutes = { $amount } m
scheduling-answer-button-time-hours = { $amount } h
scheduling-answer-button-time-days = { $amount } d
scheduling-answer-button-time-months = { $amount } me
scheduling-answer-button-time-years = { $amount } añ

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds =
    { $amount ->
        [one] { $amount } segundo
       *[other] { $amount } segundos
    }
scheduling-time-span-minutes =
    { $amount ->
        [one] { $amount } minuto
       *[other] { $amount } minutos
    }
scheduling-time-span-hours =
    { $amount ->
        [one] { $amount } hora
       *[other] { $amount } horas
    }
scheduling-time-span-days =
    { $amount ->
        [one] { $amount } día
       *[other] { $amount } días
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } mes
       *[other] { $amount } meses
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } año
       *[other] { $amount } años
    }

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    La próxima tarjeta estará lista en { $unit ->
        [seconds]
            { $amount ->
                [one] { $amount } segundo
               *[other] { $amount } segundos
            }
        [minutes]
            { $amount ->
                [one] { $amount } minuto
               *[other] { $amount } minutos
            }
       *[hours]
            { $amount ->
                [one] { $amount } hora
               *[other] { $amount } horas
            }
    }.
scheduling-learn-remaining =
    { $remaining ->
        [one] Hay una tarjeta restante en la cola de aprendizaje programada para hoy más tarde.
       *[other] Hay { $remaining } tarjetas restantes en la cola de aprendizaje programadas para hoy más tarde.
    }
scheduling-congratulations-finished = ¡Felicitaciones! Has finalizado este mazo por ahora.
scheduling-today-review-limit-reached =
    Has alcanzado el límite actual de repasos, pero todavía hay tarjetas
    a la espera de ser repasadas. Para una memorización óptima, considera
    aumentar el límite diario en las opciones.
scheduling-today-new-limit-reached =
    Hay más tarjetas nuevas disponibles, pero has alcanzado el límite
    diario. Puedes aumentar el límite en las opciones, pero ten
    en cuenta que cuantas más tarjetas nuevas introduzcas, más
    aumentará tu carga de trabajo a corto plazo.
scheduling-buried-cards-found = Una o más tarjetas fueron enterradas y se mostrarán mañana. Puedes { $unburyThem } si deseas verlas de inmediato.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = desenterrarlas
scheduling-how-to-custom-study = Si deseas estudiar fuera del horario habitual, puedes utilizar la función { $customStudy }.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = estudio personalizado

## Scheduler upgrade

scheduling-update-soon = Anki 2.1 viene con un nuevo planificador, que soluciona una serie de problemas que tenían las versiones anteriores de Anki. Se recomienda actualizar a la nueva versión del planificador.
scheduling-update-done = Planificador actualizado con éxito.
scheduling-update-button = Actualizar
scheduling-update-later-button = Más tarde
scheduling-update-more-info-button = Saber más
scheduling-update-required =
    Tu colección debe actualizarse al planificador v2. 
    Por favor, selecciona { scheduling-update-more-info-button } antes de continuar.

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = Incluir siempre el lado de la pregunta cuando se vuelva a reproducir el audio
scheduling-at-least-one-step-is-required = Se requiere al menos un paso.
scheduling-automatically-play-audio = Reproducir sonido automáticamente
scheduling-bury-related-new-cards-until-the = Enterrar tarjetas nuevas relacionadas hasta el día siguiente
scheduling-bury-related-reviews-until-the-next = Enterrar repasos relacionados hasta el día siguiente
scheduling-days = días
scheduling-description = Descripción
scheduling-easy-bonus = Bonus para fácil
scheduling-easy-interval = Intervalo para fácil
scheduling-end = (fin)
scheduling-general = General
scheduling-graduating-interval = Intervalo de graduación
scheduling-hard-interval = Intervalo para difícil
scheduling-ignore-answer-times-longer-than = Ignorar tiempos de respuesta mayores de
scheduling-interval-modifier = Modificador de intervalo
scheduling-lapses = Olvidos
scheduling-lapses2 = olvidos
scheduling-learning = Aprendiendo
scheduling-leech-action = Acción para olvidadas
scheduling-leech-threshold = Umbral para olvidadas
scheduling-maximum-interval = Intervalo máximo
scheduling-maximum-reviewsday = Repasos máximos/día
scheduling-minimum-interval = Intervalo mínimo
scheduling-mix-new-cards-and-reviews = Mezclar tarjetas nuevas y repasos
scheduling-new-cards = Tarjetas nuevas
scheduling-new-cardsday = Tarjetas nuevas/día
scheduling-new-interval = Intervalo para nuevas
scheduling-new-options-group-name = Nombre del nuevo grupo de opciones:
scheduling-options-group = Grupo de opciones:
scheduling-order = Orden
scheduling-parent-limit = (límite del superior: { $val })
scheduling-reset-counts = Restablecer recuentos de repeticiones y fallos
scheduling-restore-position = Restaurar la posición original cuando sea posible
scheduling-review = Repasar
scheduling-reviews = Repasos
scheduling-seconds = segundos
scheduling-set-all-decks-below-to = ¿Asignar este grupo de opciones a todos los mazos debajo de { $val }?
scheduling-set-for-all-subdecks = Asignar a todos los submazos
scheduling-show-answer-timer = Mostrar temporizador de respuesta
scheduling-show-new-cards-after-reviews = Mostrar tarjetas nuevas después de los repasos
scheduling-show-new-cards-before-reviews = Mostrar tarjetas nuevas antes de los repasos
scheduling-show-new-cards-in-order-added = Mostrar tarjetas nuevas en el orden en que se añadieron
scheduling-show-new-cards-in-random-order = Mostrar tarjetas nuevas en orden aleatorio
scheduling-starting-ease = Facilidad inicial
scheduling-steps-in-minutes = Pasos (en minutos)
scheduling-steps-must-be-numbers = Los pasos deben ser números.
scheduling-tag-only = Solo etiquetar
scheduling-the-default-configuration-cant-be-removed = La configuración por defecto no puede ser eliminada.
scheduling-your-changes-will-affect-multiple-decks = Tus cambios afectarán a varios mazos. Si deseas cambiar únicamente el mazo actual, añade primero un nuevo grupo de opciones.
scheduling-deck-updated =
    { $count ->
        [one] { $count } mazo actualizado.
       *[other] { $count } mazos actualizados.
    }
scheduling-set-due-date-prompt =
    { $cards ->
        [one] ¿Mostrar tarjeta en cuantos días?
       *[other] ¿Mostrar tarjetas en cuántos días?
    }
scheduling-set-due-date-prompt-hint =
    0 = hoy
    1! = mañana + cambiar el intervalo a 1
    3-7 = elección aleatoria entre 3 y 7 días
scheduling-set-due-date-done =
    { $cards ->
        [one] Establecer fecha de revisión de { $cards } tarjeta.
       *[other] Establecer fecha de revisión de { $cards } tarjetas.
    }
scheduling-graded-cards-done =
    { $cards ->
        [one] { $cards } tarjeta calificada.
       *[other] { $cards } tarjetas calificadas.
    }
scheduling-forgot-cards =
    { $cards ->
        [one] Reiniciar { $cards } tarjeta.
       *[other] Reiniciar { $cards } tarjetas.
    }
