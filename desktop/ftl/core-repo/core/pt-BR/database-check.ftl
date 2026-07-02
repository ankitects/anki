database-check-corrupt = O arquivo da coleção está corrompido. Por favor, restaure a partir de um backup automático.
database-check-rebuilt = Banco de dados reconstruído e otimizado.
database-check-card-properties =
    { $count ->
        [one] { $count } propriedade inválida de cartão foi consertada.
       *[other] { $count } propriedades inválidas de cartão foram consertadas.
    }
database-check-card-last-review-time-empty =
    { $count ->
        [one] Adicionado último horário de revisão a { $count } cartão.
       *[other] Adicionado último horário de revisão a { $count } cartões.
    }
database-check-missing-templates =
    { $count ->
        [one] Excluído { $count } cartão com o modelo perdido.
       *[other] Excluídos { $count } cartões com o modelo perdido.
    }
database-check-field-count =
    { $count ->
        [one] Consertada { $count } nota com contagem do campo errada.
       *[other] Consertadas { $count } notas com contagem do campo errada.
    }
database-check-new-card-high-due =
    { $count ->
        [one] { $count } novo cartão encontrado com um número de vencimento >= 1,000,000 - considere reposicioná-lo na janela do Painel.
       *[other] { $count } novos cartões encontrados com um número de vencimento >= 1,000,000 - considere reposicioná-los na janela do Painel.
    }
database-check-card-missing-note =
    { $count ->
        [one] Excluído { $count } cartão com nota faltando.
       *[other] Excluído { $count } cartões com nota faltando.
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] Excluído { $count } cartão com modelo duplicado.
       *[other] Excluídos { $count } cartões com modelo duplicado.
    }
database-check-missing-decks =
    { $count ->
        [one] Consertado { $count } baralho perdido.
       *[other] Consertados { $count } baralhos perdidos.
    }
database-check-revlog-properties =
    { $count ->
        [one] Consertado { $count } entrada de revisão com propriedades inválidas.
       *[other] Consertados { $count } entradas de revisão com propriedades inválidas.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] { $count } nota corrigida com caracteres UTF-8 inválidos.
       *[other] { $count } notas corrigidas com caracteres UTF-8 inválidos.
    }
database-check-fixed-invalid-ids =
    { $count ->
        [one] Corrigido { $count } objeto com registros de data/hora futuros.
       *[other] Corrigido { $count } objetos com registros de data/hora futuros.
    }
# "db-check" is always in English
database-check-notetypes-recovered = Faltava um ou mais tipos de notas. As notas que usavam esses tipos ausentes receberam novos tipos de notas começando com "db-check", mas os nomes de campo e o design do cartão foram perdidos, por isso talvez seja melhor restaurar a partir de uma cópia de segurança automática.

## Progress info

database-check-checking-integrity = Verificando coleção...
database-check-rebuilding = Recriando...
database-check-checking-cards = Verificando cartões...
database-check-checking-notes = Verificando notas...
database-check-checking-history = Verificando histórico...
database-check-title = Verificando Banco de Dados
