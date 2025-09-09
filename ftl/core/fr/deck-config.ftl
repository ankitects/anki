### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    utilisé par { $decks ->
        [one] { $decks } paquet
       *[other] { $decks } paquets
    }
deck-config-default-name = Par défaut
deck-config-title = Options du paquet

## Daily limits section

deck-config-daily-limits = Limites journalières
deck-config-new-limit-tooltip =
    Le nombre maximal de nouvelles cartes pouvant être introduites par jour (si de nouvelles cartes sont disponibles).
    Des cartes inédites peuvent augmenter votre charge de travail à court-terme, donc vous devriez éviter d'en introduire chaque jour plus de 10% de votre quota de révisions.
deck-config-review-limit-tooltip =
    Le nombre maximal de cartes déjà vues pouvant être révisées par jour,
    si des cartes doivent être révisées.
deck-config-limit-deck-v3 = Quand vous étudiez un paquet qui a des sous-paquets, les limites de chaque sous-paquet fixent le nombre maximal de cartes tirées de ce paquet. Les limites du paquet sélectionné fixent le nombre total de cartes qui seront montrées.
deck-config-limit-new-bound-by-reviews =
    La limite de révision influence la nouvelle limite. Par exemple, si la limite de révision est
    fixée à 200, et que vous avez 190 révisions en attente, un maximum de 10 nouvelles cartes seront
    introduites. Si la limite de révision est atteinte, aucune nouvelle carte ne sera
    montrée.
deck-config-limit-interday-bound-by-reviews = La limite de révision journalière affecte aussi les cartes en apprentissage interjournalier. Quand la limite est appliquée, les cartes en apprentissage interjournalier sont collectées en premier, puis celles à réviser, et enfin les nouvelles cartes.
deck-config-tab-description =
    - `Préréglage`: La limite est partagée avec tous les paquets utilisant ce préréglage.
    - `Ce paquet`: La limite est spécifique à ce paquet.
    - `Juste aujourd'hui`: Modification temporaire de la limite de ce paquet.
deck-config-new-cards-ignore-review-limit = Les nouvelles cartes ignorent la limite de révisions
deck-config-new-cards-ignore-review-limit-tooltip =
    Par défaut, la limite de révisions s'applique aussi aux nouvelles cartes, et aucune nouvelle
    carte ne sera affichée si la limite de révisions a été atteinte. Si cette option est activée, les nouvelles
    cartes seront affichées peu importe la limite de révision.
deck-config-apply-all-parent-limits = Les limites commencent à partir du paquet parent
deck-config-apply-all-parent-limits-tooltip =
    Par défaut, les limites commencent du paquet que vous sélectionnez. Si cette option est activée, les limites vont 
    commencer du paquet au plus haut niveau, ce qui peut être utile si vous souhaitez étudier des sous-paquets 
    individuellement, tout en imposant une limite totale de cartes par jour.
deck-config-affects-entire-collection = Affecte toute la collection.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Préréglage
deck-config-deck-only = Ce paquet
deck-config-today-only = Juste aujourd'hui

## New Cards section

