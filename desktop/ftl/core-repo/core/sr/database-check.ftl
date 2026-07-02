database-check-card-properties =
    { $count ->
        [one] Фиксирана { $count } картица са неважећим својствима.
        [few] Фиксиране { $count } картице са неважећим својствима.
       *[other] Фиксиране { $count } картице са неважећим својствима.
    }
database-check-corrupt = Колекција је оштећена. Погледајте упутство.
database-check-missing-templates =
    { $count ->
        [one] Избрисана { $count } карта са непостојећим шаблоном.
        [few] Избрисане { $count } карте са непостојећим шаблоном.
       *[other] Избрисано { $count } карата са непостојећим шаблоном.
    }
database-check-rebuilt = База података је обновљена и оптимизована.
database-check-card-missing-note = { $count ->
    [one] Избрисана {$count} карта са непостојећом белешком.
    [few] Избрисане {$count} карте са непостојећом белешком.
   *[other] Избрисано {$count} карата са непостојећом белешком.
  }
