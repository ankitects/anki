database-check-corrupt = Zbirka je poškodovana. Preverite uporabniški priročnik.
database-check-rebuilt = Zbirka podatkov ponovno zgrajena in optimirana.
database-check-card-properties =
    { $count ->
        [one] Popravljena { $count } lastnost kartice.
        [two] Popravljeni { $count } lastnosti kartice.
        [few] Popravljene { $count } lastnosti kartice.
       *[other] Popravljenih { $count } lastnosti kartice.
    }
database-check-missing-templates =
    { $count ->
        [one] Izbrisal { $count } kartic z manjkajočo predlogo.
        [two] Izbrisal { $count } kartico z manjkajočo predlogo.
        [few] Izbrisal { $count } kartici z manjkajočo predlogo.
       *[other] Izbrisal { $count } kartice z manjkajočo predlogo.
    }
database-check-field-count =
    { $count ->
        [one] Popravljen { $count } zapisek z napačnim številom vnosnih polj.
        [two] Popravljena { $count } zapiska z napačnim številom vnosnih polj.
        [few] Popravljeni { $count } zapiski z napačnim številom vnosnih polj.
       *[other] Popravljenih { $count } zapiskov z napačnim številom vnosnih polj.
    }
database-check-new-card-high-due =
    { $count ->
        [one] Našli smo { $count } novo kartico z rokom >=1,000,000 - premislite glede premestitve v zaslonu za brskanje.
        [two] Našli smo { $count } novi kartici z rokom >=1,000,000 - premislite glede premestitve v zaslonu za brskanje.
        [few] Našli smo { $count } nove kartice z rokom >=1,000,000 - premislite glede premestitve v zaslonu za brskanje.
       *[other] Našli smo { $count } novih kartic z rokom >=1,000,000 - premislite glede premestitve v zaslonu za brskanje.
    }
database-check-card-missing-note =
    { $count ->
        [one] Izbrisanih kartic z manjkajočim zapiskom: { $count }.
        [two] Izbrisanih kartic z manjkajočim zapiskom: { $count }.
        [few] Izbrisanih kartic z manjkajočim zapiskom: { $count }.
       *[other] Izbrisanih kartic z manjkajočim zapiskom: { $count }.
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] Izbrisanih kartic s podvojeno predlogo: { $count }.
        [two] Izbrisanih kartic s podvojeno predlogo: { $count }.
        [few] Izbrisanih kartic s podvojeno predlogo: { $count }.
       *[other] Izbrisanih kartic s podvojeno predlogo: { $count }.
    }
database-check-missing-decks =
    { $count ->
        [one] Popravljenih manjkajočih zbirk kartic: { $count }.
        [two] Popravljenih manjkajočih zbirk kartic: { $count }.
        [few] Popravljenih manjkajočih zbirk kartic: { $count }.
       *[other] Popravljenih manjkajočih zbirk kartic: { $count }.
    }
database-check-revlog-properties =
    { $count ->
        [one] Popravljenih vnosov za ponavljanje z neveljavnimi lastnostmi: { $count }.
        [two] Popravljenih vnosov za ponavljanje z neveljavnimi lastnostmi: { $count }.
        [few] Popravljenih vnosov za ponavljanje z neveljavnimi lastnostmi: { $count }.
       *[other] Popravljenih vnosov za ponavljanje z neveljavnimi lastnostmi: { $count }.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] Popravljenih zapiskov z neveljavnimi utf8 znaki: { $count }
        [two] Popravljenih zapiskov z neveljavnimi utf8 znaki: { $count }
        [few] Popravljenih zapiskov z neveljavnimi utf8 znaki: { $count }
       *[other] Popravljenih zapiskov z neveljavnimi utf8 znaki: { $count }
    }
# "db-check" is always in English
database-check-notetypes-recovered = Vsaj en tip zapiska manjka. Zapiski, ki so jih uporabljali, so dobili dodeljen nov tip zapiska, ki se pričenja z "db-check", toda imena polj in oblika kartice so bili izgubljeni. Predlagamo, da jih obnovite iz samodejne rezervne kopije.

## Progress info

database-check-checking-integrity = Preverjam kolekcijo...
database-check-rebuilding = Ponovno ustvarjanje...
database-check-checking-cards = Preverjanje kartic...
database-check-checking-notes = Preverjanje zapisov...
database-check-checking-history = Preverjanje zgodovine...
database-check-title = Preveri podatkovno bazo