deck-config-learning-steps = Étapes d'apprentissage
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Les délais peuvent être en minutes (par ex. `5m`), ou en jours (par ex. `2d`), mais les heures (par ex. `1h`) ou les secondes (par ex. `30s`) sont également valides.
deck-config-learning-steps-tooltip = Un ou plusieurs délais, séparés par des espaces. Le premier délai (par défaut 1 minute) sera appliqué quand vous noterez une nouvelle carte comme étant `À revoir`. Le bouton `Correct` passera à l'étape suivante (par défaut 10 minutes). Une fois ces étapes franchies, la carte deviendra une carte de révision, et apparaîtra un autre jour. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip = Le nombre de jours à attendre avant de revoir une carte, si le bouton `Correct` est pressé à l'étape finale d'apprentissage.
deck-config-easy-interval-tooltip = Le nombre de jours à attendre avant de revoir une carte, si le bouton `Facile` est pressé pour l'enlever immédiatement de l'apprentissage.
deck-config-new-insertion-order = Ordre d'insertion
deck-config-new-insertion-order-tooltip = Contrôle la position (date d'échéance) assignée aux nouvelles cartes que vous ajoutez. Les cartes avec une plus faible position seront montrées en premier pendant l'étude. Changer cette option va automatiquement modifier la position existante des nouvelles cartes.
deck-config-new-insertion-order-sequential = Séquentiel (les plus anciennes cartes d'abord)
deck-config-new-insertion-order-random = Aléatoire
deck-config-new-insertion-order-random-with-v3 = Avec le planificateur v3, il est préférable de laisser ce paramètre en séquentiel, et d'ajuster plutôt le nouvel ordre de rassemblement des cartes.

## Lapses section

deck-config-relearning-steps = Étapes de ré-apprentissage
deck-config-relearning-steps-tooltip = Aucun ou plusieurs délais, séparés par des espaces. Par défaut, presser le bouton 'À revoir' d'une carte à réviser l'affichera à nouveau 10 minutes plus tard. Si aucun délai n'a été fourni, la carte aura son intervalle modifié, sans entrer en réapprentissage.  { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip = Le nombre de fois où il faut appuyer sur `À revoir` sur une carte à réviser avant qu'elle ne soit considérée comme une « sangsue ». Les sangsues sont des cartes qui vous font perdre beaucoup de temps, et lorsqu'une carte est marquée comme telle, il peut être opportun de la réécrire, de la supprimer, ou de chercher un moyen mnémotechnique pour mieux s'en souvenir.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    `Étiqueter` : Ajoute une étiquette « sangsue » à la note, et affiche un pop-up.
    `Suspendre la carte` : En plus d’étiqueter la note, cache la carte jusqu'à ce qu'elle soit manuellement réactivée.

## Burying section

deck-config-bury-title = Enfouissement
deck-config-bury-new-siblings = Enfouir les nouvelles cartes sœurs
deck-config-bury-review-siblings = Enfouir les cartes sœurs à réviser
deck-config-bury-interday-learning-siblings = Enfouir les cartes sœurs en cours d'apprentissage
deck-config-bury-new-tooltip = Dans quelle mesure les autres `nouvelles` cartes liées à la même note (par ex. cartes inversées, mots de textes à trous adjacents) doivent être retardées jusqu'au lendemain.
deck-config-bury-review-tooltip = Dans quelle mesure les autres cartes `révision` liées à la même note doivent être retardées jusqu'au lendemain.
deck-config-bury-interday-learning-tooltip =
    Dans quelle mesure les autres cartes `en apprentissage` liées à la même note
    avec un intervalle supérieur à 1 jour doivent être retardées jusqu'au lendemain.
deck-config-bury-priority-tooltip =
    Quand Anki rassemble des cartes, les cartes en apprentissage du jour
    sont d'abord rassemblées, puis les révisions, et enfin les nouvelles cartes. Cela affecte
    la manière dont fonctionne l'enfouissement:
    
    - Si toutes les options d'enfouissement sont activées, la carte sœur qui vient en première
    dans cette liste sera montrée. Par exemple, une carte de révision sera montrée en la préférant
    à une nouvelle carte
    - Les cartes sœurs se trouvant après dans la liste ne peuvent pas enfouir des types de cartes précédents.
    Par exemple, si vous désactivez l'enfouissement des nouvelles cartes, et étudiez une nouvelle carte, elle n'enfouira
    aucune carte d'apprentissage ou de révision du jour, et vous pourrez voir une carte sœur de révision et une nouvelle
    carte sœur dans la même session.

## Gather order and sort order of cards

deck-config-ordering-title = Ordre d'Affichage
deck-config-new-gather-priority = Ordre de collecte des nouvelles cartes
deck-config-new-gather-priority-tooltip-2 =
    `Paquet` : rassemble les cartes de chaque paquet dans l'ordre, en commençant par le haut. Les cartes de chaque paquet sont rassemblées par ordre croissant. Si la limite quotidienne du paquet choisi est atteinte, la collecte peut s'arrêter avant que tous les paquets aient été vérifiés. Cet ordre est le plus rapide pour les grandes collections, et permet de donner la priorité aux sous-paquets qui sont plus proches du sommet.
    
    `Ordre croissant` : rassemble les cartes par position croissante (échéance), c.à.d. généralement les plus anciennes ajoutées en premier.
    
    `Ordre décroissant` : rassemble les cartes par position décroissante (échéance), c.à.d. généralement les plus récentes ajoutées en premier.
    
    `Notes aléatoires` : rassemble les cartes de notes choisies au hasard. Si l'enfouissement des cartes sœurs est désactivé, cela permet à toutes les cartes d'une même note d'apparaître dans une session (par ex. à la fois une carte recto->verso et une carte verso->recto)
    
    `Cartes aléatoires` : rassemble les cartes complètement aléatoirement
deck-config-new-card-sort-order = Ordre de classement des nouvelles cartes
deck-config-new-card-sort-order-tooltip-2 =
    `Par type de carte`: Affiche les cartes par ordre de numéro de type de carte. Si l'enfouissement des cartes sœurs est désactivé, ceci permet de s'assurer que toutes les cartes recto→verso seront vues avant les cartes verso→recto. C'est utile pour avoir toutes les cartes d'une même note présentées dans une même session, sans être trop rapprochées les unes des autres.
    
    `Par ordre d'ajout`: Présente les cartes dans l'ordre exact dans lequel elles furent ajoutées. Si l'enfouissement des cartes sœurs est désactivé, cela résultera en ce que toutes les cartes d'une même note soit vues les unes après les autres.
    
    `Par type de carte, puis aléatoire`: Comme `Par type de carte`, mais mélange les cartes de chaque numéro de type de carte. Si vous utilisez `Ordre croissant` pour rassembler les les plus vieilles cartes, vous pourriez utiliser cette option pour voir ces cartes dans un ordre aléatoire, tout en s'assurant que les cartes d'une même note ne soient pas trop proches les unes des autres.
    
    `Notes aléatoires, puis par type de carte`: Choisit des notes au hasard, puis montre toutes leurs carte sœurs dans l'ordre.
    
    `Aléatoire`: Mélange complètement les cartes rassemblées.
deck-config-new-review-priority = Ordre nouvelle/à réviser
deck-config-new-review-priority-tooltip = Quand montrer les nouvelles cartes par rapport aux cartes de révision.
deck-config-interday-step-priority = Ordre d'apprentissage/de révision interjournalier
deck-config-interday-step-priority-tooltip =
    Quand montrer les cartes de (ré)apprentissage qui franchissent une limite de jour.
    
    La limite de révision est toujours appliquée en premier lieu aux cartes d'apprentissage inter-journalières, et
    ensuite aux révisions. Cette option permet de contrôler l'ordre dans lequel les cartes rassemblées sont affichées,
    mais les cartes d'apprentissage inter-journalier seront toujours rassemblées en premier.
deck-config-review-sort-order = Ordre de classement des cartes à réviser
deck-config-review-sort-order-tooltip =
    L'ordre par défaut donne la priorité aux cartes qui ont été en attente le plus longtemps, de sorte que
    si vous avez un retard dans vos révisions, celles qui attendent depuis le plus longtemps apparaîtront
    en premier. Si vous avez un retard important qui prendra plus que quelques jours à
    résorber, ou si vous souhaitez voir les cartes dans l'ordre des sous-paquets, vous pouvez préférer les ordres de tri alternatifs.
deck-config-display-order-will-use-current-deck =
    Anki va utiliser l'ordre d'affichage du paquet que vous
    avez sélectionné pour étudier, et non ceux des sous-paquets qu'il peut avoir.

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = Paquet
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = Paquet puis notes aléatoires
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = Ordre croissant
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = Ordre décroissant
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = Notes aléatoires
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = Cartes aléatoires
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = Modèle de carte, puis aléatoirement
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = Carte aléatoire. puis type de carte
# Sort the cards randomly.
deck-config-sort-order-random = Aléatoirement
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = Modèle de carte, puis dans l'ordre de la collecte
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = Dans l'ordre collectée
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = Mélanger avec les cartes à réviser
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = Afficher après les cartes à réviser
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = Afficher avant les cartes à réviser
# Sort the cards first by due date, in ascending order (oldest due date to newest), then randomly within the same due date.
deck-config-sort-order-due-date-then-random = Date d'échéance, puis aléatoirement
# Sort the cards first by due date, in ascending order (oldest due date to newest), then by deck within the same due date.
deck-config-sort-order-due-date-then-deck = Date d'échéance, puis dans l'ordre du paquet
# Sort the cards first by deck, then by due date in ascending order (oldest due date to newest) within the same deck.
deck-config-sort-order-deck-then-due-date = Dans l'ordre du paquet, puis par date d'échéance
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = Intervalles croissants
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = Intervalles décroissants
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = Facilité croissante
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = Facilité décroissante
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = Difficulté croissante
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = Difficulté décroissante
# Sort the cards by retrievability percentage, in ascending order (0% to 100%, least retrievable to most easily retrievable).
deck-config-sort-order-retrievability-ascending = Retrouvabilité ascendante
# Sort the cards by retrievability percentage, in descending order (100% to 0%, most easily retrievable to least retrievable).
deck-config-sort-order-retrievability-descending = Retrouvabilité descendante

## Timer section

deck-config-timer-title = Chronomètre
deck-config-maximum-answer-secs = Temps de réponse maximum
deck-config-maximum-answer-secs-tooltip =
    Le nombre maximum de secondes à enregistrer pour une seule révision. Si une réponse
    dépasse ce temps (parce que vous vous êtes éloigné de l'écran par exemple),
    le temps pris sera enregistré comme la limite que vous avez fixée.
deck-config-show-answer-timer-tooltip =
    Dans l'écran de révision, affichez une minuterie qui compte le nombre de secondes que vous
    passez pour revoir chaque carte.
deck-config-stop-timer-on-answer = Arrêter le chronomètre avec la réponse
deck-config-stop-timer-on-answer-tooltip =
    S'il faut arrêter le chronomètre quand réponse est révélée.
    Cela n'affecte pas les statistiques.

## Auto Advance section

deck-config-seconds-to-show-question = Temps d'affichage de la question en secondes
deck-config-seconds-to-show-question-tooltip-3 = Lorsque l'avance automatique est activée, le nombre de secondes à attendre avant d'appliquer l'action de la question. Mettre à 0 pour désactiver.
deck-config-seconds-to-show-answer = Temps d'affichage de la réponse en secondes
deck-config-seconds-to-show-answer-tooltip-2 = Quand l'avance automatique est activée, le nombre de secondes à attendre avant de révéler la réponse. Mettre à 0 pour désactiver.
deck-config-question-action-show-answer = Afficher la réponse
deck-config-question-action-show-reminder = Afficher le rappel
deck-config-question-action = Action de la question
deck-config-question-action-tool-tip = L'action à effectuer après que la question soit montrée, et le temps écoulé.
deck-config-answer-action = Action de la réponse
deck-config-answer-action-tooltip-2 = L'action à effectuer après la réponse est affichée et le temps s'est écoulé.
deck-config-wait-for-audio-tooltip-2 = Attendez que l'audio se termine avant d'appliquer automatiquement l'action de question ou l'action de réponse.

## Audio section

deck-config-audio-title = Audio
deck-config-disable-autoplay = Ne pas lire les fichiers audio automatiquement
deck-config-disable-autoplay-tooltip =
    Si activé, Anki ne jouera pas les sons automatiquement.
    Cela peut être effectué manuellement en cliquant/appuyant sur une icône sonore, ou en utilisant l'action de réécoute audio.
deck-config-skip-question-when-replaying = Passer la question quand la réponse est rejouée
deck-config-always-include-question-audio-tooltip =
    Si le son de la question doit être inclus lorsque l'action Replay est
    utilisée pendant que l'on regarde le côté réponse d'une carte.

## Advanced section

deck-config-advanced-title = Avancé
deck-config-maximum-interval-tooltip =
    Le nombre maximum de jours d'attente pour une carte de révision. Lorsque les révisions ont
    atteint la limite, `difficile`, `correct` et `facile` donneront tous le même délai.
    Plus vous raccourcissez ce délai, plus votre charge de travail sera importante.
deck-config-starting-ease-tooltip =
    Le multiplicateur de facilité avec lequel les nouvelles cartes commencent. Par défaut, le bouton "Bien" d'une carte
    nouvellement apprise retardera le prochain examen de 2,5 fois le délai précédent.
deck-config-easy-bonus-tooltip = Un facteur supplémentaire qui est appliqué à l'intervalle d'une carte à réviser quand on répond 'Facile'.
deck-config-interval-modifier-tooltip = Ce multiplicateur est appliqué à toutes les révisions, et des ajustements mineurs peuvent être utilisés pour rendre Anki plus conservateur ou agressif dans sa planification. Veuillez consulter le manuel avant de modifier cette option.
deck-config-hard-interval-tooltip = Le facteur appliqué à l'intervalle d'une carte à réviser quand on répond 'Difficile'.
deck-config-new-interval-tooltip = Le facteur appliqué à l'intervalle d'une carte à réviser quand on répond 'À revoir'.
deck-config-minimum-interval-tooltip = L'intervalle minimum donné à une carte à réviser après avoir répondu 'À revoir'.
deck-config-custom-scheduling = Planification personnalisée
deck-config-custom-scheduling-tooltip = Cela affecte la totalité de la collection. À utiliser à vos risques et périls !

# Easy Days section

deck-config-easy-days-title = Jours faciles
deck-config-easy-days-monday = Lundi
deck-config-easy-days-tuesday = Mardi
deck-config-easy-days-wednesday = Mercredi
deck-config-easy-days-thursday = Jeudi
deck-config-easy-days-friday = Vendredi
deck-config-easy-days-saturday = Samedi
deck-config-easy-days-sunday = Dimanche
deck-config-easy-days-normal = Normal
deck-config-easy-days-reduced = Réduit
deck-config-easy-days-minimum = Minimum
deck-config-easy-days-no-normal-days = Au moins un jour doit être paramétré sur "{ deck-config-easy-days-normal }".
deck-config-easy-days-change = Les révisions existantes ne seront pas re-planifiées sauf si '{ deck-config-reschedule-cards-on-change }' est activé dans les options FSRS.

## Adding/renaming

deck-config-add-group = Ajouter un préréglage
deck-config-name-prompt = Nom
deck-config-rename-group = Renommer la présélection
deck-config-clone-group = clonage Présélection

## Removing

deck-config-remove-group = supprimer la présélection
deck-config-will-require-full-sync = La modification demandée nécessitera une synchronisation à sens unique. Si vous avez effectué des modifications sur un autre appareil et que vous ne les avez pas encore synchronisées avec cet appareil, veuillez le faire avant de poursuivre.
deck-config-confirm-remove-name = Supprimer { $name } ?

## Other Buttons

deck-config-save-button = Sauvegarder
deck-config-save-to-all-subdecks = Sauvegarder pour tout les sous-paquets
deck-config-save-and-optimize = Optimiser tous les préréglages
deck-config-revert-button-tooltip = Restaurer les paramètres par défauts.

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Gestion d'Anki 2.1.41+
deck-config-description-new-handling-hint =
    Traite les entrées comme du markdown, et nettoie les entrées HTML. Lorsqu'elle est activée, la description s'affichera également sur l'écran de félicitations.
    Markdown apparaîtra comme du texte sur Anki 2.1.40 et plus.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    Un paquet parent à une limite de { $cards ->
        [one] { $cards } carte
       *[other] { $cards } cartes
    }, ce qui va outrepasser cette limite.
deck-config-reviews-too-low =
    Pour rajouter { $cards ->
        [one] { $cards } carte inédite par jour
       *[other] { $cards } cartes inédites par jour
    }, vous devriez en réviser au moins { $expected } déjà vues.
deck-config-learning-step-above-graduating-interval = L'intervalle de graduation devrait être au moins aussi long que votre dernière étape d'apprentissage.
deck-config-good-above-easy = L'intervalle facile devrait être au moins aussi long que l'intervalle gradué.
deck-config-relearning-steps-above-minimum-interval = L'intervalle minimal devrait être au moins aussi long que votre étape finale de réapprentissage.
deck-config-maximum-answer-secs-above-recommended = Anki peut planifier vos révisions plus efficacement lorsque vous gardez chaque question courte.
deck-config-too-short-maximum-interval = Un intervalle maximal inférieur à 6 mois n'est pas recommandé.
deck-config-ignore-before-info = (Approximativement) { $included }/{ $totalCards } cartes seront utilisées pour optimiser les paramètres FSRS.

## Selecting a deck

deck-config-which-deck = Pour quel paquet souhaitez-vous afficher les options ?

## Messages related to the FSRS scheduler

deck-config-updating-cards = Mise à jour des cartes : { $current_cards_count }/{ $total_cards_count }...
deck-config-invalid-parameters = Les paramètres FSRS fournis sont invalides. Laissez les vides pour utiliser les paramètres par défaut.
deck-config-not-enough-history = L'historique des révisions est insuffisant pour effectuer cette opération.
deck-config-unable-to-determine-desired-retention = Impossible de déterminer la rétention optimale.
deck-config-must-have-400-reviews =
    { $count ->
        [one] Seulement { $count } révision a été trouvée. Vous devez avoir au moins 400 révisions pour cette opération.
       *[other] Seulement { $count } révisions ont été trouvées. Vous devez avoir au moins 400 révisions pour cette opération.
    }
# Numbers that control how aggressively the FSRS algorithm schedules cards
deck-config-weights = Paramètres du FSRS
deck-config-compute-optimal-weights = Optimiser les paramètres du FSRS
deck-config-compute-minimum-recommended-retention = Rétention minimum recommandée
deck-config-optimize-button = Optimiser
deck-config-compute-button = Calculer
deck-config-ignore-before = Ignorer les révisions avant
deck-config-time-to-optimize = Ça fait longtemps - utiliser le bouton "Optimiser tous les préréglages" est recommandé.
deck-config-evaluate-button = Évaluer
deck-config-desired-retention = Rétention souhaitée
deck-config-historical-retention = Rétention historique
deck-config-smaller-is-better = Les petits nombres indiquent de meilleures estimations de la mémoire.
deck-config-steps-too-large-for-fsrs = Lorsque le FSRS est activé, les étapes d'1 jour ou plus ne sont pas recommandées.
deck-config-get-params = Obtenir les paramètres
deck-config-predicted-minimum-recommended-retention = Rétention minimum recommandée: { $num }
deck-config-complete = { $num }% complété.
deck-config-iterations = Itération : { $count }...
deck-config-reschedule-cards-on-change = Replanifier les cartes lors d'un changement
deck-config-fsrs-tooltip =
    Affecte toute la collection.
    
    Le programmateur FSRS (Free Spaced Repetition Scheduler en anglais) est une alternative à l'ancien programmateur SuperMemo 2 (SM 2) d'Anki.
    En déterminant déterminant plus précisément quand vous êtes susceptible d'oublier, il peut vous aider à vous souvenir de plus de choses sur une même période de temps. Ce paramètre est partagé par tous les préréglages de paquets.
    
    Si vous avez déjà utilisé la version "planification personnalisée" du FSRS, veuillez faire en sorte
    d'effacer toute saisie dans cette section avant d'activer cette option.
deck-config-desired-retention-tooltip =
    La valeur par défaut de 0,9 planifie les cartes de manière à ce que vous ayez 90 % de chances de vous en souvenir lorsqu'elles apparaîtront de nouveau.
    Si vous augmentez cette valeur, Anki affichera les cartes plus fréquemment afin d'augmenter les chances que vous vous en souveniez. Si vous diminuez cette valeur, Anki montrera les cartes moins fréquemment, et vous en oublierez davantage. 
    Soyez prudent lors de l'ajustement de cette valeur - des valeurs plus élevées augmenteront considérablement votre charge de travail, et des valeurs plus faibles peuvent être démoralisantes en vous faisant oublier une grande partie du contenu appris.
deck-config-historical-retention-tooltip =
    Lorsqu'une partie de votre historique de révision est manquante, le FSRS doit combler ce manque. Par défaut, il suppose que lorsque vous avez effectué ces anciennes révisions, vous vous souveniez de 90 % du contenu. Si votre ancienne rétention était en réalité sensiblement supérieure ou inférieure à 90 %, le réglage de cette option permettra au FSRS d'obtenir une meilleure approximation des révisions manquantes.
    
    Votre historique d'examens pourrait être incomplet pour deux raisons :
    1. Parce que vous avez utilisé l'option "ignorer les révisions précédentes".
    2. Parce que vous avez supprimé des logs de révision pour libérer de l'espace, ou parce que vous avez importé du matériel à partir d'un autre programme de SRS.
    
    Ce dernier cas étant assez rare, vous n'avez probablement pas besoin d'ajuster ce paramètre, à moins que vous n'ayez utilisé la première option.
deck-config-weights-tooltip2 =
    Les paramètres FSRS modifient comment les cartes sont planifiées. Anki commencera avec les paramètres par défaut. Vous pouvez utiliser
    l'option ci-dessous pour optimiser les paramètres pour correspondre au mieux à votre performance dans les paquets utilisant ce préréglage.
deck-config-reschedule-cards-on-change-tooltip =
    Affecte l'ensemble de la collection ; n'est pas sauvegardée.
    
    Cette option détermine si les dates d'échéance des cartes seront modifiées lorsque vous activez le FSRS ou que vous optimisez les paramètres. Par défaut, les cartes ne sont pas reprogrammées : les futurs révisions utiliseront le nouveau système de planification, mais il n'y aura pas de changement immédiat dans votre charge de travail.
    Si la replanification est activée, les dates d'échéance des cartes seront modifiées lorsque vous activez le FSRS ou que vous optimisez les paramètres.
deck-config-reschedule-cards-warning =
    En fonction de la rétention souhaitée, cette option peut entraîner l'arrivée à échéance immédiate d'un grand nombre de cartes. Elle n'est donc pas recommandée lorsque vous passez pour la première fois de SM2 à FSRS.
    Utilisez cette option avec parcimonie, car elle ajoutera une entrée de révision à chacune de vos cartes et augmentera la taille de votre collection.
deck-config-ignore-before-tooltip-2 =
    Si cette option est activée, les cartes examinées avant la date indiquée seront ignorées lors de l'optimisation des paramètres FSRS.
    Cela peut être utile si vous avez importé les données de planification de quelqu'un d'autre ou si vous avez changé la façon dont vous utilisez les boutons de réponse.
deck-config-compute-optimal-weights-tooltip2 =
    Lorsque vous cliquez sur le bouton Optimiser, FSRS analysera votre historique de révision et générera des paramètres qui sont 
    optimal pour votre mémoire et le contenu que vous étudiez. Si vos paquets varient énormément en difficulté subjective,
    Il est recommandé de leur attribuer des préréglages séparés, car les paramètres des paquets faciles et des paquets durs seront différents. 
    Vous n'avez pas besoin d'optimiser vos paramètres fréquemment : une fois tous les quelques mois suffit.
    
    Par défaut, les paramètres seront calculés à partir de l’historique de révision de toutes les platines utilisant le préréglage actuel. Vous pouvez
    ajustez éventuellement la recherche avant de calculer les paramètres, si vous souhaitez modifier les cartes utilisées pour
    optimiser les paramètres.
deck-config-compute-optimal-retention-tooltip4 =
    Cet outil tentera de trouver la valeur de rétention souhaitée 
    
    qui mènera à l’apprentissage du plus grand nombre de matières, en un minimum de temps. Le nombre calculé peut servir de référence
    lorsque vous décidez sur quoi définir la rétention souhaitée. Vous souhaiterez peut-être choisir une rétention souhaitée plus élevée, si vous êtes 
    prêt(e) à échanger plus de temps d’étude contre un taux de rappel plus élevé. Définir la rétention souhaitée inférieure au minimum
    Il n'est pas conseillé, car cela entraînerait une charge de travail plus élevée, en raison du taux d’oubli élevé.
deck-config-please-save-your-changes-first = Veuillez sauvegarder vos changements d'abord.
deck-config-a-100-day-interval =
    { $days ->
        [one] Un intervalle de 100 jours deviendra un intervalle de { $days } jour.
       *[other] Un intervalle de 100 jours deviendra un intervalle de { $days } jours.
    }
deck-config-percent-of-reviews =
    { $reviews ->
        [one] { $pct }% de { $reviews } révision
       *[other] { $pct }% de { $reviews } révisions
    }
deck-config-percent-input = { $pct }%
deck-config-optimizing-preset = Optimisation des préréglages { $current_count }/{ $total_count }...
deck-config-fsrs-must-be-enabled = Le FSRS doit être préalablement activé.
deck-config-fsrs-params-optimal = Les paramètres du FSRS semblent actuellement être optimaux.
deck-config-fsrs-params-no-reviews = Aucune révision trouvée. Merci de vérifier que ce préréglage est assigné à tous les paquets que vous souhaitez optimiser (sous-paquets compris) et réessayez.
deck-config-slow-suffix = { $text } (lent)
deck-config-desired-retention-tooltip2 = Les valeurs de charge de travail fournies par la boîte d'information sont une approximation. Pour une plus grande précision, utilisez le simulateur.
deck-config-workload-factor-change = Charge de travail approximative : {$factor}x
deck-config-workload-factor-unchanged = Plus cette valeur est élevée, plus les cartes vous seront présentées fréquemment.
deck-config-desired-retention-too-low = Votre rétention souhaitée est très faible, ce qui peut entraîner des intervalles très longs.
deck-config-desired-retention-too-high = Votre rétention souhaitée est très élevée, ce qui peut entraîner des intervalles très courts.
deck-config-checking-for-improvement = Vérification des améliorations...
deck-config-fsrs-simulate-desired-retention-experimental = Simulateur de rétention souhaitée FSRS (Expérimental)
deck-config-fsrs-simulate-save-preset = Après l'optimisation, veuillez sauvegarder votre préréglage de paquet avant d'exécuter le simulateur.
deck-config-fsrs-desired-retention-help-me-decide-experimental = Aidez-moi à décider (Expérimental)
deck-config-save-options-to-preset-confirm = Écraser les options de votre préréglage actuel avec les options actuellement définies dans le simulateur ?
deck-config-fsrs-simulator-radio-ratio = Ratio Temps / Mémorisation
deck-config-fsrs-simulator-ratio-tooltip = { $time } par carte mémorisée
deck-config-health-check = Vérifier l'état lors de l'optimisation
deck-config-fsrs-bad-fit-warning = Bilan de santé :
    Votre mémoire est difficile à prédire pour FSRS. Recommandations :

    - Suspendez ou reformulez les cartes que vous oubliez constamment.
    - Utilisez les boutons de réponse de manière cohérente. Gardez à l'esprit que "Difficile" est une note de passage, pas un échec.
    - Comprenez avant de mémoriser.

    Si vous suivez ces suggestions, les performances s'amélioreront généralement au cours des prochains mois.
deck-config-fsrs-good-fit = Bilan de santé :
    FSRS peut bien s'adapter à votre mémoire.
deck-config-fsrs-simulator-y-axis-title-time = Temps de révision/Jour
deck-config-fsrs-simulator-y-axis-title-count = Nombre de révisions/Jour
deck-config-fsrs-simulator-y-axis-title-memorized = Total mémorisé
deck-config-compute-optimal-retention-tooltip = Cet outil suppose que vous partez de 0 carte et tentera de calculer la quantité de matériel que vous pourrez retenir dans le laps de temps donné. La rétention estimée dépendra grandement de vos entrées, et si elle diffère considérablement de 0,9, c'est un signe que le temps que vous avez alloué chaque jour est soit trop faible, soit trop élevé pour la quantité de cartes que vous essayez d'apprendre. Ce nombre peut être utile comme référence, mais il n'est pas recommandé de le copier dans le champ de rétention souhaitée.
deck-config-health-check-tooltip1 = Ceci affichera un avertissement si FSRS a du mal à s'adapter à votre mémoire.
deck-config-health-check-tooltip2 = Le bilan de santé n'est effectué que lors de l'utilisation de l'option Optimiser le préréglage actuel.
deck-config-compute-optimal-retention = Calculer la rétention minimale recommandée
deck-config-predicted-optimal-retention = Rétention minimale recommandée : { $num }
deck-config-weights-tooltip = Les paramètres FSRS affectent la façon dont les cartes sont planifiées. Anki commencera avec les paramètres par défaut. Une fois que vous avez accumulé plus de 1000 révisions, vous pouvez utiliser l'option ci-dessous pour optimiser les paramètres afin de correspondre au mieux à vos performances dans les paquets utilisant ce préréglage.
deck-config-wait-for-audio = Attendre l'audio
deck-config-show-reminder = Afficher le rappel
deck-config-answer-again = Réponse à revoir
deck-config-answer-hard = Réponse difficile
deck-config-answer-good = Réponse correcte
deck-config-days-to-simulate = Nombre de jours à simuler
deck-config-desired-retention-below-optimal = Votre taux de rétention souhaité est inférieur à la valeur optimale. Il est recommandé de l'augmenter.
# Description of the y axis in the FSRS simulation
# diagram (Deck options -> FSRS) showing the total number of
# cards that can be recalled or retrieved on a specific date.
deck-config-fsrs-simulator-experimental = Simulateur FSRS (expérimental)
deck-config-additional-new-cards-to-simulate = Cartes supplémentaires à simuler
deck-config-simulate = Simuler
deck-config-clear-last-simulate = Supprimer la dernière simulation
deck-config-fsrs-simulator-radio-count = Révisions
deck-config-advanced-settings = Paramètres avancés
deck-config-smooth-graph = Lisser le graphique
deck-config-suspend-leeches = Suspendre les cartes pénibles (cartes « sangsue »)
deck-config-save-options-to-preset = Sauvegarder les Changements au Prérèglage
# Radio button in the FSRS simulation diagram (Deck options -> FSRS) selecting
# to show the total number of cards that can be recalled or retrieved on a
# specific date.
deck-config-fsrs-simulator-radio-memorized = Mémorisées

## Timer section

deck-config-timer-title = Chronomètre
deck-config-maximum-answer-secs = Temps de réponse maximum
deck-config-maximum-answer-secs-tooltip =
    Le nombre maximum de secondes à enregistrer pour une seule révision. Si une réponse
    dépasse ce temps (parce que vous vous êtes éloigné de l'écran par exemple),
    le temps pris sera enregistré comme la limite que vous avez fixée.
deck-config-show-answer-timer-tooltip =
    Dans l'écran de révision, affichez une minuterie qui compte le nombre de secondes que vous
    passez pour revoir chaque carte.
deck-config-stop-timer-on-answer = Arrêter le chronomètre avec la réponse
deck-config-stop-timer-on-answer-tooltip =
    S'il faut arrêter le chronomètre quand réponse est révélée.
    Cela n'affecte pas les statistiques.

## Auto Advance section

deck-config-seconds-to-show-question = Temps d'affichage de la question en secondes
deck-config-seconds-to-show-question-tooltip-3 = Lorsque l'avance automatique est activée, le nombre de secondes à attendre avant d'appliquer l'action de la question. Mettre à 0 pour désactiver.
deck-config-seconds-to-show-answer = Temps d'affichage de la réponse en secondes
deck-config-seconds-to-show-answer-tooltip-2 = Quand l'avance automatique est activée, le nombre de secondes à attendre avant de révéler la réponse. Mettre à 0 pour désactiver.
deck-config-question-action-show-answer = Afficher la réponse
deck-config-question-action-show-reminder = Afficher le rappel
deck-config-question-action = Action de la question
deck-config-question-action-tool-tip = L'action à effectuer après que la question soit montrée, et le temps écoulé.
deck-config-answer-action = Action de la réponse
deck-config-answer-action-tooltip-2 = L'action à effectuer après la réponse est affichée et le temps s'est écoulé.
deck-config-wait-for-audio-tooltip-2 = Attendez que l'audio se termine avant d'appliquer automatiquement l'action de question ou l'action de réponse.

## Audio section

deck-config-audio-title = Audio
deck-config-disable-autoplay = Ne pas lire les fichiers audio automatiquement
deck-config-disable-autoplay-tooltip =
    Si activé, Anki ne jouera pas les sons automatiquement.
    Cela peut être effectué manuellement en cliquant/appuyant sur une icône sonore, ou en utilisant l'action de réécoute audio.
deck-config-skip-question-when-replaying = Passer la question quand la réponse est rejouée
deck-config-always-include-question-audio-tooltip =
    Si le son de la question doit être inclus lorsque l'action Replay est
    utilisée pendant que l'on regarde le côté réponse d'une carte.

## Advanced section

deck-config-advanced-title = Avancé
deck-config-maximum-interval-tooltip =
    Le nombre maximum de jours d'attente pour une carte de révision. Lorsque les révisions ont
    atteint la limite, `difficile`, `correct` et `facile` donneront tous le même délai.
    Plus vous raccourcissez ce délai, plus votre charge de travail sera importante.
deck-config-starting-ease-tooltip =
    Le multiplicateur de facilité avec lequel les nouvelles cartes commencent. Par défaut, le bouton "Bien" d'une carte
    nouvellement apprise retardera le prochain examen de 2,5 fois le délai précédent.
deck-config-easy-bonus-tooltip = Un facteur supplémentaire qui est appliqué à l'intervalle d'une carte à réviser quand on répond 'Facile'.
deck-config-interval-modifier-tooltip = Ce multiplicateur est appliqué à toutes les révisions, et des ajustements mineurs peuvent être utilisés pour rendre Anki plus conservateur ou agressif dans sa planification. Veuillez consulter le manuel avant de modifier cette option.
deck-config-hard-interval-tooltip = Le facteur appliqué à l'intervalle d'une carte à réviser quand on répond 'Difficile'.
deck-config-new-interval-tooltip = Le facteur appliqué à l'intervalle d'une carte à réviser quand on répond 'À revoir'.
deck-config-minimum-interval-tooltip = L'intervalle minimum donné à une carte à réviser après avoir répondu 'À revoir'.
deck-config-custom-scheduling = Planification personnalisée
deck-config-custom-scheduling-tooltip = Cela affecte la totalité de la collection. À utiliser à vos risques et périls !

# Easy Days section

deck-config-easy-days-title = Jours faciles
deck-config-easy-days-monday = Lundi
deck-config-easy-days-tuesday = Mardi
deck-config-easy-days-wednesday = Mercredi
deck-config-easy-days-thursday = Jeudi
deck-config-easy-days-friday = Vendredi
deck-config-easy-days-saturday = Samedi
deck-config-easy-days-sunday = Dimanche
deck-config-easy-days-normal = Normal
deck-config-easy-days-reduced = Réduit
deck-config-easy-days-minimum = Minimum
deck-config-easy-days-no-normal-days = Au moins un jour doit être paramétré sur "{ deck-config-easy-days-normal }".
deck-config-easy-days-change = Les révisions existantes ne seront pas re-planifiées sauf si '{ deck-config-reschedule-cards-on-change }' est activé dans les options FSRS.

## Adding/renaming

deck-config-add-group = Ajouter un préréglage
deck-config-name-prompt = Nom
deck-config-rename-group = Renommer la présélection
deck-config-clone-group = clonage Présélection

## Removing

deck-config-remove-group = supprimer la présélection
deck-config-will-require-full-sync = La modification demandée nécessitera une synchronisation à sens unique. Si vous avez effectué des modifications sur un autre appareil et que vous ne les avez pas encore synchronisées avec cet appareil, veuillez le faire avant de poursuivre.
deck-config-confirm-remove-name = Supprimer { $name } ?

## Other Buttons

deck-config-save-button = Sauvegarder
deck-config-save-to-all-subdecks = Sauvegarder pour tout les sous-paquets
deck-config-save-and-optimize = Optimiser tous les préréglages
deck-config-revert-button-tooltip = Restaurer les paramètres par défauts.

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Gestion d'Anki 2.1.41+
deck-config-description-new-handling-hint =
    Traite les entrées comme du markdown, et nettoie les entrées HTML. Lorsqu'elle est activée, la description s'affichera également sur l'écran de félicitations.
    Markdown apparaîtra comme du texte sur Anki 2.1.40 et plus.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    Un paquet parent à une limite de { $cards ->
        [one] { $cards } carte
       *[other] { $cards } cartes
    }, ce qui va outrepasser cette limite.
deck-config-reviews-too-low =
    Pour rajouter { $cards ->
        [one] { $cards } carte inédite par jour
       *[other] { $cards } cartes inédites par jour
    }, vous devriez en réviser au moins { $expected } déjà vues.
deck-config-learning-step-above-graduating-interval = L'intervalle de graduation devrait être au moins aussi long que votre dernière étape d'apprentissage.
deck-config-good-above-easy = L'intervalle facile devrait être au moins aussi long que l'intervalle gradué.
deck-config-relearning-steps-above-minimum-interval = L'intervalle minimal devrait être au moins aussi long que votre étape finale de réapprentissage.
deck-config-maximum-answer-secs-above-recommended = Anki peut planifier vos révisions plus efficacement lorsque vous gardez chaque question courte.
deck-config-too-short-maximum-interval = Un intervalle maximal inférieur à 6 mois n'est pas recommandé.
deck-config-ignore-before-info = (Approximativement) { $included }/{ $totalCards } cartes seront utilisées pour optimiser les paramètres FSRS.

## Selecting a deck

deck-config-which-deck = Pour quel paquet souhaitez-vous afficher les options ?

## Messages related to the FSRS scheduler

deck-config-updating-cards = Mise à jour des cartes : { $current_cards_count }/{ $total_cards_count }...
deck-config-invalid-parameters = Les paramètres FSRS fournis sont invalides. Laissez les vides pour utiliser les paramètres par défaut.
deck-config-not-enough-history = L'historique des révisions est insuffisant pour effectuer cette opération.
deck-config-unable-to-determine-desired-retention = Impossible de déterminer la rétention optimale.
deck-config-must-have-400-reviews =
    { $count ->
        [one] Seulement { $count } révision a été trouvée. Vous devez avoir au moins 400 révisions pour cette opération.
       *[other] Seulement { $count } révisions ont été trouvées. Vous devez avoir au moins 400 révisions pour cette opération.
    }
# Numbers that control how aggressively the FSRS algorithm schedules cards
deck-config-weights = Paramètres du FSRS
deck-config-compute-optimal-weights = Optimiser les paramètres du FSRS
deck-config-compute-minimum-recommended-retention = Rétention minimum recommandée
deck-config-optimize-button = Optimiser
deck-config-compute-button = Calculer
deck-config-ignore-before = Ignorer les révisions avant
deck-config-time-to-optimize = Ça fait longtemps - utiliser le bouton "Optimiser tous les préréglages" est recommandé.
deck-config-evaluate-button = Évaluer
deck-config-desired-retention = Rétention souhaitée
deck-config-historical-retention = Rétention historique
deck-config-smaller-is-better = Les petits nombres indiquent de meilleures estimations de la mémoire.
deck-config-steps-too-large-for-fsrs = Lorsque le FSRS est activé, les étapes d'1 jour ou plus ne sont pas recommandées.
deck-config-get-params = Obtenir les paramètres
deck-config-predicted-minimum-recommended-retention = Rétention minimum recommandée: { $num }
deck-config-complete = { $num }% complété.
deck-config-iterations = Itération : { $count }...
deck-config-reschedule-cards-on-change = Replanifier les cartes lors d'un changement
deck-config-fsrs-tooltip =
    Affecte toute la collection.
    
    Le programmateur FSRS (Free Spaced Repetition Scheduler en anglais) est une alternative à l'ancien programmateur SuperMemo 2 (SM 2) d'Anki.
    En déterminant déterminant plus précisément quand vous êtes susceptible d'oublier, il peut vous aider à vous souvenir de plus de choses sur une même période de temps. Ce paramètre est partagé par tous les préréglages de paquets.
    
    Si vous avez déjà utilisé la version "planification personnalisée" du FSRS, veuillez faire en sorte
    d'effacer toute saisie dans cette section avant d'activer cette option.
deck-config-desired-retention-tooltip =
    La valeur par défaut de 0,9 planifie les cartes de manière à ce que vous ayez 90 % de chances de vous en souvenir lorsqu'elles apparaîtront de nouveau.
    Si vous augmentez cette valeur, Anki affichera les cartes plus fréquemment afin d'augmenter les chances que vous vous en souveniez. Si vous diminuez cette valeur, Anki montrera les cartes moins fréquemment, et vous en oublierez davantage. 
    Soyez prudent lors de l'ajustement de cette valeur - des valeurs plus élevées augmenteront considérablement votre charge de travail, et des valeurs plus faibles peuvent être démoralisantes en vous faisant oublier une grande partie du contenu appris.
deck-config-historical-retention-tooltip =
    Lorsqu'une partie de votre historique de révision est manquante, le FSRS doit combler ce manque. Par défaut, il suppose que lorsque vous avez effectué ces anciennes révisions, vous vous souveniez de 90 % du contenu. Si votre ancienne rétention était en réalité sensiblement supérieure ou inférieure à 90 %, le réglage de cette option permettra au FSRS d'obtenir une meilleure approximation des révisions manquantes.
    
    Votre historique d'examens pourrait être incomplet pour deux raisons :
    1. Parce que vous avez utilisé l'option "ignorer les révisions précédentes".
    2. Parce que vous avez supprimé des logs de révision pour libérer de l'espace, ou parce que vous avez importé du matériel à partir d'un autre programme de SRS.
    
    Ce dernier cas étant assez rare, vous n'avez probablement pas besoin d'ajuster ce paramètre, à moins que vous n'ayez utilisé la première option.
deck-config-weights-tooltip2 =
    Les paramètres FSRS modifient comment les cartes sont planifiées. Anki commencera avec les paramètres par défaut. Vous pouvez utiliser
    l'option ci-dessous pour optimiser les paramètres pour correspondre au mieux à votre performance dans les paquets utilisant ce préréglage.
deck-config-reschedule-cards-on-change-tooltip =
    Affecte l'ensemble de la collection ; n'est pas sauvegardée.
    
    Cette option détermine si les dates d'échéance des cartes seront modifiées lorsque vous activez le FSRS ou que vous optimisez les paramètres. Par défaut, les cartes ne sont pas reprogrammées : les futurs révisions utiliseront le nouveau système de planification, mais il n'y aura pas de changement immédiat dans votre charge de travail.
    Si la replanification est activée, les dates d'échéance des cartes seront modifiées lorsque vous activez le FSRS ou que vous optimisez les paramètres.
deck-config-reschedule-cards-warning =
    En fonction de la rétention souhaitée, cette option peut entraîner l'arrivée à échéance immédiate d'un grand nombre de cartes. Elle n'est donc pas recommandée lorsque vous passez pour la première fois de SM2 à FSRS.
    Utilisez cette option avec parcimonie, car elle ajoutera une entrée de révision à chacune de vos cartes et augmentera la taille de votre collection.
deck-config-ignore-before-tooltip-2 =
    Si cette option est activée, les cartes examinées avant la date indiquée seront ignorées lors de l'optimisation des paramètres FSRS.
    Cela peut être utile si vous avez importé les données de planification de quelqu'un d'autre ou si vous avez changé la façon dont vous utilisez les boutons de réponse.
deck-config-compute-optimal-weights-tooltip2 =
    Lorsque vous cliquez sur le bouton Optimiser, FSRS analysera votre historique de révision et générera des paramètres qui sont 
    optimal pour votre mémoire et le contenu que vous étudiez. Si vos paquets varient énormément en difficulté subjective,
    Il est recommandé de leur attribuer des préréglages séparés, car les paramètres des paquets faciles et des paquets durs seront différents. 
    Vous n'avez pas besoin d'optimiser vos paramètres fréquemment : une fois tous les quelques mois suffit.
    
    Par défaut, les paramètres seront calculés à partir de l’historique de révision de toutes les platines utilisant le préréglage actuel. Vous pouvez
    ajustez éventuellement la recherche avant de calculer les paramètres, si vous souhaitez modifier les cartes utilisées pour
    optimiser les paramètres.
deck-config-compute-optimal-retention-tooltip4 =
    Cet outil tentera de trouver la valeur de rétention souhaitée 
    
    qui mènera à l’apprentissage du plus grand nombre de matières, en un minimum de temps. Le nombre calculé peut servir de référence
    lorsque vous décidez sur quoi définir la rétention souhaitée. Vous souhaiterez peut-être choisir une rétention souhaitée plus élevée, si vous êtes 
    prêt(e) à échanger plus de temps d’étude contre un taux de rappel plus élevé. Définir la rétention souhaitée inférieure au minimum
    Il n'est pas conseillé, car cela entraînerait une charge de travail plus élevée, en raison du taux d’oubli élevé.
deck-config-please-save-your-changes-first = Veuillez sauvegarder vos changements d'abord.
deck-config-a-100-day-interval =
    { $days ->
        [one] Un intervalle de 100 jours deviendra un intervalle de { $days } jour.
       *[other] Un intervalle de 100 jours deviendra un intervalle de { $days } jours.
    }
deck-config-percent-of-reviews =
    { $reviews ->
        [one] { $pct }% de { $reviews } révision
       *[other] { $pct }% de { $reviews } révisions
    }
deck-config-percent-input = { $pct }%
deck-config-optimizing-preset = Optimisation des préréglages { $current_count }/{ $total_count }...
deck-config-fsrs-must-be-enabled = Le FSRS doit être préalablement activé.
deck-config-fsrs-params-optimal = Les paramètres du FSRS semblent actuellement être optimaux.
deck-config-fsrs-params-no-reviews = Aucune révision trouvée. Merci de vérifier que ce préréglage est assigné à tous les paquets que vous souhaitez optimiser (sous-paquets compris) et réessayez.
deck-config-slow-suffix = { $text } (lent)
deck-config-desired-retention-tooltip2 = Les valeurs de charge de travail fournies par la boîte d'information sont une approximation. Pour une plus grande précision, utilisez le simulateur.
deck-config-workload-factor-change = Charge de travail approximative : {$factor}x
deck-config-workload-factor-unchanged = Plus cette valeur est élevée, plus les cartes vous seront présentées fréquemment.
deck-config-desired-retention-too-low = Votre rétention souhaitée est très faible, ce qui peut entraîner des intervalles très longs.
deck-config-desired-retention-too-high = Votre rétention souhaitée est très élevée, ce qui peut entraîner des intervalles très courts.
deck-config-checking-for-improvement = Vérification des améliorations...
deck-config-fsrs-simulate-desired-retention-experimental = Simulateur de rétention souhaitée FSRS (Expérimental)
deck-config-fsrs-simulate-save-preset = Après l'optimisation, veuillez sauvegarder votre préréglage de paquet avant d'exécuter le simulateur.
deck-config-fsrs-desired-retention-help-me-decide-experimental = Aidez-moi à décider (Expérimental)
deck-config-save-options-to-preset-confirm = Écraser les options de votre préréglage actuel avec les options actuellement définies dans le simulateur ?
deck-config-fsrs-simulator-radio-ratio = Ratio Temps / Mémorisation
deck-config-fsrs-simulator-ratio-tooltip = { $time } par carte mémorisée
deck-config-health-check = Vérifier l'état lors de l'optimisation
deck-config-fsrs-bad-fit-warning = Bilan de santé :
    Votre mémoire est difficile à prédire pour FSRS. Recommandations :

    - Suspendez ou reformulez les cartes que vous oubliez constamment.
    - Utilisez les boutons de réponse de manière cohérente. Gardez à l'esprit que "Difficile" est une note de passage, pas un échec.
    - Comprenez avant de mémoriser.

    Si vous suivez ces suggestions, les performances s'amélioreront généralement au cours des prochains mois.
deck-config-fsrs-good-fit = Bilan de santé :
    FSRS peut bien s'adapter à votre mémoire.
deck-config-fsrs-simulator-y-axis-title-time = Temps de révision/Jour
deck-config-fsrs-simulator-y-axis-title-count = Nombre de révisions/Jour
deck-config-fsrs-simulator-y-axis-title-memorized = Total mémorisé
deck-config-compute-optimal-retention-tooltip = Cet outil suppose que vous partez de 0 carte et tentera de calculer la quantité de matériel que vous pourrez retenir dans le laps de temps donné. La rétention estimée dépendra grandement de vos entrées, et si elle diffère considérablement de 0,9, c'est un signe que le temps que vous avez alloué chaque jour est soit trop faible, soit trop élevé pour la quantité de cartes que vous essayez d'apprendre. Ce nombre peut être utile comme référence, mais il n'est pas recommandé de le copier dans le champ de rétention souhaitée.
deck-config-health-check-tooltip1 = Ceci affichera un avertissement si FSRS a du mal à s'adapter à votre mémoire.
deck-config-health-check-tooltip2 = Le bilan de santé n'est effectué que lors de l'utilisation de l'option Optimiser le préréglage actuel.
deck-config-compute-optimal-retention = Calculer la rétention minimale recommandée
deck-config-predicted-optimal-retention = Rétention minimale recommandée : { $num }
deck-config-weights-tooltip = Les paramètres FSRS affectent la façon dont les cartes sont planifiées. Anki commencera avec les paramètres par défaut. Une fois que vous avez accumulé plus de 1000 révisions, vous pouvez utiliser l'option ci-dessous pour optimiser les paramètres afin de correspondre au mieux à vos performances dans les paquets utilisant ce préréglage.
deck-config-wait-for-audio = Attendre l'audio
deck-config-show-reminder = Afficher le rappel
deck-config-answer-again = Réponse à revoir
deck-config-answer-hard = Réponse difficile
deck-config-answer-good = Réponse correcte
deck-config-days-to-simulate = Nombre de jours à simuler
deck-config-desired-retention-below-optimal = Votre taux de rétention souhaité est inférieur à la valeur optimale. Il est recommandé de l'augmenter.
# Description of the y axis in the FSRS simulation
# diagram (Deck options -> FSRS) showing the total number of
# cards that can be recalled or retrieved on a specific date.
deck-config-fsrs-simulator-experimental = Simulateur FSRS (expérimental)
deck-config-additional-new-cards-to-simulate = Cartes supplémentaires à simuler
deck-config-simulate = Simuler
deck-config-clear-last-simulate = Supprimer la dernière simulation
deck-config-fsrs-simulator-radio-count = Révisions
deck-config-advanced-settings = Paramètres avancés
deck-config-smooth-graph = Lisser le graphique
deck-config-suspend-leeches = Suspendre les cartes pénibles (cartes « sangsue »)
deck-config-save-options-to-preset = Sauvegarder les Changements au Prérèglage
# Radio button in the FSRS simulation diagram (Deck options -> FSRS) selecting
# to show the total number of cards that can be recalled or retrieved on a
# specific date.
deck-config-fsrs-simulator-radio-memorized = Mémorisées

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-bury-siblings = Enfouir les cartes sœurs
deck-config-do-not-bury = Ne pas enfouir les cartes sœurs
deck-config-bury-if-new = Enfouir si nouveau
deck-config-bury-if-new-or-review = Enfouir si nouveau ou révision
deck-config-bury-if-new-review-or-interday = Enfouir en cas de nouveauté, de révision ou d'apprentissage interjournalier
deck-config-bury-tooltip = Dans quelle mesure les autres cartes liées à la même note (par ex. cartes inversées, mots de textes à trous adjacents) doivent être retardées jusqu'au lendemain.
deck-config-seconds-to-show-question-tooltip = Quand l'avance automatique est activée, le nombre de secondes à attendre avant de révéler la réponse. Mettre à 0 pour désactiver.
deck-config-answer-action-tooltip = L'action à réaliser sur la carte actuelle avant de continuer automatiquement vers la prochaine.
deck-config-wait-for-audio-tooltip = Attendre la fin de l'audio avant de révéler automatiquement la réponse ou la prochaine question
deck-config-ignore-before-tooltip = 
    Si cette option est activée, les révisions antérieures à la date indiquée seront ignorées lors de l'optimisation et de l'évaluation des paramètres FSRS.
    Cela peut être utile si vous avez importé les données de planification de quelqu'un d'autre ou si vous avez changé la façon dont vous utilisez les boutons de réponse.
deck-config-compute-optimal-retention-tooltip =
    Cet outil suppose que vous're starting with 0 cards, and will attempt to calculate the amount of material you'll
    be able to retain in the given time frame. The estimated retention will greatly depend on your inputs, and
    if it significantly differs from 0.9, it's a sign that the time you've allocated each day is either too low
    or too high for the amount of cards you're trying to learn. This number can be useful as a reference, but it
    is not recommended to copy it into the desired retention field.
deck-config-health-check-tooltip1 = This will show a warning if FSRS struggles to adapt to your memory.
deck-config-health-check-tooltip2 = Health check is performed only when using Optimize Current Preset.

deck-config-compute-optimal-retention = Compute minimum recommended retention
deck-config-predicted-optimal-retention = Minimum recommended retention: { $num }
deck-config-weights-tooltip =
    FSRS parameters affect how cards are scheduled. Anki will start with default parameters. Once
    you've accumulated 1000+ reviews, you can use the option below to optimize the parameters to best
    match your performance in decks using this preset.
