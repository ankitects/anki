### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    { $decks ->
        [one] utilizada por { $decks } baralho
       *[other] utilizada por { $decks } baralhos
    }
deck-config-default-name = Padrão
deck-config-title = Opções de Baralho

## Daily limits section

deck-config-daily-limits = Limites Diários
deck-config-new-limit-tooltip =
    O número máximo de fichas a introduzir, num dia.
    Dado que material novo vai aumentar a carga de revisão a curto-prazo, este valor
    deve ser pelo menos 10x menor que o limite de revisão.
deck-config-review-limit-tooltip = O número máximo de fichas a rever que devem ser apresentadas, num dia.
deck-config-limit-deck-v3 =
    Durante o estudo de baralhos com sub-baralhos, os limites impostos
    a cada sub-baralho define o número máximo de fichas recolhidas do mesmo.
    Enquanto que o limite no baralho será o limite total.
deck-config-limit-new-bound-by-reviews =
    O limite de fichas a rever afecta o número de fichas novas a apresentar. Por exemplo,
    se o limite for 200, e houverem 190 fichas por rever, no máximo, serão introduzidas 10
    novas fichas.
deck-config-limit-interday-bound-by-reviews =
    O limite de revisões também afecta as fichas em aprendizagem com mais dum dia.
    Aquando da aplicação do limite, primeiro serão apresentadas as fichas em aprendizagem com mais dum dia, e só depois as a rever.
deck-config-tab-description =
    - `Predefinido`: O limite aplica-se a todos os baralhos que tenham o limite predefinido.
    - `Este baralho`: O limite para este baralho em específico.
    - `Apenas hoje`: Alterar temporariamente o limite deste baralho.
deck-config-new-cards-ignore-review-limit = Fichas novas ignoram o limite de revisões
deck-config-new-cards-ignore-review-limit-tooltip =
    Por defeito, o limite de revisões também se aplica a novas fichas.
    Se esta opção for activada, novas fichas serão apresentadas independentemente do limite.
deck-config-apply-all-parent-limits = Limites herdados do topo
deck-config-apply-all-parent-limits-tooltip =
    Por defeito, os limites diários dum baralho dum nível acima não se aplicam ao estudo dos seus sub-baralhos.
    Se esta opção for activada, os limites do baralho de mais alto nível passarão a ser impostos, o que pode ser útil quando quer estudar sub-baralhos, mas ainda assim limitar o total de fichas estudadas para a árvore do baralho.
deck-config-affects-entire-collection = Afecta a colecção inteira.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Predefinição
deck-config-deck-only = Este baralho
deck-config-today-only = Apenas hoje

## New Cards section

deck-config-learning-steps = Etapas de Aprendizagem
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = A unidade dos intervalos é, normalmente, minutos (e.g. `1min`) ou dias (e.g. `2d`), mas horas (e.g. `1h`) e segundos (e.g. `30s`) também são suportados.
deck-config-learning-steps-tooltip =
    Um ou mais intervalos, separados por espaços. O primeiro intervalo será utilizado quando pressiona o botão `Novamente` numa ficha nova, e, por defeito, é 1 minuto.
    O botão `Bom` passa a ficha à próxima etapa, que é, por defeito, passados 10 minutos.
    Depois de completas todas as etapas, a ficha passa a ser uma ficha a rever, e aparecerá num dia diferente. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip = O número de dias antes de voltar a mostrar a ficha, depois de carregar no botão `Bom` na última etapa de aprendizagem.
deck-config-easy-interval-tooltip = O número de dias antes de voltar a mostrar a ficha, depois de carregar no botão `Fácil` e a ficha ser imediatamente passada à revisão.
deck-config-new-insertion-order = Ordem de inserção
deck-config-new-insertion-order-tooltip =
    Controla a posição que é dada às novas fichas.
    Fichas com um número inferior serão apresentadas primeiro para estudo.
    Alterar esta opção irá actualizar automaticamente a posição das novas fichas existentes.
deck-config-new-insertion-order-sequential = Sequencial (as mais antigas primeiro)
deck-config-new-insertion-order-random = Aleatória
deck-config-new-insertion-order-random-with-v3 = Com a versão 3 do agendador, é melhor deixar isto em sequencial, e ajustar antes a ordem de recolha para as novas fichas.

## Lapses section

