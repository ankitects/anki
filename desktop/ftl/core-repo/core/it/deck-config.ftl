### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    { $decks ->
        [one] utilizzato da 1 mazzo
       *[other] utilizzato da { $decks } mazzi
    }
deck-config-default-name = Predefinito
deck-config-title = Opzioni del mazzo

## Daily limits section

deck-config-daily-limits = Limiti giornalieri
deck-config-new-limit-tooltip =
    Il numero massimo di nuove carte da introdurre in un giorno, se sono disponibili nuove carte.
    Poiché il nuovo materiale aumenterà il carico di lavoro a breve termine, questo dovrebbe essere in genere
    almeno 10 volte inferiore al limite di ripetizioni.
deck-config-review-limit-tooltip =
    Il numero massimo di carte da ripetere in un giorno,
    se le carte sono pronte per essere ripassate.
deck-config-limit-deck-v3 =
    Quando si studia un mazzo che contiene dei mazzi figli, i limiti fissati su ciascun mazzo figlio determinano
    il numero massimo di carte recuperate da quel particolare mazzo.
    I limiti del mazzo padre determinano il numero totale delle carte che verranno mostrate.
deck-config-limit-new-bound-by-reviews =
    Il limite di ripetizioni influisce sul limite delle nuove carte. Per esempio, se il limite
    di ripetizioni è 200 e ci sono 190 carte in attesa, verranno introdotte al massimo 10 nuove carte.
    Se il limite di ripetizioni è stato raggiunto, non verrà mostrata alcuna nuova carta.
deck-config-limit-interday-bound-by-reviews =
    Il limite di ripetizioni influisce anche sulle carte in apprendimento intergiornaliero.
    Quando si applica il limite, vengono recuperate prima le carte in apprendimento intergiornaliero e poi quelle da ripetere.
deck-config-tab-description =
    - `Preimpostazione`: Il limite è condiviso da tutti i mazzi che usano questa preimpostazione.
    - `Questo Mazzo`: Il limite è specifico per questo mazzo.
    - `Solo Oggi`: Modifica temporanea del limite di questo mazzo.
deck-config-new-cards-ignore-review-limit = Ignora le nuove carte nel conteggio ripetizioni/giorno
deck-config-new-cards-ignore-review-limit-tooltip =
    Per impostazione predefinita, il limite di ripetizioni si applica anche alle nuove carte
    e non verranno mostrate nuove carte quando il limite di ripetizioni è stato raggiunto.
    Se questa opzione è abilitata, le nuove carte verranno mostrate
    indipendentemente dal limite di ripetizioni.
deck-config-apply-all-parent-limits = I limiti iniziano dalla cima
deck-config-apply-all-parent-limits-tooltip =
    Per impostazione predefinita, i limiti partono dal mazzo selezionato. 
    Attivando questa opzione, invece, i limiti saranno calcolati
    a partire dal mazzo genitore di massimo livello, il che può risultare utile
    per studiare singoli mazzi figli, pur mantenendo un limite complessivo
    sul numero di carte da studiare ogni giorno.
deck-config-affects-entire-collection = Influisce sull'intera collezione.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Preimpostazione
deck-config-deck-only = Questo mazzo
deck-config-today-only = Solo oggi

## New Cards section

deck-config-learning-steps = Passi di apprendimento
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Gli intervalli sono generalmente espressi in minuti (es. `1m`) o giorni (es. `2g`), ma sono supportate anche ore (es. `1h`) e secondi (es. `30s`).
deck-config-learning-steps-tooltip =
    Uno o più intervalli, separati da spazi. Il primo intervallo viene usato
    quando viene premuto il pulsante `Ripeti` su una nuova carta, di default 1 minuto.
    Il pulsante `Normale` fa avanzare la carta al passo successivo, di default 10 minuti.
    Una volta che tutti i passi sono stati completati, la carta diventa una carta di ripasso,
    e verrà mostrata in un giorno diverso. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip =
    Il numero di giorni da attendere prima di mostrare nuovamente una carta, dopo che il pulsante "Normale"
    è stato premuto nel passo di apprendimento finale.
deck-config-easy-interval-tooltip =
    Il numero di giorni da attendere prima di mostrare nuovamente una carta, dopo che il pulsante "Facile"
    è usato per rimuovere immediatamente una carta dall'apprendimento.
deck-config-new-insertion-order = Ordine di inserimento
deck-config-new-insertion-order-tooltip =
    Determina la posizione (numero di scadenza) assegnata alle nuove carte quando se ne aggiungono di nuove.
    Carte con un numero di scadenza inferiore verranno mostrate per prime durante lo studio. Cambiando
    questa opzione si aggiornerà automaticamente la posizione attuale delle nuove carte.
deck-config-new-insertion-order-sequential = Sequenziale (prima le carte più vecchie)
deck-config-new-insertion-order-random = Casuale
deck-config-new-insertion-order-random-with-v3 = Con il pianificatore V3, è meglio lasciare selezionata l'opzione "sequenziale" e modificare invece l'ordine di recupero delle nuove carte.

## Lapses section

