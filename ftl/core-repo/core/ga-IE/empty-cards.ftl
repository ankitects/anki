empty-cards-for-note-type = Cártaí folmha de chuid { $notetype }:
empty-cards-count-line =
    { $existing_count ->
        [one] { $existing_count } chárta folamh amháin ({ $template_names }).
        [two] { $empty_count } as { $existing_count } chárta fholamh ({ $template_names }).
        [few] { $empty_count } as { $existing_count } chárta folamh ({ $template_names }).
        [many] { $empty_count } as { $existing_count } gcárta folamh ({ $template_names }).
       *[other] { $empty_count } as { $existing_count } cárta folamh ({ $template_names }).
    }
empty-cards-window-title = Cártaí Folmha
empty-cards-preserve-notes-checkbox = Coinnigh nótaí nach bhfuil cárta bailí acu.
empty-cards-delete-button = Scrios
empty-cards-not-found = Níl aon chárta folamh.
empty-cards-deleted-count =
    Scriosadh  { $cards ->
        [one] { $cards } chárta amháin.
        [two] { $cards } chárta.
        [few] { $cards } chárta.
        [many] { $cards } gcárta.
       *[other] { $cards } cárta.
    }
empty-cards-delete-empty-cards = Scrios Cártaí Folmha
empty-cards-delete-empty-notes = Scrios Nótaí Folmha
empty-cards-deleting = Ag scriosadh...
