update cards
set factor = 2500,
    usn = ?,
    mod = ?
where factor != 0
    and factor <= 2000
    and (
        did in DECK_IDS
        or odid in DECK_IDS
    )