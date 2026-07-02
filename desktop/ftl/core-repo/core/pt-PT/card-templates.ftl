# This word is used by TTS voices instead of the elided part of a cloze.
card-templates-blank = vazia
card-templates-changes-will-affect-notes =
    { $count ->
        [one] As alterações abaixo irão impactar a { $count } nota que usa este tipo de ficha.
       *[other] As alterações abaixo irão impactar as { $count } notas que usam este tipo de ficha.
    }
card-templates-card-type = Tipo de ficha:
card-templates-front-template = Modelo da Frente
card-templates-back-template = Modelo do Verso
card-templates-template-styling = Estilo
card-templates-front-preview = Visualizar a Frente
card-templates-back-preview = Visualizar o Verso
card-templates-preview-box = Pré-visualização
card-templates-template-box = modelo
card-templates-sample-cloze = Isto é uma { "{{c1::" }amostra{ "}}" } duma omissão.
card-templates-fill-empty = Preencha os Campos Vazios
card-templates-night-mode = Modo Nocturno
# Add "mobile" class to card preview, so the card appears like it would
# on a mobile device.
card-templates-add-mobile-class = Adicionar classe CSS para dispositivos móveis
card-templates-preview-settings = Opções
card-templates-invalid-template-number = Modelo de ficha { $number } associado a nota '{ $notetype }' tem um problema.
card-templates-identical-front = A frente é idêntica ao modelo de ficha { $number }.
card-templates-no-front-field = A frente do modelo da ficha não tem nenhum campo da nota.
card-templates-missing-cloze = São esperados campos de omissão do tipo '{ "{{" }cloze:Text{ "}}" }' na frente e verso do modelo da ficha.
card-templates-extraneous-cloze = 'cloze:' só pode ser utilizado em notas de omissão.
card-templates-see-preview = Para mais informação utilize a pré-visualização.
card-templates-field-not-found = O campo '{ $field }' não foi encontrado.
card-templates-changes-saved = Alterações guardadas.
card-templates-discard-changes = Descartar alterações?
card-templates-add-card-type = Adicionar tipo de ficha...
card-templates-anki-couldnt-find-the-line-between = O Anki não conseguiu encontrar a linha entre a questão e a resposta. Por favor, ajuste o modelo manualmente para alternar entre a questão e a resposta.
card-templates-at-least-one-card-type-is = Ao menos um tipo de ficha é requerido.
card-templates-browser-appearance = Aspeto do explorador...
card-templates-card = Ficha { $val }
card-templates-card-types-for = Tipos de ficha para { $val }
card-templates-cloze = Omissão de Palavras
card-templates-deck-override = Substituição de Baralho...
card-templates-copy-info = Copiar info para a Área de Transferência
card-templates-delete-the-as-card-type-and = Eliminar o tipo de ficha '{ $template }' e os { $cards }?
card-templates-enter-deck-to-place-new = Abra o baralho para colocar { $val } novas fichas nele, ou deixe em branco:
card-templates-enter-new-card-position-1 = Digite a nova posição da ficha (1...{ $val }):
card-templates-flip = Virar
card-templates-form = Formulário
card-templates-off = (desligado)
card-templates-on = (ligado)
card-templates-remove-card-type = Eliminar tipo de ficha...
card-templates-rename-card-type = Eliminar tipo de ficha...
card-templates-reposition-card-type = Reposicionar Tipo de Ficha...
card-templates-card-count =
    { $count ->
        [one] { $count } ficha
       *[other] { $count } fichas
    }
card-templates-this-will-create-card-proceed =
    { $count ->
        [one] Isto criará { $count } ficha. Continuar?
       *[other] Isto criará { $count } fichas. Continuar?
    }
card-templates-type-boxes-warning = Só é permitida uma caixa de texto por modelo de ficha.
card-templates-restore-to-default = Restaurar o padrão
card-templates-restore-to-default-confirmation =
    Isto irá reiniciar todos os campos e modelos associados a este tipo de nota de volta aos seus 
    valores padrão, removendo campos e modelos adicionados, bem como o seu conteúdo e estilos personalizados. Deseja proceder?
card-templates-restored-to-default = Este tipo de nota foi restaurado ao seu estado original.
