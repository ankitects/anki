importing-failed-debug-info = Malsukcesis enporti. Senerarigaj informoj:
importing-aborted = Nuligita: { $val }
importing-added-duplicate-with-first-field = Aldono de duoblaĵo kun la unua kampo: { $val }
importing-all-supported-formats = Ĉiuj subtenataj dosiertipoj { $val }
importing-allow-html-in-fields = Permesi HTML en kampoj
importing-anki-files-are-from-a-very = .anki-dosieroj devenas de malnovegaj versioj de Anki. Vi povas enporti ilin per la aldonaĵo 175027074 aŭ per Anki 2.0 , kiu estas disponebla en la Anki-retejo.
importing-anki2-files-are-not-directly-importable = .anki2-dosieroj ne estas senpere enporteblaj – anstataŭ tio, enportu la ricevitan dosieron .apkg aŭ .zip.
importing-appeared-twice-in-file = Troviĝis duoble en dosiero: { $val }
importing-by-default-anki-will-detect-the = Laŭnorme Anki detektos la disigilon: tebelilon, komon, ktp. Se Anki malĝuste detektas signon, vi povas enmeti ĝin tie ĉi. Uzu \t por reprezenti tabelilon.
importing-cannot-merge-notetypes-of-different-kinds =
    Nototipoj truteksto ne povas esti kunfanditaj kun normaj nototipoj.
    Vi plue povas enporti la dosieron kun malaktiva agordo «{ importing-merge-notetypes }».
importing-change = Ŝanĝi
importing-colon = dupunkto
importing-comma = komo
importing-empty-first-field = Malplena unua kampo: { $val }
importing-field-separator = Apartigilo de kampoj
importing-field-separator-guessed = Apartigilo de kampoj (divenita)
importing-field-mapping = Mapigi kampojn
importing-field-of-file-is = Kampo <b>{ $val }</b> de dosiero estas:
importing-fields-separated-by = Kampoj estas apartigataj per: { $val }
importing-file-must-contain-field-column = Dosiero devas enhavi almenaŭ unu kolumnon, kiu povas esti mapigita al la kampo de noto.
importing-file-version-unknown-trying-import-anyway = La versio de la dosiero estas nekonata. Tamen estas provata realigi la enporton.
importing-first-field-matched = La unua kampo estas egala al: { $val }
importing-identical = Identa
importing-ignore-field = Ignori kampon
importing-ignore-lines-where-first-field-matches = Ignori liniojn, en kiuj la unua kampo kongruas kun ekzistanta noto
importing-ignored = <ignorata>
importing-import-even-if-existing-note-has = Enporti eĉ se ekzistanta noto havas la saman unuan kampon
importing-import-options = Agordoj pri enporto
importing-importing-complete = Enporto finiĝis.
importing-invalid-file-please-restore-from-backup = Malvalida dosiero. Bonvolu rekreu ĝin el sekurkopio.
importing-map-to = Mapigi al { $val }
importing-map-to-tags = Mapigi al etikedoj
importing-mapped-to = mapigita al <b>{ $val }</b>
importing-mapped-to-tags = Mapigita al <b>etikedoj</b>
# the action of combining two existing note types to create a new one
importing-merge-notetypes = Kunfandi nototipojn
importing-merge-notetypes-help =
    Se markita, kaj kiam vi aŭ aŭtoro de kartaro modifos skemon de nototipon, Anki kunfandos ilin, anstataŭ teni la du versiojn.
    
    Per modifi nototipon oni konsideras: ŝanĝi, forigi, reordigi kampojn aŭ ŝablonojn, aŭ ŝanĝi la ordigan kampon. Kiel kontraŭekzemplo: ŝanĝi la frontan kampon de ekzistanta ŝablono *ne* estas konsiderata kiel modifi skemon.
    
    Averto: tiu ĉi ŝanĝo postulos unudirektan samtempigon kaj povas marki ekzistantajn notojn kiel modifitajn.
