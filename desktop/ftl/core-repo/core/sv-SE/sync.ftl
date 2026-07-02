### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = Tillagt: { $up }↑ { $down }↓
sync-media-removed-count = Tog bort: { $up }↑ { $down }↓
sync-media-checked-count = Kontrollerade: { $count }
sync-media-starting = Mediasynkronisering startar ...
sync-media-complete = Mediasynkronisering slutförd.
sync-media-failed = Mediasynkronisering misslyckades.
sync-media-aborting = Mediasynkronisering avbryts ...
sync-media-aborted = Mediasynkronisering avbruten.
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = Mediasynkronisering inaktiverad.
# Title of the screen that shows syncing progress history
sync-media-log-title = Mediasynkroniseringslogg

## Error messages / dialogs

sync-conflict = Endast en Anki-instans kan synkronisera till ett konto samtidigt. Var god vänta ett par minuter och försök igen.
sync-server-error = AnkiWeb har stött på ett problem. Var god försök igen om ett par minuter.
sync-client-too-old = Den aktuella Anki-versionen är utdaterad. Var god uppdatera till den senaste versionen för att fortsätta synkroniseringen.
sync-wrong-pass = ID:t eller lösenordet för AnkiWeb var felaktigt; var god försök igen.
sync-resync-required = Vad god synkronisera igen. Om detta meddelande fortsätter att uppstå, var god skapa ett ärende på supportwebbplatsen.
sync-must-wait-for-end = Anki synkroniserar för närvarande. Var god vänta på att synkroniseringen slutförs, och försök sedan igen.
sync-confirm-empty-download = Den lokala samlingen har inga kort. Vill du ladda ned från AnkiWeb?
sync-confirm-empty-upload = AnkiWeb-samlingen har inga kort. Ersätt den med lokal samling?
sync-conflict-explanation =
    Dina kortlekar här och på AnkiWeb skiljer sig från varandra på ett sådant sätt att de inte kan sammanfogas. Det är nödvändigt att skriva över kortlekarna på den ena sidan med kortlekarna på den andra.
    
    Om du väljer ladda ned kommer Anki att ladda ned samlingen från AnkiWeb. Alla ändringar du har gjort på din dator sedan den senaste synkroniseringen kommer att gå förlorade.
    
    Om du väljer ladda upp kommer Anki att ladda upp samlingen till AnkiWeb. Alla ändringar du har gjort på AnkiWeb, eller på dina andra enheter sedan den senaste synkroniseringen till den enheten, kommer att gå förlorade.
    
    När alla enheter är synkade kommer framtida repetitioner och tillagda kort automatiskt att sammanfogas med varandra.
sync-conflict-explanation2 =
    Det finns en konflikt mellan kortlekar på denna enhet och AnkiWeb. Vilken version som ska behållas måste väljas:
    
    - Välj **{ sync-download-from-ankiweb }** för att ersätta kortlekar på denna enhet med motsvarande AnkiWeb-version. Eventuella förändringar på denna enhet efter den senaste synkroniseringen kommer gå förlorade.
    - Välj **{ sync-upload-to-ankiweb }** för att skriva över AnkiWeb-versionerna med kortlekar från denna enhet och ta bort eventuella förändringar på AnkiWeb.
    
    När konflikten har lösts kommer synkronisering fungera som vanligt.
sync-ankiweb-id-label = ID för AnkiWeb:
sync-password-label = Lösenord:
sync-account-required =
    <h1>Konto krävs</h1>
    Ett gratis konto krävs för att hålla din samling synkroniserad. <a href="{ $link }">Registrera</a> ett konto och ange sedan dina detaljer nedan.
sync-sanity-check-failed = Vad god använd funktionen Kontrollera databas och synkronisera sedan igen. Om problemen fortsätter, gör en envägssynkronisering i inställningsskärmen.
sync-clock-off = Kunde ej synkronisera - datorklockan är inte korrekt synkroniserad.
# “details” expands to a string such as “300.14 MB > 300.00 MB”
sync-upload-too-large =
    Samlingen är för stor för att skickas till AnkiWeb. Dess storlek kan minskas
    genom att ta bort oönskade kortlekar (och eventuellt exportera dem först), och
    sedan använda Kontrollera databas för att krympa filstorleken. ({ $details })
sync-sign-in = Logga in
sync-ankihub-dialog-heading = AnkiHub-inloggning
sync-ankihub-username-label = Användarnamn eller E-post:
sync-ankihub-login-failed = Det gick inte att logga in till AnkiHub med de tillhandahållna inloggningsuppgifterna.
sync-ankihub-addon-installation = Installation av AnkiHub-tillägg

## Buttons

sync-media-log-button = Medialogg
sync-abort-button = Avbryt
sync-download-from-ankiweb = Ladda ner från AnkiWeb
sync-upload-to-ankiweb = Ladda upp till AnkiWeb
sync-cancel-button = Avbryt

## Normal sync progress

sync-downloading-from-ankiweb = Laddar ned från AnkiWeb ...
sync-uploading-to-ankiweb = Laddar upp till AnkiWeb ...
sync-syncing = Synkroniserar ...
sync-checking = Kontrollerar ...
sync-connecting = Ansluter ...
sync-added-updated-count = Tillagda/ändrade: { $up }↑ { $down }↓
sync-log-in-button = Logga in
sync-log-out-button = Logga ut
sync-collection-complete = Samlingssynkronisering slutförd.
