importing-ignored = <ignoratum>
importing-note-added =
    { $count ->
        [one] { $count } nota addita
       *[other] { $count } notae additae
    }
importing-note-imported =
    { $count ->
        [one] { $count } nota importata.
       *[other] { $count } notae importatae.
    }
importing-note-unchanged =
    { $count ->
        [one] { $count } nota immutata
       *[other] { $count } notae immutatae
    }
importing-note-updated =
    { $count ->
        [one] { $count } nota mutata
       *[other] { $count } notae mutatae
    }
importing-added = Additum esse
