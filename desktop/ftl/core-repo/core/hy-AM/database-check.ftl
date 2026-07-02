database-check-card-properties =
    { $count ->
        [one] Սխալ հատկություններով { $count } քարտը շտկված է:
       *[other] Սխալ հատկություններով { $count } քարտը շտկված է:
    }
database-check-corrupt = Հավաքածուն վնասված է: Մանրամասների համար կարդացեք ձեռնարկը:
database-check-missing-templates =
    { $count ->
        [one] Ջնջվեց { $count } բացակայող կաղապարով քարտը:
       *[other] Ջնջվեց { $count } բացակայող կաղապարով քարտը:
    }
database-check-rebuilt = Շտեմարանը կառուցված և լավարկված է:
database-check-card-missing-note = { $count ->
    [one] Ջնջվեց {$count} բացակայող գրառումով քարտը:
   *[other] Ջնջվեց {$count} բացակայող գրառումով քարտը:
  }
