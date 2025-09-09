database-check-corrupt = Le fichier de la collection est corrompu. Restaurez le à partir d'une sauvegarde automatique.
database-check-rebuilt = La base de données est reconstruite et optimisée.
database-check-card-properties =
    { $count ->
        [one] Corrigé { $count } carte invalide.
       *[other] Corrigé { $count } cartes invalides.
    }
database-check-card-last-review-time-empty =
    { $count ->
        [one] Date de dernière révision ajoutée à { $count } carte.
       *[other] Date de dernière révision ajoutée à { $count } cartes.
    }
database-check-missing-templates =
    { $count ->
        [one] { $count } carte sans modèle supprimée.
       *[other] { $count } cartes sans modèle supprimées.
    }
database-check-field-count =
    { $count ->
        [one] Une note fixée car elle avait un mauvais nombre de champs.
       *[other] { $count } notes fixées car elles avaient un mauvais nombre de champs.
    }
database-check-new-card-high-due =
    { $count ->
        [one] Il y a une nouvelle carte dont la valeur "dû" est au moins un million. Vous devriez considérer de la repositionner dans l'explorateur.
       *[other] Il y a { $count } nouvelles cartes dont la valeur "dû" est au moins un million. Vous devriez considérer de les repositionner dans l'explorateur.
    }
database-check-card-missing-note =
    { $count ->
        [one] Carte { $count } supprimée avec une note manquante.
       *[other] Cartes { $count } supprimées avec une note manquante.
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] Une carte supprimée car son type était en double.
       *[other] { $count } cartes supprimées car leurs types étaient en double.
    }
database-check-missing-decks =
    { $count ->
        [one] Un deck manquant réparé.
       *[other] { $count } decks manquants réparés.
    }
database-check-revlog-properties =
    { $count ->
        [one] Une revue a été réparé car ses propriétés étaient invalides.
       *[other] { $count } revues ont été réparés car leurs propriétés étaient invalides.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] note réparée avec des caractères utf8 invalides
       *[other] notes réparées avec des caractères utf8 invalides
    }
database-check-fixed-invalid-ids =
    { $count ->
        [one] { $count } objet corrigé avec un horodatage dans le futur.
       *[other] { $count } objets corrigés avec un horodatage dans le futur.
    }
# "db-check" is always in English
database-check-notetypes-recovered = Un ou plusieurs types de notes sont manquants. Les notes qui y sont liées ont reçu un nouveau type commençant par "db-check". Les noms de champ et design de carte ont été perdus, vous devriez restaurer votre précédente sauvegarde automatique.

## Progress info

database-check-checking-integrity = Vérification de la collection...
database-check-rebuilding = Reconstruction...
database-check-checking-cards = Vérification des cartes...
database-check-checking-notes = Vérification des notes...
database-check-checking-history = Vérification de l'historique...
database-check-title = Vérifier la base de données
