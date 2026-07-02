scheduling-time-span-seconds =
    { $amount ->
        [one] { $amount } seconde
       *[other] { $amount } seconden
    }
scheduling-time-span-minutes =
    { $amount ->
        [one] { $amount } minuut
       *[other] { $amount } minuten
    }
scheduling-time-span-hours =
    { $amount ->
        [one] { $amount } uur
       *[other] { $amount } uur
    }
scheduling-time-span-days =
    { $amount ->
        [one] { $amount } dag
       *[other] { $amount } dagen
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } maand
       *[other] { $amount } maanden
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } jaar
       *[other] { $amount } jaar
    }
scheduling-congratulations-finished = Proficiat! U bent voorlopig klaar met deze set.
scheduling-today-review-limit-reached =
    Het maximum te herhalen kaarten is voor vandaag bereikt, maar er
    zijn nog steeds kaarten die voor vandaag gepland waren. Overweeg deze
    dagelijkse limiet in de instellingen te verhogen, om optimaal te leren.
scheduling-today-new-limit-reached =
    Er zijn meer nieuwe kaarten beschikbaar, maar de dagelijkse limiet
    is bereikt. U kunt de limiet in de instellingen verhogen, maar let er op
    dat dit uw werkdruk op de korte termijn zal verzwaren.
scheduling-buried-cards-were-delayed = Enkele gerelateerde of begraven kaarten zijn uitgesteld tot een latere sessie.
scheduling-always-include-question-side-when-replaying = Neem altijd de vraagzijde op bij het afspelen van audio
scheduling-at-least-one-step-is-required = Minstens één leerstap is vereist.
scheduling-automatically-play-audio = Geluid automatisch afspelen
scheduling-bury-related-new-cards-until-the = Begraaf gerelateerde nieuwe kaarten tot het eind van de dag
scheduling-bury-related-reviews-until-the-next = Begraaf gerelateerde herhalingen tot de volgende dag
scheduling-days = dagen
scheduling-description = Beschrijving
scheduling-easy-bonus = Bonus voor makkelijke kaarten
scheduling-easy-interval = Interval voor makkelijke kaarten
scheduling-end = (einde)
scheduling-general = Algemeen
scheduling-graduating-interval = Interval na leren
scheduling-hard-interval = Interval voor moeilijke kaarten
scheduling-ignore-answer-times-longer-than = Negeer antwoordtijden langer dan
scheduling-interval-modifier = Intervalsfactor
scheduling-lapses = Vergissingen
scheduling-lapses2 = vergissingen
scheduling-learning = Aan het leren
scheduling-leech-action = Actie moeilijke kaarten
scheduling-leech-threshold = Drempelwaarde moeilijke kaart
scheduling-maximum-interval = Maximuminterval
scheduling-maximum-reviewsday = Maximum herhalingen/dag
scheduling-minimum-interval = Minimuminterval
scheduling-mix-new-cards-and-reviews = Meng nieuwe kaarten en herhalingen
scheduling-new-cards = Nieuwe kaarten
scheduling-new-cardsday = Nieuwe kaarten/dag
scheduling-new-interval = Interval nieuwe kaarten
scheduling-new-options-group-name = Nieuwe naam voor deze optiegroep:
scheduling-options-group = Optiegroep:
scheduling-order = Volgorde
scheduling-parent-limit = (limiet van bovenliggend niveau: { $val })
scheduling-review = Herhalen
scheduling-reviews = Herhalingen
scheduling-seconds = seconden
scheduling-set-all-decks-below-to = Deze optiegroep op alle sets onder { $val } toepassen?
scheduling-set-for-all-subdecks = Op alle subsets toepassen
scheduling-show-answer-timer = Antwoordtimer tonen
scheduling-show-new-cards-after-reviews = Nieuwe kaarten na herhalingen tonen
scheduling-show-new-cards-before-reviews = Nieuwe kaarten vóór herhalingen tonen
scheduling-show-new-cards-in-order-added = Nieuwe kaarten in volgorde van toevoegen tonen
scheduling-show-new-cards-in-random-order = Nieuwe kaarten in willekeurige volgorde tonen
scheduling-starting-ease = Beginwaarde gemak
scheduling-steps-in-minutes = Leerstappen (in minuten)
scheduling-steps-must-be-numbers = Leerstappen moeten in minuten worden ingevoerd.
scheduling-tag-only = Alleen taggen
scheduling-the-default-configuration-cant-be-removed = De standaardconfiguratie kan niet verwijderd worden.
scheduling-your-changes-will-affect-multiple-decks = Uw aanpassingen zullen een effect hebben op verschillende sets. Als u enkel deze set wenst te veranderen, gelieve eerst een nieuwe optiegroep aan te maken.
scheduling-deck-updated = { $count ->
    [one] { $count } set bijgewerkt.
   *[other] { $count } sets bijgewerkt.
  }
