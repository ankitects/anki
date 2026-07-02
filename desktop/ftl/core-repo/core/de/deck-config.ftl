### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    verwendet von { $decks ->
        [one] 1 Stapel
       *[other] { $decks } Stapeln
    }
deck-config-default-name = Standard
deck-config-title = Stapelprofile

## Daily limits section

deck-config-daily-limits = Tageshöchstwerte
deck-config-new-limit-tooltip = Die maximale Anzahl neuer Karten, die pro Tag eingeführt werden sollen. Da neue Karten kurzfristig das Arbeitspensum für Wiederholungen erhöhen, sollte der Tageshöchstwert für neue Karten mindestens zehnmal kleiner sein als der für Wiederholungskarten.
deck-config-review-limit-tooltip = Die maximale Anzahl an Wiederholungskarten, die pro Tag angezeigt werden sollen.
deck-config-limit-deck-v3 = Wenn Sie einen übergeordneten Stapel lernen (sprich einen Stapel mit Unterstapeln), legen die in den einzelnen Unterstapeln gesetzten Tageshöchstwerte fest, wie viele Karten aus jedem Unterstapel maximal eingesammelt werden. Der Tageshöchstwert des übergeordneten Stapels steuert hingegen die Gesamtanzahl der anzuzeigenden Karten aus allen Unterstapeln zusammen.
deck-config-limit-new-bound-by-reviews = Der Tageshöchstwert für Wiederholungskarten begrenzt auch die Anzahl neuer Karten, die eingeführt werden können. Wenn der Tageshöchstwert für Wiederholungskarten z. B. bei 200 liegt und 190 Wiederholungskarten anstehen, werden maximal 10 neue Karten eingeführt – selbst wenn der Tageshöchstwert für neue Karten höher ist und weitere neue Karten zur Verfügung stehen.
deck-config-limit-interday-bound-by-reviews = Der Tageshöchstwert für Wiederholungskarten wirkt sich nicht nur auf Wiederholungskarten, sondern auch auf tagesübergreifendes Lernen aus. Zuerst werden die Karten aus dem tagesübergreifenden Lernen eingesammelt, gefolgt von den Wiederholungskarten.
deck-config-tab-description =
    - `Vorgabe`: Der Tageshöchstwert gilt für alle Stapel dieses Stapelprofils, außer wenn in einem Stapel „Dieser Stapel“ oder „Nur heute“ gewählt ist.
    - `Dieser Stapel`: Der Tageshöchstwert gilt nur für diesen Stapel.
    - `Nur heute`: Ändert den Tageshöchstwert dieses Stapels nur vorübergehend für heute.
deck-config-new-cards-ignore-review-limit = Neue Karten ignorieren den Tageshöchstwert für Wiederholungskarten
deck-config-new-cards-ignore-review-limit-tooltip = Standardmäßig begrenzt der Tageshöchstwert für Wiederholungskarten auch die Anzahl neuer Karten, die eingeführt werden können. Wenn hingegen diese Einstellung aktiviert ist, werden neue Karten unabhängig vom Tageshöchstwert für Wiederholungskarten eingeführt.
deck-config-apply-all-parent-limits = Tageshöchstwerte gelten auch für Unterstapel
deck-config-apply-all-parent-limits-tooltip =
    Standardmäßig wirken sich Tageshöchstwerte der übergeordneten Stapel nicht aus, wenn Sie direkt einen Unterstapel lernen. Mit dieser Einstellung werden hingegen die Tageshöchstwerte aller übergeordneten Stapel auch beim direkten Lernen eines Unterstapels berücksichtigt.
    
    Dies ist nützlich, wenn Sie verschiedene Unterstapel nacheinander lernen und die Gesamtanzahl der angezeigten Karten aus allen Unterstapeln zusammen begrenzen möchten.
deck-config-affects-entire-collection = Wirkt sich auf die gesamte Sammlung aus.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Vorgabe
deck-config-deck-only = Dieser Stapel
deck-config-today-only = Nur heute

## New Cards section

deck-config-learning-steps = Lernstufen
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Intervalle können in Sekunden (`30s`), Minuten (`5m`), Stunden (`1h`) oder Tagen (`2d`) angegeben werden.
deck-config-learning-steps-tooltip = Ein oder mehrere Intervalle, durch Leerzeichen getrennt. Der Standardwert ist `1m 10m`. Das erste Intervall (1 Minute) wird benutzt, wenn Sie bei einer neuen Karte „Nochmal“ drücken. „Gut“ lässt die Karte voranschreiten. Das Intervall beträgt dann 10 Minuten. Nach Abschluss aller Lernstufen wird die Karte zur Wiederholungskarte und erscheint erst wieder an einem späteren Tag. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip = Anzahl der Tage, bis eine Karte wieder angezeigt wird, nachdem in der abschließenden Lernstufe „Gut“ gedrückt wurde.
deck-config-easy-interval-tooltip = Anzahl der Tage, bis eine Karte wieder angezeigt wird, nachdem der „Einfach“-Knopf verwendet wurde, um die Karte sofort aus der Lernphase zu entfernen.
deck-config-new-insertion-order = Positionsnummer
deck-config-new-insertion-order-tooltip =
    Legt die Positionsnummer fest, die neuen Karten beim Hinzufügen zugewiesen wird. Eine Änderung dieser Einstellung ändert auch die Positionsnummern bereits vorhandener neuer Karten.
    
    Ob die Positionsnummer beim Einsammeln neuer Karten berücksichtigt wird, hängt von einer separaten Einstellung ab.
deck-config-new-insertion-order-sequential = Fortlaufend
deck-config-new-insertion-order-random = Zufällig
deck-config-new-insertion-order-random-with-v3 = Es wird empfohlen, diese Einstellung auf „Der Reihe nach“ zu belassen und stattdessen die Reihenfolge für das Einsammeln neuer Karten anzupassen.

## Lapses section