importing-mnemosyne-20-deck-db = Kartaro Mnemosyne 2.0 (*.db)
importing-multicharacter-separators-are-not-supported-please = Plursignaj apartigiloj ne estas subtenataj. Enigu nur unu signon.
importing-new-deck-will-be-created = Nova kartaro estos kreita: { $name }
importing-notes-added-from-file = Notoj aldonitaj el la dosiero: { $val }
importing-notes-found-in-file = Notoj, kiuj estas trovitaj en la dosiero: { $val }
importing-notes-skipped-as-theyre-already-in = Notoj preterpasitaj, ĉar iliaj ĝisdataj kopioj jam troviĝas en via kolekto: { $val }
importing-notes-skipped-update-due-to-notetype = Notoj ne ĝisdatigitaj, ĉar nototipo estis modifita ekde via unua enporto de notoj: { $val }
importing-notes-updated-as-file-had-newer = Notoj estas ĝisdatigitaj, ĉar dosiero havas pli novan version: { $val }
importing-include-reviews = Inkluzivi ripetojn
importing-also-import-progress = Enporti progreson de lernado
importing-with-deck-configs = Enporti antaŭagordojn de kartoj
importing-updates = Ĝisdatigoj
importing-include-reviews-help = Se aktiva, ĉiuj antaŭaj ripetoj – alligitaj de kunhaviginto de la kartaro – ankaŭ estos enportitaj. Aliokaze ĉiuj kartoj estos enportitaj kiel novaj kartoj kaj ĉiuj etikedoj «leech» kaj «marked» estos forigitaj.
importing-with-deck-configs-help = Se aktiva, ĉiuj preferoj de kartaro – alligitaj de kunhaviginto de la kartaro – ankaŭ estos enportitaj. Aliokaze ĉiuj kartaroj ricevos la implicitan antaŭagordon.
importing-packaged-anki-deckcollection-apkg-colpkg-zip = Pakita kartaro/kolekto Anki (*.apkg *.colpkg *.zip)
# the '|' character
importing-pipe = vertikala streko
# Warning displayed when the csv import preview table is clipped (some columns were hidden)
# $count is intended to be a large number (1000 and above)
importing-preview-truncated =
    { $count ->
        [one] Nur la { $count } unua kolumno estas montrata. Se io aspektas malĝuste, provu ŝanĝi apartigilon.
       *[other] Nur la { $count } unuaj kolumnoj estas montrataj. Se io aspektas malĝuste, provu ŝanĝi apartigilon.
    }
importing-rows-had-num1d-fields-expected-num2d = '{ $row }' havis { $found } kampojn, anstataŭ la atendita { $expected }
importing-selected-file-was-not-in-utf8 = La elektita dosiero ne havis la datumaranĝon UTF-8. Legu la ĉapitron «Importing» (Enporto) en la dokumentaro.
importing-semicolon = punktokomo
importing-skipped = Preterpasita
importing-tab = tabo
importing-tag-modified-notes = Etikedi modifitajn notojn:
importing-text-separated-by-tabs-or-semicolons = Teksto apartigita per taboj aŭ punktokomoj (*)
importing-the-first-field-of-the-note = La unua kampo de la nototipo devas esti mapigita.
importing-the-provided-file-is-not-a = La elektita dosiero ne estas valida dosiero .apkg.
importing-this-file-does-not-appear-to = Tiu ĉi dosiero verŝajne ne estas valida dosiero .apkg. Se tiu ĉi eraro okazas pri dosiero, kiun vi elŝutis de AnkiWeb, la elŝutado verŝajne malsukcesis. Reprovu kaj se la problemo reokazas, reprovu uzi alian retfoliumilon.
importing-this-will-delete-your-existing-collection = Tio ĉi forigos vian ekzistantan kolekton kaj anstataŭigos ĝin per la datumoj el la enportata dosiero. Ĉu vi certas?
importing-unable-to-import-from-a-readonly = Enporto el nurlega dosiero ne estas ebla.
importing-unknown-file-format = Nekonata dosierformo.
importing-update-existing-notes-when-first-field = Ĝisdatigi ekzistantan noton, kiam la unua kampo kongruas kun
importing-updated = Ĝisdatigita
importing-update-if-newer = se pli nova
importing-update-always = ĉiam
importing-update-never = neniam
importing-update-notes = Ĝisdatigi noton
importing-update-notes-help = Elektu kiam ĝisdatigi ekzistan noton en via kolekto. Implicite tio ĉi estas farita, kiam la kongrua enportata noto estis antaŭe modifita.
importing-update-notetypes = Ĝisdatigi nototipon
importing-update-notetypes-help = Elektu kiam ĝisdatigi ekzistan nototipon en via kolekto. Implicite tio ĉi fariĝos, kiam la kongrua enportata nototipo estis antaŭe modifita. Ŝanĝoj al teksto de ŝablono kaj al stilo ĉiam povas esti enportitaj; sed por enporti ŝanĝojn al skemo (ekz. ŝanĝita nombro da kolumnoj), la agordo “{ importing-merge-notetypes }” devas esti aktiva.
importing-note-added =
    { $count ->
        [one] aldonis { $count } noton
       *[other] aldonis { $count } notojn
    }
