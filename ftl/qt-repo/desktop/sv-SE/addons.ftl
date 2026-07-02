addons-possibly-involved = Tillägg möjligen inblandade: { $addons }
addons-failed-to-load =
    Ett tillägg du installerat kunde inte laddas. Om problemet kvarstår, gå till Verktyg>Tillägg i menyn och inaktivera eller ta bort tillägget.
    
    Medan '{ $name }' laddades:
    { $traceback }
addons-failed-to-load2 =
    Följande tillägg kunde inte laddas:
    { $addons }
    
    De kan behöva uppdateras för att stödja denna Anki-version. Tryck på { addons-check-for-updates }-knappen
    för att se om några uppdateringar är tillgängliga
    
    Du kan använda { about-copy-debug-info }-knappen för att få information som du kan klistra in i en felanmälan
    till tilläggets skapare.
    
    Tillägg som inte har en tillgänglig uppdatering kan du inaktivera eller ta bort för att förhindra detta
    meddelande från att synas.
addons-startup-failed = Tillägg misslyckades att starta
# Shown in the add-on configuration screen (Tools>Add-ons>Config), in the title bar
addons-config-window-title = Konfigurera "{ $name }"
addons-config-validation-error = Ett problem uppstod med den tillhandahållna konfigurationen: { $problem }, vid sökvägen { $path }, mot schemat { $schema }.
addons-window-title = Tillägg
addons-addon-has-no-configuration = Tillägg saknar konfiguration.
addons-addon-installation-error = Tilläggsinstallationsfel
addons-browse-addons = Bläddra tillägg
addons-changes-will-take-effect-when-anki = Ändringar kommer att träda i kraft när Anki startas om.
addons-check-for-updates = Sök efter uppdateringar
addons-checking = Kontrollerar ...
addons-code = Kod:
addons-config = Konfiguration
addons-configuration = Konfiguration
addons-corrupt-addon-file = Korrupt tilläggsfil.
addons-disabled = (inaktiverad)
addons-disabled2 = (inaktiverad)
addons-download-complete-please-restart-anki-to = Nedladdning klar. Var god starta om Anki för att tillämpa förändringar.
addons-downloaded-fnames = Laddade ned { $fname }
addons-downloading-adbd-kb02fkb = Laddar ned { $part } av { $total } ({ $kilobytes } KB) ...
addons-error-downloading-ids-errors = Fel vid nedladdning av <i>{ $id }</i>: { $error }
addons-error-installing-bases-errors = Fel vid installering av <i>{ $base }</i>{ $error }
addons-get-addons = Hämta tillägg
addons-important-as-addons-are-programs-downloaded = <b>Viktigt</b>: Då tillägg är program hämtade från internet är det potentiellt skadliga. <b>Du bör endast installera tillägg du litar på.</b><br><br>Är du säker på att du vill fortsätta med installationen av följande Anki-tillägg?<br><br>%(names)s
addons-install-addon = Installera tillägg
addons-install-addons = Installera tillägg
addons-install-anki-addon = Installera Anki-tillägg
addons-install-from-file = Installera från fil
addons-installation-complete = Installation färdig
addons-installed-names = Installerat { $name }
addons-installed-successfully = Installation lyckad.
addons-invalid-addon-manifest = Ogiltigt tilläggsmanifest.
addons-invalid-code = Ogiltig kod.
addons-invalid-code-or-addon-not-available = Ogiltig kod, eller så är tillägget inte tillgängligt för din version av Anki.
addons-invalid-configuration = Ogiltig konfiguration:
addons-invalid-configuration-top-level-object-must = Ogiltig konfiguration: objekt på toppnivå måste vara av typen map
addons-no-updates-available = Inga uppdateringar är tillgängliga.
addons-one-or-more-errors-occurred = Ett eller fler fel inträffade:
addons-packaged-anki-addon = Paketerat Anki-tillägg
addons-please-check-your-internet-connection = Var god kontrollera din internetanslutning.
addons-please-report-this-to-the-respective = Var god anmäl detta till tilläggens skapare.
addons-please-restart-anki-to-complete-the = <b>Var god starta om Anki för att slutföra installationen.</b>
addons-please-select-a-single-addon-first = Var god välj ett enskilt tillägg först.
addons-requires = (kräver { $val })
addons-restored-defaults = Återställde till förvalda inställningar
addons-the-following-addons-are-incompatible-with = Följande tillägg är inkompatiibla med { $name } och har inaktiverats: { $found }
addons-the-following-addons-have-updates-available = Följande tillägg har uppdateringar tillgängliga. Installera dem nu?
addons-the-following-conflicting-addons-were-disabled = De följande inbördes inkompatibla tilläggen inaktiverades:
addons-this-addon-is-not-compatible-with = Detta tillägg är inte kompatibelt med din version av Anki.
addons-to-browse-addons-please-click-the = För att utforska tillägg, klicka på bläddra-knappen nedan.<br><br>När du hittat ett tillägg som du tycker om, klistra in dess kod nedan. Du kan klistra in flera koder, avskiljda med mellanslag.
addons-toggle-enabled = Växla aktiverad
addons-unable-to-update-or-delete-addon = Misslyckades att uppdatera eller ta bort tillägg. Var god starta Anki med skifttangenten nedtryckt för att temporärt inaktivera tillägg och försök igen. Felsökningsinfo: { $val }
addons-unknown-error = Okänt fel: { $val }
addons-view-addon-page = Visa tilläggets sida
addons-view-files = Visa filer
addons-delete-the-numd-selected-addon =
    { $count ->
        [one] Ta bort det { $count } valda tillägget?
       *[other] Ta bort de { $count } valda tilläggen?
    }
addons-choose-update-window-title = Uppdatera tillägg
addons-choose-update-update-all = Uppdatera alla
