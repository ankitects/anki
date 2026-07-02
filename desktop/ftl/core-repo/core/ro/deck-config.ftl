### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    folosit de { $decks ->
        [one] { $decks } pachet
        [few] { $decks } pachete
       *[other] { $decks } pachete
    }
deck-config-default-name = Implicit
deck-config-title = Opţiuni pachet

## Daily limits section

deck-config-daily-limits = Limite zilnice
deck-config-new-limit-tooltip =
    Numărul maxim de carduri noi de introdus într-o zi, dacă sunt disponibile carduri noi.
    Deoarece materialul nou va crește volumul de lucru pentru repetarea pe termen scurt, acest lucru ar trebui de obicei
    să fie de cel puțin 10 ori mai mică decât limita de repetare.
deck-config-review-limit-tooltip =
    Numărul maxim de carduri de repetat de afișat într-o zi,
    dacă cardurile sunt gata pentru examinare.
deck-config-limit-deck-v3 =
    Când studiezi un pachet care are sub-pachete în interior, limitele stabilite pentru fiecare
    sub-pachet controlează numărul maxim de carduri extrase din pachetul respectiv.
    Limitele pachetului selectat controlează numărul total de carduri care vor fi afișate.
deck-config-limit-new-bound-by-reviews =
    Limita de repetare afectează limita pentru carduri noi. De exemplu, dacă limita de repetare este
    setată la 200 și există 190 de carduri în așteptare, vor fi maxim 10 carduri noi introduse.
    Dacă limita de repetare a fost atinsă, nu vor fi carduri noi afișate.
deck-config-limit-interday-bound-by-reviews =
    Limita de repetare afectează și cardurile de memorat între zile. Când se aplică limita,
    cardurile de memorat între zile sunt preluate mai întâi, apoi cardurile de repetat și, în sfârșit, cardurile noi.

## New Cards section

deck-config-learning-steps = Pașii de învățare
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Întârzierile sunt de obicei minute (de exemplu, `1m`) sau zile (de exemplu, `2d`), dar sunt acceptate și ore (de exemplu, `1h`) și secunde (de exemplu, `30s`).
deck-config-learning-steps-tooltip =
    Una sau mai multe întârzieri, separate prin spații. Prima întârziere va fi folosită
    atunci când apeşi butonul „Din nou” de pe un card nou, și este de 1 minut în mod implicit.
    Butonul „Bine” va trece la pasul următor, care este de 10 minute în mod implicit.
    Odată ce toți pașii au fost parcurși, cardul va deveni un card de repetare și
    va apărea într-o altă zi. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip =
    Numărul de zile de așteptat înainte de a afișa din nou un card, după ce butonul „Bine”
    este apăsat la pasul final de memorare.
deck-config-easy-interval-tooltip =
    Numărul de zile de așteptat înainte de a afișa din nou un card, după ce butonul `Ușor`
    este folosit pentru a elimina imediat cardul respectiv din ciclul de memorare.