deck-config-relearning-steps = Passi di riapprendimento
deck-config-relearning-steps-tooltip =
    Zero o più intervalli, separati da spazi. Per impostazione predefinita, premendo il pulsante `Ripeti`
    su una carta di ripasso la mostrerà nuovamente dopo 10 minuti. Se nessun intervallo
    è specificato, l'intervallo della carta verrà cambiato, senza entrare
    nella fase di riapprendimento. { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip =
    Il numero di volte che `Ripeti` deve essere premuto su una carta di ripasso prima
    che questa venga contrassegnata come sanguisuga. Le sanguisughe sono carte che richiedono molto
    del proprio tempo, e quando una carta è contrassegnata come tale, è una buona idea riscriverla,
    cancellarla o pensare ad un espediente mnemonico per ricordarla.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    `Etichetta Soltanto`: aggiungi l'etichetta `Sanguisuga` alla nota, e mostra un pop-up.
    
    `Sospendi Carta`: oltre ad aggiungere l'etichetta, nascondi la carta fino a
    quando non è rimossa manualmente dalla sospensione.

## Burying section

deck-config-bury-title = Sepoltura
deck-config-bury-new-siblings = Seppellisci le nuove carte sorelle fino al giorno successivo
deck-config-bury-review-siblings = Seppellisci le carte sorelle di ripasso fino al giorno successivo
deck-config-bury-interday-learning-siblings = Seppellisci le carte sorelle in apprendimento intergiornaliero
deck-config-bury-new-tooltip =
    Scegli se altre `nuove` carte della stessa nota (es. carte invertite, cancellazioni cloze adiacenti)
    debbano essere rimandate fino al giorno successivo.
deck-config-bury-review-tooltip = Scegli se le altre carte `da ripetere` della stessa nota debbano essere rimandate fino al giorno successivo.
deck-config-bury-interday-learning-tooltip = Scegli se le altre carte `in apprendimento` della stessa nota, con intervallo > 1 giorno debbano essere rimandate fino al giorno successivo.
deck-config-bury-priority-tooltip =
    Quando Anki recupera le carte, recupera prima le carte
    in apprendimento intragiornaliero, poi quelle in apprendimento intergiornaliero,
    quindi le carte da ripetere e infine le nuove carte. 
    Questo ha un impatto sulla funzione di "sepoltura" delle carte:
    
    - Se hai abilitato tutte le opzioni di sepoltura, verrà mostrata la carta sorella che viene prima nella lista appena descritta. 
    Ad esempio, una carta da ripetere avrà la priorità rispetto a una nuova carta.
    - Le carte sorelle che seguono nella lista non influiscono sui tipi di carte precedenti. 
    Ad esempio, se disabiliti la funzione di sepoltura per le nuove carte e studi una nuova carta, 
    non verrà seppellita alcuna carta in apprendimento intergiornaliero o da ripetere. 
    Pertanto, potresti incontrare sia una carta sorella da ripetere che una nuova carta sorella nella stessa sessione.

## Gather order and sort order of cards

deck-config-ordering-title = Ordine di presentazione
deck-config-new-gather-priority = Ordine di recupero delle nuove carte
deck-config-new-gather-priority-tooltip-2 =
    `Mazzo`: recupera carte da ogni mazzo in ordine, iniziando dalla cima. Le carte di ciascun mazzo vengono recuperate in posizione crescente. 
    Se viene raggiunto il limite giornaliero del mazzo selezionato, il recupero potrebbe interrompersi prima che siano stati controllati tutti i mazzi. 
    Questo ordine è veloce soprattutto per collezioni di grandi dimensioni, e permette di dare la priorità ai mazzi figli più vicini alla cima dell'elenco.
    
    `Posizione crescente`: recupera le carte in ordine crescente (numero di scadenza); in genere, ciò vuol dire dare la priorità alle carte aggiunte per prime. 
    
    `Posizione decrescente`: recupera le carte in ordine decrescente (numero di scadenza); in genere ciò significa dare la priorità alle carte aggiunte più di recente.
    
    `Casuale (note)`: seleziona delle note in maniera casuale e quindi ne recupera le carte. 
    Se la sepoltura delle carte sorelle è disabilitata, ciò permette di vedere tutte le carte di una nota in una singola sessione (es. sia la carta fronte→retro che la carta retro→fronte).
    
    `Casuale (carte)`: recupera le carte in maniera completamente casuale.
deck-config-new-card-sort-order = Ordine delle nuove carte
deck-config-new-card-sort-order-tooltip-2 =
    `Tipo di carta, poi in ordine di recupero`: mostra le carte seguendo l'ordine dei tipi di carta. Se la sepoltura delle carte sorelle è disabilitata, questo assicura per es. che tutte le carte fronte→retro vengano mostrate prima di quelle retro→fronte. Questo è utile per avere tutte le carte della stessa nota mostrate nella stessa sessione, ma non troppo vicine le une alle altre.
    
    `Ordine di recupero`: mostra le carte nell'ordine di recupero. Se la sepoltura delle carte sorelle è disabilitata, in genere questo farà sì che tutte le carte di una stessa nota vengano visualizzate una dopo l'altra.
    
    `Tipo di carta, quindi casuale`: identico a `Tipo di carta, poi ordine di recupero`, ma le carte dello stesso tipo vengono mostrate in ordine casuale. Se usi `Posizione crescente` per vedere le carte più vecchie per prime, potresti sfruttare questa impostazione per vedere tali carte in ordine casuale, ma assicurando sempre che le carte di una stessa nota non finiscano troppo vicine le une alle altre.
    
    `Nota casuale, quindi tipo di carta`: recupera le note in maniera casuale, quindi mostra tutte le loro carte, in ordine.
    
    `Casuale`: mescola completamente le carte recuperate.
deck-config-new-review-priority = Ordine nuove/da ripassare
deck-config-new-review-priority-tooltip = Determina quando mostrare le nuove carte in relazione a quelle di ripasso.
deck-config-interday-step-priority = Ordine apprendimento intergiornaliero/ripetizioni
deck-config-interday-step-priority-tooltip =
    Determina quando mostrare le carte in (re)apprendimento che superano la soglia di un giorno.
    
    Il limite di ripetizioni è sempre applicato prima alle carte in apprendimento intergiornaliero
    e solo poi a quelle da ripetere. Questa opzione determina l'ordine secondo il quale vengono mostrate le carte recuperate, ma le carte in apprendimento intergiornaliero sono sempre recuperate per prime.
deck-config-review-sort-order = Ordine delle carte di ripasso
deck-config-review-sort-order-tooltip =
    L'ordine predefinito dà priorità alle carte che sono in attesa da più tempo, così
    se hai una lista di carte arretrate, quella che sta aspettando da più tempo
    apparirà per prima. Se hai una lunga lista che richiederebbe più di qualche giorno
    per essere completata, o desideri vedere le carte secondo l'ordine dei mazzi figli,
    troverai più utili i metodi di ordinamento alternativi.
deck-config-display-order-will-use-current-deck =
    Verrà usato l'ordine di apparizione del mazzo selezionato da studiare,
    e non di suoi eventuali mazzi figli.

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = Mazzo
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = Mazzo, quindi note casuali
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = Posizione crescente
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = Posizione decrescente
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = Casuale (note)
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = Casuale (carte)
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = Tipo di carta, poi in ordine casuale
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = Nota casuale, quindi tipo di carta
# Sort the cards randomly.
deck-config-sort-order-random = Casuale
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = Tipo di carta
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = Ordine di recupero
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = Mischia con le carte di ripasso
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = Mostra dopo le carte di ripasso
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = Mostra prima delle carte di ripasso
# Sort the cards first by due date, in ascending order (oldest due date to newest), then randomly within the same due date.
deck-config-sort-order-due-date-then-random = Data di scadenza, poi a caso
# Sort the cards first by due date, in ascending order (oldest due date to newest), then by deck within the same due date.
deck-config-sort-order-due-date-then-deck = Data di scadenza, poi ordine del mazzo
# Sort the cards first by deck, then by due date in ascending order (oldest due date to newest) within the same deck.
deck-config-sort-order-deck-then-due-date = Ordine del mazzo, poi data di scadenza
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = Intervalli crescenti
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = Intervalli decrescenti
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = Facilità crescente
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = Facilità decrescente
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = Difficoltà crescente
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = Difficoltà decrescente
# Sort the cards by retrievability percentage, in ascending order (0% to 100%, least retrievable to most easily retrievable).
deck-config-sort-order-retrievability-ascending = Rammentabilità crescente
# Sort the cards by retrievability percentage, in descending order (100% to 0%, most easily retrievable to least retrievable).
deck-config-sort-order-retrievability-descending = Rammentabilità decrescente

## Timer section

deck-config-timer-title = Timer
deck-config-maximum-answer-secs = Tempo massimo di risposta in secondi
deck-config-maximum-answer-secs-tooltip =
    Determina il numero massimo di secondi da registrare per singola ripetizione.
    Se una risposta supera questo tempo (per esempio perché
    ti sei allontanato dallo schermo), verrà registrato il tempo massimo
    qui impostato.
deck-config-show-answer-timer-tooltip =
    Mostra un timer nella schermata delle ripetizioni che conta il numero di secondi
    impiegati per ripassare ciascuna carta.
deck-config-stop-timer-on-answer = Ferma il timer al momento della risposta
deck-config-stop-timer-on-answer-tooltip =
    Determina se bloccare o meno il timer una volta che è stata rivelata la risposta.
    Questa opzione non influisce sulle statistiche.

## Auto Advance section

deck-config-seconds-to-show-question = Secondi prima di mostrare la domanda
deck-config-seconds-to-show-question-tooltip-3 = Determina il numero di secondi da attendere prima di mostrare la domanda, quando l'avanzamento automatico è abilitato. Imposta a 0 per disabilitare.
deck-config-seconds-to-show-answer = Secondi prima di mostrare la risposta
deck-config-seconds-to-show-answer-tooltip-2 = Determina il numero di secondi da attendere prima di mostrare la risposta, quando l'avanzamento automatico è abilitato. Imposta a 0 per disabilitare.
deck-config-question-action-show-answer = Mostra risposta
deck-config-question-action-show-reminder = Mostra promemoria
deck-config-question-action = Azione per la domanda
deck-config-question-action-tool-tip = L'azione da svolgere sulla carta attuale prima di passare a quella successiva.
deck-config-answer-action = Azione di risposta
deck-config-answer-action-tooltip-2 = L'azione da svolgere dopo che la risposta è stata mostrata e il tempo è scaduto.
deck-config-wait-for-audio-tooltip-2 = Attendi la fine dell'audio prima di applicare automaticamente l'azione per la risposta o la domanda.

## Audio section

deck-config-audio-title = Audio
deck-config-disable-autoplay = Non riprodurre l'audio automaticamente
deck-config-disable-autoplay-tooltip =
    Se abilitato, l'audio non verrà riprodotto automaticamente.
    Può essere riprodotto manualmente facendo clic/toccando un'icona audio, o utilizzando il comando di riproduzione audio.
deck-config-skip-question-when-replaying = Salta la domanda durante la riproduzione della risposta
deck-config-always-include-question-audio-tooltip =
    Determina se l'audio della domanda deve essere incluso quando si usa l'azione "Riproduci di nuovo"
    mentre si guarda il lato della risposta di una carta.

## Advanced section

deck-config-advanced-title = Avanzato
deck-config-maximum-interval-tooltip =
    Il numero massimo di giorni di attesa per una carta di ripasso. Quando
    le ripetizioni hanno raggiunto il limite, `Difficile`, `Normale`, `Facile` daranno tutti lo
    stesso intervallo di tempo. Quanto più breve è questo valore, tanto maggiore sarà il carico di lavoro.
deck-config-starting-ease-tooltip =
    Il moltiplicatore di facilità con il quale iniziano le nuove carte. Per impostazione predefinita, il pulsante
    `Facile` su una carta appena appresa ritarda la ripetizione successiva di 2,5 volte rispetto all'intervallo precedente.
deck-config-easy-bonus-tooltip =
    Un moltiplicatore aggiuntivo che è applicato all'intervallo di una carta ripassata
    quando la si valuta `Facile`.
deck-config-interval-modifier-tooltip =
    Questo moltiplicatore è applicato a tutte le ripetizioni, e minime variazioni
    del suo valore rendono la pianificazione più conservativa o aggressiva.
    Consultare il manuale prima di cambiare questa opzione.
deck-config-hard-interval-tooltip = Il moltiplicatore aggiunto all'intervallo di una ripetizione quando la si valuta `Difficile`.
deck-config-new-interval-tooltip = Il moltiplicatore applicato all'intervallo di una ripetizione quando viene premuto `Ripeti`.
deck-config-minimum-interval-tooltip = L'intervallo minimo dato ad una carta ripassata dopo aver premuto `Ripeti`.
deck-config-custom-scheduling = Pianificazione personalizzata
deck-config-custom-scheduling-tooltip = Influisce sull'intera collezione. Usare a proprio rischio e pericolo!

## Easy Days section.

deck-config-easy-days-title = Giorni di riposo
deck-config-easy-days-monday = Lunedì
deck-config-easy-days-tuesday = Martedì
deck-config-easy-days-wednesday = Mercoledì
deck-config-easy-days-thursday = Giovedì
deck-config-easy-days-friday = Venerdì
deck-config-easy-days-saturday = Sabato
deck-config-easy-days-sunday = Domenica
deck-config-easy-days-normal = Normale
deck-config-easy-days-reduced = Ridotto
deck-config-easy-days-minimum = Minimo
deck-config-easy-days-no-normal-days = Almeno un giorno deve essere impostato come '{ deck-config-easy-days-normal }'.
deck-config-easy-days-change = Le ripetizioni esistenti non saranno ripianificate a meno che l'opzione "{ deck-config-reschedule-cards-on-change }" non sia attiva nelle impostazioni di FSRS.

## Adding/renaming

deck-config-add-group = Aggiungi preimpostazione
deck-config-name-prompt = Nome
deck-config-rename-group = Rinomina preimpostazione
deck-config-clone-group = Clona preimpostazione

## Removing

deck-config-remove-group = Rimuovi preimpostazione
deck-config-will-require-full-sync =
    Il cambiamento richiesto richiede una sincronizzazione a senso unico. Se hai effettuato
    cambiamenti su un altro dispositivo, e non li hai ancora sincronizzati con questo
    dispositivo, per favore fallo prima di procedere.
deck-config-confirm-remove-name = Rimuovere { $name }?

## Other Buttons

deck-config-save-button = Salva
deck-config-save-to-all-subdecks = Salva su tutti i mazzi figli
deck-config-save-and-optimize = Ottimizza tutte le preimpostazioni
deck-config-revert-button-tooltip = Ripristina questa impostazione al suo valore predefinito.

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Gestione Anki 2.1.41+
deck-config-description-new-handling-hint =
    Tratta l'input come Markdown, e cancella l'input HTML. Quando abilitato, la descrizione sarà mostrata anche sulla schermata di congratulazioni.
    Markdown apparirà come testo su versioni di Anki inferiori o uguali a 2.1.40.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    Un mazzo padre ha un limite di { $cards ->
        [one] { $cards } carta
       *[other] { $cards } carte
    }, che sovrascriverà questo limite.
deck-config-reviews-too-low =
    { $cards ->
        [one] Se viene aggiunta { $cards } nuova carta al giorno, il proprio limite di ripetizioni dovrebbe essere almeno { $expected }.
       *[other] Se vengono aggiunte { $cards } nuove carte al giorno, il proprio limite di ripetizioni dovrebbe essere almeno { $expected }.
    }
deck-config-learning-step-above-graduating-interval = L'intervallo di promozione dovrebbe essere lungo almeno quanto il passo finale di apprendimento.
deck-config-good-above-easy = L'intervallo delle carte facili dovrebbe essere lungo almeno quanto l'intervallo di promozione.
deck-config-relearning-steps-above-minimum-interval = L'intervallo minimo dovrebbe essere lungo almeno quanto il passo finale di riapprendimento.
deck-config-maximum-answer-secs-above-recommended = È possibile pianificare le ripetizioni in maniera più efficiente se le domande sono mantenute brevi.
deck-config-too-short-maximum-interval = È sconsigliato impostare un intervallo massimo sotto i 6 mesi.
deck-config-ignore-before-info = Per ottimizzare i parametri FSRS, verranno utilizzate (all'incirca) { $included }/{ $totalCards } carte.

## Selecting a deck

deck-config-which-deck = Per quale mazzo desideri visualizzare le opzioni?

## Messages related to the FSRS scheduler

deck-config-updating-cards = Aggiornamento delle carte in corso: { $current_cards_count }/{ $total_cards_count }...
deck-config-invalid-parameters = I parametri FSRS inseriti non sono validi. Lascia il campo vuoto per usare quelli predefiniti.
deck-config-not-enough-history = La mole della storia delle ripetizioni è insufficiente per eseguire questa operazione.
deck-config-unable-to-determine-desired-retention = Impossibile determinare una ritenzione ottimale.
deck-config-must-have-400-reviews =
    { $count ->
        [one] Trovata solo { $count } ripetizione. È necessario avere almeno 400 ripetizioni per questa operazione.
       *[other] Trovate solo { $count } ripetizioni. È necessario avere almeno 400 ripetizioni per questa operazione.
    }
# Numbers that control how aggressively the FSRS algorithm schedules cards
deck-config-weights = Parametri FSRS
deck-config-compute-optimal-weights = Ottimizza i parametri FSRS
deck-config-compute-minimum-recommended-retention = Ritenzione minima consigliata
deck-config-optimize-button = Ottimizza
# Indicates that a given function or label, provided via the "text" variable, operates slowly.
deck-config-slow-suffix = { $text } (lento)
deck-config-compute-button = Calcola
deck-config-ignore-before = Escludi le carte studiate prima di
deck-config-time-to-optimize = È passato diverso tempo dall'ultima ottimizzazione – è consigliato usare il pulsante "Ottimizza tutto".
deck-config-evaluate-button = Valuta
deck-config-desired-retention = Ritenzione desiderata
deck-config-historical-retention = Ritenzione storica
deck-config-smaller-is-better = Numeri più bassi indicano stime di memoria migliori.
deck-config-steps-too-large-for-fsrs = Quando FSRS è abilitato, è sconsigliato usare passi di (re)apprendimento intergiornalieri (cioè ≥ 1 giorno).
deck-config-get-params = Ottieni parametri
deck-config-predicted-minimum-recommended-retention = Ritenzione minima consigliata: { $num }
deck-config-complete = { $num }% completo.
deck-config-iterations = Iterazione: { $count }...
deck-config-reschedule-cards-on-change = Ripianifica le carte in caso di modifica
deck-config-fsrs-tooltip =
    Influisce sull'intera collezione.
    
    FSRS (Free Spaced Repetition Scheduler; in italiano: "Pianificatore di Ripetizione Dilazionata Libera") 
    costituisce un'alternativa al vecchio pianificatore SM2 (SuperMemo 2) di Anki.
    Determinando in modo più accurato quando è probabile che un'informazione venga dimenticata,
    può aiutare a ricordare più materiale nello stesso lasso di tempo. Questa impostazione è condivisa
    da tutte le preimpostazioni.
    
    Qualora in precedenza si fosse utilizzata la versione non nativa di FSRS (basata sull'aggiunta
    di codice personalizzato nella sezione "Pianificazione personalizzata"), è fondamentale assicurarsi
    di aver svuotato tale sezione prima di abilitare questa opzione.
deck-config-desired-retention-tooltip =
    Il valore predefinito di 0,9 pianificherà le carte in modo da avere una probabilità del 90% di ricordarle quando riemergeranno per la revisione. Aumentando questo valore, le carte verranno mostrate più frequentemente per incrementare la probabilità di ricordarle. Diminuendo questo valore, le carte verranno mostrate meno frequentemente e crescerà il rischio di dimenticarle.
    
    È consigliato essere prudenti nell'apportare modifiche a questo parametro: valori più alti aumenteranno notevolmente il proprio carico di lavoro, mentre valori più bassi possono causare demoralizzazione quando comportano il dimenticare molte informazioni.
deck-config-desired-retention-tooltip2 = I valori di carico di lavoro mostrati nel riquadro informativo sono una stima approssimativa. Usa il simulatore per una maggiore accuratezza.
deck-config-historical-retention-tooltip =
    Quando manca parte dello storico delle ripetizioni, FSRS deve necessariamente colmare le lacune.
    Per impostazione predefinita si presuppone che sia stato ricordato il 90% del materiale delle vecchie ripetizioni.
    Qualora la vecchia ritenzione fosse significativamente più alta o più bassa, la regolazione di questa opzione
    permette di approssimare meglio le ripetizioni mancanti.
    
    Lo storico delle ripetizioni potrebbe essere incompleto per due motivi principali:
    1. L'utilizzo dell'opzione "escludi le ripetizioni prima di".
    2. L'eliminazione di voci dal registro delle ripetizioni per liberare spazio oppure l'importazione di materiale
    da un programma SRS diverso.
    
    Quest'ultima eventualità è piuttosto rara, quindi, a meno che non si sia utilizzata l'opzione di cui al punto 1,
    probabilmente non è necessario modificare questa impostazione.
deck-config-weights-tooltip2 =
    I parametri FSRS sono dei valori che influenzano la pianificazione delle carte.
    Sono presenti dei parametri predefiniti che, attraverso l'opzione sottostante,
    è possibile ottimizzare in modo che si adattino alle proprie prestazioni
    nei mazzi che utilizzano questa preimpostazione.
deck-config-reschedule-cards-on-change-tooltip =
    N.B.: influisce sull'intera collezione.
    
    Questa opzione determina se le scadenze delle carte verranno modificate quando si abilita FSRS o si cambiano i parametri. 
    L'impostazione predefinita è quella di non ripianificare le carte: le ripetizioni future utilizzeranno la nuova pianificazione, 
    ma non ci sarà alcuna modifica immediata al proprio carico di lavoro. 
    Se la ripianificazione è abilitata, le scadenze delle carte verranno modificate.
deck-config-reschedule-cards-warning =
    A seconda della ritenzione desiderata, ciò può comportare la scadenza di un gran numero di carte, per cui non è consigliato abilitare tale opzione la prima volta che si usa FSRS.
    
    Usare questa opzione con parsimonia, poiché aggiungerà una voce di revisione per ciascuna delle carte e
    aumenterà le dimensioni della collezione.
deck-config-ignore-before-tooltip-2 =
    Le carte studiate prima della data specificata saranno escluse dall'ottimizzazione dei parametri FSRS.
    Questo può risultare utile nel caso in cui si fossero importati i dati di pianificazione di un'altra persona o qualora siano cambiate le proprie abitudini nell'utilizzo dei pulsanti di risposta.
deck-config-compute-optimal-weights-tooltip2 =
    Facendo clic sul pulsante Ottimizza, FSRS analizza la cronologia delle ripetizioni e genera parametri ottimizzati per la propria memoria e per il contenuto che si sta studiando. Se i mazzi variano molto in termini di difficoltà, è consigliato assegnare loro delle preimpostazioni separate, in quanto i parametri per i mazzi facili e per quelli difficili sono necessariamente diversi. 
    Non è necessario ottimizzare i parametri frequentemente ma è sufficiente farlo una volta ogni qualche mese.
    
    Per impostazione predefinita, i parametri vengono calcolati in base alla cronologia delle ripetizioni di tutti i mazzi che utilizzano la preimpostazione attuale. Tuttavia è possibile decidere quali carte sono utilizzate per l'ottimizzazione agendo sul contenuto della casella di ricerca.
deck-config-compute-optimal-retention-tooltip4 =
    Questo strumento cerca di trovare il valore di ritenzione
    che permette di apprendere la maggior quantità di materiale
    nel minor tempo possibile. Il numero calcolato può essere utile come riferimento
    per decidere a quale valore impostare la ritenzione desiderata.
    È possibile scegliere un valore di ritenzione desiderata più alto,
    qualora si sia disposti a passare più tempo a studiare a fronte di un tasso di ritenzione più elevato. Non è invece consigliato impostare
    un valore di ritenzione desiderata inferiore al minimo,
    in quanto comporta una mole di lavoro maggiore, a causa del tasso elevato di oblio (dimenticanza nel tempo).
deck-config-please-save-your-changes-first = Per favore salva prima le modifiche.
deck-config-workload-factor-change =
    Carico di lavoro approssimativo: { $factor }x
    (rispetto al { $previousDR }% di ritenzione desiderata)
deck-config-workload-factor-unchanged = All'aumentare di questo valore le carte vengono mostrate più frequentemente.
deck-config-desired-retention-too-low = La ritenzione desiderata è molto bassa, il che può portare a intervalli molto lunghi.
deck-config-desired-retention-too-high = La ritenzione desiderata è molto alta, il che può portare a intervalli molto brevi.
deck-config-percent-of-reviews =
    { $reviews ->
        [one] { $pct }% di { $reviews } ripetizione
       *[other] { $pct }% di { $reviews } ripetizioni
    }
deck-config-percent-input = { $pct }%
# This message appears during FSRS parameter optimization.
deck-config-checking-for-improvement = Controllo miglioramenti...
deck-config-optimizing-preset = Ottimizzazione della preimpostazione in corso { $current_count }/{ $total_count }...
deck-config-fsrs-must-be-enabled = Abilita prima FSRS.
deck-config-fsrs-params-optimal = Attualmente i parametri FSRS sembrano essere ottimali.
deck-config-fsrs-params-no-reviews = Nessuna ripetizione trovata. Verificare che la preimpostazione sia assegnata a tutti i mazzi che si desidera ottimizzare (inclusi i sotto-mazzi) e riprovare.
deck-config-wait-for-audio = Attendi l'audio
deck-config-show-reminder = Mostra promemoria
deck-config-answer-again = Rispondi Ripeti
deck-config-answer-hard = Rispondi Difficile
deck-config-answer-good = Rispondi Normale
deck-config-days-to-simulate = Giorni da simulare
deck-config-desired-retention-below-optimal = La ritenzione desiderata attualmente impostata è inferiore a quella ottimale. È consigliato aumentarla.
# Description of the y axis in the FSRS simulation
# diagram (Deck options -> FSRS) showing the total number of
# cards that can be recalled or retrieved on a specific date.
deck-config-fsrs-simulator-experimental = Simulatore FSRS (sperimentale)
deck-config-fsrs-simulate-desired-retention-experimental = Simulatore di ritenzione desiderata FSRS (sperimentale)
deck-config-fsrs-desired-retention-help-me-decide-experimental = Aiutami a decidere (sperimentale)
deck-config-additional-new-cards-to-simulate = Ulteriori nuove carte da simulare
deck-config-simulate = Simula
deck-config-clear-last-simulate = Rimuovi ultima simulazione
deck-config-fsrs-simulator-radio-count = Ripetizioni
deck-config-advanced-settings = Impostazioni avanzate
deck-config-smooth-graph = Grafico smussato
deck-config-suspend-leeches = Sospendi carte sanguisuga
deck-config-save-options-to-preset = Applica modifiche alla preimpostazione
deck-config-save-options-to-preset-confirm = Sovrascrivere le opzioni nella preimpostazione attuale con quelle del simulatore?
deck-config-plotted-on-x-axis = (rappresentata sull'asse x)
# Radio button in the FSRS simulation diagram (Deck options -> FSRS) selecting
# to show the total number of cards that can be recalled or retrieved on a
# specific date.
deck-config-fsrs-simulator-radio-memorized = Memorizzate
deck-config-fsrs-simulator-radio-ratio = Rapporto tra durata e carte memorizzate
# $time here is pre-formatted e.g. "10 Seconds" 
deck-config-fsrs-simulator-ratio-tooltip = { $time } per carta memorizzata

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.

# Checkbox
deck-config-health-check = Verifica l'integrità durante l'ottimizzazione
# Message box showing the result of the health check
deck-config-fsrs-bad-fit-warning =
    Verifica dell'integrità:
    La memoria è difficile da prevedere per FSRS. Suggerimenti:
    
    - Sospendi o riformula le carte sanguisuga.
    - Usa i pulsanti di risposta in maniera coerente. Ricorda che "Difficile" è una valutazione di superamento, non di fallimento.
    - Comprendi prima di memorizzare.
    
    Seguendo questi suggerimenti, le prestazioni in genere migliorano nel giro di qualche mese.
# Message box showing the result of the health check
deck-config-fsrs-good-fit =
    Verifica dell'integrità:
    FSRS si adatta bene alla tua memoria.

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-a-100-day-interval =
    { $days ->
        [one] Un intervallo di 100 giorni diventerà di { $days } giorno.
       *[other] Un intervallo di 100 giorni diventerà di { $days } giorni.
    }
deck-config-fsrs-simulator-y-axis-title-time = Durata ripetizioni/giorno
deck-config-fsrs-simulator-y-axis-title-count = Numero ripetizioni/giorno
deck-config-fsrs-simulator-y-axis-title-memorized = N° carte memorizzate
deck-config-bury-siblings = Seppellisci carte sorelle
deck-config-do-not-bury = Non seppellire carte sorelle
deck-config-bury-if-new = Seppellisci se nuove
deck-config-bury-if-new-or-review = Seppellisci se nuove o di ripasso
deck-config-bury-if-new-review-or-interday = Seppellisci se nuove, di ripasso, o in apprendimento intergiornaliero
deck-config-bury-tooltip =
    La carte sorelle sono altre carte appartenenti alla stessa nota (es. carte fronte→retro e retro→fronte, 
    oppure altre cancellazioni cloze dallo stesso testo).
    
    Quando questa opzione è disattivata, più carte dalla stessa nota possono essere visualizzate lo stesso giorno. 
    Quando è attivata, le carte sorelle verranno seppellite automaticamente, nascondendole fino al giorno successivo. 
    Questa opzione consente di scegliere quali tipi di carta verranno sepolti quando si risponde ad una delle loro carte sorelle.
    
    Quando si utilizza il pianificatore V3, è possibile seppellire anche le carte in apprendimento intergiornaliero. 
    Le carte in apprendimento intergiornaliero sono carte con un passo di apprendimento attuale di uno o più giorni.
deck-config-seconds-to-show-question-tooltip = Determina il numero di secondi da attendere prima di rivelare la risposta, quando l'avanzamento automatico è abilitato. Imposta a 0 per disabilitare.
deck-config-answer-action-tooltip = L'azione da svolgere sulla carta attuale prima di passare automaticamente a quella successiva.
deck-config-wait-for-audio-tooltip = Attendi la fine dell'audio prima di rivelare automaticamente la risposta o passare alla domanda successiva
deck-config-ignore-before-tooltip =
    Le ripetizioni precedenti alla data specificata saranno escluse dall'ottimizzazione e dalla valutazione dei parametri FSRS.
    Questo può risultare utile nel caso in cui si fossero importati i dati di pianificazione di un'altra persona o qualora siano cambiate le proprie abitudini nell'utilizzo dei pulsanti di risposta.
deck-config-compute-optimal-retention-tooltip =
    Questo strumento presuppone che si inizi con 0 carte e cercherà di calcolare la quantità di materiale 
    memorizzabile nel periodo di tempo specificato. 
    La ritenzione stimata dipenderà notevolmente dai dati di input, e se differisce significativamente da 0,9, 
    è un segno che il tempo allocato ogni giorno allo studio è troppo oppure troppo poco rispetto
    alla quantità di carte che si sta cercando di imparare. 
    Questo numero può essere utile come riferimento, ma non è consigliato copiarlo nel campo
    Ritenzione desiderata.
deck-config-health-check-tooltip1 = Questo mostrerà un avviso se FSRS fatica ad adattarsi alla tua memoria.
deck-config-health-check-tooltip2 = La verifica dell'integrità viene eseguita solo quando si utilizza "Ottimizza".
deck-config-compute-optimal-retention = Calcola ritenzione ottimale
deck-config-predicted-optimal-retention = Ritenzione ottimale stimata: { $num }
deck-config-weights-tooltip =
    I parametri FSRS influiscono sulla pianificazione delle carte. Alla prima attivazione di FSRS,
    verranno utilizzati i parametri predefiniti e, una volta accumulate 1000 o più ripetizioni,
    sarà possibile utilizzare l'opzione sottostante per ottimizzare i parametri in base alle prestazioni dei mazzi
    che utilizzano questa preimpostazione.
deck-config-compute-optimal-weights-tooltip =
    Dopo aver completato 1000 o più ripetizioni in Anki, è possibile usare il pulsante Ottimizza per analizzare lo storico delle ripetizioni
    e generare automaticamente parametri ottimali per la propria memoria e i contenuti che si stanno studiando. 
    Se sono presenti mazzi che variano notevolmente in difficoltà, è consigliabile assegnare loro preimpostazioni separate, poiché i parametri per i mazzi facili e quelli difficili saranno diversi. 
    Non è necessario ottimizzare i parametri frequentemente; è sufficiente farlo una volta ogni pochi mesi.
    
    Di default, i parametri saranno calcolati in base allo storico delle ripetizioni di tutti i mazzi che utilizzano la preimpostazione attuale. Facoltativamente, prima di calcolare i parametri, è possibile modificare i criteri di ricerca così da personalizzare la scelta delle carte da usare per l'ottimizzazione.
deck-config-compute-optimal-retention-tooltip2 =
    Questo strumento presuppone che si inizi con 0 carte apprese e cerca di calcolare il valore di ritenzione desiderata
    che consente di imparare la maggior parte del materiale nel minor tempo possibile. Il numero risultante
    può essere usato come riferimento per l'impostazione della propria ritenzione desiderata. Qualora si sia disposti
    a dedicare più tempo allo studio per ottenere un tasso di rammentabilità maggiore, è possibile scegliere
    un valore di ritenzione desiderata più alto. D'altra parte, è sconsigliato impostare la ritenzione desiderata al di sotto
    del livello ottimale, in quanto non vi sarebbe alcun beneficio apprezzabile a fronte di una mole di lavoro più elevata.
deck-config-compute-optimal-retention-tooltip3 =
    Questo strumento presuppone che si inizi con 0 carte apprese e cerca di calcolare il valore di ritenzione desiderata
    che consente di imparare la maggior parte del materiale nel minor tempo possibile. Per simulare accuratamente
    il processo di apprendimento, questa funzionalità richiede un minimo di 400 ripetizioni. Il numero calcolato
    può essere usato come riferimento per l'impostazione della propria ritenzione desiderata. Qualora si sia disposti
    a dedicare più tempo allo studio per ottenere un tasso di rammentabilità maggiore, è possibile scegliere
    un valore di ritenzione desiderata più alto. D'altra parte, è sconsigliato impostare la ritenzione desiderata al di sotto
    del livello ottimale, in quanto non vi sarebbe alcun beneficio apprezzabile a fronte di una mole di lavoro più elevata,
    causata da un tasso elevato di oblio.
deck-config-seconds-to-show-question-tooltip-2 = Determina il numero di secondi da attendere prima di rivelare la risposta, quando l'avanzamento automatico è abilitato. Imposta a 0 per disabilitare.
deck-config-invalid-weights = I parametri devono essere lasciati vuoti per utilizzare i valori predefiniti, oppure devono essere 17 numeri separati da virgole.
deck-config-fsrs-on-all-clients =
    Assicurarsi che tutte le versioni di Anki siano Anki(Mobile) 23.10+ o AnkiDroid 2.17+. 
    FSRS non funzionerà correttamente se una delle versioni utilizzate è più vecchia.
deck-config-optimize-all-tip = Ottimizza tutte le preimpostazioni contemporaneamente dal menù a tendina vicino a "Salva".
