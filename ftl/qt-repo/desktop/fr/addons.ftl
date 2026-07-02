addons-possibly-involved = Greffons possiblement impliqués : { $addons }
addons-failed-to-load =
    Un greffon que vous avez installé n’a pu se charger. Si le problème persiste, allez dans le menu Outils > Greffons et désactivez ou supprimez ce greffon.
    
    Pendant le chargement de « { $name } » :
    { $traceback }
addons-failed-to-load2 =
    Les greffons suivants ont échoué à charger:
    { $addons }
    
    Peut-être qu'ils ont besoin d'etre mis a jour pour soutenir cette version d'Anki. Clic le bouton de { addons-check-for-updates }
    pour voir s’il y a des mis-a-jours disponibles.
    
    Vous pouvez utiliser le bouton { about-copy-debug-info } pour obtenir des informations que vous pouvez coller dans un rapport a
    l'auteur(e) du greffon
    
    Pour les greffons qui n'ont pas des mis-a-jours disponibles, vous pouvez désactiver ou supprimer le greffon pour empêcher ce 
    message d’apparaître
addons-startup-failed = Démarrage des greffons échoué
# Shown in the add-on configuration screen (Tools>Add-ons>Config), in the title bar
addons-config-window-title = Paramétrer « { $name } »
addons-config-validation-error = Il y a eu un problème avec la configuration fournie : { $problem }, depuis le chemin { $path }, contre le schéma { $schema }.
addons-window-title = Greffons
addons-addon-has-no-configuration = Le greffon n’a pas de configuration.
addons-addon-installation-error = Erreur lors de l’installation du greffon
addons-browse-addons = Parcourir les greffons
addons-changes-will-take-effect-when-anki = Les changements seront effectifs au redémarrage d’Anki.
addons-check-for-updates = Vérifier les mises à jour
addons-checking = Vérification...
addons-code = Code :
addons-config = Configuration
addons-configuration = Configuration
addons-corrupt-addon-file = Fichier de greffon corrompu.
addons-disabled = (désactivé)
addons-disabled2 = (désactivé)
addons-download-complete-please-restart-anki-to = Téléchargement terminé. Veuillez redémarrer Anki pour appliquer les modifications.
addons-downloaded-fnames = { $fname } téléchargé(s)
addons-downloading-adbd-kb02fkb = Téléchargement de { $part }/{ $total } ({ $kilobytes } Ko)...
addons-error-downloading-ids-errors = Erreur en téléchargeant <i>{ $id }</i> : { $error }
addons-error-installing-bases-errors = Erreur d’installation <i>{ $base }</i> : { $error }
addons-get-addons = Acquérir des greffons...
addons-important-as-addons-are-programs-downloaded = <b>Important</b> : Comme les greffons sont des programmes téléchargés à partir d’Internet, ils sont potentiellement malveillants.<b>Vous ne devriez installer que des greffons en qui vous avez confiance.</b><br><br>Êtes-vous sûr de vouloir procéder à l’installation de(s) greffons(s) Anki suivants ?<br><br>%(names)s
addons-install-addon = Installer un greffon
addons-install-addons = Installer le(s) greffon(s)
addons-install-anki-addon = Installer un greffon
addons-install-from-file = Installer à partir d’un fichier...
addons-installation-complete = Installation terminée
addons-installed-names = { $name } installé
addons-installed-successfully = Installé avec succès.
addons-invalid-addon-manifest = Manifeste de greffon invalide.
addons-invalid-code = Code invalide.
addons-invalid-code-or-addon-not-available = Code invalide ou greffon non-disponible pour votre version d’Anki.
addons-invalid-configuration = Configuration invalide
addons-invalid-configuration-top-level-object-must = Configuration invalide : l’objet de niveau supérieur doit être un dossier
addons-no-updates-available = Aucune mise-à-jour disponible.
addons-one-or-more-errors-occurred = Une ou plusieurs erreurs sont survenues :
addons-packaged-anki-addon = Greffon Anki paqueté
addons-please-check-your-internet-connection = Veuillez vérifier votre connexion Internet.
addons-please-report-this-to-the-respective = Veuillez le signaler à l’auteur ou aux auteurs du greffon concerné.
addons-please-restart-anki-to-complete-the = <b>Veuillez redémarrer Anki pour terminer l’installation.</b>
addons-please-select-a-single-addon-first = Veuillez ne sélectionner qu’un seul greffon pour l’instant.
addons-requires = (nécessite { $val })
addons-restored-defaults = Valeurs par défaut restaurées
addons-the-following-addons-are-incompatible-with = Les greffons suivants sont incompatibles avec { $name } et ont été désactivés : { $found }
addons-the-following-addons-have-updates-available = Ces greffons ont des mises-à-jour disponibles. Les installer ?
addons-the-following-conflicting-addons-were-disabled = Les greffons conflictuels suivants ont été désactivés :
addons-this-addon-is-not-compatible-with = Ce greffon n’est pas compatible avec votre version d’Anki.
addons-to-browse-addons-please-click-the = Pour parcourir les greffons de cliquez sur le bouton Parcourir ci-dessous. <br><br>Quand vous trouvez un greffon qui vous plaît, collez le code ci-bas. Vous pouvez coller plusieurs codes en les séparant par une espace.
addons-toggle-enabled = Activer/Désactiver
addons-unable-to-update-or-delete-addon = Impossible de mettre à jour ou de supprimer le greffon. Veuillez démarrer Anki en maintenant la touche majuscule enfoncée pour désactiver temporairement les greffons et réessayez.  Info de débogage: { $val }
addons-unknown-error = Erreur inconnue : { $val }
addons-view-addon-page = Voir la page du greffon
addons-view-files = Afficher les fichiers
addons-delete-the-numd-selected-addon =
    { $count ->
        [one] Supprimer le greffon { $count } sélectionné ?
       *[other] Supprimer les greffons { $count } sélectionnés ?
    }
addons-choose-update-window-title = Mise à jour des greffons
addons-choose-update-update-all = Tout mettre à jour