importing-note-imported =
    { $count ->
        [one] enportis { $count } noton.
       *[other] enportis { $count } notojn.
    }
importing-note-unchanged =
    { $count ->
        [one] ne ŝanĝis { $count } noton
       *[other] ne ŝanĝis { $count } notojn
    }
importing-note-updated =
    { $count ->
        [one] ĝisdatigis { $count } noton
       *[other] ĝisdatigis { $count } notojn
    }
importing-processed-media-file =
    { $count ->
        [one] Prilaboris { $count } aŭdvidaĵon
       *[other] Prilaboris { $count } aŭdvidaĵojn
    }
importing-importing-file = Enportado de dosiero…
importing-extracting = Eltirado de datumoj…
importing-gathering = Kolektado de datumoj…
importing-failed-to-import-media-file = Malsukcesis enporti aŭdvidaĵon: { $debugInfo }
importing-processed-notes =
    { $count ->
        [one] Prilaboris { $count } noton…
       *[other] Prilaboris { $count } notojn…
    }
importing-processed-cards =
    { $count ->
        [one] Prilaboris { $count } karton…
       *[other] Prilaboris { $count } kartojn…
    }
importing-existing-notes = Ekzistaj notoj
# "Existing notes: Duplicate" (verb)
importing-duplicate = duobligi
# "Existing notes: Preserve" (verb)
importing-preserve = ne ŝanĝi
# "Existing notes: Update" (verb)
importing-update = ĝisdatigi
importing-tag-all-notes = Etikedi ĉiujn notojn
importing-tag-updated-notes = Etikedi ĝisdatigitajn notojn
importing-file = Dosiero
# "Match scope: notetype / notetype and deck". Controls how duplicates are matched.
importing-match-scope = Amplekso de kongruo
# Used with the 'match scope' option
importing-notetype-and-deck = nototipo kaj kartaro
importing-cards-added =
    { $count ->
        [one] Aldonis { $count } karton.
       *[other] Aldonis { $count } kartojn.
    }
importing-file-empty = La elektita dosiero estas malplena.
importing-notes-added =
    { $count ->
        [one] Enportis { $count } novan noton.
       *[other] Enportis { $count } novajn notojn.
    }
importing-notes-updated =
    { $count ->
        [one] Uzis { $count } noton por ĝisdatigi ekzistajn notojn.
       *[other] Uzis { $count } notojn por ĝisdatigi ekzistajn notojn.
    }
importing-existing-notes-skipped =
    { $count ->
        [one] { $count } noto jam ekzistas en via kolekto.
       *[other] { $count } notoj jam ekzistas en via kolekto.
    }
importing-notes-failed =
    { $count ->
        [one] Ne povis enporti { $count } noton.
       *[other] Ne povis enporti { $count } notojn.
    }
importing-conflicting-notes-skipped =
    { $count ->
        [one] Ne enportis { $count } noton, ĉar ĝia nototipo ŝanĝiĝis.
       *[other] Ne enportis { $count } notojn, ĉar iliaj nototipoj ŝanĝiĝis.
    }