deck-config-relearning-steps = Etapas de reaprendizagem
deck-config-relearning-steps-tooltip =
    Zero ou mais intervalos, separados por espaços. Por defeito, quando pressiona o botão `Novamente` numa ficha a rever esta será apresentada novamente em 10 minutos.
    Se não forem introduzidos intervalos, a ficha terá o seu intervalo alterado, sem entrar na reaprendizagem. { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip =
    O número de vezes que o botão `Outra vez` tem de ser pressionado para que uma ficha seja marcada como sanguessuga.
    Sanguessugas são fichas que consomem muito tempo.
    Quando uma for assim marcada, deve analisá-la para tentar perceber o problema, e depois corrigi-la, apagá-la ou arranjar um nova mnemónica.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    `Apenas etiqueta`: Adicionar uma etiqueta 'sanguessuga' à nota, e apresentar uma janela de informação.
    
    `Suspender ficha`: Para além de marcar a nota com a etiqueta, suspender ainda a ficha até que esta seja manualmente activa.

## Burying section

deck-config-bury-title = Adiamentos
deck-config-bury-new-siblings = Adiar fichas novas relacionadas
deck-config-bury-review-siblings = Adiar fichas a rever relacionadas
deck-config-bury-interday-learning-siblings = Adiar fichas relacionadas em aprendizagem
deck-config-bury-new-tooltip =
    Se as novas fichas associadas à mesma nota (e.g. fichas invertidas, oclusões adjacentes)
    devem ser adiadas para o próximo dia.
deck-config-bury-review-tooltip = Se as fichas a rever associadas à mesma nota devem ser adiadas para o próximo dia.
deck-config-bury-interday-learning-tooltip =
    Se as fichas em fase de aprendizagem associadas à mesma nota, com intervalos
    superiores a 1 dia, devem ser adiadas para o próximo dia.
deck-config-bury-priority-tooltip =
    Quando o Anki reúne fichas, começa pelas fichas em aprendizagem dentro dum mesmo dia, depois pelas em aprendizagem entre dias, a seguir pelas em revisão, e, no fim, pelas novas fichas.
    Esta priorização afecta o funcionamento do adiar de fichas:
    - se tem todas as opções de adiamento activas, a irmã que vem primeiro na lista será apresentada. Por exemplo, uma ficha de revisão tem preferência em relação a uma nova ficha.
    - irmãs mais à frente na lista não podem adiar tipos de ficha que vêm antes. Por exemplo, se desactivar o adiar de fichas novas, e rever uma ficha nova, não adiará nenhuma ficha em aprendizagem entre dias ou fichas de revisão, e podem ser-lhe apresentadas na mesma sessão uma ficha de revisão irmã e uma nova irmã.

## Gather order and sort order of cards

deck-config-ordering-title = Ordem de exibição
deck-config-new-gather-priority = Ordem de recolha para novas fichas
deck-config-new-gather-priority-tooltip-2 =
    `Baralho`: recolhe as fichas de cada um dos sub-baralhos por ordem, a começar vindo de cima. As fichas de cada um dos sub-baralhos são recolhidas por ordem ascendente de posição. Se o limite diário do baralho for atingido, podem não ser recolhidas fichas de todos os sub-baralhos. A recolha utilizando esta ordem é mais rápida em grandes colecções, e prioriza os sub-baralhos no topo.
    
    `Posição ascendente`: recolhe as fichas por ordem ascendente de posição, o que normalmente quer dizer a mais antiga primeiro.
    
    `Posição descendente`: recolhe as fichas por ordem descendente de posição, o que normalmente quer dizer a mais recente primeiro.
    
    `Notas aleatórias`: escolhe notas aleatoriamente, e depois recolhe as suas fichas.
    
    `Fichas aleatórias`: recolhe fichas aleatoriamente.
deck-config-new-card-sort-order = Ordem para fichas novas
deck-config-new-card-sort-order-tooltip-2 =
    `Tipo de ficha, depois ordem de recolha`: Mostrar fichas ordenadas por tipo de ficha.
    Fichas do mesmo tipo são apresentadas na ordem em que foram recolhidas.
    Se tem desactivada a opção de adiar irmãs, isto assegurará que todas as fichas frente→verso são vistas antes de qualquer ficha verso→frente.
    Isto é útil para que sejam apresentadas todas as fichas da mesma nota na mesma sessão, mas não demasiado perto umas das outras.
    
    `Ordem de recolha`: Mostrar fichas exactamente na ordem em que foram recolhidas.
    Se tem desactivada a opção de adiar irmãs, normalmente, isto resultará em que sejam apresentadas todas as fichas duma mesma nota seguidas umas às outras.
    
    `Tipo de ficha, depois aleatória`: Mostrar fichas ordenadas por tipo de ficha.
    Fichas do mesmo tipo são apresentadas de forma aleatória.
    Este tipo de ordenação é útil se não quer que fichas irmãs sejam apresentadas umas a seguir às outras, mas quer à mesma que as fichas sejam apresentadas de forma aleatória.
    
    `Nota aleatória, depois tipo de ficha`: São escolhidas notas de forma aleatória, e depois são apresentadas todas as suas fichas.
    
    `Aleatória`: Mostrar fichas de forma aleatória.
deck-config-new-review-priority = Ordem novas/revisão
deck-config-new-review-priority-tooltip = Quando mostrar fichas novas em relação a fichas de revisão.
deck-config-interday-step-priority = Ordem de fichas em aprendizagem entre dias/revisão
deck-config-interday-step-priority-tooltip =
    Quando apresentar fichas em (re-)aprendizagem entre vários dias.
    
    O limite de revisão é sempre aplicado primeiso a fichas em aprendizagem entre dias, 
    e só depois a fichas de revisão. Esta opção controla a ordem em que as fichas recolhidas são apresentadas, 
    mas fichas em aprendizagem entre dias serão sempre recolhidas primeiro.
deck-config-review-sort-order = Ordem das revisões
deck-config-review-sort-order-tooltip =
    A ordenação padrão prioriza fichas que estão há mais tempo à espera, 
    garantido que as mais antigas vão aparecer primeiro, mesmo que tenha deixado
    revisões por fazer. Se tiver uma grande número de fichas para trás, 
    ou desejar ver as fichas por ordem de sub-baralho, pode preferir as ordenações
    alternativas.
deck-config-display-order-will-use-current-deck =
    O Anki vai utilizar a ordem de exibição do baralho que
    seleccionou para estudar, e não sub-baralhos que este possa ter.

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = Baralho
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = Baralho, depois notas aleatórias
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = Crescente
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = Descendente
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = Notas aleatórias
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = Fichas aleatórias
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = Tipo de ficha, depois aleatório
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = Nota aleatória, depois tipo de ficha
# Sort the cards randomly.
deck-config-sort-order-random = Aleatória
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = Tipo de ficha, depois ordem obtida
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = Ordem de recolha
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = Misturar com revisões
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = Mostrar depois das revisões
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = Mostrar antes das revisões
# Sort the cards first by due date, in ascending order (oldest due date to newest), then randomly within the same due date.
deck-config-sort-order-due-date-then-random = Data de revisão, depois aleatória
# Sort the cards first by due date, in ascending order (oldest due date to newest), then by deck within the same due date.
deck-config-sort-order-due-date-then-deck = Data de revisão, depois baralho
# Sort the cards first by deck, then by due date in ascending order (oldest due date to newest) within the same deck.
deck-config-sort-order-deck-then-due-date = Baralho, depois data de revisão
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = Intervalos crescentes
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = Intervalos decrescentes
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = Aumentando a facilidade
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = Diminuindo a facilidade
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = Fichas fáceis primeiro
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = Fichas difíceis primeiro
# Sort the cards by retrievability percentage, in ascending order (0% to 100%, least retrievable to most easily retrievable).
deck-config-sort-order-retrievability-ascending = Facilidade em relembrar ascendente
# Sort the cards by retrievability percentage, in descending order (100% to 0%, most easily retrievable to least retrievable).
deck-config-sort-order-retrievability-descending = Facilidade em relembrar descendente

## Timer section

deck-config-timer-title = Temporizadores
deck-config-maximum-answer-secs = Número máximo de segundos para responder
deck-config-maximum-answer-secs-tooltip =
    O número máximo de segundos contabilizados por revisão. Se uma resposta
    exceder este tempo (porque, por exemplo se afastou do ecrã por alguma razão),
    o tempo considerado será o do limite.
deck-config-show-answer-timer-tooltip = Mostrar um temporizador para cada revisão, na página de estudo.
deck-config-stop-timer-on-answer = Parar temporizador aquando da resposta
deck-config-stop-timer-on-answer-tooltip =
    Se o temporizador deve parar quando a resposta é revelada.
    Isto não afecta as estatísticas.

## Auto Advance section

deck-config-seconds-to-show-question = Número de segundos para mostrar a pergunta
deck-config-seconds-to-show-question-tooltip-3 = O número de segundos de espera antes de aplicar a acção da pergunta, quando o avançar automaticamente está activo. Atribua o valor 0 para desactivar.
deck-config-seconds-to-show-answer = O número de segundos para mostrar a resposta
deck-config-seconds-to-show-answer-tooltip-2 = O número de segundos de espera antes de aplicar a acção da resposta, quando o avançar automaticamente está activo. Atribua o valor 0 para desactivar.
deck-config-question-action-show-answer = Mostrar Resposta
deck-config-question-action-show-reminder = Mostrar Lembrete
deck-config-question-action = Acção da pergunta
deck-config-question-action-tool-tip = A acção a aplicar depois de ter sido mostrada a pergunta, e o tempo limite tenha sido ultrapassado.
deck-config-answer-action = Acção da resposta
deck-config-answer-action-tooltip-2 = A acção a aplicar depois de ter sido mostrada a resposta, e o tempo limite tenha sido ultrapassado.
deck-config-wait-for-audio-tooltip-2 = Aguardar pelo fim do áudio antes de aplicar a acção da pergunta ou da resposta.

## Audio section

deck-config-audio-title = Áudio
deck-config-disable-autoplay = Não reproduzir áudio automacticamente
deck-config-disable-autoplay-tooltip =
    Quando activado, o Anki não reproduzirá o áudio automacticamente.
    Este pode ser reproduzido ao tocar no ícone do áudio, ou utilizando o botão Reprod..
deck-config-skip-question-when-replaying = Passar a pergunta à frente aquando da repetição da resposta
deck-config-always-include-question-audio-tooltip = Se o áudio da pergunta deve ou não ser reproduzido aquando da da utilização do botão Reprod. enquanto vê o lado da resposta.

## Advanced section

deck-config-advanced-title = Avançadas
deck-config-maximum-interval-tooltip =
    O número máximo de dias que uma revisão deve aguardar. Quando o número
    de revisões atingiu o limite, `Difícil`, `Bom` e `Fácil` vão todos adicionar o mesmo atraso.
    Quanto menor for este valor, maior será a sua carga.
deck-config-starting-ease-tooltip =
    O multiplicador de facilidade com que as novas fichas começam. Por defeito, o botão `Bom` numa
    ficha recém aprendida vai programar a próxima revisão para daí a 2.5x o atraso anterior.
deck-config-easy-bonus-tooltip =
    Um multiplicador extra que é aplicado ao intervalo duma ficha quando
    esta é classificada como `Fácil`.
deck-config-interval-modifier-tooltip =
    Este multiplicador é aplicado a todas as revisões, e pequenos ajustes permitem 
    que o Anki seja mais conservador ou mais agressivo no seu planeamento. Por favor
    consulte o manual antes de alterar esta opção.

## Easy Days section.

deck-config-easy-days-title = Dias Fáceis
deck-config-easy-days-monday = Seg
deck-config-easy-days-tuesday = Ter
deck-config-easy-days-wednesday = Qua
deck-config-easy-days-thursday = Qui
deck-config-easy-days-friday = Sex
deck-config-easy-days-saturday = Sáb
deck-config-easy-days-sunday = Dom
deck-config-easy-days-reduced = Reduzido
deck-config-easy-days-minimum = Mínimo
deck-config-easy-days-no-normal-days = Deve configurar pelo menos um dia como '{ deck-config-easy-days-normal }'.
deck-config-easy-days-change = Revisões existentes não serão reagendadas a não ser que a opção '{ deck-config-reschedule-cards-on-change }' esteja activa nas opções de FSRS.

## Adding/renaming

deck-config-add-group = Adicionar modelo
deck-config-rename-group = Renomear modelo
deck-config-clone-group = Clonar modelo

## Removing

deck-config-remove-group = Remover modelo

## Other Buttons

deck-config-save-button = Guardar
deck-config-save-to-all-subdecks = Atribuir a todos os sub-baralhos
deck-config-save-and-optimize = Optimizar todos os modelos

## These strings are shown via the Description button at the bottom of the
## overview screen.


## Warnings shown to the user

deck-config-relearning-steps-above-minimum-interval = O intervalo mínimo deve ser pelo menos tão longo quanto a sua última etapa de reaprendizagem.

## Selecting a deck

deck-config-which-deck = Pretende apresentar as definições para que baralho?

## Messages related to the FSRS scheduler

deck-config-optimize-button = Optimizar o modelo actual
deck-config-time-to-optimize = Já há algum tempo que aqui não vinha - é recomendado utilizar o botão "Optimizar todos os modelos".

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.


## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-optimize-all-tip = Pode optimizar todos os modelos duma só vez utilizando o botão com a seta para baixo ao lado de "Guardar".
