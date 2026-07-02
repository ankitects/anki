## Shown at the top of the media check screen

media-check-window-title = Verificar Mídia
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count =
    Pasta da lixeira: { $count ->
        [one] 1 arquivo, { $megs }MB
       *[other] { $count } arquivos, { $megs }MB
    }
media-check-missing-count = Arquivos perdidos: { $count }
media-check-unused-count = Arquivos não usados: { $count }
media-check-renamed-count = Arquivos renomeados: { $count }
media-check-oversize-count = Acima de 100MB: { $count }
media-check-subfolder-count = Subpastas: { $count }
media-check-extracted-count = Imagens extraídas: { $count }

## Shown at the top of each section

media-check-renamed-header = Alguns arquivos foram renomeados para compatibilidade:
media-check-oversize-header = Arquivos acima de 100MB não podem ser sincronizados com AnkiWeb.
media-check-subfolder-header = As pastas dentro da pasta de mídia não são suportadas.
media-check-missing-header = Os seguintes arquivos são referenciados por cartões, mas não foram encontrados na pasta de mídia:
media-check-unused-header = Os seguintes arquivos foram encontrados na pasta de mídia, mas não parecem ser usados em nenhum cartão:
media-check-template-references-field-header =
    Anki não consegue detectar arquivos usados quando você usa referências { "{{Field}}" } em etiquetas de mídia/LaTeX. As etiquetas de mídia/LaTeX devem ser colocadas em notas individuais.
    
    Modelos de referência:

## Shown once for each file

media-check-renamed-file = Renomeado: { $old } -> { $new }
media-check-oversize-file = Acima de 100MB: { $filename }
media-check-subfolder-file = Pasta: { $filename }
media-check-missing-file = Perdido: { $filename }
media-check-unused-file = Não usado: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = Verificado { $count }...

## Deleting unused media

media-check-delete-unused-confirm = Apagar mídia não utilizada?
media-check-files-remaining =
    { $count ->
        [one] 1 arquivo
       *[other] { $count } arquivos
    }  não usado(s).
media-check-delete-unused-complete =
    { $count ->
        [one] 1 arquivo
       *[other] { $count } arquivos
    } movido para a lixeira.
media-check-trash-emptied = A pasta da lixeira agora está vazia.
media-check-trash-restored = Arquivos excluídos foram restaurados para a pasta de mídia.

## Rendering LaTeX

media-check-all-latex-rendered = Todo LaTeX renderizado.

## Buttons

media-check-delete-unused = Apagar Arquivos Não Utilizados
media-check-render-latex = Renderizar LaTeX
# button to permanently delete media files from the trash folder
media-check-empty-trash = Esvaziar Lixeira
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = Restaurar Excluído
media-check-check-media-action = Verificação e Mídia
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = mídia-ausente
# add a tag to notes with missing media
media-check-add-tag = Etiquetar ausente