importing-conflicting-notes-skipped2 =
    { $count ->
        [one] Ne enportis { $count } noton, ĉar ĝia nototipo ŝanĝiĝis kaj la agordo “{ importing-merge-notetypes }” estis malaktiva.
       *[other] Ne enportis { $count } notojn, ĉar iliaj nototipoj ŝanĝiĝis kaj la agordo “{ importing-merge-notetypes }” estis malaktiva.
    }
importing-import-log = Protokolo pri enporto
importing-no-notes-in-file = Trovis neniun noton en la dosiero.
importing-notes-found-in-file2 =
    { $notes ->
        [one] Trovis { $notes } noton en la dosiero:
       *[other] Trovis { $notes } notojn en la dosiero:
    }
importing-show = Vidigi
importing-details = Detaloj
importing-status = Stato
importing-duplicate-note-added = Aldonis duobligon de noto
importing-added-new-note = Aldonis novan noton
importing-existing-note-skipped = Preterpasis noton, ĉar ĝia ĝisdata kopio jam estas en via kolekto
importing-note-skipped-update-due-to-notetype = Ne ĝisdatigis noton, ĉar nototipo estis modifita ekde via unua enporto de la noto
importing-note-skipped-update-due-to-notetype2 = Ne ĝisdatigis noton, ĉar nototipo estis modifita ekde via unua enporto de la noto kaj la agordo “{ importing-merge-notetypes }” estis malaktiva
importing-note-updated-as-file-had-newer = Ĝisdatigis noton, ĉar dosiero havis pli novan version
importing-note-skipped-due-to-missing-notetype = Preterpasis noton, ĉar ĝia nototipo mankis
importing-note-skipped-due-to-missing-deck = Preterpasis noton, ĉar ĝia kartaro mankis
importing-note-skipped-due-to-empty-first-field = Preterpasis noton, ĉar ĝia unua kampo estis malplena
importing-field-separator-help =
    Signo apartiganta kampojn en la teksta dosiero. Vi povas uzi la antaŭvidon por kontroli, ĉu kampoj estas ĝuste apartigataj.
    
    Rimarku, ke se tiu ĉi signo aperos en iu kampo, tiu ĉi kampo devas esti inter citiloj laŭ la normo CSV. Tabelkalkuliloj kiel LibreOffice kutime faras tion ĉi aŭtomate.
    
    Ĝi estas neŝanĝebla, se la teksta dosiero devigas uzi la difinitan apartigilon en la dosierkapo.Se dosierkapo mankas, Anki provos diveni la apartigilon.
importing-allow-html-in-fields-help = Aktivigu tiun ĉi agordon, se la dosiero enhavas HTML-datum-aranĝon, ekzemple se dosiero enhavas la tekstĉenon “&lt;br&gt;”, ĝi montriĝos kiel nova linio sur via karto. Se tiu ĉi agordo restos malaktiva, sur la karto montriĝos la signoj “&lt;br&gt;”.
importing-notetype-help =
    Nove enportitaj notoj havos tiun ĉi nototipon kaj nur ekzistaj notoj kun tiu ĉi nototipo estos ĝisdatigitaj.
    
    Vi povas akordi kiuj kampoj en la dosiero respondu al kiuj kampoj de la nototipo per la ilo “Mapigi kampojn”.
importing-deck-help = Kartoj estos enportitaj al tiu ĉi kartaro.
importing-existing-notes-help =
    Kion fari, se la enportata noto jam estas en la kolekto.
    
     - `{ importing-update }`: ĝisdatigi la ekzistan noton.
     - `{ importing-preserve }`: preterpasi.
     - `{ importing-duplicate }`: krei novan noton.
importing-match-scope-help = Nur ekzistaj notoj de la sama nototipo estos kontrolataj pri duobligoj. Tio ĉi povas esti limigita al notoj kun kartoj en la sama kartaro.
importing-tag-all-notes-help = Etikedoj aldonotaj al kaj nove enportitaj kaj al ĝisdatigitaj notoj.
importing-tag-updated-notes-help = Etikedoj aldonotaj al ĉiuj ĝisdatigitaj notoj.
importing-overview = Sumigo

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

