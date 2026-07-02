importing-failed-debug-info = Importação falhou. Informações para depuração:
importing-aborted = Abortado: { $val }
importing-added-duplicate-with-first-field = Duplicata adicionada com o primeiro campo: { $val }
importing-all-supported-formats = Todos os formatos suportados { $val }
importing-allow-html-in-fields = Permitir HTML em campos
importing-anki-files-are-from-a-very = Os arquivos .anki são de uma versão muito antiga do Anki. Você pode importá-los com a extensão 175027074 ou com o Anki 2.0, disponível no site do Anki.
importing-anki2-files-are-not-directly-importable = arquivos .anki2 não são diretamente importáveis - por favor, ao invés disso, importe o arquivo .apkg ou .zip que você recebeu
importing-appeared-twice-in-file = Aparece 2 vezes no arquivo: { $val }
importing-by-default-anki-will-detect-the = Por padrão, o Anki detecta o caractere entre os campos, como um tab, vírgula, etc. Se o Anki estiver detectando incorretamente, você pode digitá-lo aqui. Use \t para representar tab.
importing-cannot-merge-notetypes-of-different-kinds =
    Tipos de nota Cloze não podem ser mesclados com tipos de nota regulares.
    Você ainda pode importar o arquivo com '{ importing-merge-notetypes }' desativado.
importing-change = Alterar
importing-colon = Dois pontos
importing-comma = Vírgula
importing-empty-first-field = Primeiro campo vazio: { $val }
importing-field-separator = Separador de campo
importing-field-separator-guessed = Separador de campo (detectado auto)
importing-field-mapping = Mapeamento de campo
importing-field-of-file-is = Campo <b>{ $val }</b> do arquivo é:
importing-fields-separated-by = Campos separados por: { $val }
importing-file-must-contain-field-column = O arquivo deve conter pelo menos uma coluna que possa ser mapeada para um campo de nota.
importing-file-version-unknown-trying-import-anyway = Versão do arquivo desconhecida, tentando importar de qualquer maneira.
importing-first-field-matched = Primeiro campo encontrado: { $val }
importing-identical = Idêntico
importing-ignore-field = Ignorar o campo
importing-ignore-lines-where-first-field-matches = Ignorar linhas onde o primeiro campo corresponda a uma nota existente.
importing-ignored = <ignorado>
importing-import-even-if-existing-note-has = Importar mesmo que existam notas com o primeiro campo igual
importing-import-options = Opções de importação
importing-importing-complete = Importação completa.
importing-invalid-file-please-restore-from-backup = Arquivo inválido. Por favor, restaure a partir da cópia de segurança.
importing-map-to = Mapear para { $val }
importing-map-to-tags = Mapear para Etiquetas
importing-mapped-to = mapeado para <b>{ $val }</b>
importing-mapped-to-tags = <b>Etiquetas</b> mapeadas
# the action of combining two existing note types to create a new one
importing-merge-notetypes = Tipo de Cartão Mesclar
importing-merge-notetypes-help =
    Se marcado, e você ou o autor do baralho alterarem o esquema de um tipo de nota, o Anki irá
    mesclar as duas versões em vez de manter ambas.
    
    Alterar o esquema de um tipo de nota significa adicionar, remover, ou reordenar campos ou modelos,
    ou alterar o campo de ordenação.
    Como contraexemplo, alterar o lado frontal de um modelo existente *não* constitui
    uma alteração de esquema.
    
    Aviso: Isso exigirá uma sincronização unidirecional, e pode marcar notas existentes como modificadas.
importing-mnemosyne-20-deck-db = Baralho Mnemosyne 2.0 (*.db)
importing-multicharacter-separators-are-not-supported-please = Separadores multi-caractere não são suportados. Por favor, digite apenas um caractere.
importing-new-deck-will-be-created = Um novo deck será criado: { $name }
importing-notes-added-from-file = Notas adicionadas do arquivo: { $val }
importing-notes-found-in-file = Notas encontradas no arquivo: { $val }
importing-notes-skipped-as-theyre-already-in = Notas ignoradas, pois já estão em sua coleção: { $val }
importing-notes-skipped-update-due-to-notetype = Notas não atualizadas, pois o tipo de nota foi modificado desde que você importou as notas pela primeira vez: { $val }
importing-notes-updated-as-file-had-newer = Notas atualizadas, pois o arquivo tinha uma versão mais recente: { $val }
importing-include-reviews = Incluir revisões
importing-also-import-progress = Importar também qualquer progresso de aprendizagem
importing-with-deck-configs = Importar qualquer configuração de baralho
importing-updates = Atualizações
importing-include-reviews-help =
    Se ativado, quaisquer revisões anteriores incluídas pelo compartilhador do baralho também serão importadas.
    Caso contrário, todos os cartões serão importados como novos cartões.
