addons-possibly-involved = Extensões possivelmente envolvidas: { $addons }
addons-failed-to-load =
    Um complemento que você instalou falhou ao carregar. Se os problemas persistirem, por favor, vá ao menu Ferramentas > Complementos e desative ou exclua o complemento.
    
    Ao carregar '{ $name }':
    { $traceback }
addons-failed-to-load2 =
    As seguintes extensões falharam ao carregar:
    { $addons }
    
    Eles podem precisar ser atualizados para oferecer suporte a esta versão do Anki. Clique no botão { addons-check-for-updates }
    para ver se há atualizações disponíveis.
    
    Você pode usar o botão { about-copy-debug-info } para obter informações que podem ser coladas em um relatório para
    o autor da extensão.
    
    Para extensões que não têm uma atualização disponível, você pode desabilitar ou excluir a extensão para evitar que esta
    mensagem apareça.
addons-startup-failed = Erro na Inicialização da Extensão
# Shown in the add-on configuration screen (Tools>Add-ons>Config), in the title bar
addons-config-window-title = Configurar '{ $name }'
addons-config-validation-error = Houve um problema com a configuração fornecida: { $problem }, no caminho { $path }, contra o esquema { $schema }.
addons-window-title = Extensões
addons-addon-has-no-configuration = A extensão não tem configuração.
addons-addon-installation-error = Erro ao instalar a extensão
addons-browse-addons = Buscar Extensões
addons-changes-will-take-effect-when-anki = As alterações surtirão efeito quando o Anki for reiniciado.
addons-check-for-updates = Verificar se há atualizações
addons-checking = Verificando...
addons-code = Código:
addons-config = Configurar
addons-configuration = Configuração
addons-corrupt-addon-file = Arquivo de extensão corrompido.
addons-disabled = (desativado)
addons-disabled2 = (desativado)
addons-download-complete-please-restart-anki-to = Download concluído. Por favor, reinicie o Anki para aplicar as alterações.
addons-downloaded-fnames = { $fname } foi baixado
addons-downloading-adbd-kb02fkb = Baixando { $part }/{ $total } ({ $kilobytes }KB)...
addons-error-downloading-ids-errors = Erro ao baixar <i>{ $id }</i>: { $error }
addons-error-installing-bases-errors = Erro instalando <i>{ $base }</o>: { $error }
addons-get-addons = Obter extensões...
addons-important-as-addons-are-programs-downloaded = <b>Importante</b>: Como as extensões são programas baixados da internet, elas são potencialmente maliciosas.<b>Você só deve instalar as extensões que confia.</b><br><br>Tem certeza que deseja prosseguir com a instalação das seguintes extensões do Anki?<br><br>%(names)s
addons-install-addon = Instalar Extensão
addons-install-addons = Instalar extensão(ões)
addons-install-anki-addon = Instalar extensão do Anki
addons-install-from-file = Instalar de um arquivo...
addons-installation-complete = Instalação concluída
addons-installed-names = Instalou { $name }
addons-installed-successfully = Instalado com sucesso.
addons-invalid-addon-manifest = Manifesto de extensão inválido.
addons-invalid-code = Código inválido.
addons-invalid-code-or-addon-not-available = Código inválido ou extensão não disponível para a sua versão do Anki.
addons-invalid-configuration = Configuração inválida:
addons-invalid-configuration-top-level-object-must = Configuração Inválida: objeto de nível superior deve ser um mapa
addons-no-updates-available = Sem atualizações disponíveis.
addons-one-or-more-errors-occurred = Um ou mais erros ocorreram:
addons-packaged-anki-addon = Extensão do Anki empacotada
addons-please-check-your-internet-connection = Por favor, verifique sua conexão internet.
addons-please-report-this-to-the-respective = Por favor, informe o(s) respectivo(s) autor(es) da extensão.
addons-please-restart-anki-to-complete-the = <b>Por favor, reinicie o Anki para concluir a instalação.</b>
addons-please-select-a-single-addon-first = Por favor, selecione uma extensão primeiro.
addons-requires = (requer { $val })
addons-restored-defaults = Restaurados os padrões
addons-the-following-addons-are-incompatible-with = As seguintes extensões são incompatíveis com { $name } e foram desativadas: { $found }
addons-the-following-addons-have-updates-available = As seguintes extensões têm atualizações disponíveis. Instalar agora?
addons-the-following-conflicting-addons-were-disabled = As seguintes extensões conflitantes foram desativados:
addons-this-addon-is-not-compatible-with = Essa extensão não é compatível com sua versão do Anki.
addons-to-browse-addons-please-click-the = Para buscar extensões, clique no botão abaixo.<br><br>Quando encontrar uma extensão que goste, cole o código abaixo. Você pode colar vários códigos, separados por espaços.
addons-toggle-enabled = Ativar/Desativar
addons-unable-to-update-or-delete-addon = Não foi possível atualizar ou excluir a extensão. Inicie Anki enquanto mantém pressionada a tecla Shift para desativar temporariamente as extensões e tente novamente. Informações de depuração: { $val }
addons-unknown-error = Erro desconhecido: { $val }
addons-view-addon-page = Ver Página de Extensões
addons-view-files = Ver Arquivos
addons-delete-the-numd-selected-addon =
    { $count ->
        [one] Apagar a extensão { $count } selecionada?
       *[other] Apagar as extensões { $count } selecionadas?
    }
addons-choose-update-window-title = Atualizar Extensões
addons-choose-update-update-all = Atualizar todos
