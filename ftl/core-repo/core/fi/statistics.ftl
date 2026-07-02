# The date a card will be ready to review
statistics-due-date = Erääntyy
# The count of cards waiting to be reviewed
statistics-due-count = Erääntyneet
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card = Uudet nro { $number }

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } korttia/minuutissa
statistics-average-answer-time = { $average-seconds } s ({ statistics-cards-per-min })

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds =
    { $amount ->
        [one] { $amount } sekunnissa
       *[other] { $amount } sekunnissa
    }
statistics-in-time-span-minutes =
    { $amount ->
        [one] { $amount } minuutissa
       *[other] { $amount } minuutissa
    }
statistics-in-time-span-hours =
    { $amount ->
        [one] { $amount } tunnissa
       *[other] { $amount } tunnissa
    }
statistics-in-time-span-days =
    { $amount ->
        [one] { $amount } päivässä
       *[other] { $amount } päivässä
    }
statistics-in-time-span-months =
    { $amount ->
        [one] { $amount } kuukaudessa
       *[other] { $amount } kuukaudessa
    }
statistics-in-time-span-years =
    { $amount ->
        [one] { $amount } vuodessa
       *[other] { $amount } vuodessa
    }
# Shown at the bottom of the deck list, and in the statistics screen.
# eg "Studied 3 cards in 13 seconds today (4.33s/card)."
# The { statistics-in-time-span-seconds } part should be pasted in from the English
# version unmodified.
statistics-studied-today =
    Tänään opiskeltiin { statistics-cards }
    { $unit ->
        [seconds] { statistics-in-time-span-seconds }
        [minutes] { statistics-in-time-span-minutes }
        [hours] { statistics-in-time-span-hours }
        [days] { statistics-in-time-span-days }
        [months] { statistics-in-time-span-months }
       *[years] { statistics-in-time-span-years }
    } ({ $secs-per-card } s/kortti)

##

statistics-cards =
    { $cards ->
        [one] { $cards } kortti
       *[other] { $cards } korttia
    }
statistics-notes =
    { $notes ->
        [one] { $notes } muistiinpano
       *[other] { $notes } muistiinpanoa
    }
# a count of how many cards have been answered, eg "Total: 34 reviews"
statistics-reviews =
    { $reviews ->
        [one] { $reviews } kertaus
       *[other] { $reviews } kertausta
    }
# This fragment of the tooltip in the FSRS simulation
# diagram (Deck options -> FSRS) shows the total number of
# cards that can be recalled or retrieved on a specific date.
statistics-memorized = { $memorized } opittu ulkoa
statistics-today-title = Tänään
statistics-today-again-count = Uudelleen näyttettäväksi pyydettyjen korttien lukumäärä:
statistics-today-type-counts = Opittavat: { $learnCount }, Kerrattavat: { $reviewCount }, Uudelleen opittavat: { $relearnCount }, Suodatetut: { $filteredCount }
statistics-today-no-cards = Tänään ei ole opiskeltu yhtään korttia.
statistics-today-no-mature-cards = Tänään ei opiskeltu yhtään varmaa korttia.
statistics-today-correct-mature = Varmojen korttien oikeat vastaukset: { $correct }/{ $total } ({ $percent }%)
statistics-counts-total-cards = Kortteja yhteensä
statistics-counts-new-cards = Uudet
statistics-counts-young-cards = Nuoret
statistics-counts-mature-cards = Varmat
statistics-counts-suspended-cards = Hyllytetyt
statistics-counts-buried-cards = Haudatut
statistics-counts-filtered-cards = Suodatetut
statistics-counts-learning-cards = Opittavat
statistics-counts-relearning-cards = Uudelleen opittavat
statistics-counts-title = Korttien lukumäärät
statistics-counts-separate-suspended-buried-cards = Erota hyllytetyt ja haudatut kortit

## True Retention represents your actual retention rate from past reviews, in
## comparison to the "desired retention" parameter of FSRS, which forecasts
## future retention. True Retention is the percentage of all reviewed cards
## that were marked as "Hard," "Good," or "Easy" within a specific time period.
##
## Most of these strings are used as column / row headings in a table.
## (Excluding -title and -subtitle)
## It is important to keep these translations short so that they do not make
## the table too large to display on a single stats card.
##
## N.B. Stats cards may be very small on mobile devices and when the Stats
##      window is certain sizes.

