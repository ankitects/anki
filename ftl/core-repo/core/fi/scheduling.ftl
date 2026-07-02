## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount } s
scheduling-answer-button-time-minutes = { $amount } min
scheduling-answer-button-time-hours = { $amount } h
scheduling-answer-button-time-days = { $amount } vrk
scheduling-answer-button-time-months = { $amount } kk
scheduling-answer-button-time-years = { $amount } v

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds =
    { $amount ->
        [one] { $amount } sekunti
       *[other] { $amount } sekuntia
    }
scheduling-time-span-minutes =
    { $amount ->
        [one] { $amount } minuutti
       *[other] { $amount } minuuttia
    }
scheduling-time-span-hours =
    { $amount ->
        [one] { $amount } tunti
       *[other] { $amount } tuntia
    }
scheduling-time-span-days =
    { $amount ->
        [one] { $amount } päivä
       *[other] { $amount } päivää
    }
scheduling-time-span-months =
    { $amount ->
        [one] { $amount } kuukausi
       *[other] { $amount } kuukautta
    }
scheduling-time-span-years =
    { $amount ->
        [one] { $amount } vuosi
       *[other] { $amount } vuotta
    }

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    Seuraava opittava kortti on valmis { $unit ->
        [seconds]
            { $amount ->
                [one] { $amount } sekunnin
               *[other] { $amount } sekunnin
            }
        [minutes]
            { $amount ->
                [one] { $amount } minuutin
               *[other] { $amount } minuutin
            }
       *[hours]
            { $amount ->
                [one] { $amount } tunnin
               *[other] { $amount } tunnin
            }
    } kuluttua.
scheduling-learn-remaining =
    { $remaining ->
        [one] Jäljellä on vielä yksi kortti, joka erääntyy myöhemmin tänään.
       *[other] Jäljellä on vielä { $remaining } korttia, jotka erääntyvät myöhemmin tänään.
    }
scheduling-congratulations-finished = Onneksi olkoon! Olet käynyt tämän pakan kertaukset läpi toistaiseksi.
scheduling-today-review-limit-reached = Tämän päivän kertausyläraja on tullut vastaan, mutta jonossa on vielä kerrattavia kortteja. Harkitse päivittäisen ylärajan nostamista valinnoissa muistamisen optimoimiseksi.
scheduling-today-new-limit-reached = Pakassa on vielä uusia kortteja, mutta päivittäinen yläraja on tullut vastaan. Voit kasvattaa ylärajaa valinnoissa, mutta pidä mielessä että mitä enemmän uusia kortteja alat opiskella sitä suuremmaksi lyhyen aikavälin kertauskuormasi tulee.
scheduling-buried-cards-found = Yksi tai useampi kortti piilotettiin, ja ne näytetään huomenna. Voit { $unburyThem }, jos haluat nähdä ne heti.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = poistaa niiden piilotuksen
scheduling-how-to-custom-study = Jos haluat opiskella normaalin aikataulun ulkopuolella, voit käyttää { $customStudy } -toimintoa.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = mukautettu opiskelu

## Scheduler upgrade

