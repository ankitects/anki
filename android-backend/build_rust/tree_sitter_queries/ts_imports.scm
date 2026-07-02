; Match typical import of a backend function in Anki, following this format:
; import { importDone } from "@generated/backend";
(import_statement
    (import_clause
        (named_imports
            (import_specifier
                ; First capture
                name: (identifier) @name
            )
        )
    )
    ; Second capture
    source: (string) @src
)

; Add more ways of matching imports as necessary.