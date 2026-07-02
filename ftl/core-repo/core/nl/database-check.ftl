database-check-card-properties =
    { $count ->
        [one] { $count } kaart met ongeldige eigenschappen hersteld.
       *[other] { $count } kaarten met ongeldige eigenschappen hersteld.
    }
database-check-corrupt = Collectie is beschadigd. Gelieve de handleiding te raadplegen.
database-check-missing-templates =
    { $count ->
        [one] { $count } kaart met ontbrekend sjabloon verwijderd.
       *[other] { $count } kaarten met ontbrekend sjabloon verwijderd.
    }
database-check-rebuilt = Database herbouwd en geoptimaliseerd.
database-check-card-missing-note = { $count ->
    [one] {$count} kaart met ontbrekende aantekening verwijderd.
   *[other] {$count} kaarten met ontbrekende aantekening verwijderd.
  }
