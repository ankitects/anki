exporting-all-decks = All Decks
exporting-anki-20-deck = Anki 2.0 Deck
exporting-anki-collection-package = Anki Collection Package
exporting-anki-deck-package = Anki Deck Package
exporting-cards-in-plain-text = Cards in Plain Text
exporting-collection = collection
exporting-collection-exported = Collection exported.
exporting-colpkg-too-new = Please update to the latest Anki version, then import the .colpkg/.apkg file again.
exporting-couldnt-save-file = Couldn't save file: { $val }
exporting-export = Export...
exporting-export-format = <b>Export format</b>:
exporting-include = <b>Include</b>:
exporting-include-html-and-media-references = Include HTML and media references
exporting-include-html-and-media-references-help =
    If enabled, markup and media information will be kept. Suitable if the file is intended to be reimported by Anki
    or another app that can render HTML.
exporting-include-media = Include media
exporting-include-media-help = If enabled, referenced media files will be bundled.
exporting-include-scheduling-information = Include scheduling information
exporting-include-scheduling-information-help =
    If enabled, study data like your review history and card intervals will be exported.
    Unsuitable for sharing decks with others.
exporting-include-deck-configs = Include deck presets
exporting-include-deck-configs-help =
    If enabled, your deck option prests will be exported. However, the default preset is *never* shared.
exporting-include-tags = Include tags
exporting-include-tags-help = If enabled, an additional column with note tags will be included.
exporting-support-older-anki-versions = Support older Anki versions (slower/larger files)
exporting-support-older-anki-versions-help =
    If enabled, the resulting file may also be imported by some outdated Anki clients, but it will
    be larger, and importing and exporting will take longer.
exporting-notes-in-plain-text = Notes in Plain Text
exporting-selected-notes = Selected Notes
exporting-card-exported =
    { $count ->
        [one] { $count } card exported.
       *[other] { $count } cards exported.
    }
exporting-exported-media-file =
    { $count ->
        [one] Exported { $count } media file
       *[other] Exported { $count } media files
    }
exporting-note-exported =
    { $count ->
        [one] { $count } note exported.
       *[other] { $count } notes exported.
    }
exporting-exporting-file = Exporting file...
exporting-processed-media-files =
    { $count ->
        [one] Processed { $count } media file...
       *[other] Processed { $count } media files...
    }
exporting-include-deck = Include deck name
exporting-include-deck-help =
    If enabled, an additional column with deck names will be included. This will allow
    the importer to sort cards into the intended decks.
exporting-include-notetype = Include notetype name
exporting-include-notetype-help =
    If enabled, an additional column with notetype names will be included. This will allow
    the importer to assign notes the intended notetypes.
exporting-include-guid = Include unique identifier
exporting-include-guid-help =
    If enabled, an additional column with unique notetype identifiers will be included.
    This allows to identify and update the exact orignal notes when the file is later reimported.
exporting-format = Format
exporting-content = Content
exporting-format-help =
    Anki supports multiple file formats for differing use cases:
    
    - `{ exporting-anki-collection-package }`: Contains your entire collection. Useful for back-ups or moving between devices.
    - `{ exporting-anki-deck-package }`: Lets you control exactly which notes and what data to include. Ideal for sharing decks with other users.
    - `{ exporting-notes-in-plain-text }`: Converts notes into the universal CSV format, readable by many third-party tools like text editors or spreadsheet apps.
    - `{ exporting-cards-in-plain-text }`: Converts the rendered front and back sides of cards into CSV format.
