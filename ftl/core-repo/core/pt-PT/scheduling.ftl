## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount }s
scheduling-answer-button-time-minutes = { $amount }min(s)
scheduling-answer-button-time-hours = { $amount }h
scheduling-answer-button-time-days = { $amount }dia(s)
scheduling-answer-button-time-months = { $amount }me
scheduling-answer-button-time-years = { $amount } ano(s)

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
        [one] { $amount } dia
       *[other] { $amount } dias
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } mês
       *[other] { $amount } meses
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } ano
       *[other] { $amount } anos
    }

## Shown in the "Congratulations!" message after study finishes.

scheduling-congratulations-finished = Parabéns! Você terminou este baralho por enquanto.
scheduling-today-review-limit-reached =
    O limite de revisão de hoje foi atingido, porém ainda existem fichas
    a serem revistas. Para melhorar a sua memória, considere aumentar
    o limite diário nas opções.
scheduling-today-new-limit-reached =
    Há mais fichas novas disponíveis, mas o limite diário foi atingido.
    Pode aumentar o limite nas opções, porém, tenha presente que
    quanto mais fichas novas estudar, maior será a sua carga de
    revisão a curto prazo.

## Scheduler upgrade

scheduling-update-done = Agendador actualizado com sucesso.

## Other scheduling strings

scheduling-at-least-one-step-is-required = Ao menos um passo é necessário.
scheduling-automatically-play-audio = Tocar áudio automaticamente
scheduling-bury-related-new-cards-until-the = Ocultar fichas relacionadas até ao dia seguinte
scheduling-bury-related-reviews-until-the-next = Ocultar revisões relacionadas até o próximo dia
scheduling-days = dias
scheduling-description = Descrição
scheduling-easy-bonus = Bónus por ser Fácil
scheduling-easy-interval = Intervalo fácil
scheduling-end = (fim)
scheduling-general = Geral
scheduling-graduating-interval = Repetir 'Bom' em
scheduling-ignore-answer-times-longer-than = Ignorar resposta dada acima de
scheduling-interval-modifier = Modificar o intervalo
scheduling-lapses = Erros
scheduling-lapses2 = respostas erradas
scheduling-learning = Aprendizagem
scheduling-leech-action = Ação de sanguessuga
scheduling-leech-threshold = Limite de sanguessuga
scheduling-maximum-interval = Intervalo máximo
scheduling-maximum-reviewsday = Revisões máximas/dia
scheduling-minimum-interval = Intervalo mínimo
scheduling-mix-new-cards-and-reviews = Misturar fichas novas e a rever
scheduling-new-cards = Novas fichas
scheduling-new-cardsday = Novas fichas/dia
scheduling-new-interval = Novo intervalo
scheduling-new-options-group-name = Novo nome do grupo de opções:
scheduling-options-group = Grupo de opções:
scheduling-order = Ordem
scheduling-parent-limit = (limite pai: { $val })
scheduling-review = Revisão
scheduling-reviews = Revisões
scheduling-seconds = segundos
scheduling-set-all-decks-below-to = Definir todos os baralhos abaixo { $val } com este grupo de opções?
scheduling-set-for-all-subdecks = Definir para todos os sub-baralhos
scheduling-show-answer-timer = Mostrar cronómetro de resposta
scheduling-show-new-cards-after-reviews = Mostrar novas fichas depois das revisões
scheduling-show-new-cards-before-reviews = Mostrar novas fichas antes das revisões
scheduling-show-new-cards-in-order-added = Mostrar novas fichas na ordem em que foram adicionadas
scheduling-show-new-cards-in-random-order = Mostrar novas fichas em ordem aleatória
scheduling-starting-ease = Multiplicador de dias
scheduling-steps-in-minutes = Passos (em minutos)
scheduling-steps-must-be-numbers = Passos devem ser números.
scheduling-tag-only = Somente Etiquetas
scheduling-the-default-configuration-cant-be-removed = A configuração padrão não pode ser excluída.
scheduling-your-changes-will-affect-multiple-decks = Suas mudanças afetam múltiplos decks. Se você quer modificar apenas o deck atual, por favor, adicione novas opções de grupo primeiro.
scheduling-deck-updated =
    { $count ->
        [one] { $count } baralho atualizado.
       *[other] { $count } baralhos atualizados
    }
