importing-failed-debug-info = Importação falhou. Informações para depuração:
importing-aborted = Abortado: { $val }
importing-added-duplicate-with-first-field = Duplicata adicionada com o primeiro campo: { $val }
importing-allow-html-in-fields = Permitir HTML em campos
importing-anki-files-are-from-a-very = Ficheiros .anki eram utilizados numa versão bastante antiga do Anki. Pode importá-los com o complemento 175027074 ou com o Anki 2.0, disponível no site do Anki.
importing-appeared-twice-in-file = Aparece 2 vezes no arquivo: { $val }
importing-by-default-anki-will-detect-the = Por definição, Anki detecta os caracteres entre os campos, como um tab, vírgula, etc. Se Anki estiver detectando incorretamente, você pode digitá-lo aqui. Use \t para representar tab.
importing-change = Alterar
importing-colon = Dois pontos
importing-comma = Vírgula
importing-empty-first-field = Primeiro campo vazio: { $val }
importing-field-mapping = Mapeamento de campo
importing-field-of-file-is = Campo <b>{ $val }</b> do ficheiro é:
importing-fields-separated-by = Campos separados por: { $val }
importing-first-field-matched = Primeiro campo encontrado: { $val }
importing-ignore-field = Ignorar campo
importing-ignore-lines-where-first-field-matches = Ignorar linhas onde o primeiro campo corresponda a uma nota existente.
importing-ignored = <ignorado>
importing-import-even-if-existing-note-has = Importar mesmo que existam notas com o primeiro campo igual
importing-import-options = Opções de importação
importing-importing-complete = Importação completa.
importing-invalid-file-please-restore-from-backup = Ficheiro inválido. Por favor, restaure a cópia de segurança.
importing-map-to = Mapear para { $val }
importing-map-to-tags = Mapear para Etiquetas
importing-mapped-to = mapeado para <b>{ $val }</b>
importing-mapped-to-tags = mapeado para <b>Etiquetas</b>
importing-mnemosyne-20-deck-db = Baralho Mnemosyne 2.0 (*.db)
importing-pauker-18-lesson-paugz = Pauker Lição 1.8 (*.pau.gz)
# Warning displayed when the csv import preview table is clipped (some columns were hidden)
# $count is intended to be a large number (1000 and above)
importing-preview-truncated =
    { $count ->
       *[other] Apenas as primeiras { $count } colunas serão apresentada. Se algo não lhe parecer correcto, tente alterar o separador de campos.
    }
importing-rows-had-num1d-fields-expected-num2d = '{ $row }' tem { $found } campos, de { $expected } esperados
importing-selected-file-was-not-in-utf8 = O ficheiro selecionado não encontra-se no formato UTF-8. Por favor, veja no manual como fazer a importação corretamente.
importing-semicolon = Ponto e vírgula
importing-supermemo-xml-export-xml = Exportação em Supermemo XML (*.xml)
importing-tab = Tabulación
importing-text-separated-by-tabs-or-semicolons = Texto separado por tabs ou ponto e vírgula (*)
importing-the-first-field-of-the-note = O primeiro campo do tipo de nota deve ser mapeado.
importing-the-provided-file-is-not-a = O ficheiro fornecido não é um ficheiro .apkg válido.
importing-this-file-does-not-appear-to = Este ficheiro não parece ser um ficheiro .apkg válido. Se recebe este erro de um ficheiro descarregado do AnkiWeb, provavelmente a descarga falhou. Por favor, tente novamente, e se o problema persistir, tente com um navegador diferente.
importing-this-will-delete-your-existing-collection = Isto eliminará a sua colecção existente e substituirá os dados pelos do ficheiro importado. Tem certeza?
importing-unable-to-import-from-a-readonly = Não é possível importar de ficheiro somente leitura.
importing-unknown-file-format = Formato de ficheiro desconhecido.
importing-update-existing-notes-when-first-field = Atualizar notas existentes quando o primeiro campo coincidir
importing-note-added =
    { $count ->
        [one] { $count } nota adicionada
       *[other] { $count } notas adicionadas
    }
importing-note-imported =
    { $count ->
        [one] { $count } nota importada.
       *[other] { $count } notas importadas.
    }
importing-note-unchanged =
    { $count ->
        [one] { $count } nota inalterada
       *[other] { $count } notas inalteradas
    }
importing-note-updated =
    { $count ->
        [one] { $count } nota atualizada
       *[other] { $count } notas atualizadas
    }
importing-processed-media-file =
    { $count ->
        [one] { $count } ficheiro de média processado
       *[other] { $count } ficheiros de média processados
    }
importing-cards-added =
    { $count ->
        [one] { $count } ficha adicionada.
       *[other] { $count } fichas adicionadas.
    }

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

importing-added = Adicionado