statistics-true-retention-title = Todellinen retentio
statistics-true-retention-subtitle = Läpäisyprosentti korteille, joiden kertausväli on ≥ 1 päivä.
statistics-true-retention-tooltip = Jos käytät FSRS:ää, todellisen retention odotetaan olevan lähellä toivottua retentiota. Muista, että yhden päivän tiedoissa on paljon vaihtelua, joten on parempi tarkastella kuukausittaisia tietoja.
statistics-true-retention-range = Aikaväli
statistics-true-retention-pass = Oikein
statistics-true-retention-fail = Väärin
# This will usually be the same as statistics-counts-total-cards
statistics-true-retention-total = Kortteja yhteensä
statistics-true-retention-count = Määrä
statistics-true-retention-retention = Retentio
# This will usually be the same as statistics-counts-young-cards
statistics-true-retention-young = Nuoret
# This will usually be the same as statistics-counts-mature-cards
statistics-true-retention-mature = Varmat
statistics-true-retention-all = Kaikki
statistics-true-retention-today = Tänään
statistics-true-retention-yesterday = Eilen
statistics-true-retention-week = Viime viikko
statistics-true-retention-month = Viime kuukausi
statistics-true-retention-year = Viime vuosi
statistics-true-retention-all-time = Alusta alkaen
# If there are no reviews within a specific time period, the retention
# percentage cannot be calculated and is displayed as "N/A."
statistics-true-retention-not-applicable = Ei laskettavissa

##

statistics-range-all-time = pakan elinkaari
statistics-range-1-year-history = viimeiset 12 kuukautta
statistics-range-all-history = koko historia
statistics-range-deck = pakka
statistics-range-collection = kokoelma
statistics-range-search = Etsi
statistics-card-ease-title = Korttien helppous
statistics-card-difficulty-title = Kortin vaikeus
statistics-card-stability-title = Korttien vakaus
statistics-card-stability-subtitle = Ennustettu viive, jonka kuluttua muistat asian 90 % todennäköisyydellä.
statistics-median-stability = Mediaanivakaus
statistics-card-retrievability-title = Kortin palautettavuus
statistics-card-ease-subtitle = Mitä pienempi helppous, sitä useammin kortti ilmestyy kerrattavaksi.
statistics-card-difficulty-subtitle2 = Mitä vaikeampi kortti, sitä hitaammin vakaus kasvaa.
statistics-retrievability-subtitle = Ilmaisee kuinka hyvin pystyt palauttamaan muiston mieleesi.
# eg "3 cards with 150-170% ease"
statistics-card-ease-tooltip =
    { $cards ->
        [one] { $cards } kortti { $percent } helppoudella
       *[other] { $cards } korttia { $percent } helppoudella
    }
statistics-card-difficulty-tooltip =
    { $cards ->
        [one] { $cards } kortti , jonka vaikeus on { $percent }
       *[other] { $cards } korttia, joiden vaikeus on { $percent }
    }
statistics-retrievability-tooltip =
    { $cards ->
        [one] { $cards } kortti, jonka palautettavuus on { $percent }
       *[other] { $cards } korttia, joiden palautettavuus on { $percent }
    }
statistics-future-due-title = Ennuste
statistics-future-due-subtitle = Tulevaisuudessa erääntyvien kertausten määrä.
statistics-added-title = Lisätty
statistics-added-subtitle = Uusien lisäämiesi korttien lukumäärä.
statistics-reviews-count-subtitle = Vastaamiesi kysymysten määrä.
statistics-reviews-time-subtitle = Kysymyksiin vastaamiseen käytetty aika.
statistics-answer-buttons-title = Vastauspainikkeet
# eg Button: 4
statistics-answer-buttons-button-number = Painike
# eg Times pressed: 123
statistics-answer-buttons-button-pressed = Painalluskerrat
statistics-answer-buttons-subtitle = Kunkin painikkeen painalluskertojen määrä.
statistics-reviews-title = Kertaukset
statistics-reviews-time-checkbox = Aika
statistics-in-days-single =
    { $days ->
        [0] Tänään
        [1] Huomenna
        [one] { $days } päivän kuluttua
       *[other] { $days } päivän kuluttua
    }
statistics-in-days-range = { $daysStart }–{ $daysEnd } päivän kuluttua
statistics-days-ago-single =
    { $days ->
        [1] Eilen
        [one] { $days } päivä sitten
       *[other] { $days } päivää sitten
    }
statistics-days-ago-range = { $daysStart }–{ $daysEnd } päivää sitten
statistics-running-total = Juokseva summa
statistics-cards-due =
    { $cards ->
        [one] { $cards } kortti erääntyy
       *[other] { $cards } korttia erääntyy
    }
