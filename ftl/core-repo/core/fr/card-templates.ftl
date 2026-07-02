# This word is used by TTS voices instead of the elided part of a cloze.
card-templates-blank = vide
card-templates-changes-will-affect-notes =
    { $count ->
        [one] Les changements ci-bas affecteront { $count } note utilisant ce type de carte.
       *[other] Les changements ci-bas affecteront { $count } notes utilisant ce type de carte.
    }
card-templates-card-type = Type de carte :
card-templates-front-template = Modèle du recto
card-templates-back-template = Modèle du verso
card-templates-template-styling = Styles
card-templates-front-preview = Aperçu du recto
card-templates-back-preview = Aperçu du verso
card-templates-preview-box = Aperçu
card-templates-template-box = Modèle
card-templates-sample-cloze = Il s'agit d'une suppression de { "{{c1::" }echantillon { "}}" } .
card-templates-fill-empty = Remplissez les champs vides
card-templates-night-mode = Mode nuit
# Add "mobile" class to card preview, so the card appears like it would
# on a mobile device.
card-templates-add-mobile-class = Ajout d'une classe pour les appareils mobiles
card-templates-preview-settings = Options
card-templates-invalid-template-number = Le modèle de la carte { $number } à un problème.
card-templates-identical-front = La face avant est identique au modèle { $number }.
card-templates-no-front-field = On s'attendait à trouver un remplacement de champ sur le devant du modèle de carte.
card-templates-missing-cloze = Je m'attendais à trouver '{ "{{" }cloze:Text{ "}}" }' ou un texte similaire au recto et au verso du modèle de carte.
card-templates-extraneous-cloze = 'cloze:' peut uniquement être utilisé sur le type de note "texte à trous".
card-templates-see-preview = Veuillez prévisualiser pour plus d'informations.
card-templates-field-not-found = Pas de champ "{ $field }" trouvé.
card-templates-changes-saved = Changements sauvegardés.
card-templates-discard-changes = Perdre les changements ?
card-templates-add-card-type = Ajouter une sorte de carte...
card-templates-anki-couldnt-find-the-line-between = Anki n’a pas pu trouver la ligne entre la question et la réponse. Veuillez ajuster le modèle manuellement pour intervertir question et réponse.
card-templates-at-least-one-card-type-is = Au moins un type de carte est requis.
card-templates-browser-appearance = Apparence du navigateur...
card-templates-card = Carte { $val }
card-templates-card-types-for = Types de carte pour { $val }
card-templates-cloze = Texte à trous
card-templates-deck-override = Deck pour cette carte
card-templates-copy-info = Copier les informations dans le presse-papiers
card-templates-delete-the-as-card-type-and = Supprimer le type de carte « { $template } », et ses { $cards } ?
card-templates-enter-deck-to-place-new = Indiquez dans quel paquet placer les { $val } nouvelles cartes, ou laissez vide :
card-templates-enter-new-card-position-1 = Entrez la position de la carte (1…{ $val }) :
card-templates-flip = Retourner
card-templates-form = Formulaire
card-templates-off = (inactif)
card-templates-on = (actif)
card-templates-remove-card-type = Supprimer le type de carte...
card-templates-rename-card-type = Renommer le type de carte...
card-templates-reposition-card-type = Repositionner le type de carte
card-templates-card-count =
    { $count ->
        [one] { $count } carte
       *[other] { $count } cartes
    }
card-templates-this-will-create-card-proceed =
    { $count ->
        [one] Ceci créera { $count } carte. Procéder?
       *[other] Ceci créera { $count } cartes. Procéder ?
    }
card-templates-type-boxes-warning = Seule une zone de texte par modèle est prise en charge
card-templates-restore-to-default = Restaurer les paramètres par défaut
card-templates-restore-to-default-confirmation =
    Cela va réinitialiser tous les champs et modèles de ce type de note à leurs
    valeurs par défaut, retirant tout champ et modèle additionnel et son contenu, ainsi que son style. Voulez vous continuer ?
card-templates-restored-to-default = Le type de note a été restauré à son état originel.
