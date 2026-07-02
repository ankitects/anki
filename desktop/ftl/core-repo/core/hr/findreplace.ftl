findreplace-notes-updated =
    { $total ->
        [one]
            { $changed ->
                [one] { $changed } od { $total } bilješke je aktualizirana
                [few] { $changed } od { $total } bilješke su aktualizirane
               *[other] { $changed } od { $total } bilješke je aktualizirano
            }
        [few]
            { $changed ->
                [one] { $changed } od { $total } bilješke je aktualizirana
                [few] { $changed } od { $total } bilješke su aktualizirane
               *[other] { $changed } od { $total } bilješke je aktualizirano
            }
       *[other]
            { $changed ->
                [one] { $changed } od { $total } bilješki je aktualizirana
                [few] { $changed } od { $total } bilješki su aktualizirane
               *[other] { $changed } od { $total } bilješki je aktualizirano
            }
    }