importing-with-deck-configs-help =
    Se ativado, todas as opções de baralho incluídas pelo compartilhador do baralho também serão importadas.
    Caso contrário, todos os baralhos serão atribuídos ao preset padrão.
importing-packaged-anki-deckcollection-apkg-colpkg-zip = Pacote de Baralho/Coleção do Anki (*.apkg *.colpkg *.zip)
# the '|' character
importing-pipe = Pipe
# Warning displayed when the csv import preview table is clipped (some columns were hidden)
# $count is intended to be a large number (1000 and above)
importing-preview-truncated =
    { $count ->
        [one] Apenas as primeiras { $count } colunas são exibidas. Se isso não parecer correto, tente alterar o separador de campo.
       *[other] { "" }
    }
importing-rows-had-num1d-fields-expected-num2d = '{ $row }' tem { $found } campos, de { $expected } esperados
importing-selected-file-was-not-in-utf8 = O arquivo selecionado não encontra-se no formato UTF-8. Por favor, veja no manual como fazer a importação corretamente.
importing-semicolon = Ponto e vírgula
importing-skipped = Ignorado
importing-tab = Tab
importing-tag-modified-notes = Notas com etiquetas modificadas:
importing-text-separated-by-tabs-or-semicolons = Texto separado por tabs ou ponto e vírgula (*)
importing-the-first-field-of-the-note = O primeiro campo do tipo de nota deve ser mapeado.
importing-the-provided-file-is-not-a = O arquivo fornecido não é um arquivo .apkg válido.
importing-this-file-does-not-appear-to = Este arquivo não parece ser um arquivo .apkg válido. Se estiver recebendo este erro de um arquivo baixado do AnkiWeb, é provável que seu download tenha falhado. Tente novamente e, se o problema persistir, tente com um navegador diferente
importing-this-will-delete-your-existing-collection = Isto apagará sua coleção existente e substituirá os dados pelos do arquivo importado. Você tem certeza?
importing-unable-to-import-from-a-readonly = Não é possível importar de um arquivo somente de leitura.
importing-unknown-file-format = Formato de arquivo desconhecido.
importing-update-existing-notes-when-first-field = Atualizar notas existentes quando o primeiro campo coincidir
importing-updated = Atualizado
importing-update-if-newer = Se mais recente
importing-update-always = Sempre
importing-update-never = Nunca
importing-update-notes = Atualizar notas
importing-update-notes-help =
    Quando atualizar um cartão existente em sua coleção. Por padrão, isso é feito apenas
    se o cartão importado correspondente foi modificado mais recentemente.
importing-update-notetypes = Atualizar tipo de nota
importing-update-notetypes-help =
    { importing-merge-notetypes }Quando atualizar um tipo de nota existente em sua coleção. Por padrão, isso é feito apenas
    se o tipo de nota importado correspondente foi modificado mais recentemente. Mudanças no texto do modelo
    e estilização sempre podem ser importadas, mas para mudanças de esquema (por exemplo, o número ou ordem dos
    campos mudou), a opção '{ importing-merge-notetypes }' também precisará estar ativada.
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
        [one] Processados { $count } arquivos de mídia
       *[other] Processados { $count } arquivos de mídia
    }
importing-importing-file = Importando arquivo...
importing-extracting = Extraindo dados...
importing-gathering = Juntando informação...
importing-failed-to-import-media-file = Falha ao importar arquivo de mídia: { $debugInfo }
importing-processed-notes =
    { $count ->
        [one] Processada { $count } nota...
       *[other] Processadas { $count } notas...
    }
importing-processed-cards =
    { $count ->
        [one] { $count } Cartão processado
       *[other] { $count } Cartões processados
    }
importing-existing-notes = Notas existentes
# "Existing notes: Duplicate" (verb)
importing-duplicate = Duplicar
# "Existing notes: Preserve" (verb)
importing-preserve = Preservar
# "Existing notes: Update" (verb)
importing-update = Atualizar
importing-tag-all-notes = Marcar todas as notas
importing-tag-updated-notes = Marcas a notas atualizadas
importing-file = Arquivo
# "Match scope: notetype / notetype and deck". Controls how duplicates are matched.
importing-match-scope = Âmbito de Correspondência
# Used with the 'match scope' option
importing-notetype-and-deck = Tipo de nota e baralho
importing-cards-added =
    { $count ->
        [one] { $count } cartão adicionado.
       *[other] { $count } cartões adicionados.
    }
importing-file-empty = O arquivo que você selecionou está vazio.
importing-notes-added =
    { $count ->
        [one] { $count } nova nota importada.
       *[other] { $count } novas notas importadas.
    }