deck-config-relearning-steps = Lernstufen für das Wiedererlernen
deck-config-relearning-steps-tooltip = Null oder mehr Intervalle, durch Leerzeichen getrennt. Der Standardwert ist „10m“: Wenn Sie bei einer Wiederholungskarte „Nochmal“ drücken, wird diese nach 10 Minuten erneut angezeigt. Werden keine Intervalle angegeben, ändert sich die Zeitplanung der Karte, ohne dass sie in den Status „Wiedererlernen“ übergeht. { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip = Wie oft „Nochmal“ gedrückt werden muss, bevor eine Wiederholungskarte als Lernbremse gilt. Lernbremsen sind Karten, die besonders viel Zeit in Anspruch nehmen. Wenn eine Karte als Lernbremse eingestuft wird, ist es ratsam, sie zu überarbeiten, zu löschen oder sich eine Gedächtnisstütze (Eselsbrücke) zu machen, um sich besser an sie zu erinnern.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    `Nur verschlagworten`: Füge der Notiz das Schlagwort „leech“ hinzu und zeige ein Pop-up an.
    
    `Karte ausschließen`: Zusätzlich zum Verschlagworten der Notiz wird die Karte ausgeschlossen, bis sie manuell wieder aktiviert wird.

## Burying section

deck-config-bury-title = Aufschieben
deck-config-bury-new-siblings = Geschwisterkarten mit Status „Neu“ aufschieben
deck-config-bury-review-siblings = Geschwisterkarten mit Status „Wiederholung“ aufschieben
deck-config-bury-interday-learning-siblings = Geschwisterkarten mit Status „Tagesübergreifendes Lernen“ aufschieben
deck-config-bury-new-tooltip = Ob andere `neue` Karten derselben Notiz (z. B. Karten in umgekehrter Richtung oder benachbarte Lückentexte) auf den nächsten Tag aufgeschoben werden.
deck-config-bury-review-tooltip = Ob andere `Wiederholungs`karten derselben Notiz auf den nächsten Tag aufgeschoben werden.
deck-config-bury-interday-learning-tooltip = Ob andere `Lern`karten derselben Notiz mit einem Intervall von mehr als einem Tag auf den nächsten Tag aufgeschoben werden.
deck-config-bury-priority-tooltip =
    Wenn Anki Karten einsammelt, geschieht dies in folgender Reihenfolge: Zuerst die Lernkarten im eintägigen Lernen, dann die Karten im tagesübergreifenden Lernen, gefolgt von den Wiederholungskarten und schließlich den neuen Karten. Diese Reihenfolge beeinflusst, wie das Aufschieben von Geschwisterkarten funktioniert:
    
    - Wenn das Aufschieben für alle Arten von Karten aktiviert ist, wird diejenige Geschwisterkarte angezeigt, die in der oben genannten Reihenfolge zuerst kommt. So erhält beispielsweise eine Wiederholungskarte Vorrang vor einer neuen Karte.
    - Geschwisterkarten, die weiter hinten in der Liste stehen, können weiter vorne stehende Geschwisterkarten nicht aufschieben. Wenn Sie z. B. das Aufschieben neuer Karten deaktivieren und eine neue Karte lernen, wird diese keine Karten mit dem Status „Tagesübergreifendes Lernen“ oder „Wiederholung“ aufschieben. Somit können Sie am selben Tag sowohl eine Geschwisterkarte mit dem Status „Wiederholung“ als auch eine mit dem Status „Neu“ sehen.

## Gather order and sort order of cards

deck-config-ordering-title = Anzeigereihenfolge
deck-config-new-gather-priority = Einsammelreihenfolge für neue Karten
deck-config-new-gather-priority-tooltip-2 =
    `Stapel`: Sammelt Karten aus jedem Unterstapel der Reihe nach ein, beginnend beim obersten. Innerhalb der Unterstapel erfolgt das Einsammeln in aufsteigender Positionsnummer. Wird der Tageshöchstwert des ausgewählten Stapels erreicht, stoppt das Einsammeln, bevor alle Unterstapel durchlaufen sind. Dieses Vorgehen ist bei großen Sammlungen besonders schnell und priorisiert Unterstapel, die weiter oben stehen.
    
    `Positionsnummer (aufsteigend)`: Sammelt Karten nach Positionsnummer in aufsteigender Reihenfolge ein. Typischerweise bedeutet das, die älteste hinzugefügte Karte zuerst.
    
    `Positionsnummer (absteigend)`: Sammelt Karten nach Positionsnummer in absteigender Reihenfolge ein. Typischerweise bedeutet das, die zuletzt hinzugefügte Karte zuerst.
    
    `Zufällige Notizen`: Wählt Notizen zufällig aus und sammelt dann jeweils alle dazugehörigen Karten in einem Durchgang ein.
    
    `Zufällige Karten`: Sammelt Karten in zufälliger Reihenfolge ein.
deck-config-new-card-sort-order = Sortierreihenfolge für neue Karten
deck-config-new-card-sort-order-tooltip-2 =
    `Kartentyp, dann Einsammelreihenfolge`: Zeigt Karten geordnet nach Kartentyp an. Karten desselben Kartentyps werden in der Reihenfolge angezeigt, in der sie eingesammelt wurden. Wenn das Aufschieben von Geschwisterkarten deaktiviert ist, stellt dies sicher, dass alle „Vorderseite → Rückseite“-Karten vor allen „Rückseite → Vorderseite“-Karten angezeigt werden. Dieses Verfahren ist nützlich, um alle Karten einer Notiz in derselben Sitzung anzuzeigen, aber nicht zu kurz hintereinander.
    
    `Einsammelreihenfolge`: Zeigt Karten in der Reihenfolge, in der sie eingesammelt wurden. Wenn das Aufschieben von Geschwisterkarten deaktiviert ist, wird dies typischerweise dazu führen, dass alle Karten einer Notiz direkt hintereinander angezeigt werden.
    
    `Kartentyp, dann zufällig`:  Zeigt Karten geordnet nach Kartentyp an. Karten desselben Kartentyps werden in zufälliger Reihenfolge angezeigt.  Dieses Verfahren ist nützlich, wenn Sie nicht möchten, dass Geschwisterkarten zu kurz hintereinander erscheinen, aber dennoch die Karten in zufälliger Reihenfolge sehen möchten.
    
    `Zufällige Notiz, dann Kartentyp`: Wählt Notizen zufällig aus und zeigt dann jeweils alle Karten dieser Notiz nach Kartentyp geordnet an.
    
    `Zufällig`: Zeigt Karten in zufälliger Reihenfolge an.
deck-config-new-review-priority = Wann neue Karten anzeigen
deck-config-new-review-priority-tooltip = Wann neue Karten im Verhältnis zu Wiederholungskarten angezeigt werden.
deck-config-interday-step-priority = Wann Karten mit Status „tagesübergreifenden Lernen” anzeigen
deck-config-interday-step-priority-tooltip =
    Wann Lernkarten und Wiedererlernkarten angezeigt werden, die die Tagesgrenze überschreiten.
    
    Der Tageshöchstwert für Wiederholungskarten wird immer zuerst auf Karten im „tagesübergreifenden Lernen” angewendet, und danach auf die Wiederholungskarten. Diese Einstellung steuert die Reihenfolge, in der die eingesammelten Karten angezeigt werden, jedoch werden Karten im „tagesübergreifenden Lernen“ immer zuerst eingesammelt.
deck-config-review-sort-order = Sortierreihenfolge für Wiederholungskarten
deck-config-review-sort-order-tooltip = Die Standardsortierreihenfolge zeigt zuerst die Karten an, die am längsten auf ihre Wiederholung warten. Bei einem sehr großen Rückstand, dessen Abarbeitung viele Tage dauern würde, oder wenn Sie die Karten in der Reihenfolge der Unterstapel anzeigen möchten, könnte eine andere Sortierreihenfolge sinnvoller sein.
deck-config-display-order-will-use-current-deck = Anki berücksichtigt ausschließlich die Anzeigereihenfolge des gewählten Stapels, nicht jedoch die der Unterstapel.

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = Stapel
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = Zuerst Stapel, dann zufällige Notizen
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = Positionsnummer (aufsteigend)
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = Positionsnummer (absteigend)
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = Zufällige Notizen
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = Zufällige Karten
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = Kartentyp, dann zufällig
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = Zufällige Notiz, dann Kartentyp
# Sort the cards randomly.
deck-config-sort-order-random = Zufällig
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = Kartentyp, dann Einsammelreihenfolge
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = Einsammelreihenfolge
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = Mit Wiederholungskarten mischen
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = Nach Wiederholungskarten anzeigen
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = Vor Wiederholungskarten anzeigen
# Sort the cards first by due date, in ascending order (oldest due date to newest), then randomly within the same due date.
deck-config-sort-order-due-date-then-random = Fälligkeitsdatum, dann zufällig
# Sort the cards first by due date, in ascending order (oldest due date to newest), then by deck within the same due date.
deck-config-sort-order-due-date-then-deck = Fälligkeitsdatum, dann Stapel
# Sort the cards first by deck, then by due date in ascending order (oldest due date to newest) within the same deck.
deck-config-sort-order-deck-then-due-date = Stapel, dann Fälligkeitsdatum
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = Intervall (aufsteigend)
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = Intervall (absteigend)
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = Leichtigkeitsgrad (aufsteigend)
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = Leichtigkeitsgrad (absteigend)
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = Schwierigkeitsgrad (aufsteigend)
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = Schwierigkeitsgrad (absteigend)
# Sort the cards by retrievability percentage, in ascending order (0% to 100%, least retrievable to most easily retrievable).
deck-config-sort-order-retrievability-ascending = Abrufbarkeit (aufsteigend)
# Sort the cards by retrievability percentage, in descending order (100% to 0%, most easily retrievable to least retrievable).
deck-config-sort-order-retrievability-descending = Abrufbarkeit (absteigend)

## Timer section

deck-config-timer-title = Timer
deck-config-maximum-answer-secs = Ignoriere Antwortzeiten länger als (Sekunden)
deck-config-maximum-answer-secs-tooltip = Die maximale Zeit, die für eine einzelne Wiederholung in der Statistik erfasst wird. Wenn die tatsächliche Antwortzeit diesen Wert überschreitet (z. B. weil Sie den Bildschirm verlassen haben), wird statt der tatsächlichen Zeit die eingestellte maximale Antwortzeit in der Statistik gespeichert.
deck-config-show-answer-timer-tooltip = Beim Lernbildschirm einen Bildschirm-Timer anzeigen, welcher die Zeit zählt, die Sie zum Lernen einer Karte benötigen.
deck-config-stop-timer-on-answer = Bildschirm-Timer pausieren, während die Antwort aufgedeckt ist
deck-config-stop-timer-on-answer-tooltip = Ob der Bildschirm-Timer angehalten werden soll, wenn die Antwort aufgedeckt wird. Wirkt sich nicht auf die Statistiken aus.

## Auto Advance section

deck-config-seconds-to-show-question = Anzeigedauer der Frage (Sekunden)
deck-config-seconds-to-show-question-tooltip-3 = Anzahl der Sekunden, die gewartet werden, bevor die Frageaktion angewendet wird. Setzen Sie den Wert auf 0, um die Funktion zu deaktivieren.
deck-config-seconds-to-show-answer = Anzeigedauer der Antwort (Sekunden)
deck-config-seconds-to-show-answer-tooltip-2 = Wenn Automatisches Aufdecken eingeschaltet ist, die Anzahl der Sekunden, die gewartet wird, bevor die Antwortaktion ausgeführt wird. Zum Ausschalten auf 0 setzen.
deck-config-question-action-show-answer = Antwort anzeigen
deck-config-question-action-show-reminder = Erinnerung anzeigen
deck-config-question-action = Frageaktion
deck-config-question-action-tool-tip = Die Aktion, die ausgeführt werden soll, nachdem die Frage angezeigt und die eingestellte Zeit überschritten wurde.
deck-config-answer-action = Antwortaktion
deck-config-answer-action-tooltip-2 = Die Aktion, die ausgeführt werden soll, nachdem die Antwort angezeigt und die eingestellte Zeit überschritten wurde.
deck-config-wait-for-audio-tooltip-2 = Das Ende des Audios abwarten, bevor die Frage- oder Antwortaktion angewendet wird.

## Audio section

deck-config-audio-title = Audio
deck-config-disable-autoplay = Audio nicht automatisch abspielen
deck-config-disable-autoplay-tooltip = Wenn aktiviert, wird Anki die Audiodateien nicht automatisch abspielen. Sie können sie jedoch manuell abspielen, indem Sie auf das Audio-Symbol klicken/tippen oder die Aktion „Erneut abspielen“ verwenden.
deck-config-skip-question-when-replaying = Beim erneuten Abspielen der Antwort die Frage nicht abspielen
deck-config-always-include-question-audio-tooltip = Ob auch das Audio auf der Frageseite abgespielt werden soll, wenn die Aktion „Erneut abspielen“ ausgelöst wird, während bereits die Antwortseite einer Karte angezeigt wird.

## Advanced section

deck-config-advanced-title = Erweitert
deck-config-maximum-interval-tooltip = Die maximale Anzahl an Tagen, die der Zeitplaner als Intervall für Wiederholungskarten festlegen kann. `Schwer`, `Gut` und `Einfach` führen nie zu einem längeren Intervall als diesem Wert. Ein niedrigerer Wert erhöht das Arbeitspensum.
deck-config-starting-ease-tooltip = Der Anfangswert des Leichtigkeitsgrads (Faktors) für neue Karten. Standardmäßig sorgt der „Gut“-Knopf bei einer neu gelernten Karte dafür, dass das nächste Intervall 2,5-mal so lang ist wie das vorherige.
deck-config-easy-bonus-tooltip = Wenn bei einer Wiederholungskarte „Einfach“ gewählt wird, wird ein Intervall verwendet, das dem für „Gut“ entspricht, jedoch mit diesem Zusatzfaktor multipliziert wird. Beim Standardwert von 1,30 ist das Intervall für „Einfach“ um 30% länger als das für „Gut“. Liegt das „Gut“-Intervall beispielsweise bei 10 Tagen, beträgt das „Einfach“-Intervall 13 Tage.
deck-config-interval-modifier-tooltip = Dieser Faktor wird auf alle Intervalle angewendet, und durch kleinere Anpassungen kann Anki in seiner Zeitplanung konservativer oder aggressiver eingestellt werden. Bitte lesen Sie das Handbuch, bevor Sie diese Einstellung ändern.
deck-config-hard-interval-tooltip = Der Faktor, der bei Wiederholungskarten angewendet wird, wenn eine Karte mit „Schwer“ bewertet wird. Der Wert bezieht sich auf das vorherige Intervall. Mit dem Standardwert von 1,20 würde eine Karte mit einem 10-Tage-Intervall ein neues Intervall von 12 Tagen erhalten.
deck-config-new-interval-tooltip = Der auf das Wiederholungsintervall angewendete Faktor, nachdem eine Karte mit „Nochmal“ bewertet wurde.
deck-config-minimum-interval-tooltip = Gibt die Mindestanzahl an Tagen für das Intervall an, das einer Wiederholungskarte nach dem Abschluss des Wiederlernvorgangs zugewiesen werden soll. Der Standardwert ist 1 Tag, was bedeutet, dass die Karte nach Abschluss des Wiederlernens am nächsten Tag erneut angezeigt wird.
deck-config-custom-scheduling = Benutzerdefinierte Zeitplanung
deck-config-custom-scheduling-tooltip = Wirkt sich auf die gesamte Sammlung aus. Nutzung auf eigene Gefahr!

## Easy Days section.

deck-config-easy-days-title = Tage mit weniger Wiederholungen
deck-config-easy-days-monday = Mo.
deck-config-easy-days-tuesday = Di.
deck-config-easy-days-wednesday = Mi.
deck-config-easy-days-thursday = Do.
deck-config-easy-days-friday = Fr.
deck-config-easy-days-saturday = Sa.
deck-config-easy-days-sunday = So.
deck-config-easy-days-normal = Nor­mal
deck-config-easy-days-reduced = Redu­ziert
deck-config-easy-days-minimum = Mini­mum
deck-config-easy-days-no-normal-days = Mindestens ein Tag sollte auf „{ deck-config-easy-days-normal }“ eingestellt sein.
deck-config-easy-days-change = Bereits vorhandene Wiederholungen werden nicht umgeplant, außer "{ deck-config-reschedule-cards-on-change }" ist in den FSRS-Einstellungen aktiviert.

## Adding/renaming

deck-config-add-group = Stapelprofil hinzufügen
deck-config-name-prompt = Name
deck-config-rename-group = Stapelprofil umbenennen
deck-config-clone-group = Stapelprofil duplizieren

## Removing

deck-config-remove-group = Stapelprofil entfernen
deck-config-will-require-full-sync = Die angeforderte Änderung erfordert eine Einweg-Synchronisierung. Wenn Sie auf einem anderen Gerät Änderungen vorgenommen haben und diese noch nicht mit diesem Gerät synchronisiert wurden, tun Sie dies bitte, bevor Sie fortfahren.
deck-config-confirm-remove-name = „{ $name }“ entfernen?

## Other Buttons

deck-config-save-button = Speichern
deck-config-save-to-all-subdecks = Auf alle Unterstapel anwenden
deck-config-save-and-optimize = Alle Stapelprofile optimieren
deck-config-revert-button-tooltip = Diese Einstellung auf den Standardwert zurücksetzen.

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Neues Verhalten ab Anki 2.1.41 verwenden
deck-config-description-new-handling-hint = Behandelt Eingaben als Markdown und bereinigt HTML-Eingaben. Wenn aktiviert, wird die Beschreibung auch auf der Gratulationsseite angezeigt. Markdown wird in Anki-Version 2.1.40 und älter als Text angezeigt.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    { $cards ->
        [one] Ein übergeordneter Stapel hat einen Tageshöchstwert von { $cards } Karte, welcher diesen Tageshöchstwert überschreiben wird.
       *[other] Ein übergeordneter Stapel hat einen Tageshöchstwert von { $cards } Karten, welcher diesen Tageshöchstwert überschreiben wird.
    }
deck-config-reviews-too-low =
    { $cards ->
        [one] Wenn jeden Tag { $cards } neue Karte gelernt wird, dann sollte Ihr Tageshöchstwert für Wiederholungskarten mindestens { $expected } betragen.
       *[other] Wenn jeden Tag { $cards } neue Karten gelernt werden, dann sollte Ihr Tageshöchstwert für Wiederholungskarten mindestens { $expected } betragen.
    }
deck-config-learning-step-above-graduating-interval = Das Aufstiegsintervall sollte mindestens so lang sein wie die abschließende Lernstufe.
deck-config-good-above-easy = Das Intervall für einfache Karten sollte mindestens so lang sein wie das Aufstiegsintervall.
deck-config-relearning-steps-above-minimum-interval = Das Mindestintervall sollte mindestens so lang sein wie die abschließende Lernstufe für das Wiedererlernen.
deck-config-maximum-answer-secs-above-recommended = Die Zeitplanung funktioniert besser, wenn die Antwortzeit kürzer ist.
deck-config-too-short-maximum-interval = Ein Höchstintervall von weniger als 6 Monaten wird nicht empfohlen.
deck-config-ignore-before-info = Rund { $included } von insgesamt { $totalCards } werden zur Optimierung der FSRS-Parameter verwendet.

## Selecting a deck

deck-config-which-deck = Für welchen Stapel möchten Sie die Einstellungen anzeigen?

## Messages related to the FSRS scheduler

deck-config-updating-cards = Kartenaktualisierung: { $current_cards_count }/{ $total_cards_count } …
deck-config-invalid-parameters = Die angegebenen FSRS-Parameter sind ungültig. Lassen Sie das Feld leer, um die Standardparameter zu verwenden.
deck-config-not-enough-history = Zu wenige Wiederholungen um diese Aktion durchzuführen.
deck-config-must-have-400-reviews =
    { $count ->
        [one] Es wurde nur { $count } Wiederholung gefunden. Für diesen Vorgang sind mindestens 400 Wiederholungen erforderlich.
       *[other] Es wurden nur { $count } Wiederholungen gefunden. Für diesen Vorgang sind mindestens 400 Wiederholungen erforderlich.
    }
# Numbers that control how aggressively the FSRS algorithm schedules cards
deck-config-weights = FSRS-Parameter
deck-config-compute-optimal-weights = Optimierung von FSRS-Parametern
deck-config-optimize-button = Dieses Stapelprofil optimieren
# Indicates that a given function or label, provided via the "text" variable, operates slowly.
deck-config-slow-suffix = { $text } (langsam)
deck-config-compute-button = Berechnen
deck-config-ignore-before = Wiederholungen vor diesem Datum ignorieren
deck-config-time-to-optimize = Die letzte Optimierung ist eine Weile her – es wird empfohlen, den Knopf „Alle Stapelprofile optimieren“ zu verwenden.
deck-config-evaluate-button = Evaluieren
deck-config-desired-retention = Gewünschte Erinnerungsquote
deck-config-historical-retention = Frühere Erinnerungsquote
deck-config-smaller-is-better = Je kleiner die Zahlen, desto besser passen die Parameter zu Ihrem Wiederholungsverlauf.
deck-config-steps-too-large-for-fsrs = Wenn FSRS aktiviert ist, sind Lernstufen von 1 Tag oder mehr nicht empfohlen.
deck-config-get-params = Parameter abrufen
deck-config-complete = { $num }% abgeschlossen.
deck-config-iterations = Wiederholungszyklus: { $count } …
deck-config-reschedule-cards-on-change = Bei Änderungen alle Karten umplanen
deck-config-fsrs-tooltip =
    Wirkt sich auf die gesamte Sammlung aus.
    
    FSRS (Free Spaced Repetition Scheduler, „Freier Zeitplaner für verteile Wiederholung“) ist eine Alternative zum klassischem SM‑2 (SuperMemo 2) von Anki. FSRS prognostiziert genauer, wie wahrscheinlich es ist, dass Sie eine Karte zu einem bestimmten Zeitpunkt vergessen, und ermöglicht Ihnen so, bei gleichem Zeitaufwand mehr zu lernen.
deck-config-desired-retention-tooltip =
    Standardmäßig legt Anki die Zeitplanung so fest, dass Sie sich an 90% der Karten erinnern, die zur Wiederholung anstehen.
    
    Wenn Sie diesen Wert erhöhen, wird Anki die Karten häufiger anzeigen, um die Wahrscheinlichkeit zu steigern, dass Sie sich daran erinnern. Reduzieren Sie den Wert, zeigt Anki die Karten seltener, was dazu führt, dass Sie mehr vergessen.
    
    Ändern Sie diesen Wert mit Bedacht: Ein hoher Wert erhöht Ihr Arbeitspensum deutlich, während ein niedriger Wert entmutigend wirken kann, weil Sie Karten häufiger vergessen.
deck-config-desired-retention-tooltip2 = Die Werte im Infofeld stellen lediglich eine grobe Schätzung des Arbeitspensums dar. Für eine genauere Berechnung kann der Simulator verwendet werden.
deck-config-historical-retention-tooltip =
    Wenn ein Teil des Wiederholungsverlaufs fehlt, muss FSRS eine Schätzung vornehmen. Standardmäßig wird angenommen, dass Sie sich bei den alten Wiederholungen an 90% der Karten erinnert haben. War die tatsächliche Erinnerungsquote jedoch deutlich höher oder niedriger als 90%, ermöglicht die Anpassung dieses Werts FSRS eine genauere Schätzung des fehlenden Wiederholungsverlaufs.
    
    Ihr Wiederholungsverlauf kann aus folgenden Gründen unvollständig sein:
    1. Weil Sie die Einstellung 'Wiederholungen vor diesem Datum ignorieren' nutzen.
    2. Weil Sie den Wiederholungsverlauf gelöscht haben, um Speicherplatz freizugeben.
    3. Weil Sie Material aus einem anderen SRS-Programm importiert haben.
    
    Die beiden letzten Gründe sind eher selten. Daher ist eine Anpassung dieses Werts wahrscheinlich nicht nötig, es sei denn, Sie haben die erstgenannte Einstellung genutzt.
deck-config-weights-tooltip2 = FSRS-Parameter beeinflussen die Zeitplanung der Karten. Anki beginnt mit Standardparametern. Sie können die untenstehende Funktion verwenden, um die Parameter so zu optimieren, dass sie am besten zu Ihrer Leistung in Stapeln mit diesem Stapelprofil passen.
deck-config-reschedule-cards-on-change-tooltip =
    Wirkt sich auf die gesamte Sammlung aus und wird nicht gespeichert.
    
    Diese Einstellung legt fest, ob die Fälligkeitsdaten von Karten angepasst werden, wenn FSRS aktiviert oder dessen Parameter geändert werden.
    
    Standardmäßig werden die Karten nicht umgeplant. Die neue Zeitplanung greift erst bei zukünftigen Wiederholungen, sodass sich das aktuelle Arbeitspensum nicht sofort ändert. Wenn die Umplanung aktiviert ist, werden die Fälligkeitsdaten aller Karten jedoch sofort angepasst.
deck-config-reschedule-cards-warning =
    Je nach gewünschter Erinnerungsquote kann diese Einstellung dazu führen, dass viele Karten sofort fällig werden. Daher ist sie nicht zu empfehlen, wenn Sie gerade von SM-2 zu FSRS wechseln.
    
    Verwenden Sie diese Einstellung mit Bedacht, da sie bei jeder Karte einen zusätzlichen Wiederholungseintrag erzeugt und den benötigten Speicherplatz erhöht.
deck-config-ignore-before-tooltip-2 = Karten, die vor dem angegebenen Datum wiederholt wurden, werden bei der Optimierung der FSRS-Parameter ignoriert. Dies kann hilfreich sein, wenn Sie die Zeitplanung von jemand anderem importiert haben oder die Art und Weise, wie Sie die Bewertungsknöpfe verwenden, geändert haben.
deck-config-compute-optimal-weights-tooltip2 =
    Wenn Sie auf „Optimieren“ klicken, analysiert FSRS Ihren Wiederholungsverlauf und ermittelt Parameter, die optimal auf Ihr Gedächtnis und die Inhalte abgestimmt sind, die Sie lernen.
    
    Für Stapel mit stark unterschiedlichen subjektiven Schwierigkeitsgraden empfiehlt es sich, separate Stapelprofile anzulegen. So können die Parameter besser an den jeweiligen Schwierigkeitsgrad angepasst werden.
    
    Es ist nicht nötig, die Parameter häufig zu optimieren – einmal alle paar Monate genügt.
    
    Standardmäßig werden die Parameter basierend auf dem Wiederholungsverlauf aller Stapel ermittelt, die das aktuelle Stapelprofil nutzen. Sie können vor der Berechnung die Suchkriterien anpassen, um festzulegen, welche Karten zur Optimierung der Parameter herangezogen werden sollen.
deck-config-please-save-your-changes-first = Bitte speichern Sie zuerst Ihre Änderungen.
deck-config-workload-factor-change = Das Arbeitspensum beträgt etwa { $factor } mal so viel wie bei einer gewünschten Erinnerungsquote von { $previousDR }%.
deck-config-workload-factor-unchanged = Je höher dieser Wert, desto öfter werden die Karten gezeigt.
deck-config-desired-retention-too-low = Die gewünschte Erinnerungsquote ist sehr niedrig. Das kann zu sehr langen Intervallen führen.
deck-config-desired-retention-too-high = Die gewünschte Erinnerungsquote ist sehr hoch. Das kann zu sehr kurzen Intervallen führen.
deck-config-percent-of-reviews =
    { $reviews ->
        [one] { $pct }% von { $reviews } Wiederholung
       *[other] { $pct }% von { $reviews } Wiederholungen
    }
deck-config-percent-input = { $pct }%
# This message appears during FSRS parameter optimization.
deck-config-checking-for-improvement = Optimieren …
deck-config-optimizing-preset = Optimiere Stapelprofil { $current_count }/{ $total_count } …
deck-config-fsrs-must-be-enabled = FSRS muss zunächst aktiviert werden.
deck-config-fsrs-params-optimal = Die FSRS-Parameter sind bereits optimal.
deck-config-fsrs-params-no-reviews = Keine Wiederholungen gefunden. Bitte prüfen Sie, ob dieses Stapelprofil allen Stapeln und Unterstapeln zugewiesen ist, die Sie optimieren möchten und versuchen Sie es anschließend erneut.
deck-config-wait-for-audio = Auf Audio warten
deck-config-show-reminder = Erinnerung anzeigen
deck-config-answer-again = Mit „Nochmal“ bewerten
deck-config-answer-hard = Mit „Schwer“ bewerten
deck-config-answer-good = Mit „Gut“ bewerten
deck-config-days-to-simulate = Zu simulierende Tage
deck-config-desired-retention-below-optimal = Ihre gewünschte Erinnerungsquote liegt unter dem empfohlenen Mindestwert. Es ist ratsam, sie zu erhöhen.
# Description of the y axis in the FSRS simulation
# diagram (Deck options -> FSRS) showing the total number of
# cards that can be recalled or retrieved on a specific date.
deck-config-fsrs-simulator-experimental = FSRS-Simulator (experimentell)
deck-config-fsrs-simulate-desired-retention-experimental = FSRS-Simulator für die gewünschte Erinnerungsquote (experimentell)
deck-config-fsrs-simulate-save-preset = Das Stapelprofil wurde optimiert. Es muss vor dem Start des Simulators gespeichert werden.
deck-config-fsrs-desired-retention-help-me-decide-experimental = Entscheidungshilfe (experimentell)
deck-config-additional-new-cards-to-simulate = Zusätzliche fiktive neue Karten
deck-config-simulate = Simulieren
deck-config-clear-last-simulate = Letzte Simulation löschen
deck-config-fsrs-simulator-radio-count = Wiederholungen
deck-config-advanced-settings = Erweitert
deck-config-smooth-graph = Kurve glätten
deck-config-suspend-leeches = Lernbremsen dauerhaft ausschließen
deck-config-save-options-to-preset = Einstellungen ins Stapelprofil übertragen
deck-config-save-options-to-preset-confirm = Die Einstellungen im Stapelprofil mit den derzeit im Simulator gesetzten Einstellungen überschreiben?
# Radio button in the FSRS simulation diagram (Deck options -> FSRS) selecting
# to show the total number of cards that can be recalled or retrieved on a
# specific date.
deck-config-fsrs-simulator-radio-memorized = Abrufbare Karten
deck-config-fsrs-simulator-radio-ratio = Zeitaufwand pro abrufbarer Karte
# $time here is pre-formatted e.g. "10 Seconds" 
deck-config-fsrs-simulator-ratio-tooltip = { $time } pro abrufbarer Karte

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.

# Checkbox
deck-config-health-check = Beim Optimieren zusätzlich prüfen, ob sich FSRS gut an Ihr Gedächtnis angepasst hat
# Message box showing the result of the health check
deck-config-fsrs-bad-fit-warning =
    FSRS hat Schwierigkeiten, sich an Ihr Gedächtnis anzupassen. Empfehlungen:
    
    - Formulieren Sie Lernbremsen um oder schließen Sie sie aus.
    - Nutzen Sie die Antworttasten stets auf die gleiche Weise. Denken Sie daran: Nur „Nochmal“ gilt als Fehlversuch – „Schwer“, „Gut“ und „Einfach“ gelten alle als bestanden.
    - Wiederholen Sie Inhalte erst mit Anki, nachdem Sie sie verstanden haben.
    
    Wenn Sie diese Empfehlungen umsetzen, wird sich FSRS im Laufe der nächsten Monate besser an Ihr Gedächtnis anpassen.
# Message box showing the result of the health check
deck-config-fsrs-good-fit = FSRS hat sich gut an Ihr Gedächtnis angepasst.

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-unable-to-determine-desired-retention = Bestimmung des empfohlenen Mindestwerts für die Erinnerungsquote nicht möglich.
deck-config-predicted-minimum-recommended-retention = Empfohlener Mindestwert für die Erinnerungsquote: { $num }
deck-config-compute-minimum-recommended-retention = Empfohlener Mindestwert für die Erinnerungsquote
deck-config-compute-optimal-retention-tooltip4 =
    Ermittelt die Erinnerungsquote, bei der der Zeitaufwand im Verhältnis zum Lernerfolg am geringsten ist.
    
    Dieser Wert hilft Ihnen bei der Wahl Ihrer gewünschten Erinnerungsquote. Sie können einen höheren Wert wählen, wenn Sie bereit sind, dafür mehr Zeit zu investieren. Es ist jedoch nicht sinnvoll, einen niedrigeren Wert zu wählen, da dies den Zeitaufwand durch eine erhöhte Vergessensrate ebenfalls vergrößern würde.
deck-config-plotted-on-x-axis = (siehe x-Achse)
deck-config-a-100-day-interval =
    { $days ->
        [one] Ein Intervall von 100 Tagen wird zu { $days } Tag.
       *[other] Ein Intervall von 100 Tagen wird zu { $days } Tagen.
    }
deck-config-fsrs-simulator-y-axis-title-time = Zeitaufwand pro Tag
deck-config-fsrs-simulator-y-axis-title-count = Anzahl Wiederholungen pro Tag
deck-config-fsrs-simulator-y-axis-title-memorized = Abrufbare Karten
deck-config-bury-siblings = Geschwisterkarten aufschieben
deck-config-do-not-bury = Geschwisterkarten nicht aufschieben
deck-config-bury-if-new = Aufschieben wenn neu
deck-config-bury-if-new-or-review = Aufschieben, wenn Karte neu oder zum Wiederholen
deck-config-bury-if-new-review-or-interday = Aufschieben, wenn Karte neu, zum Wiederholen oder zum „tagesübergreifenden Lernen“
deck-config-bury-tooltip =
    Geschwisterkarten sind andere Karten derselben Notiz (z. B. Karten in umgekehrter Abfragerichtung oder Lückentextkarten zum gleichen Inhalt).
    
    Ist diese Einstellung deaktiviert, können mehrere Karten derselben Notiz am selben Tag angezeigt werden. Bei aktivierter Einstellung wird Anki Geschwisterkarten automatisch bis zum nächsten Tag *aufschieben*. Zudem können Sie festlegen, welche Arten von Karten aufgeschoben werden sollen, wenn Sie eine ihrer Geschwisterkarten sehen.
    
    Mit dem V3-Zeitplaner werden auch Karten mit dem Status „tagesübergreifendes Lernen“ aufgeschoben. Dabei handelt es sich um Karten, deren aktuelle Lernstufe ein oder mehrere Tage umfasst.
deck-config-seconds-to-show-question-tooltip = Anzahl der Sekunden, die gewartet werden, bevor die Antwort angezeigt wird. Setzen Sie den Wert auf 0, um die Funktion zu deaktivieren.
deck-config-answer-action-tooltip = Die Aktion, die ausgeführt werden soll, bevor automatisch zur nächsten Karte gewechselt wird.
deck-config-wait-for-audio-tooltip = Auf das Ende des Audios warten, bevor automatisch die Antwort/nächste Frage gezeigt wird.
deck-config-ignore-before-tooltip = Karten, die vor dem angegebenen Datum wiederholt wurden, werden bei der Optimierung und Auswertung der FSRS-Parameter ignoriert. Dies kann hilfreich sein, wenn Sie die Zeitplanung von jemand anderem importiert haben oder die Art und Weise, wie Sie die Antwortknöpfe verwenden, geändert haben.
deck-config-compute-optimal-retention-tooltip = Dieses Werkzeug geht davon aus, dass Sie mit 0 Karten beginnen, und schätzt die Menge an Karten, die Sie im vorgegebenen Zeitrahmen zu behalten vermögen. Die ermittelte Erinnerungsquote hängt stark von Ihren Eingaben ab, und wenn sie deutlich von 0,9 abweicht, ist das ein Zeichen dafür, dass die Zeit, die Sie pro Tag eingeplant haben, entweder zu niedrig oder zu hoch für die Menge an Karten ist, die Sie zu lernen versuchen. Diese Zahl kann als Referenz nützlich sein, aber es ist nicht empfehlenswert, sie in das Feld für die gewünschte Erinnerungsquote zu kopieren.
deck-config-health-check-tooltip1 = Prüft, ob sich FSRS gut an Ihr Gedächtnis angepasst hat.
deck-config-health-check-tooltip2 = Die Prüfung erfolgt nur, wenn die Funktion „Dieses Stapelprofil optimieren“ verwendet wird.
deck-config-compute-optimal-retention = Empfohlener Mindestwert für die Erinnerungsquote
deck-config-predicted-optimal-retention = Empfohlener Mindestwert für die Erinnerungsquote: { $num }
deck-config-weights-tooltip = Die FSRS-Parameter beeinflussen die Zeitplanung der Karten. Anki beginnt mit den Standardeinstellungen. Sobald Sie mehr als 1000 Wiederholungen gesammelt haben, können Sie die untenstehende Einstellung nutzen, um die Parameter so zu optimieren, dass sie Ihren Leistungen in den Stapeln mit dieser Stapelprofil entsprechen.
deck-config-compute-optimal-weights-tooltip =
    Sobald Sie mehr als tausend Wiederholungen in Anki gemacht haben, können Sie auf Optimieren klicken, um Ihren Wiederholungsverlauf zu analysieren, und automatisch Einstellungen generieren, die für Ihr Gedächtnis und die Inhalte, die Sie lernen, optimal sind. Wenn Sie Stapel mit sehr unterschiedlichen Schwierigkeitsgraden haben, empfiehlt es sich, ihnen separate Stapelprofile zuzuweisen, da die Einstellungen für einfache Stapel und schwere Stapel unterschiedlich sind. Es besteht keine Notwendigkeit, Ihre Einstellungen häufig zu optimieren - einmal alle paar Monate ist ausreichend.
    
    Standardmäßig werden die Einstellungen aus dem Wiederholungsverlauf aller Stapel berechnet, die das aktuelle Stapelprofil verwenden. Sie können optional die Suche vor der Berechnung der Einstellungen anpassen, wenn Sie ändern möchten, welche Karten für die Optimierung der Einstellungen verwendet werden.
deck-config-compute-optimal-retention-tooltip2 =
    Diese Funktion geht davon aus, dass Sie mit 0 gelernten Karten beginnen, und ermittelt die Erinnerungsquote, bei der der Zeitaufwand im Verhältnis zum Lernerfolg am geringsten ist.
    
    Dieser Wert hilft Ihnen bei der Wahl Ihrer gewünschten Erinnerungsquote. Sie können einen höheren Wert wählen, wenn Sie bereit sind, dafür mehr Zeit zu investieren. Es ist jedoch nicht sinnvoll, einen niedrigeren Wert zu wählen, da dies den Zeitaufwand durch eine erhöhte Vergessensrate ebenfalls vergrößern würde.
deck-config-compute-optimal-retention-tooltip3 =
    Diese Funktion geht davon aus, dass Sie mit 0 gelernten Karten beginnen und ermittelt die Erinnerungsquote, bei der der Zeitaufwand im Verhältnis zum Lernerfolg am geringsten ist.
    
    Für eine präzise Simulation Ihres Lernfortschritts sind mindestens 400 Wiederholungen erforderlich.
    
    Dieser Wert hilft Ihnen bei der Wahl Ihrer gewünschten Erinnerungsquote. Sie können einen höheren Wert wählen, wenn Sie bereit sind, dafür mehr Zeit zu investieren. Es ist jedoch nicht sinnvoll, einen niedrigeren Wert zu wählen, da dies den Zeitaufwand durch eine erhöhte Vergessensrate ebenfalls vergrößern würde.
deck-config-seconds-to-show-question-tooltip-2 = Wenn Automatisches Aufdecken eingeschaltet ist, die Anzahl der Sekunden, die gewartet wird, bevor die Antwort angezeigt wird. Zum Ausschalten auf 0 setzen.
deck-config-invalid-weights = Die Parameter müssen entweder leer gelassen werden, um die Standardwerte zu verwenden, oder sie müssen aus 17 durch Kommata getrennten Zahlen bestehen.
deck-config-fsrs-on-all-clients = FSRS funktioniert nur richtig, wenn alle genutzten Apps die Mindestanforderungen erfüllen (Anki und AnkiMobile ab 23.10, AnkiDroid ab 2.17).
deck-config-optimize-all-tip = Sie können alle Stapelprofile gleichzeitig optimieren, indem Sie den Dropdown-Knopf neben „Speichern“ verwenden.