scheduling-update-soon = Anki 2.1:n mukana tulee uusi aikataulutusohjelma, joka korjaa useita ongelmia, joita aiemmissa Anki-versioissa oli. Siihen päivittäminen on suositeltavaa.
scheduling-update-done = Aikataulutusohjelman päivitys onnistui.
scheduling-update-button = Päivitä
scheduling-update-later-button = Myöhemmin
scheduling-update-more-info-button = Lisätietoja
scheduling-update-required =
    Kokoelmasi on päivitettävä käyttämään V2-aikataulutusohjelmaa.
    Valitse { scheduling-update-more-info-button } ennen kuin jatkat.

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = Sisällytä aina kysymyspuoli, kun äänitettä toistetaan
scheduling-at-least-one-step-is-required = Vaaditaan vähintään yksi vaihe.
scheduling-automatically-play-audio = Toista äänitiedosto automaattisesti
scheduling-bury-related-new-cards-until-the = Piilota tähän liittyvät uudet kortit seuraavaan päivään saakka
scheduling-bury-related-reviews-until-the-next = Piilota tähän liittyvät kertaukset seuraavaan päivään saakka
scheduling-days = päiv.
scheduling-description = Kuvaus
scheduling-easy-bonus = Vastauksesta "Helppo" saatava bonus
scheduling-easy-interval = Helpon kortin kertausväli
scheduling-end = (loppu)
scheduling-general = Yleistä
scheduling-graduating-interval = Valmistumisen jälkeinen kertausväli
scheduling-hard-interval = Vaikea kertausväli
scheduling-ignore-answer-times-longer-than = Älä huomioi pidempiä vastausaikoja kuin
scheduling-interval-modifier = Kertausvälimuokkaaja
scheduling-lapses = Virheet
scheduling-lapses2 = virheet
scheduling-learning = Opittavat
scheduling-leech-action = Resurssisyöpön toimenpide
scheduling-leech-threshold = Resurssisyöpön alaraja
scheduling-maximum-interval = Enimmäiskertausväli
scheduling-maximum-reviewsday = Kertausten enimmäismäärä/päivä
scheduling-minimum-interval = Vähimmäiskertausväli
scheduling-mix-new-cards-and-reviews = Sekoita uudet kortit ja kertaukset
scheduling-new-cards = Uudet kortit
scheduling-new-cardsday = Uusia kortteja/päivä
scheduling-new-interval = Uusi kertausväli
scheduling-new-options-group-name = Uusi valintaryhmän nimi:
scheduling-options-group = Valintaryhmä:
scheduling-order = Järjestys
scheduling-parent-limit = (emorajoitus: { $val })
scheduling-reset-counts = Nollaa kertausten ja virheiden laskurit
scheduling-restore-position = Palauta alkuperäinen sijainti, jos mahdollista
scheduling-review = Kertaus
scheduling-reviews = Kertaukset
scheduling-seconds = sekuntia
scheduling-set-all-decks-below-to = Asetetaanko kaikki { $val } alapuoliset pakat tähän valintaryhmään?
scheduling-set-for-all-subdecks = Aseta kaikille alipakoille
scheduling-show-answer-timer = Näytä vastausaika
scheduling-show-new-cards-after-reviews = Näytä uudet kortit kertausten jälkeen
scheduling-show-new-cards-before-reviews = Näytä uudet kortit ennen kertauksia
scheduling-show-new-cards-in-order-added = Näytä uudet kortit lisäysjärjestyksessä
scheduling-show-new-cards-in-random-order = Näytä uudet kortit satunnaisessa järjestyksessä
scheduling-starting-ease = Aloitushelppous
scheduling-steps-in-minutes = Vaiheet (minuuteissa)
scheduling-steps-must-be-numbers = Vaiheiden täytyy olla numeroita.
scheduling-tag-only = Liitä vain tunniste
scheduling-the-default-configuration-cant-be-removed = Oletusasetuksia ei voi poistaa.
scheduling-your-changes-will-affect-multiple-decks = Muutoksesi vaikuttavat useisiin pakkoihin. Jos haluat muuttaa vain nykyistä pakkaa, lisää ensin uusi valintaryhmä.
scheduling-deck-updated =
    { $count ->
        [one] { $count } pakka päivitetty.
       *[other] { $count } pakkaa päivitetty.
    }
scheduling-set-due-date-prompt =
    { $cards ->
        [one] Kuinka monen päivän kuluttua kortti näytetään?
       *[other] Kuinka monen päivän kuluttua kortti näytetään?
    }
scheduling-set-due-date-prompt-hint =
    0 = tänään
    1! = huomenna + nollaa kertausväli
    3–7 = valitaan satunnaisesti 3–7 päivän väliltä
scheduling-set-due-date-done =
    { $cards ->
        [one] Aseta { $cards } kortin eräpäivä.
       *[other] Aseta { $cards } kortin eräpäivä.
    }
scheduling-graded-cards-done =
    { $cards ->
        [one] { $cards } kortti arvioitu.
       *[other] { $cards } korttia arvioitu.
    }
scheduling-forgot-cards =
    { $cards ->
        [one] Unohda { $cards } kortti.
       *[other] Unohda { $cards } korttia.
    }
