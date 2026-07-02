database-check-card-properties =
    { $count ->
        [one] { $count } kaart reggestel met ongeldige eienskappe.
       *[other] { $count } kaarte reggestel met ongeldige eienskappe.
    }
database-check-corrupt = Versameling is korrup. Sien asseblief die handleiding.
database-check-missing-templates =
    { $count ->
        [one] Geskrapte { $count } kaart met vermiste profieelvorm.
       *[other] Geskrapte { $count } kaarte met vermiste profieelvorm.
    }
database-check-rebuilt = Databasis herbou en geoptimaliseer.
database-check-card-missing-note = { $count ->
    [one] Geskrapte {$count} kaart met vermiste nota
   *[other] Geskrapte {$count} kaarte met vermiste nota
  }
