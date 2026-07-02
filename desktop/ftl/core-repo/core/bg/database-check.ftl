database-check-card-properties =
    { $count ->
        [one] Поправена { $count } карта с невалидни атрибути.
       *[other] Поправени { $count } карти с невалидни атрибути.
    }
database-check-corrupt = Колекцията е повередена. Моля, проверете в ръководството.
database-check-missing-templates =
    { $count ->
        [one] Беше изтрита { $count } карта с липсващ шаблон.
       *[other] Бяха изтрити { $count } карти с липсващ шаблон
    }
database-check-rebuilt = Базата данни беше създадена наново и оптимизирана.
database-check-card-missing-note = { $count ->
    [one] Беше изтрита {$count} карт с липсващи бележки
   *[other] Бяха изтрити {$count} карти с липсващи бележки
  }
