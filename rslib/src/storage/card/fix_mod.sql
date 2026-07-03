UPDATE cards
SET mod = cast(mod AS integer)
WHERE mod != cast(mod AS integer)