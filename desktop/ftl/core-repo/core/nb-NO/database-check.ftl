database-check-card-properties =
    { $count ->
        [one] Reparerte { $count } kort med ugyldige egenskaper.
       *[other] Reparerte { $count } kort med ugyldige egenskaper.
    }
database-check-corrupt = Samling er korrupt. Venneligst sjekk manualen.
database-check-missing-templates = { $count ->
    [one] Slettet {$count} kort med manglende mal.
   *[other] Slettet {$count} kort med manglende mal.
  }
database-check-card-missing-note = { $count ->
    [one] Slettet {$count} kort med manglende notat.
   *[other] Slettet {$count} kort med manglende notat.
  }
