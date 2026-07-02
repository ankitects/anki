### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    käytössä { $decks ->
        [one] { $decks } pakassa
       *[other] { $decks } pakassa
    }
deck-config-default-name = Oletusarvo
deck-config-title = Pakan valinnat

## Daily limits section

deck-config-daily-limits = Päivittäiset rajat
deck-config-new-limit-tooltip =
    Enintään näin monta uutta korttia esitellään päivän aikana, jos uusia kortteja on saatavilla.
    Koska uusi materiaali lisää kertauksen työmäärää lyhyellä aikavälillä, tämän on hyvä olla enintään kymmenesosa päivittäin kerrattavien korttien enimmäismäärästä.
deck-config-review-limit-tooltip =
    Enintään näin monta kerrattavaa korttia näytetään päivän aikana,
    jos kortteja on valmiina kerrattavaksi.
deck-config-limit-deck-v3 =
    Kun opiskelet pakkaa, jonka sisällä on alipakkoja, kullekin alipakalle asetetut
    rajoitukset säätelevät kyseisestä pakasta nostettavien korttien enimmäismäärää.
    Valitun pakan rajat ohjaavat näytettävien korttien kokonaismäärää.
deck-config-limit-new-bound-by-reviews =
    Kertausten enimmäismäärä vaikuttaa esiteltävien uusien korttien määrään.
    Ajattele esimerkiksi tilannetta, jossa kerrattavien korttien päivittäiseksi enimmäismääräksi on asetettu 200,
    ja 190 korttia odottaa kertausta. Tällöin esiteltäisiin enintään 10 uutta korttia.
    Jos kertausten enimmäismäärä on saavutettu, uusia kortteja ei näytetä.
deck-config-limit-interday-bound-by-reviews =
    Kertausten enimmäismäärä vaikuttaa myös usean päivän aikana opittaviin kortteihin. Kun rajoitusta sovelletaan,
    usean päivän aikana opittavat kortit haetaan ensin, sitten kerrattavat kortit, ja lopuksi uudet kortit.
deck-config-tab-description =
    - `Esiasetus`: Raja jaetaan kaikille tätä esiasetusta käyttäville pakoille.
    - `Tämä pakka`: Raja koskee vain tätä pakkaa.
    - `Vain tänään`: Tee väliaikainen muutos tämän pakan rajaan.
deck-config-new-cards-ignore-review-limit = Uudet kortit eivät huomioi kertausten enimmäismäärää
deck-config-new-cards-ignore-review-limit-tooltip = Oletusarvoisesti kertausten enimmäismäärä koskee myös uusia kortteja, eikä uusia kortteja näytetä, kun raja on saavutettu. Jos tämä vaihtoehto on käytössä, uudet kortit näytetään kertausten enimmäismäärästä riippumatta.
deck-config-apply-all-parent-limits = Käytä ylimmän tason pakan rajoja
deck-config-apply-all-parent-limits-tooltip = Oletusarvoisesti rajat koskevat valitsemaasi pakkaa. Jos tämä vaihtoehto on käytössä, rajoitukset koskevat sen sijaan ylimmän tason pakkaa, mikä voi olla hyödyllistä, jos haluat tutkia yksittäisiä alipakkoja ja samalla asettaa korttien kokonaismäärälle päiväkohtaisen rajoituksen.
deck-config-affects-entire-collection = Vaikuttaa koko kokoelmaan.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Esiasetus
deck-config-deck-only = Tämä pakka
deck-config-today-only = Vain tänään

## New Cards section