deck-config-new-insertion-order = Ordinea de introducere
deck-config-new-insertion-order-tooltip =
    Controlează ce poziţie (due #) primesc cardurile noi atunci când sunt adăugate.
    Cardurile cu un număr scadent mai mic vor fi afișate mai întâi când studiezi. 
    Schimbarea acestei opţiuni va actualiza automat poziția existentă a cardurilor noi.
deck-config-new-insertion-order-sequential = Secvenţial (cele mai vechi carduri mai întâi)
deck-config-new-insertion-order-random = Aleatoriu
deck-config-new-insertion-order-random-with-v3 =
    Cu planificatorul V3, este mai bine să lași acest set la secvențial și
    în schimb, să ajustezi noua ordine de colectare a cardurilor.

## Lapses section

deck-config-relearning-steps = Paşi de reînvăţare
deck-config-relearning-steps-tooltip =
    Zero sau mai multe amânări, separate prin spații. În mod implicit, apăsând butonul `Din nou`
    de pe un card de repetare îl va afișa din nou 10 minute mai târziu. Dacă nu sunt cerute amânări,
    cardul va avea intervalul schimbat, fără a intra în re-memorare. { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip =
    De câte ori trebuie apăsat „Din nou” pe un card de repetare înainte de a fi
    marcat ca o lipitoare. Lipitorile sunt carduri care vă consumă mult timp și
    când un card este marcat ca o lipitoare, este o idee bună să-l rescrii, să-l ștergi sau
    sî-i găseşti un mnemonic care să te ajute să îţi aminteşti conţinutul.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    „Numai marcajul”: Adaugă un marcaj „lipitoare” la notiţă și afișează o fereastră pop-up.
    
    `Suspendă cardul`: Pe lângă marcarea notiţei, ascunde cardul până când este
    nesuspendat manual.

## Burying section

deck-config-bury-title = Îngropare
deck-config-bury-new-siblings = Îngroapă carduri frați ”noi” până a doua zi
deck-config-bury-review-siblings = Îngroapă carduri frați ”în repetare” până a doua zi
deck-config-bury-interday-learning-siblings = Îngroapă cardurile frați în învățare dintre zile
deck-config-bury-tooltip =
    Dacă alte carduri care aparţin de aceeași notiţă 
    (de exemplu carduri inversate, carduri cloze adiacente)
    vor fi amânate până a doua zi.

## Ordering section

deck-config-ordering-title = Ordine de afişare
deck-config-new-gather-priority = Ordine nouă de plasare a cardurilor
deck-config-new-gather-priority-tooltip-2 =
    `Pachet`: adună cardurile din fiecare pachet în ordine, începând de sus. Cardurile din fiecare pachet sunt
    ordonate in pozitie ascendentă. Dacă limita zilnică a pachetului selectat este atinsă, ordonarea lor
    se poate opri înainte ca toate pachetele să fi fost verificate. Această comandă este cea mai rapidă în colecțiile mari și
    îți permite să acorzi prioritate sub-pachetelor care sunt mai aproape de partea de sus.
    
    `Poziție ascendentă`: ordonează carduri în funcție de poziția ascendentă (numărul programat), ceea ce este de obicei
    cel mai vechi adăugat primul.
    
    `Poziție descendentă`: adună carduri în funcție de poziția descendentă (numărul programat), ceea ce este de obicei
    cel mai recent adăugat primul.
    
    `Notițe aleatorii`: adună carduri de notițe aleatorii. Când îngroparea cardurilor frați este
    dezactivată, acest lucru permite ca toate cardurile unei notițe să fie văzute într-o sesiune (de exemplu, atât card față->spate
    și spate->față)
    
    `Carduri aleatorii`: ordonează cardurile complet aleatoriu.
deck-config-new-gather-priority-deck = Pachet
deck-config-new-gather-priority-position-lowest-first = Poziție ascendentă
deck-config-new-gather-priority-position-highest-first = Poziție descendentă
deck-config-new-gather-priority-random-notes = Notițe aleatorii
deck-config-new-gather-priority-random-cards = Carduri aleatorii
deck-config-new-card-sort-order = Ordine de sortare după carduri noi
deck-config-new-card-sort-order-tooltip-2 =
    `Șablon de card`: Afișează cardurile în ordinea șablonului de card. Dacă ai ”îngroparea cardurilor frați”
    dezactivată, acest lucru va asigura că toate cardurile față->spate sunt văzute înaintea oricăror carduri din spate->față.
    
    „Ordinea adunată”: arată cardurile exact așa cum au fost adunate. Dacă „îngroparea cardurilor frați„ este dezactivată,
    acest lucru va face, de obicei, ca toate cardurile unei notițe să fie văzute una după alta.
    
    `Șablon de card, apoi aleatoriu`: La fel ca`Șablon de card`, dar amestecă cardurile fiecărui șablon.
    Atunci când este combinată cu o ordine de adunare în poziție ascendentă, aceasta poate fi folosită pentru a afișa
    cele mai vechi carduri într-o ordine aleatorie, de exemplu.
    
    `Notă aleatorie, apoi șablon de card`: alege notițele la întâmplare, apoi îi arată pe toți frații lor
    în ordine.
    
    `Random`: amestecă complet cardurile adunate.
deck-config-sort-order-card-template-then-lowest-position = Șablon de card, apoi poziție ascendentă
deck-config-sort-order-card-template-then-highest-position = Șablon de card, apoi poziție descendentă
deck-config-sort-order-card-template-then-random = Șablon de card, apoi aleatoriu
deck-config-sort-order-random-note-then-template = Notiță aleatorie, apoi șablon de card
deck-config-sort-order-lowest-position = Poziţie ascendentă
deck-config-sort-order-highest-position = Poziţie descendentă
deck-config-sort-order-random = Aleatoriu
deck-config-sort-order-template-then-gather = Șablon de card, apoi ordinea de colectare
deck-config-sort-order-gather = Ordinea de colectare
deck-config-new-review-priority = Ordine noi/în revizuire
deck-config-new-review-priority-tooltip = Când să se afişeze carduri noi în raport cu cardurile  de revăzut.
deck-config-interday-step-priority = Ordine de învățare/revizuire între zile
deck-config-interday-step-priority-tooltip =
    Când să se afişeze carduri de (re)învățare care depășesc graniţa unei zile.
    
    Limita de revizuire este întotdeauna aplicată mai întâi cardurilor de învățare între zile și apoi celor de revăzut. 
    Această opțiune va controla ordinea în care sunt afișate cardurile adunate,
    dar cardurile de memorare între zile vor fi întotdeauna aranjate primele.
deck-config-review-mix-mix-with-reviews = Amestecă cu cele de repetat
deck-config-review-mix-show-after-reviews = Arată după cardurile de repetat
deck-config-review-mix-show-before-reviews = Arată înainte de cardurile de repetat
deck-config-review-sort-order = Ordine de sortare după cardurile de repetat
deck-config-review-sort-order-tooltip =
    Ordinea implicită acordă prioritate cardurilor care au așteptat cel mai mult timp, astfel încât
    dacă se acumulează multe carduri de repetat, cele mai vechi vor apărea mai întâi.
    Dacă există o acumulare mare de carduri de repetat şi va dura mai mult de câteva zile pentru a le parcurge pe toate,
    sau vrei să parcurgi cardurile în ordinea sub-pachetelor, este posibil să găseşti mai utile modurile alternative de sortare.
deck-config-sort-order-due-date-then-random = Data scadentă, apoi aleatoriu
deck-config-sort-order-due-date-then-deck = Data scadentă, apoi pachetul
deck-config-sort-order-deck-then-due-date = Pachetul, apoi data scadentă
deck-config-sort-order-ascending-intervals = Intervale ascendente
deck-config-sort-order-descending-intervals = Intervale descendente
deck-config-sort-order-ascending-ease = Uşurinţă ascendentă
deck-config-sort-order-descending-ease = Uşurinţă descendentă
deck-config-display-order-will-use-current-deck =
    Anki va folosi ordinea de afișare din pachetul pe care îl 
    selectezi pentru studiu și nu orice sub-pachete pe care le-ar putea avea.

## Timer section

deck-config-timer-title = Temporizator
deck-config-maximum-answer-secs = Nr. maxim de secunde pentru răspuns
deck-config-maximum-answer-secs-tooltip =
    Numărul maxim de secunde de contorizat pentru o singură repetare. Dacă un răspuns
    depășește acest timp (pentru că te-ai îndepărtat de ecran, de exemplu),
    timpul necesar va fi înregistrat conform limitei pe care ai stabilit-o.
deck-config-show-answer-timer-tooltip = În ecranul de repetare, afișează un cronometru care numără de câte secunde ai nevoie pentru a răspunde la fiecare card.

## Audio section

deck-config-audio-title = Audio
deck-config-disable-autoplay = Nu rula fişierul audio în mod automat.
deck-config-skip-question-when-replaying = Omite întrebarea când revezi răspunsul
deck-config-always-include-question-audio-tooltip =
    Dacă întrebarea audio ar trebui inclusă atunci când acțiunea Replay este
    folosită în timp ce te uiţi la răspunsul unui card.

## Advanced section

deck-config-advanced-title = Avansat
deck-config-maximum-interval-tooltip =
    Numărul maxim de zile pe care un card de repetat îl va aștepta. Când numărul de repetări
    au atins limita, `Greu`, `Bine` și `Ușor`, toate vor avea aceeași întârziere.
    Cu cât setezi acest lucru mai scurt, cu atât volumul tău de lucru va fi mai mare.
deck-config-starting-ease-tooltip =
    Multiplicatorul de ușurință cu care încep cardurile noi. În mod implicit, butonul „Bine” de pe 
    un card nou de memorat va întârzia următoarea revizuire cu de 2,5 ori întârzierea anterioară.
deck-config-easy-bonus-tooltip = Un multiplicator suplimentar care se aplică intervalului de revedere al unui card  atunci când îl evaluezi ca `Ușor`.
deck-config-interval-modifier-tooltip =
    Acest multiplicator se aplică tuturor repetărilor și pot fi utilizate ajustări minore
    pentru a-l face pe Anki mai conservator sau mai agresiv în programarea sa. 
    Te rog să consulţi manualul înainte de a schimba această opțiune.
deck-config-hard-interval-tooltip = Multiplicatorul aplicat unui interval de repetare atunci când răspunzi „Greu”.
deck-config-new-interval-tooltip = Multiplicatorul aplicat unui interval de repetare atunci când răspunzi „Din nou”.
deck-config-minimum-interval-tooltip = Intervalul minim acordat unui card de repetare după ce răspunzi „Din nou”.
deck-config-custom-scheduling = Programare personalizată
deck-config-custom-scheduling-tooltip = Aceasta afectează întreaga colecție. Foloseşte pe propria răspundere!

## Adding/renaming

deck-config-add-group = Adaugă presetare
deck-config-name-prompt = Nume
deck-config-rename-group = Redenumeşte presetare
deck-config-clone-group = Clonează presetare

## Removing

deck-config-remove-group = Elimină presetare
deck-config-will-require-full-sync =
    Modificarea solicitată va necesita o sincronizare unidirecțională. Dacă ai făcut modificări
    pe alt dispozitiv și nu le-ai sincronizat încă cu acest dispozitiv, te rog să faci acest lucru înainte de a continua.
deck-config-confirm-remove-name = Elimin { $name }?

## Other Buttons

deck-config-save-button = Salvează
deck-config-save-to-all-subdecks = Salvează toate sub-pachetele
deck-config-revert-button-tooltip = Restabileşte această setare la valoarea implicită.

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling-hint =
    Tratează intrarea ca reducere și curăță intrarea HTML. Când este activat,
    descrierea va fi afișată și pe ecranul de felicitări.
    Reducerea va apărea ca text pe Anki 2.1.40 și mai jos.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    Un pachet părinte are o limită de { $cards ->
        [one] { $cards } card
        [few] { $cards } carduri
       *[other] { $cards } carduri
    }, care va/vor depăși această limită.
deck-config-reviews-too-low =
    dacă se adaugă{ $cards ->
        [one] { $cards } card nou în fiecare zi
        [few] { $cards } carduri noi în fiecare zi
       *[other] { $cards } carduri noi în fiecare zi
    }, limita ta de revizuire ar trebui să fie de cel puțin { $expected }.
deck-config-learning-step-above-graduating-interval = Intervalul de gradare ar trebui să fie cel puțin la fel de lung ca etapa finală de învățare.
deck-config-good-above-easy = Intervalul uşurinţă ar trebui să fie cel puțin la fel de lung ca intervalul de gradare.
deck-config-relearning-steps-above-minimum-interval = Intervalul minim de interval ar trebui să fie cel puțin la fel de lung ca pasul final de reînvățare.

## Selecting a deck

deck-config-which-deck = Ce pachet ai vrea?

## NO NEED TO TRANSLATE. These strings have been replaced with new versions, and will be removed in the future.

deck-config-new-card-sort-order-tooltip =
    Cum sunt sortate cardurile după ce au fost adunate. În mod implicit, Anki sortează
    mai întâi după șablonul cardului, pentru a evita mai multe arduri ale aceleiași notiţe
    să fie arătate succesiv.
deck-config-new-gather-priority-tooltip =
    ”Pachet”: adună cardurile din fiecare sub-pachet, în ordine și se oprește când 
    a fost depășită limita pachetului selectat. Acest lucru este mai rapid și îţi permite
    să prioritizezi sub-pachetele care sunt mai aproape de vârf.
    
    ”Poziție”: adună cardurile din toate pachetele înainte de a fi sortate. Aceasta
    asigură cardurilor o apariţie strictă în ordinea poziției (due #), chiar dacă limita pentru părinte nu este
    suficient de ridicată pentru a vedea cardurile din toate pachetele.
deck-config-wait-for-audio = Așteaptă audio
deck-config-show-reminder = Afișează memento
deck-config-answer-again = Răspuns încă o dată
deck-config-answer-hard = Răspuns greu
deck-config-answer-good = Răspuns bun