importing-notes-updated =
    { $count ->
        [one] { $count } nota foi usada para atualizar as existentes.
       *[other] { $count } notas foram usadas para atualizar as existentes.
    }
importing-existing-notes-skipped =
    { $count ->
        [one] { $count } nota já está presente em sua coleção.
       *[other] { $count } notas já estão presentes em sua coleção.
    }
importing-notes-failed =
    { $count ->
        [one] { $count } nota não pôde ser importada.
       *[other] { $count } notas não puderam ser importadas.
    }
importing-conflicting-notes-skipped =
    { $count ->
        [one] { $count } nota não foi importada, pois o tipo de nota foi alterado.
       *[other] { $count } notas não foram importadas, pois seus tipos de nota foram alterados.
    }
importing-conflicting-notes-skipped2 =
    { $count ->
        [one] { $count } nota não foi importada, pois seu tipo de nota foi alterado, e '{ importing-merge-notetypes }' não estava ativado.
       *[other] { $count } notas não foram importadas, pois seus tipos de nota foram alterados, e '{ importing-merge-notetypes }' não estava ativado.
    }
importing-import-log = Log de Importação
importing-no-notes-in-file = Nenhuma nota encontrada no arquivo.
importing-notes-found-in-file2 =
    { $notes ->
        [one] { $notes } nota
       *[other] { $notes } notas
    } encontradas no arquivo. Dentre essas:
importing-show = Mostrar
importing-details = Detalhes
importing-status = Status
importing-duplicate-note-added = Nota duplicada adicionada
importing-added-new-note = Nova nota adicionada
importing-existing-note-skipped = Nota ignorada, pois uma cópia atualizada já está em sua coleção
importing-note-skipped-update-due-to-notetype = Nota não atualizada, pois o tipo de nota foi modificado desde que você importou a nota pela primeira vez
importing-note-skipped-update-due-to-notetype2 = A nota não foi atualizada, pois o tipo de nota foi modificado desde a primeira importação, e '{ importing-merge-notetypes }' não estava ativado.
importing-note-updated-as-file-had-newer = Nota atualizada, pois o arquivo tinha uma versão mais nova
importing-note-skipped-due-to-missing-notetype = Nota ignorada, pois seu tipo de nota estava ausente
importing-note-skipped-due-to-missing-deck = Nota ignorada, pois seu baralho estava ausente
importing-note-skipped-due-to-empty-first-field = Nota ignorada, pois seu primeiro campo está vazio
importing-field-separator-help =
    O caractere que separa os campos no arquivo de texto. Você pode usar a visualização para verificar
    se os campos estão separados corretamente.
    
    Observe que, se esse caractere aparecer em qualquer campo em si, o campo deve ser
    citado de acordo com o padrão CSV. Programas de planilha, como o LibreOffice, farão
    isso automaticamente.
importing-allow-html-in-fields-help =
    Ative isso se o arquivo contiver formatação HTML. Por exemplo, se o arquivo contiver a string
    '&lt;br&gt;', ela aparecerá como uma quebra de linha no seu cartão. Por outro lado, com esta
    opção desativada, os caracteres literais '&lt;br&gt;' serão renderizados.
importing-notetype-help =
    Notas recém-importadas terão este tipo de nota, e apenas notas existentes com este
    tipo de nota serão atualizadas.
    
    Você pode escolher quais campos no arquivo correspondem a quais campos do tipo de nota com a
    ferramenta de mapeamento.
importing-deck-help = Os cartões importados serão colocados neste baralho.
importing-existing-notes-help =
    O que fazer se uma nota importada corresponder a uma existente.
    
    - `{ importing-update }`: Atualizar a nota existente.
    - `{ importing-preserve }`: Não fazer nada.
    - `{ importing-duplicate }`: Criar uma nova nota.
importing-match-scope-help =
    Somente as notas existentes com o mesmo tipo de nota serão verificadas quanto a duplicatas. Isso pode
    adicionalmente ser restrito a notas com cartões no mesmo baralho.
importing-tag-all-notes-help = Essas tags serão adicionadas tanto em notas recém-importadas quanto nas atualizadas
importing-tag-updated-notes-help = Estas tags serão adicionadas a qualquer nota atualizada.
importing-overview = Visão geral

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

importing-importing-collection = Importando coleção...
importing-unable-to-import-filename = Não foi possível importar { $filename }: tipo de arquivo não suportado
importing-notes-that-could-not-be-imported = Notas que não pudiam ser importadas conforme o tipo de nota foram alteradas: { $val }
importing-added = Adicionado
importing-pauker-18-lesson-paugz = Pauker Lição 1.8 (*.pau.gz)
importing-supermemo-xml-export-xml = Exportação em Supermemo XML (*.xml)
