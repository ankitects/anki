## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount }s
scheduling-answer-button-time-minutes = { $amount }min
scheduling-answer-button-time-hours = { $amount }h
scheduling-answer-button-time-days = { $amount }g
scheduling-answer-button-time-months = { $amount }me
scheduling-answer-button-time-years = { $amount }a

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds =
    { $amount ->
        [one] { $amount } secondo
       *[other] { $amount } secondi
    }
scheduling-time-span-minutes =
    { $amount ->
        [one] { $amount } minuto
       *[other] { $amount } minuti
    }
scheduling-time-span-hours =
    { $amount ->
        [one] { $amount } ora
       *[other] { $amount } ore
    }
scheduling-time-span-days =
    { $amount ->
        [one] { $amount } giorno
       *[other] { $amount } giorni
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } mese
       *[other] { $amount } mesi
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } anno
       *[other] { $amount } anni
    }

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    La prossima carta da studiare sarà pronta in { $unit ->
        [seconds]
            { $amount ->
                [one] { $amount } secondo
               *[other] { $amount } secondi
            }
        [minutes]
            { $amount ->
                [one] { $amount } minuto
               *[other] { $amount } minuti
            }
       *[hours]
            { $amount ->
                [one] { $amount } ora
               *[other] { $amount } ore
            }
    }.
scheduling-learn-remaining =
    { $remaining ->
        [one] Resta una carta da studiare più tardi in giornata.
       *[other] Ci sono { $remaining } carte da studiare più tardi in giornata.
    }
scheduling-congratulations-finished = Congratulazioni! Hai completato questo mazzo per adesso.
scheduling-today-review-limit-reached =
    Il limite delle ripetizioni per oggi è stato raggiunto, ma ci sono ancora carte che
    aspettano di essere ripetute. Per una memorizzazione ottimale, considera
    di aumentare il limite giornaliero nelle opzioni.
scheduling-today-new-limit-reached =
    Sono disponibili ulteriori carte nuove, ma il limite giornaliero è stato raggiunto.
    È possibile aumentare il limite nelle opzioni, ma è bene ricordare
    che l'introduzione di un numero maggiore di carte nuove comporta un aumento
    del proprio carico di lavoro per le ripetizioni a breve termine.
scheduling-buried-cards-found = Una o più carte sono state seppellite e verranno mostrate domani. Puoi { $unburyThem } se desideri vederle immediatamente.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = disseppellirle
scheduling-how-to-custom-study = Se desideri studiare al di fuori dalla pianificazione abituale, puoi utilizzare la funzionalità { $customStudy }.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = studio personalizzato

## Scheduler upgrade

scheduling-update-soon = Anki 2.1 è dotato di un nuovo pianificatore che risolve diversi problemi presenti nelle versioni precedenti di Anki. È consigliato aggiornare Anki a questa versione.
scheduling-update-done = Pianificatore aggiornato con successo.
scheduling-update-button = Aggiorna
scheduling-update-later-button = Più tardi
scheduling-update-more-info-button = Scopri di più
scheduling-update-required =
    La tua collezione necessita l'aggiornamento al pianificatore V2.
    Selezionare { scheduling-update-more-info-button } prima di procedere.

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = Includi sempre il lato con la domanda quando si riproduce nuovamente l'audio
scheduling-at-least-one-step-is-required = È richiesto almeno un passo.
scheduling-automatically-play-audio = Riproduci automaticamente l'audio
scheduling-bury-related-new-cards-until-the = Seppellisci le carte nuove correlate fino al giorno seguente
scheduling-bury-related-reviews-until-the-next = Seppellisci le ripetizioni correlate fino al giorno seguente
scheduling-days = giorni
scheduling-description = Descrizione
scheduling-easy-bonus = Bonus facile
scheduling-easy-interval = Intervallo facile
scheduling-end = (fine)
scheduling-general = Generale
scheduling-graduating-interval = Intervallo di promozione
scheduling-hard-interval = Intervallo difficile
scheduling-ignore-answer-times-longer-than = Ignora i tempi di risposta più lunghi di
scheduling-interval-modifier = Modificatore intervallo
scheduling-lapses = Errori
scheduling-lapses2 = errori
scheduling-learning = In apprendimento
scheduling-leech-action = Azione sanguisughe
scheduling-leech-threshold = Soglia sanguisughe
scheduling-maximum-interval = Intervallo massimo
scheduling-maximum-reviewsday = Ripetizioni/giorno massime
scheduling-minimum-interval = Intervallo minimo
scheduling-mix-new-cards-and-reviews = Mescola le carte nuove e le ripetizioni
scheduling-new-cards = Carte nuove
scheduling-new-cardsday = Carte nuove/giorno
scheduling-new-interval = Nuovo intervallo
scheduling-new-options-group-name = Nome del nuovo gruppo di opzioni:
scheduling-options-group = Gruppo di opzioni:
scheduling-order = Ordine
scheduling-parent-limit = (limite mazzo superiore: { $val })
scheduling-reset-counts = Azzera conteggio di ripetizioni ed errori
scheduling-restore-position = Ripristina posizione originale ove possibile
scheduling-review = Ripetizione
scheduling-reviews = Ripetizioni
scheduling-seconds = secondi
scheduling-set-all-decks-below-to = Assegnare questo gruppo di opzioni a tutti i mazzi sotto { $val }?
scheduling-set-for-all-subdecks = Imposta per tutti i mazzi figli
scheduling-show-answer-timer = Mostra il timer
scheduling-show-new-cards-after-reviews = Mostra le carte nuove dopo le ripetizioni
scheduling-show-new-cards-before-reviews = Mostra le carte nuove prima delle ripetizioni
scheduling-show-new-cards-in-order-added = Mostra le carte nuove in ordine di aggiunta
scheduling-show-new-cards-in-random-order = Mostra le carte nuove in ordine casuale
scheduling-starting-ease = Facilità iniziale
scheduling-steps-in-minutes = Passi (in minuti)
scheduling-steps-must-be-numbers = I passi devono essere numeri.
scheduling-tag-only = Etichetta soltanto
scheduling-the-default-configuration-cant-be-removed = La configurazione predefinita non può essere rimossa.
scheduling-your-changes-will-affect-multiple-decks = I cambiamenti avranno effetto su più mazzi. Per limitare le modifiche al mazzo attuale, aggiungere dapprima un nuovo gruppo di opzioni.
scheduling-deck-updated =
    { $count ->
        [one] { $count } mazzo aggiornato.
       *[other] { $count } mazzi aggiornati.
    }
scheduling-set-due-date-prompt =
    { $cards ->
        [one] Tra quanti giorni mostrare la carta?
       *[other] Tra quanti giorni mostrare le carte?
    }
scheduling-set-due-date-prompt-hint =
    0 = oggi
    1! = domani + cambia l'intervallo di ripasso a 1
    3-7 = scelta casuale da 3 a 7 giorni
scheduling-set-due-date-done =
    { $cards ->
        [one] Imposta la data di scadenza di { $cards } carta.
       *[other] Imposta la data di scadenza di { $cards } carte.
    }
scheduling-graded-cards-done =
    { $cards ->
        [one] Valutata { $cards } carta.
       *[other] Valutate { $cards } carte.
    }
scheduling-forgot-cards =
    { $cards ->
        [one] Dimenticata { $cards } carta.
       *[other] Dimenticate { $cards } carte.
    }
