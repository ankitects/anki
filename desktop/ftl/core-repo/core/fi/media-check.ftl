## Shown at the top of the media check screen

media-check-window-title = Tarkista media
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    Roskakori: { $count ->
        [one] { $count } tiedosto, { $megs } Mt
       *[other] { $count } tiedostoa, { $megs } Mt
    }
media-check-missing-count = Puuttuvia tiedostoja: { $count }
media-check-unused-count = Käyttämättömiä tiedostoja: { $count }
media-check-renamed-count = Uudelleennimettyjä tiedostoja: { $count }
media-check-oversize-count = Yli 100 Mt:n kokoisia: { $count }
media-check-subfolder-count = Alikansioita: { $count }
media-check-extracted-count = Purettuja kuvia: { $count }

## Shown at the top of each section

media-check-renamed-header = Joitakin tiedostoja on nimetty uudelleen yhteensopivuuden vuoksi:
media-check-oversize-header = Yli 100 Mt:n tiedostoja ei voida synkronoida AnkiWebiin.
media-check-subfolder-header = Mediakansiossa olevia kansioita ei tueta.
media-check-missing-header = Käytetty korteissa mutta puuttuu mediakansiosta:
media-check-unused-header = Löytyy mediakansiosta, mutta ei käytössä missään korteissa:
media-check-template-references-field-header = Anki ei voi tunnistaa käytettyjä tiedostoja, kun käytät { "{{Field}}" } -viittauksia media-/LaTeX-tunnisteissa. Media-/LaTeX-tunnisteet tulisi sen sijaan sijoittaa yksittäisiin muistiinpanoihin.

## Shown once for each file

media-check-renamed-file = Nimetty uudelleen: { $old } -> { $new }
media-check-oversize-file = Yli 100 Mt: { $filename }
media-check-subfolder-file = Kansio: { $filename }
media-check-missing-file = Puuttuu: { $filename }
media-check-unused-file = Käyttämätön: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = Tarkistettu { $count }...

## Deleting unused media

media-check-delete-unused-confirm = Poistetaanko käyttämätön media?
media-check-files-remaining =
    { $count ->
        [one] { $count } tiedosto
       *[other] { $count } tiedostoa
    } jäljellä.
media-check-delete-unused-complete =
    { $count ->
        [one] { $count } tiedosto
       *[other] { $count } tiedostoa
    } siirretty roskakoriin.
media-check-trash-emptied = Roskakori on nyt tyhjä.
media-check-trash-restored = Poistetut tiedostot palautettiin mediakansioon.

## Rendering LaTeX

media-check-all-latex-rendered = Kaikki LaTeX-merkinnät renderöity.

## Buttons

media-check-delete-unused = Poista käyttämättömät tiedostot
media-check-render-latex = Renderöi LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = Tyhjennä roskakori
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Palauta poistetut
media-check-check-media-action = Tarkista mediatiedostot
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = media-puuttuu
# add a tag to notes with missing media
media-check-add-tag = Lisää 'media puuttuu' -tunniste