statistics-backlog-checkbox = Rästiin jääneet
statistics-intervals-title = Kertausvälit
statistics-intervals-subtitle = Viivästykset, joiden jälkeen kerrattavat kortit näytetään uudestaan.
statistics-intervals-day-range =
    { $cards ->
        [one] { $cards } kortti { $daysStart }–{ $daysEnd } päivän viivästyksellä
       *[other] { $cards } korttia { $daysStart }–{ $daysEnd } päivän viivästyksellä
    }
statistics-intervals-day-single =
    { $cards ->
        [one] { $cards } kortti { $daysStart }–{ $daysEnd } päivän viivästyksellä
       *[other] { $cards } korttia { $daysStart }–{ $daysEnd } päivän viivästyksellä
    }
statistics-stability-day-range =
    { $cards ->
        [one] { $cards } kortti { $daysStart }~{ $daysEnd } päivän vakaudella
       *[other] { $cards } korttia { $daysStart }~{ $daysEnd } päivän vakaudella
    }
statistics-stability-day-single =
    { $cards ->
        [one] { $cards } kortti { $day } päivän vakaudella
       *[other] { $cards } korttia { $day } päivän vakaudella
    }
# hour range, eg "From 14:00-15:00"
statistics-hours-range = { $hourStart }.00–{ $hourEnd }.00
statistics-hours-correct = { $correct }/{ $total } oikein ({ $percent } %)
statistics-hours-correct-info = → (ei 'Uudelleen')
# the emoji depicts the graph displaying this number
statistics-hours-reviews = 📊 { $reviews } kertausta
# the emoji depicts the graph displaying this number
statistics-hours-correct-reviews = 📈 { $percent } % oikein ({ $reviews })
statistics-hours-title = Tuntijakauma
statistics-hours-subtitle = Kertausten onnistumisaste tunneittain.
# shown when graph is empty
statistics-no-data = EI TIETOJA
statistics-calendar-title = Kalenteri

## An amount of elapsed time, used in the graphs to show the amount of
## time spent studying. For example, English would show "5s" for 5 seconds,
## "13.5m" for 13.5 minutes, and so on.
##
## Please try to keep the text short, as longer text may get cut off.

statistics-elapsed-time-seconds = { $amount } s
statistics-elapsed-time-minutes = { $amount } min
statistics-elapsed-time-hours = { $amount } t
statistics-elapsed-time-days = { $amount } vrk
statistics-elapsed-time-months = { $amount } kk
statistics-elapsed-time-years = { $amount } v

##

statistics-average-for-days-studied = Opiskelupäivien keskiarvo
# This term is used in a variety of contexts to refers to the total amount of
# items (e.g., cards, mature cards, etc) for a given period, rather than the
# total of all existing items.
statistics-total = Yhteensä
statistics-days-studied = Opiskelupäivät
statistics-average-answer-time-label = Keskimääräinen vastausaika
statistics-average = Keskiarvo
statistics-median-interval = Mediaanikertausväli
statistics-due-tomorrow = Erääntyy huomenna
# This string, ‘Daily load,’ appears in the ‘Future due’ table and represents a
# forecasted estimate of the number of cards expected to be reviewed daily in 
# the future. Unlike the other strings in the table that display actual data 
# derived from the current scheduling (e.g., ‘Average’, ‘Due tomorrow’),
# ‘Daily load’ is a projection based on the given data.
statistics-daily-load = Päivittäinen työmäärä
# eg 5 of 15 (33.3%)
statistics-amount-of-total-with-percentage = { $amount }/{ $total } ({ $percent } %)
statistics-average-over-period = Jos olisit opiskellut joka päivä
statistics-reviews-per-day =
    { $count ->
        [one] { $count } kertaus/päivä
       *[other] { $count } kertausta/päivä
    }
statistics-minutes-per-day =
    { $count ->
        [one] { $count } minuutti/päivä
       *[other] { $count } minuuttia/päivä
    }
statistics-cards-per-day =
    { $count ->
        [one] { $count } kortti/päivä
       *[other] { $count } korttia/päivä
    }
statistics-median-ease = Mediaanihelppous
statistics-median-difficulty = Mediaanivaikeus
statistics-average-retrievability = Keskimääräinen palautettavuus
statistics-estimated-total-knowledge = Arvioitu kokonaisosaaminen
statistics-save-pdf = Tallenna PDF
statistics-saved = Tallennettu.
statistics-stats = tilastot
statistics-title = Tilastot

## These strings are no longer used - you do not need to translate them if they
## are not already translated.

statistics-average-stability = Keskimääräinen vakaus
statistics-average-interval = Keskimääräinen kertausväli
statistics-average-ease = Keskimääräinen helppous
statistics-average-difficulty = Keskimääräinen vaikeus
