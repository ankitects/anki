## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount }s
scheduling-answer-button-time-minutes = { $amount }m
scheduling-answer-button-time-hours = { $amount }h
scheduling-answer-button-time-days = { $amount }d
scheduling-answer-button-time-months = { $amount }me
scheduling-answer-button-time-years = { $amount }a

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
        [one] { $amount } ano
       *[other] { $amount } anos
    }

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    { $unit ->
        [seconds]
            { $amount ->
                [one] A próxima tarxeta estará dispoñíbel en { $amount } segundo.
               *[other] A próxima tarxeta estará dispoñíbel en { $amount } segundos.
            }
        [minutes]
            { $amount ->
                [one] A próxima tarxeta estará dispoñíbel en { $amount } minuto.
               *[other] A próxima tarxeta estará dispoñíbel en { $amount } minutos.
            }
       *[hours]
            { $amount ->
                [one] A próxima tarxeta estará dispoñíbel en { $amount } hora.
               *[other] A próxima tarxeta estará dispoñíbel en { $amount } horas.
            }
    }
scheduling-learn-remaining =
    { $remaining ->
        [one] Queda unha tarxeta na cola de aprendizaxe para máis tarde hoxe.
       *[other] Quedan  { $remaining } tarxetas na cola de aprendizaxe para máis tarde hoxe.
    }
scheduling-congratulations-finished = Parabéns! Remataches esta baralla por agora.
scheduling-today-review-limit-reached =
    O límite de revisión para hoxe foi acadado, pero aínda hai cartas
    pendentes de ser revisadas. Para unha óptima memoria, considere
    incrementar o límite diario nas opcións.
scheduling-today-new-limit-reached =
    Hai máis tarxetas dispoñíbeis, pero alcanzaches o límite diario.
    Podes incrementar o límite nas opcións, pero ten en conta: 
    cantas máis tarxetas introduzas, máis alta será a túa carga de
    traballo a curto prazo.
scheduling-buried-cards-found = Unha ou máis tarxetas foron agochadas e amosaranse mañá. Podes { $unburyThem } se prefires repasalas agora.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = desagochalas
scheduling-how-to-custom-study = Se queres estudar fóra da programación habitual, podes usar a opción { $customStudy }.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = estudo personalizado

## Scheduler upgrade

scheduling-update-soon = O Anki 2.1 vén cun novo planificador que corrixe algúns problemas presentes en versións anteriores do Anki. Porén, recoméndase actualizar ao novo planificador.
scheduling-update-done = Actualizouse o planificador con éxito.
scheduling-update-button = Actualizar
scheduling-update-later-button = Máis tarde
scheduling-update-more-info-button = Saber máis
scheduling-update-required =
    A túa colección precisa seren actualizada á versión 2 do planificador.
    Selecciona { scheduling-update-more-info-button } antes de continuar.

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = Incluír sempre o anverso cando se volva a reproducir o son.
scheduling-at-least-one-step-is-required = Requirese polo menos  un paso.
scheduling-automatically-play-audio = Reproducir o son automaticamente
scheduling-bury-related-new-cards-until-the = Descarta as novas tarxetas relacionadas ata o día seguinte
scheduling-bury-related-reviews-until-the-next = Soterrar as revisións relativas ata o próximo día.
scheduling-days = días
scheduling-description = Descrición
scheduling-easy-bonus = Bonus por seren fácil
scheduling-easy-interval = Intervalo para fácil
scheduling-end = (fin)
scheduling-general = Xeral
scheduling-graduating-interval = Intervalo para pasar
scheduling-hard-interval = Intervalo para difícil
scheduling-ignore-answer-times-longer-than = Ignorar os tempos de resposta maiores de
scheduling-interval-modifier = Modificador do intervalo
scheduling-lapses = Períodos
scheduling-lapses2 = períodos
scheduling-learning = Aprendendo
scheduling-leech-action = Acción de samesugas
scheduling-leech-threshold = Limiar para samesugas
scheduling-maximum-interval = Intervalo máximo
scheduling-maximum-reviewsday = Repasos máximo/día
scheduling-minimum-interval = Intervalo mínimo
scheduling-mix-new-cards-and-reviews = Misturar tarxetas novas e repasos
scheduling-new-cards = Novas tarxetas
scheduling-new-cardsday = Tarxetas novas/día
scheduling-new-interval = Intervalo novo
scheduling-new-options-group-name = Nome do novo grupo de opcións:
scheduling-options-group = Grupo de opcións:
scheduling-order = Orde
scheduling-parent-limit = (límite anterior: { $val })
scheduling-reset-counts = Reiniciar o contador de repeticións e lapsos
scheduling-restore-position = Restaurar a posición orixinal cando sexa posíbel
scheduling-review = Repaso
scheduling-reviews = Repasos
scheduling-seconds = segundos
scheduling-set-all-decks-below-to = Asignar este grupo de opcións a tódalas barallas embaixo de { $val }?
scheduling-set-for-all-subdecks = Definir para tódalas barallas secundarias
scheduling-show-answer-timer = Amosar o temporizador de respostas
scheduling-show-new-cards-after-reviews = Amosar as novas tarxetas despois dos repasos
scheduling-show-new-cards-before-reviews = Amosar as novas tarxetas antes dos repasos
scheduling-show-new-cards-in-order-added = Amosar as novas tarxetas na orde engadida
scheduling-show-new-cards-in-random-order = Amosar as novas tarxetas ao chou
scheduling-starting-ease = Facilidade inicial
scheduling-steps-in-minutes = Pasos (en minutos)
scheduling-steps-must-be-numbers = Os pasos deben ser números.
scheduling-tag-only = Só as etiquetas
scheduling-the-default-configuration-cant-be-removed = A configuración predeterminada non pode ser retirada.
scheduling-your-changes-will-affect-multiple-decks = Os cambios afectarán a varias barallas. Se queres cambiar só a baralla actual, engade primeiro un novo grupo de opcións.
scheduling-deck-updated =
    { $count ->
        [one] { $count } baralla actualizada.
       *[other] { $count } barallas actualizadas.
    }
scheduling-set-due-date-prompt =
    { $cards ->
        [one] Dentro de cantos días queres amosar a tarxeta?
       *[other] Dentro de cantos días queres amosar as tarxetas?
    }
scheduling-set-due-date-prompt-hint =
    0 = hoxe
    1! = mañá + cambiar o intervalo a 1
    3-7 = escolla aleatoria de 3 a 7 días
scheduling-set-due-date-done =
    { $cards ->
        [one] Estabeleceuse a data de repaso de { $cards } tarxeta.
       *[other] Estabelecéronse as datas de repaso de { $cards } tarxetas.
    }
scheduling-graded-cards-done =
    { $cards ->
        [one] Avaliouse { $cards } tarxeta.
       *[other] Avaliáronse { $cards } tarxetas.
    }
scheduling-forgot-cards =
    { $cards ->
        [one] Esqueceuse { $cards } tarxeta.
       *[other] Esquecéronse { $cards } tarxetas.
    }