deck-config-learning-steps = Oppimisaskeleet
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Viiveet ovat yleensä minuutteja (esim. `1m`) tai päiviä (esim. `2d`), mutta myös tunteja (esim. `1h`) ja sekunteja (esim. `30s`) voidaan käyttää.
deck-config-learning-steps-tooltip =
    Yksi tai useampi aikaväli, välilyönneillä eroteltuna. Ensimmäistä aikaväliä käytetään,
    kun painat `Uudestaan`-painiketta uutta korttia opiskeltaessa, ja se on oletuksena 1 minuutti.
    `Hyvä`-painike siirtää kortin käyttämään seuraavaa aikaväliä, joka on oletuksena 10 minuuttia.
    Kun korttiin vastataan oikein kaikkien aikavälien ("askeleiden") jälkeen, se siirtyy
    opittavasta kortista kerrattavaksi kortiksi, ja se näytetään toisena päivänä.
    
    { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip = Näin monta päivää on odotettava, ennen kuin kortti voidaan näyttää uudelleen sen jälkeen, kun `Hyvä`-painiketta on painettu viimeisessä oppimisaskeleessa.
deck-config-easy-interval-tooltip = Näin monta päivää on odotettava, ennen kuin kortti näytetään uudelleen sen jälkeen, kun kortti on poistettu opittavista kortteista `Helppo`-painikkeella.
deck-config-new-insertion-order = Lisäysjärjestys
deck-config-new-insertion-order-tooltip =
    Säätää paikkaa (erääntymisluku), johon lisäämäsi uudet kortit sijoitetaan.
    Opiskeltaessa näytetään ensin kortit, joiden erääntymisluku on pieni. Tämän
    asetuksen muuttaminen päivittää automaattisesti uusien korttien nykyisen sijainnin.
deck-config-new-insertion-order-sequential = Peräkkäinen (vanhimmat kortit ensin)
deck-config-new-insertion-order-random = Satunnainen
deck-config-new-insertion-order-random-with-v3 = Kun V3-aikataulutusohjelma on käytössä, on parempi valita "Peräkkäinen" ja muuttaa uusien korttien keräysjärjetystä tämän sijaan.

## Lapses section

deck-config-relearning-steps = Uudelleen oppimisen askeleet
deck-config-relearning-steps-tooltip =
    Nolla tai useampi aikaväli, välilyönneillä eroteltuna. Oletusarvoisesti `Uudestaan`-painikkeen
    painaminen korttia kerrattaessa näyttää sen uudelleen 10 minuutin päästä. Jos aikavälejä
    ei määritellä, kortin aikaväliä muutetaan ilman, että kortti merkitään uudelleen opittavaksi kortiksi.
    
    { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip =
    Näin monta kertaa `Uudelleen`-painiketta voidaan painaa, ennen kuin kortti
    merkitään resurssisyöpöksi. Resurssisyöpöt kortit ovat sellaisia, joiden opiskelu vie
    paljon aikaa, ja kun kortti merkitään resurssisyöpöksi, kannattaa harkita sen muuttamista
    tai poistamista, tai keksiä muistisääntö, joka auttaa sen muistamisessa.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    `Liitä vain tunniste`: Lisää "resurssisyöppö" tunniste muistiinpanoon, ja näytä ponnahdusikkuna.
    
    `Hyllytä kortti`: Tunnisteen lisäämisen lisäksi kortti piilotetaan, kunnes se poistetaan
    käsin hyllytetyistä korteista.

## Burying section

deck-config-bury-title = Hautaaminen
deck-config-bury-new-siblings = Hautaa uudet sisarkortit
deck-config-bury-review-siblings = Hautaa kerrattavat sisarkortit
deck-config-bury-interday-learning-siblings = Hautaa usean päivän aikana opittavat sisarkortit
deck-config-bury-new-tooltip =
    Viivästetäänkö muita `uusia` samasta muistiinpanosta tehtyjä kortteja (esim. käänteisiä kortteja,
    vierekkäisiä täyttötehtäviä) seuraavaan päivään.
deck-config-bury-review-tooltip =
    Viivästetäänkö muita `kerrattavia` samasta muistiinpanosta tehtyjä kortteja (esim. käänteisiä kortteja,
    vierekkäisiä täyttötehtäviä) seuraavaan päivään.
deck-config-bury-interday-learning-tooltip =
    Viivästetäänkö muita `opittavia` samasta muistiinpanosta tehtyjä kortteja (esim. käänteisiä kortteja,
    vierekkäisiä täyttötehtäviä) seuraavaan päivään.
deck-config-bury-priority-tooltip =
    Kun Anki kerää kortteja, se kerää ensin samana päivänä opittavat kortit, sitten useana päivänä opittavat kortit, sitten kerrattavat ja lopuksi uudet kortit. Tämä vaikuttaa miten hautaaminen toimii.
    
    - Jos kaikki hautaustoiminnot ovat käytössä, näytetään se sisarkortti, joka tulee luettelossa ensimmäisenä. Esimerkiksi kerrattava kortti näytetään ennen uutta korttia.
    - Luettelossa myöhemmin olevat sisarkortit eivät voi haudata aikaisempia korttityyppejä. Jos esimerkiksi poistat uusien korttien hautaamisen käytöstä ja opiskelet uutta korttia, usean päivän aikana opittavia tai kerrattavia kortteja ei haudata, ja saatat nähdä sekä kerrattavan sisarkortin että uuden sisarkortin saman istunnon aikana.

## Gather order and sort order of cards

deck-config-ordering-title = Esitysjärjestys
deck-config-new-gather-priority = Uusien korttien keräysjärjestys
deck-config-new-gather-priority-tooltip-2 =
    `Pakan järjestyksen mukaan`: kerää kortteja jokaisesta pakasta järjestyksessä päällimmäisestä alkaen. Kortit kerätään kustakin pakasta nousevassa järjestyksessä (erääntymisluvun mukaan). Jos valitun pakan päivittäinen raja saavutetaan, kerääminen voidaan lopettaa ennen kuin kaikki pakat on tarkistettu. Tämä järjestys on nopein suurissa kokoelmissa, ja sen avulla voit asettaa etusijalle alipakat, jotka ovat lähempänä yläpäätä.
    
    `Nousevassa järjestyksessä`: kerää kortteja erääntymisluvun mukaan nousevassa järjestyksessä, jolloin yleensä vanhimmat kortit kerätään ensin.
    
    `Laskevassa järjestyksessä`: kerää kortteja erääntymisluvun mukaan laskevassa järjestyksessä, jolloin yleensä uusimmat kortit kerätään ensin.
    
    `Satunnaiset muistiinpanot`: kerää kortteja satunnaisesti valituista muistiinpanoista. Kun sisarkorttien hautaaminen on poistettu käytöstä, tämä mahdollistaa sen, että kaikki muistiinpanon kortit näkyvät samassa istunnossa (esim. sekä etupuoli->kääntöpuoli että kääntöpuoli->etupuoli -kortit)
    
    `Satunnaiset kortit`: kerää kortteja täysin satunnaisesti.
deck-config-new-card-sort-order = Uusien korttien lajittelujärjestys
deck-config-new-card-sort-order-tooltip-2 =
    `Korttimallin mukaan`: Näyttää kortit korttimallien mukaisessa järjestyksessä. Jos sisarkorttien hautaaminen on pois käytöstä, tämä varmistaa, että kaikki etupuoli->kääntöpuoli-kortit näkyvät ennen kääntöpuoli->etupuoli-kortteja.
    
    `Keräysjärjestyksen mukaan`: Näyttää kortit täsmälleen siinä järjestyksessä kuin ne on kerätty. Jos sisarkorttien hautaaminen on pois käytöstä, tämä johtaa yleensä siihen, että kaikki muistiinpanon kortit näkyvät peräkkäin.
    
    `Korttimallin mukaan, sitten satunnaisesti`: Kuten `Korttimallin mukaan`, mutta sekoittaa kunkin mallin kortit. Kun tämä yhdistetään nousevaan järjestykseen, tätä voidaan käyttää esimerkiksi vanhimpien korttien näyttämiseen satunnaisessa järjestyksessä.
    
    `Satunnainen muistiinpano, sitten korttimallin mukaan`: Poimii muistiinpanoja satunnaisesti ja näyttää sitten kaikki niiden sisarkortit järjestyksesä.
    
    `Satunnainen`: Sekoittaa kerätyt kortit täysin.
deck-config-new-review-priority = Uusien ja kerrattavien keskinäinen järjestys
deck-config-new-review-priority-tooltip = Milloin uudet kortit näytetään suhteessa kerrattaviin kortteihin.
deck-config-interday-step-priority = Usean päivän aikana opittavien ja kerrattavien keskinäinen järjestys
deck-config-interday-step-priority-tooltip =
    Milloin näytetään opittavat kortit (tai uudelleen opittavat kortit), joita tarvitsee opiskella usean päivän ajan.
    
    Kertausten enimmäismäärää sovelletaan aina ensin usean päivän aikana opittaviin kortteihin, ja sitten kerrattaviin. Tämä asetus ohjaa järjestystä, jossa kerätyt kortit näytetään, mutta usean päivän aikana opittavat kortit kerätään aina ensin.
deck-config-review-sort-order = Kerrattavien korttien lajittelujärjestys
deck-config-review-sort-order-tooltip = Oletusarvoisesti asetetaan etusijalle kortit, jotka ovat odottaneet pisimpään, joten jos kerrattavaa on paljon, pisimpään kertausta odottaneet kortit näytetään ensin. Jos kerrattavia kortteja on rästissä paljon, ja niiden läpikäymiseen kuluisi useampi päivä, tai jos haluat nähdä kortit alipakkojen mukaisessa järjestyksessä, vaihtoehtoinen lajittelujärjestys saattaa olla harkitsemisen arvoinen.
deck-config-display-order-will-use-current-deck = Anki käyttää sen pakan esitysjärjestystä, jonka valitset opiskeltavaksi, eikä sen mahdollisten alipakkojen esitysjärjestystä.

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = Pakan järjestyksen mukaan
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = Ensin pakka, sitten satunnaiset muistiinpanot
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = Nousevassa järjestyksessä
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = Laskevassa järjestyksessä
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = Satunnaiset muistiinpanot
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = Satunnaiset kortit
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = Korttimallin mukaan, sitten satunnaisesti
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = Satunnainen muistiinpano, sitten korttimallin mukaan
# Sort the cards randomly.
deck-config-sort-order-random = Satunnainen
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = Korttimallin mukaan
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = Keräysjärjestyksen mukaan
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = Sekoita kerrattavien kanssa
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = Näytä kerrattavien korttien jälkeen
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = Näytä ennen kerrattavia kortteja
# Sort the cards first by due date, in ascending order (oldest due date to newest), then randomly within the same due date.
deck-config-sort-order-due-date-then-random = Eräpäivän mukaan, sitten satunnaisesti
# Sort the cards first by due date, in ascending order (oldest due date to newest), then by deck within the same due date.
deck-config-sort-order-due-date-then-deck = Eräpäivän mukaan, sitten pakasta
# Sort the cards first by deck, then by due date in ascending order (oldest due date to newest) within the same deck.
deck-config-sort-order-deck-then-due-date = Pakan järjestyksen mukaan, sitten eräpäivän mukaan
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = Kertausvälien mukaan nousevassa järjestyksessä
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = Kertausvälien mukaan laskevassa järjestyksessä
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = Helpoimmasta vaikeimpaan
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = Vaikeimmasta helpoimpaan
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = Kasvava vaikeus
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = Laskeva vaikeus
# Sort the cards by retrievability percentage, in ascending order (0% to 100%, least retrievable to most easily retrievable).
deck-config-sort-order-retrievability-ascending = Palautettavuus, nouseva
# Sort the cards by retrievability percentage, in descending order (100% to 0%, most easily retrievable to least retrievable).
deck-config-sort-order-retrievability-descending = Palautettavuus, laskeva

## Timer section

deck-config-timer-title = Ajastin
deck-config-maximum-answer-secs = Vastauksen enimmäiskesto sekunneissa
deck-config-maximum-answer-secs-tooltip = Yksittäisen kertauksen enimmäiskesto tilastointia varten. Jos vastaus ylittää tämän ajan (esimerkiksi siksi, että lähdit pois näytön ääreltä), kulunut aika tallennetaan asettamasi raja-arvon mukaisesti.
deck-config-show-answer-timer-tooltip = Näytä kertausnäkymässä ajastin, joka laskee, kuinka monta sekuntia käytät kunkin kortin kertaukseen.
deck-config-stop-timer-on-answer = Pysäytä ajastin vastaamisen jälkeen
deck-config-stop-timer-on-answer-tooltip =
    Pysäytetäänkö ajastin kun vastaus paljastetaan.
    Tämä ei vaikuta tilastoihin.

## Auto Advance section

deck-config-seconds-to-show-question = Kysymyksen näyttöaika (s)
deck-config-seconds-to-show-question-tooltip-3 = Kun automaattinen eteneminen on käytössä, odota näin monta sekuntia ennen kysymystoiminnon suorittamista. Poista käytöstä asettamalla arvoksi 0.
deck-config-seconds-to-show-answer = Vastauksen näyttöaika (s)
deck-config-seconds-to-show-answer-tooltip-2 = Kun automaattinen eteneminen on käytössä, odota näin monta sekuntia ennen kuin vastaus annetaan. Poista käytöstä asettamalla arvoksi 0.
deck-config-question-action-show-answer = Näytä vastaus
deck-config-question-action-show-reminder = Näytä muistutus
deck-config-question-action = Kysymysten toiminto
deck-config-question-action-tool-tip = Toiminto, joka suoritetaan, kun kysymys on esitetty ja aika on loppunut.
deck-config-answer-action = Vastaustoiminto
deck-config-answer-action-tooltip-2 = Toiminto, joka suoritetaan vastauksen näyttämisen ja ajan kulumisen jälkeen.
deck-config-wait-for-audio-tooltip-2 = Odota äänen loppumista ennen kysymys- tai vastaustoiminnon automaattista suorittamista.

## Audio section

deck-config-audio-title = Ääni
deck-config-disable-autoplay = Älä toista ääniä automaattisesti
deck-config-disable-autoplay-tooltip =
    Tämän ollessa käytössä Anki ei toista ääntä automaattisesti.
    Äänen voi toistaa manuaalisesti klikkaamalla/napauttamalla äänikuvaketta tai käyttämällä toista ääni -toimintoa.
deck-config-skip-question-when-replaying = Ohita kysymys, kun vastausta toistetaan uudelleen
deck-config-always-include-question-audio-tooltip = Toistetaanko vastauksen lisäksi myös kysymyksen ääni, kun Toista uudelleen -toimintoa käytetään kortin vastauspuolta tarkasteltaessa.

## Advanced section

deck-config-advanced-title = Lisäasetukset
deck-config-maximum-interval-tooltip = Suurin mahdollinen viive kertausten välillä, päivissä mitattuna. Kun kerrattava kortti on saavuttanut tämän rajan, `Vaikea`, `Hyvä` ja `Helppo` antavat kaikki saman viiveen. Mitä lyhyemmäksi asetat tämän, sitä suurempi työmääräsi on.
deck-config-starting-ease-tooltip = Helppouskerroin, jonka uudet kortit saavat aluksi. Oletusarvoisesti `Hyvä`-painikkeen painaminen juuri opitussa kortissa viivästyttää seuraavaa kerausta 2,5-kertaisella viiveellä edelliseen verrattuna.
deck-config-easy-bonus-tooltip = Lisäkerroin, jota sovelletaan kerrattaviin kortteihin, joissa käytät `Helppo`-painiketta.
deck-config-interval-modifier-tooltip = Tätä kerrointa sovelletaan kaikkiin kertauksiin, ja pienillä säädöillä voidaan tehdä Ankin aikataulutusta varovaisemmaksi tai aggressiivisemmaksi. Lue käyttöopas, ennen kuin muutat tätä asetusta.
deck-config-hard-interval-tooltip = Kertausväliin sovellettava kerroin, kun `Vaikea`-painiketta käytetään.
deck-config-new-interval-tooltip = Kertausväliin sovellettava kerroin, kun `Uudelleen`-painiketta käytetään.
deck-config-minimum-interval-tooltip = Kertausvälin vähimmäispituus, kun käytetään `Uudelleen`-painiketta.
deck-config-custom-scheduling = Mukautettu aikataulutus
deck-config-custom-scheduling-tooltip = Vaikuttaa koko kokoelmaan. Käytä omalla vastuulla!

## Easy Days section.

deck-config-easy-days-title = Helpot päivät
deck-config-easy-days-monday = Maanantai
deck-config-easy-days-tuesday = Tiistai
deck-config-easy-days-wednesday = Keskiviikko
deck-config-easy-days-thursday = Torstai
deck-config-easy-days-friday = Perjantai
deck-config-easy-days-saturday = Lauantai
deck-config-easy-days-sunday = Sunnuntai
deck-config-easy-days-normal = Normaali
deck-config-easy-days-reduced = Vähennetty
deck-config-easy-days-minimum = Minimi
deck-config-easy-days-no-normal-days = '{ deck-config-easy-days-normal }' -arvon tulisi olla vähintään yksi päivä.
deck-config-easy-days-change = Nykyisiä kertauksia ei ajasteta uudelleen, ellei ”{ deck-config-reschedule-cards-on-change }” ole käytössä FSRS-asetuksissa.

## Adding/renaming

deck-config-add-group = Lisää asetusmalli
deck-config-name-prompt = Nimi
deck-config-rename-group = Nimeä asetusmalli uudelleen
deck-config-clone-group = Tee asetusmallista kopio

## Removing

deck-config-remove-group = Poista asetusmalli
deck-config-will-require-full-sync = Pyydetty muutos edellyttää yksisuuntaista synkronointia. Jos olet tehnyt muutoksia toisella laitteella etkä ole vielä synkronoinut niitä tähän laitteeseen, tee se ennen kuin ennen kuin jatkat.
deck-config-confirm-remove-name = Poistetaanko { $name }?

## Other Buttons

deck-config-save-button = Tallenna
deck-config-save-to-all-subdecks = Tallenna kaikkiin alipakkoihin
deck-config-save-and-optimize = Optimoi kaikki esiasetukset
deck-config-revert-button-tooltip = Palauta tämä asetus oletusarvoonsa.

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Anki 2.1.41+:n mukainen käsittelytapa
deck-config-description-new-handling-hint =
    Käsittelee syötettä markdownina ja puhdistaa HTML-syötteen. Kun tämä on käytössä,
    kortin kuvaus näytetään myös onnitteluruudussa.
    Markdown näkyy tekstinä Anki 2.1.40:ssä ja sitä vanhemmissa versioissa.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    Yläpakan raja on { $cards ->
        [one] { $cards } kortti
       *[other] { $cards } korttia
    }, ja se ohittaa tämän rajoituksen.
deck-config-reviews-too-low =
    Jos joka päivä lisätään { $cards ->
        [one] { $cards } uusi kortti
       *[other] { $cards } uutta korttia
    }, kertausten enimmäismäärän tulisi olla vähintään { $expected }
deck-config-learning-step-above-graduating-interval = Valmistumisen jälkeisen kertausvälin tulisi olla vähintään yhtä pitkä kuin viimeisen oppimisaskeleen.
deck-config-good-above-easy = Helpon kortin kertausvälin tulisi olla vähintään yhtä pitkä kuin valmistumisen jälkeisen kertausvälin.
deck-config-relearning-steps-above-minimum-interval = Vähimmäiskertausvälin tulisi olla ainakin yhtä suuri kuin viimeisen uudelleen oppimisen askeleen.
deck-config-maximum-answer-secs-above-recommended = Anki ajoittaa kertaukset tehokkaammin, kun pidät kun pidät kysymykset lyhyinä.
deck-config-too-short-maximum-interval = Enimmäisaikaväliksi ei suositella alle 6:a kuukautta.
deck-config-ignore-before-info = (Arviolta) { $included }/{ $totalCards } kortista käytetään FSRS-painokertoimien optimointiin.

## Selecting a deck

deck-config-which-deck = Minkä pakan haluaisit?

## Messages related to the FSRS scheduler

deck-config-updating-cards = Päivitetään kortteja: { $current_cards_count }/{ $total_cards_count }...
deck-config-invalid-parameters = Annetut FSRS:n painokertoimet ovat virheellisiä. Jätä ne tyhjiksi käyttääksesi oletusarvoja.
deck-config-not-enough-history = Ei riittävästi menneitä kertauksia tämän toiminnon suorittamiseen.
deck-config-must-have-400-reviews =
    { $count ->
        [one] Vain { $count } kertaus löytyi. Tämä toiminto edellyttää vähintään 400 kertausta.
       *[other] Vain { $count } kertausta löytyi. Tämä toiminto edellyttää vähintään 400 kertausta.
    }
# Numbers that control how aggressively the FSRS algorithm schedules cards
deck-config-weights = Mallin painokertoimet
deck-config-compute-optimal-weights = Laske optimaaliset painokertoimet
deck-config-optimize-button = Optimoi
# Indicates that a given function or label, provided via the "text" variable, operates slowly.
deck-config-slow-suffix = { $text } (hidas)
deck-config-compute-button = Laske
deck-config-ignore-before = Älä huomioi kertauksia, jotka tapahtuivat ennen
deck-config-time-to-optimize = Viime kerrasta on jo aikaa - on suositeltavaa käyttää Optimoi kaikki esiasetukset -painiketta.
deck-config-evaluate-button = Arvioi
deck-config-desired-retention = Toivottu retentio
deck-config-historical-retention = Historiallinen retentio
deck-config-smaller-is-better = Pienemmät numerot merkitsevät parempia arvioita muistista.
deck-config-steps-too-large-for-fsrs = Kun FSRS on päällä, usean päivän aikaisen (uudelleen)oppimisen askelten käyttöä ei suositella.
deck-config-get-params = Hae painokertoimet
deck-config-complete = { $num } % valmis.
deck-config-iterations = Iteraatio: { $count }...
deck-config-reschedule-cards-on-change = Aikatauluta kortit uudelleen muutoksen yhteydessä
deck-config-fsrs-tooltip =
    Free Spaced Repetition Scheduler (FSRS) on vaihtoehto Ankin vanhalle SuperMemo 2 (SM2) -aikataulutusohjelmalle.
    Se määrittää tarkemmin, milloin olet vaarassa unohtaa oppimaasi, ja auttaa sinua muistamaan enemmän materiaalia samassa ajassa. Tämä asetus vaikuttaa kaikkien pakkojen esiasetuksiin.
deck-config-desired-retention-tooltip = Oletusarvo 0,9 ajoittaa kortit niin, että sinulla on 90 % mahdollisuus muistaa ne, kun ne tulevat uudelleen kerrattaviksi. Jos arvoa kasvatetaan, Anki näyttää kortteja useammin, jotta muistaisit ne todennäköisemmin. Jos arvoa pienennetään, Anki näyttää kortteja harvemmin, ja unohtanet niistä aiempaa suuremman osan. Ole varovainen säätäessäsi tätä arvoa - suuremmat arvot lisäävät työmäärääsi huomattavasti, ja pienemmät arvot saattavat johtaa siihen, että et muista oppimaasi materiaalia yhtä hyvin, mikä voi olla lannistavaa.
deck-config-desired-retention-tooltip2 = Tietolaatikon näyttämä työmäärä on karkea arvio. Käytä simulaattoria, jos kaipaat tarkempaa arviota.
deck-config-historical-retention-tooltip =
    Kun osa kertaushistoriastasi puuttuu, FSRS:n on täytettävä aukot. Lähtökohtaisesti se olettaa, että muistit aikanaan kerratessasi 90 % oppimateriaalista. Jos vanha retentionsi oli huomattavasti suurempi tai pienempi kuin 90 %, tämän vaihtoehdon säätäminen antaa FSRS:lle mahdollisuuden arvioida puuttuvia kertauksia paremmin.
    
    Kertaushistoriasi voi olla puutteellinen kahdesta syystä:
    1. Koska olet käyttänyt "Älä huomioi kertauksia, jotka tapahtuivat ennen” -toimintoa.
    2. Koska olet aiemmin poistanut kertaustiedot vapauttaaksesi tilaa tai tuonut aineistoa toisesta aikavälikertausohjelmasta.
    
    Jälkimmäinen tapaus on melko harvinainen, joten jos et ole käyttänyt ensimmäistä vaihtoehtoa, sinun ei todennäköisesti tarvitse muokata tätä asetusta.
deck-config-weights-tooltip2 = FSRS-painokertoimet vaikuttavat korttien ajoitukseen. Anki aloittaa oletusarvoisilla parametreilla. Voit käyttää alla olevaa valintaa optimoidaksesi painokertoimet, jotta ne vastaavat parhaiten tasoasi tätä esiasetusta käyttävissä pakoissa.
deck-config-reschedule-cards-on-change-tooltip = Tällä valinnalla määritetään, muutetaanko korttien eräpäiviä, kun otat FSRS:n käyttöön tai muutat painokertoimia. Oletusarvoisesti kortteja ei ajoiteta uudelleen: tulevissa tarkistuksissa käytetään uutta ajoitusta, mutta työmäärään ei tule välitöntä muutosta. Jos aikataulun muuttaminen otetaan käyttöön, korttien eräpäiviä muutetaan.
deck-config-reschedule-cards-warning = Riippuen toivomastasi retentioajasta, tämä voi johtaa siihen, että suuri määrä kortteja erääntyy, joten sitä ei suositella, kun siirryt ensimmäistä kertaa SM2:sta.
deck-config-ignore-before-tooltip-2 =
    Jos tämä asetetaan, ennen annettua päivämäärää tarkistettuja kortteja ei oteta huomioon FSRS-parametreja optimoitaessa.
    Tämä voi olla hyödyllistä, jos olet tuonut jonkun toisen aikataulutiedot tai muuttanut tapaa, jolla käytät vastauspainikkeita.
deck-config-compute-optimal-weights-tooltip2 =
    Kun painat Optimoi-painiketta, FSRS analysoi kertaushistoriasi ja luo painokertoimet, jotka ovat optimaaliset muistisi ja opiskelemasi sisällön kannalta. Jos eri pakkojen vaikeusasteiden välillä on suuria eroja, on suositeltavaa määrittää niille erilliset esiasetukset, sillä helppojen ja vaikeiden pakkojen painokertoimet tulevat olemaan erilaiset. Painokertoimia ei tarvitse optimoida usein – kerta muutaman kuukauden välein riittää.
    Oletusarvoisesti painokertoimet lasketaan kaikkien nykyistä esiasetusta käyttävien pakkojen kertaushistoriasta. Voit valinnaisesti säätää hakua ennen painokertoimien laskemista, jos haluat vaikuttaa siihen, mitä kortteja käytetään painokertoimien optimointiin.
deck-config-please-save-your-changes-first = Tallenna muutoksesi ennen tämän toiminnon suorittamista.
deck-config-workload-factor-change =
    Arvioitu työmäärä: { $factor }x
    (verrattuna { $previousDR } % toivottuun retentioon)
deck-config-workload-factor-unchanged = Mitä korkeampi tämä arvo on, sitä useammin kortit näytetään.
deck-config-desired-retention-too-low = Toivottu retentiosi on hyvin matala, mikä voi johtaa erittäin pitkiin kertausväleihin.
deck-config-desired-retention-too-high = Toivottu retentiosi on hyvin ,korkea mikä voi johtaa erittäin lyhyisiin kertausväleihin.
deck-config-percent-of-reviews =
    { $reviews ->
        [one] { $pct } % { $reviews } kertauksesta
       *[other] { $pct } % { $reviews } kertauksesta
    }
deck-config-percent-input = { $pct } %
# This message appears during FSRS parameter optimization.
deck-config-checking-for-improvement = Etsitään parannettavaa...
deck-config-optimizing-preset = Optimoidaan esiasetuksia { $current_count }/{ $total_count }...
deck-config-fsrs-must-be-enabled = FSRS on otettava käyttöön ensin.
deck-config-fsrs-params-optimal = FSRS:n painokertoimet vaikuttavat tällä hetkellä optimaalisilta.
deck-config-fsrs-params-no-reviews = Kertauksia ei löytynyt. Tarkista että tämä esiasetus on käytössä kaikissa pakoissa jotka haluat optimoida (mukaan luken alipakat), ja yritä sitten uudelleen.
deck-config-wait-for-audio = Odota äänen päättymistä
deck-config-show-reminder = Näytä muistutus
deck-config-answer-again = Vastaa uudelleen
deck-config-answer-hard = Vastaa Vaikea
deck-config-answer-good = Vastaa Hyvä
deck-config-days-to-simulate = Simuloitavien päivien määrä
deck-config-desired-retention-below-optimal = Valitsemasi retentio on alle optimitason. Sen korottaminen on suositeltavaa.
# Description of the y axis in the FSRS simulation
# diagram (Deck options -> FSRS) showing the total number of
# cards that can be recalled or retrieved on a specific date.
deck-config-fsrs-simulator-experimental = FSRS-simulaattori (kokeellinen)
deck-config-fsrs-simulate-desired-retention-experimental = FSRS:n toivotun retention simulaattori (kokeellinen)
deck-config-fsrs-simulate-save-preset = Tallenna pakan esiasetukset optimoinnin jälkeen ennen kuin käytät simulaattoria.
deck-config-fsrs-desired-retention-help-me-decide-experimental = Auta valitsemaan (kokeellinen)
deck-config-additional-new-cards-to-simulate = Simulaatioon sisällytettävät uudet lisäkortit
deck-config-simulate = Simuloi
deck-config-clear-last-simulate = Poista viimeisin simulaatio
deck-config-fsrs-simulator-radio-count = Kertaukset
deck-config-advanced-settings = Lisäasetukest
deck-config-smooth-graph = Sulavalinjainen kuvaaja
deck-config-suspend-leeches = Hyllytä resurssisyöpöt
deck-config-save-options-to-preset = Tallenna muutokset esiasetuksiin
deck-config-save-options-to-preset-confirm = Korvataanko nykyisen esiasetuksen asetusket asetuksilla, jotka ovat tällä hetkellä käytössä simulaattorissa?
# Radio button in the FSRS simulation diagram (Deck options -> FSRS) selecting
# to show the total number of cards that can be recalled or retrieved on a
# specific date.
deck-config-fsrs-simulator-radio-memorized = Opittu ulkoa
deck-config-fsrs-simulator-radio-ratio = Suhdeluku: aika / ulkoa opittujen määrä
# $time here is pre-formatted e.g. "10 Seconds" 
deck-config-fsrs-simulator-ratio-tooltip = { $time } per ulkoa opittu kortti

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.

# Checkbox
deck-config-health-check = Suorita kuntotarkistus optimoinnin yhteydessä
# Message box showing the result of the health check
deck-config-fsrs-bad-fit-warning =
    Kuntotarkastus:
    FSRS:n on vaikea ennustaa muistiasi. Suosituksia:
    
    - Hyllytä tai muotoile resurssisyöpöt kortit uudelleen.
    - Käytä vastauspainikkeita johdonmukaisesti. Pidä mielessä, että ”Vaikea” on hyväksytty arvosana, ei hylätty.
    - Ymmärrä ennen kuin opettelet ulkoa.
    
    Jos noudatat näitä suosituksia, suorituskyky paranee yleensä seuraavien kuukausien aikana.
# Message box showing the result of the health check
deck-config-fsrs-good-fit =
    Kuntotarkistus:
    FSRS mukautuu muistisi toimintaan hyvin.

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-unable-to-determine-desired-retention = Optimaalisen retention määrittäminen ei onnistunut.
deck-config-predicted-minimum-recommended-retention = Pienin suositeltu retentio: { $num }
deck-config-compute-minimum-recommended-retention = Pienin suositeltu retentio
deck-config-compute-optimal-retention-tooltip4 =
    Tämä työkalu yrittää löytää sopivan retentioarvon, jonka avulla opit mahdollisimman paljon materiaalia mahdollisimman lyhyessä ajassa.
    Voit käyttää tätä lukua vertailukohtana päättäessäsi, mihin arvoon toivottu retentio asetetaan. Voit halutessasi valita korkeamman toivotun retentioarvon, jos olet valmis käyttämään enemmän aikaa oppimiseen ja sitä kautta muistamaan enemmän. Ei ole suositeltavaa asettaa toivottua retentiota tätä arvoa alhaisemmaksi, koska se tekee unohtamisesta todennäköisempää ja näin ollen lisää työmäärää.
deck-config-plotted-on-x-axis = (Piirretty X-akselille)
deck-config-a-100-day-interval =
    { $days ->
        [one] 100 päivän kertausvälistä tulee { $days } päivän mittainen.
       *[other] 100 päivän kertausvälistä tulee { $days } päivän mittainen.
    }
deck-config-fsrs-simulator-y-axis-title-time = Kertausaika / päivä
deck-config-fsrs-simulator-y-axis-title-count = Kertausmäärä / päivä
deck-config-fsrs-simulator-y-axis-title-memorized = Ulkoa opittu yhteensä
deck-config-bury-siblings = Hautaa sisarkortit
deck-config-do-not-bury = Älä hautaa sisarkortteja
deck-config-bury-if-new = Hautaa uudet
deck-config-bury-if-new-or-review = Hautaa uudet tai kerrattavat
deck-config-bury-if-new-review-or-interday = Hautaa uudet, kerrattavat ja usean päivän aikana opittavat
deck-config-bury-tooltip =
    Sisarkortit ovat muita kortteja samasta muistiinpanosta (esim. käänteiset kortit tai muut samasta tekstistä tehdyt täyttötehtävät ).
    
    Kun tämä asetus on pois päältä, useita kortteja samasta muistiinpanosta voi esiintyä samana päivänä. Kun se on päällä, Anki *hautaa* sisarkortit automaattisesti ja piilottaa ne seuraavaan päivään asti. Tämän vaihtoehdon avulla voit valita, minkälaiset kortit voidaan haudata, kun vastaat johonkin niiden sisarkorteista.
deck-config-seconds-to-show-question-tooltip = Kun automaattinen eteneminen on käytössä, odota näin monta sekuntia ennen kuin vastaus näytetään. Poista käytöstä asettamalla arvoksi 0.
deck-config-answer-action-tooltip = Toiminto, joka suoritetaan nykyiselle kortille ennen kuin siirrytään automaattisesti seuraavaan korttiin.
deck-config-wait-for-audio-tooltip = Odota äänen loppumista ennen kuin vastaus tai seuraava kysymys näytetään automaattisesti.
deck-config-ignore-before-tooltip =
    Jos tämä asetus on käytössä, ennen annettua päivämäärää tehdyt kertaukset jätetään huomiotta FSRS:n painokertoimien optimoinnissa ja arvioinnissa.
    Tämä voi olla hyödyllistä, jos olet tuonut jonkun muun aikataulutiedot tai muuttanut tapaa, jolla käytät vastauspainikkeita.
deck-config-compute-optimal-retention-tooltip = Tämä työkalu olettaa, että aloitat 0 kortista, ja yrittää laskea, kuinka paljon materiaalia pystyt säilyttämään muistissasi annetussa ajassa (retentio). Arvioitu retentio riippuu huomattavasti syöttötiedoistasi, ja jos se poikkeaa merkittävästi 0,9:stä, se on merkki siitä, että kullekin päivälle varattu aika on joko liian pieni tai liian suuri siihen korttimäärään nähden, jonka yrität oppia. Tämä luku voi olla hyödyllinen viitteenä, mutta sitä ei suositella kopioitavaksi Toivottu retentio -kenttään.
deck-config-health-check-tooltip1 = Tässä näytetään varoitus jos FSRS:llä on ongelmia mukautua muistisi mukaan.
deck-config-health-check-tooltip2 = Kuntotarkistus suoritetaan vain, kun nykyistä esiasetusta varten optimointi on käytössä.
deck-config-compute-optimal-retention = Laske optimaalinen retentio
deck-config-predicted-optimal-retention = Ennustettu optimaalinen retentio: { $num }
deck-config-weights-tooltip = Mallin painokertoimet vaikuttavat korttien aikataulutukseen. Kun kertauksia on yli 1000, voit optimoida painokertoimet alapuolella.
deck-config-compute-optimal-weights-tooltip =
    Kun olet tehnyt yli 1000 kertausta Ankissa, voit käyttää Optimoi-painiketta, joka analysoi kertaushistoriasi ja luo automaattisesti painokertoimet, jotka ovat optimaaliset muistisi ja opiskelemasi materiaalin kannalta. Jos sinulla on vaikeusasteeltaan vaihtelevia pakkoja, on suositeltavaa määrittää kullekin erilliset esiasetukset, sillä helppojen ja vaikeiden pakkojen painokertoimista tulee erilaisia. Painokertoimia ei tarvitse optimoida usein - kerta muutaman kuukauden välein riittää.
    
    Oletusarvoisesti painotukset lasketaan kaikkien nykyisiä esiasetuksia käyttävien pakkojen kertaushistoriasta. Voit halutessasi säätää hakua ennen painokertoimien laskemista, jos haluat muuttaa, mitä kortteja painokertoimien optimointiin käytetään.
deck-config-compute-optimal-retention-tooltip2 = Tämä työkalu olettaa, että aloitat 0 opitulla kortilla, ja yrittää löytää sellaisen retentioarvon, jonka avulla opit mahdollisimman paljon materiaalia mahdollisimman lyhyessä ajassa. Voit käyttää tätä lukua vertailukohtana päättäessäsi, mihin arvoon toivottu retentio asetetaan. Voit halutessasi valita korkeamman toivotun retentioarvon, jos olet valmis käyttämään enemmän aikaa oppimiseen ja sitä kautta muistamaan enemmän. Ei ole suositeltavaa asettaa toivottua retentiota optimitasoa alhaisemmaksi, koska silloin työmäärä kasvaa ilman että siitä on hyötyä.
deck-config-compute-optimal-retention-tooltip3 =
    Tämä työkalu olettaa, että aloitat 0 opitusta kortista, ja yrittää löytää halutun retentioarvon, jonka avulla opit mahdollisimman paljon materiaalia mahdollisimman lyhyessä ajassa.
    Jotta oppimisprosessia voidaan simuloida tarkasti, tämä toiminto vaatii vähintään 400+ suoritettua kertausta. Voit käyttää tätä lukua vertailukohtana päättäessäsi, mihin arvoon toivottu retentio asetetaan. Voit halutessasi valita korkeamman toivotun retentioarvon, jos olet valmis käyttämään enemmän aikaa oppimiseen ja sitä kautta muistamaan enemmän. Ei ole suositeltavaa asettaa toivottua retentiota optimitasoa alhaisemmaksi, koska silloin työmäärä kasvaa ilman että siitä on hyötyä.
deck-config-seconds-to-show-question-tooltip-2 = Kun automaattinen eteneminen on käytössä, odota näin monta sekuntia ennen kuin vastaus näytetään. Poista käytöstä asettamalla arvoksi 0.
deck-config-invalid-weights = Painokertoimet on joko jätettävä tyhjäksi, jolloin käytetään oletusarvoja, tai niiden on oltava 17 pilkulla erotettua numeroa.
deck-config-fsrs-on-all-clients = Varmista, että kaikki käyttämäsi Anki-ohjelmat ovat versioita Anki(Mobile) 23.10+ tai AnkiDroid 2.17+. FSRS ei toimi oikein, jos käytät vanhempaa versiota jollakin laitteella.
deck-config-optimize-all-tip = Voit optimoida kaikki esiasetukset kerralla käyttämällä ylhäällä olevaa painiketta.
