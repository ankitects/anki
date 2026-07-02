database-check-card-properties =
    { $count ->
        [one] Opravených { $count } kariet s neplatnými vlastnosťami.
        [few] Opravená { $count } karta s neplatnými vlastnosťami.
        [many] Opravené { $count } karty s neplatnými vlastnosťami.
       *[other] Opravené { $count } karty s neplatnými vlastnosťami.
    }
database-check-corrupt = Balíček je poškodený. Prosím pozrite sa do manuálu.
database-check-missing-templates =
    { $count ->
        [one] Odstránených { $count } kariet s chýbajúcou šablónou.
        [few] Odstránená { $count } karta s chýbajúcou šablónou.
        [many] Odstránené { $count } karty s chýbajúcou šablónou.
       *[other] Odstránené { $count } karty s chýbajúcou šablónou.
    }
database-check-rebuilt = Databáza bola zrekonštruovaná a optimalizovaná.
database-check-card-missing-note = { $count ->
    [one] Odstránených {$count} kariet s chýbajúcou poznámkou.
    [few] Odstránená {$count} karta s chýbajúcou poznámkou.
    [many] Odstránené {$count} karty s chýbajúcou poznámkou.
   *[other] Odstránené {$count} karty s chýbajúcou poznámkou.
  }
