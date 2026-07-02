database-check-corrupt = A coleção está corrompida. Por favor, veja o manual.
database-check-rebuilt = Banco de dados reconstruído e otimizado.
database-check-card-properties =
    { $count ->
        [one] { $count } ficha com propriedades inválidas foi reparada.
       *[other] { $count } fichas com propriedades inválidas foram reparadas.
    }
database-check-missing-templates =
    { $count ->
        [one] { $count } ficha com o modelo em falta eliminada.
       *[other] { $count } fichas com o modelo em falta eliminadas.
    }
database-check-field-count =
    { $count ->
        [one] Corrigida { $count } nota com o número de campos errado.
       *[other] Corrigidas { $count } notas com o número de campos errado.
    }
database-check-new-card-high-due =
    { $count ->
        [one] Encontrada { $count } ficha nova com com o número de revisão >= 1,000,000 - considere reposicioná-la no ecrã de navegação.
       *[other] Encontradas { $count } fichas novas com com o número de revisão >= 1,000,000 - considere reposicioná-las no ecrã de navegação.
    }
database-check-card-missing-note =
    { $count ->
        [one] { $count } ficha com a nota em falta eliminada.
       *[other] { $count } fichas com a nota em falta eliminadas.
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] Apagada { $count } ficha com modelo duplicado.
       *[other] Apagadas { $count } fichas com modelo duplicado.
    }
database-check-missing-decks =
    { $count ->
        [one] Corrigido { $count } baralho em falta.
       *[other] Corrigidos { $count } baralhos em falta.
    }
database-check-revlog-properties =
    { $count ->
        [one] Corrigida { $count } entrada de revisão com propriedades inválidas.
       *[other] Corrigidas { $count } entradas de revisão com propriedades inválidas.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] Corrigida { $count } nota com caracteres utf8 inválidos.
       *[other] Corrigidas { $count } notas com caracteres utf8 inválidos.
    }
database-check-fixed-invalid-ids =
    { $count ->
        [one] Corrigido { $count } objecto com datas futuras.
       *[other] Corrigidos { $count } objectos com datas futuras.
    }
# "db-check" is always in English
database-check-notetypes-recovered = Um ou mais tipos de ficha desapareceram. As notas que tinham estes tipos foram classificadas com novos tipos começados por "db-check", mas os nomes dos campos e modelos das fichas foram perdidos. Talvez seja melhor restaurar a partir dum cópia de segurança.

## Progress info

database-check-checking-integrity = A verificar colecção...
database-check-rebuilding = A reconstruir...
database-check-checking-cards = A verificar fichas...
database-check-checking-notes = A verificar notas...
database-check-checking-history = A verificar histórico...
database-check-title = Verificar Base de Dados
