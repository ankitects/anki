scheduling-time-span-seconds = { $amount ->
    [one] {$amount} tasint
   *[other] {$amount} tasinin
  }
scheduling-time-span-minutes = { $amount ->
    [one] {$amount} tasdat
   *[other] {$amount} tisdatin
  }
scheduling-time-span-hours = { $amount ->
    [one] {$amount} asrag
   *[other] {$amount} isragen
  }
scheduling-time-span-days = { $amount ->
    [one] {$amount} ass
   *[other] {$amount} ussan
  }
scheduling-time-span-months = { $amount ->
    [one] {$amount} aggur
   *[other] {$amount} agguren
  }
scheduling-time-span-years = { $amount ->
    [one] {$amount} aseggas
   *[other] {$amount} iseggasen
  }
scheduling-days = ussan
scheduling-description = Aseglem
scheduling-end = (tagara)
scheduling-general = Amatu
scheduling-order = Amizzwer
scheduling-parent-limit = (talast tamarawt: { $val })
scheduling-review = Cegger
scheduling-seconds = tasinin
scheduling-steps-in-minutes = Imecwaṛen (s tesdatin)
scheduling-steps-must-be-numbers = Yesssefk imecwaṛen ad ilin d imḍanen.
scheduling-deck-updated = { $count ->
    [one] { $count } ukemmus yettwalqem.
   *[other] { $count } ikemmusen  ttwaleqmen.
  }
