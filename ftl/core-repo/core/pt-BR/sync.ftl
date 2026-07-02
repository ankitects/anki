### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = Adicionado: { $up }↑ { $down }↓
sync-media-removed-count = Removido: { $up }↑ { $down }↓
sync-media-checked-count = Verificado: { $count }
sync-media-starting = Sincronização de mídia iniciando...
sync-media-complete = Sincronização de mídia concluída.
sync-media-failed = Falha na sincronização de mídia.
sync-media-aborting = Abortando sincronização de mídia...
sync-media-aborted = Sincronização de mídia abortada.
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = Sincronização de mídia desativada.
# Title of the screen that shows syncing progress history
sync-media-log-title = Log da Sincronização de Mídia

## Error messages / dialogs

sync-conflict = Apenas uma cópia do Anki pode ser sincronizada com sua conta por vez. Aguarde alguns minutos e tente novamente.
sync-server-error = AnkiWeb encontrou um problema. Por favor, tente novamente em alguns minutos.
sync-client-too-old = Sua versão do Anki é muito antiga. Atualize para a versão mais recente para continuar sincronizando.
sync-wrong-pass = O Usuário AnkiWeb ou senha está incorreto; por favor, tente outra vez.
sync-resync-required = Sincronize novamente. Se esta mensagem continuar aparecendo, poste no site de suporte.
sync-must-wait-for-end = O Anki está sincronizando no momento. Aguarde a conclusão da sincronização e tente novamente.
sync-confirm-empty-download = A coleção local não possui cartões. Baixar do 'AnkiWeb'?
sync-confirm-empty-upload = Não há cartões na coleção AnkiWeb. Deseja substituí-la pela coleção local?
sync-conflict-explanation =
    Seus baralhos aqui e no 'AnkiWeb' diferem tanto que não podem ser mesclados, então é necessário que um deles sobrescreva o outro.
    
    Se você escolher baixar, o 'Anki' trará a coleção do 'AnkiWeb' e todas as mudanças que você tiver feito desde a última sincronização serão perdidas.
    
    Se você escolher enviar, o 'Anki' copiará sua coleção para o AnkiWeb e todas as mudanças que você tenha feito no 'AnkiWeb' ou em outros aparelhos desde a última sincronização serão perdidas.
    
    Depois que todos os aparelhos estiverem sincronizados, as futuras revisões e os cartõs adicionados serão mescladas automaticamente.
sync-conflict-explanation2 =
    Há um conflito entre os baralhos neste dispositivo e AnkiWeb. Você deve escolher qual versão manter:
    
    - Selecione **{ sync-download-from-ankiweb }** para substituir os baralhos aqui pela versão do AnkiWeb. Você perderá todas as alterações feitas neste dispositivo desde sua última sincronização.
    - Selecione **{ sync-upload-to-ankiweb }** para substituir as versões do AnkiWeb pelos baralhos deste dispositivo e excluir todas as alterações no AnkiWeb.
    
    Assim que o conflito for resolvido, a sincronização funcionará normalmente.
sync-ankiweb-id-label = Usuário AnkiWeb:
sync-password-label = Senha:
sync-account-required =
    <h1>Requer uma conta</h1>
    Uma conta grátis é necessária para manter sua coleção sincronizada. Por favor, <a href="{ $link }">registre-se</a> e então insira os detalhes abaixo.
sync-sanity-check-failed = Use a função Verificar banco de dados e sincronize novamente. Se o problema persistir, force uma sincronização completa na tela de preferências.
sync-clock-off = Não foi possível sincronizar - seu relógio não está definido para a hora correta.
sync-upload-too-large =
    Seu arquivo de coleção é muito grande para ser enviado ao AnkiWeb. Você
    pode reduzir seu tamanho removendo quaisquer baralhos indesejados 
    (opcionalmente, exportando-os primeiro), e em seguida, usando 'Verificar Banco 
    de Dados' para reduzir o tamanho do arquivo. ({ $details })
sync-sign-in = Iniciar sessão
sync-ankihub-dialog-heading = Login no AnkiHub
sync-ankihub-username-label = Nome de usuário ou E-mail:
sync-ankihub-login-failed = Não foi possível iniciar sessão no AnkiHub com as credenciais fornecidas.
sync-ankihub-addon-installation = Instalação da extensão AnkiHub

## Buttons

sync-media-log-button = Log da Mídia
sync-abort-button = Abortar
sync-download-from-ankiweb = Baixar do AnkiWeb
sync-upload-to-ankiweb = Enviar para o AnkiWeb
sync-cancel-button = Cancelar

## Normal sync progress

sync-downloading-from-ankiweb = Baixando do AnkiWeb...
sync-uploading-to-ankiweb = Enviando para o AnkiWeb...
sync-syncing = Sincronizando...
sync-checking = Verificando...
sync-connecting = Conectando...
sync-added-updated-count = Adicionado/modificado: { $up }↑ { $down }↓
sync-log-in-button = Entrar
sync-log-out-button = Deslogar
sync-collection-complete = Sincronização da coleção concluída.
