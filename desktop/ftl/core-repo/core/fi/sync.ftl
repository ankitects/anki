### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = Lisätty: { $up }↑ { $down }↓
sync-media-removed-count = Poistettu: { $up }↑ { $down }↓
sync-media-checked-count = Tarkistettu: { $count }
sync-media-starting = Median synkronointi alkaa...
sync-media-complete = Median synkronointi valmis.
sync-media-failed = Median synkronointi epäonnistui.
sync-media-aborting = Median synkronointi keskeytetään...
sync-media-aborted = Median synkronointi keskeytetty.
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = Median synkronointi poistettu käytöstä.
# Title of the screen that shows syncing progress history
sync-media-log-title = Median synkronoinnin loki

## Error messages / dialogs

sync-conflict = Vain yksi Anki-kopio voi synkronoida tilillesi kerrallaan. Odota muutama minuutti ja yritä sitten uudelleen.
sync-server-error = AnkiWeb kohtasi ongelman. Yritä uudelleen muutaman minuutin kuluttua.
sync-client-too-old = Anki-versiosi on liian vanha. Päivitä uusimpaan versioon jatkaaksesi synkronointia.
sync-wrong-pass = AnkiWeb-käyttäjätunnus tai -salasana on väärä. Yritä uudestaan.
sync-resync-required = Synkronoi uudelleen. Jos tämä viesti näkyy toistuvasti, lähetä viesti tukisivustolla.
sync-must-wait-for-end = Anki synkronoi parhaillaan. Odota, että synkronointi on valmis, ja yritä sitten uudelleen.
sync-confirm-empty-download = Paikallisessa kokoelmassa ei ole kortteja. Ladataanko AnkiWebistä?
sync-confirm-empty-upload = AnkiWebin kokoelmassa ei ole kortteja. Korvataanko se paikallisella kokoelmalla?
sync-conflict-explanation =
    Tällä laitteella olevat pakat ja AnkiWebissä olevat pakat eroavat toisistaan siten, että niitä ei voi yhdistää toisiinsa, joten on välttämätöntä korvata toisen puolen pakat toisen puolen pakoilla.
    
    Jos valitset lataamisen AnkiWebistä, Anki lataa kokoelman AnkiWebistä, ja kaikki muutokset, jotka olet tehnyt tällä laitteella edellisen synkronoinnin jälkeen, menetetään.
    
    Jos valitset lähetyksen AnkiWebiin, Anki lähettää tämän laitteen tiedot AnkiWebiin, ja kaikki AnkiWebissä odottavat muutokset menetetään.
    
    Sen jälkeen kun kaikki laitteet ovat synkronoitu, tulevat kertaukset ja lisätyt kortit voidaan yhdistää automaattisesti.
sync-conflict-explanation2 =
    Tällä laitteella olevien pakkojen ja AnkiWebin välillä on ristiriita. Valitse, kumpi versio säilytetään:
    
    - Valitse **{ sync-download-from-ankiweb }** korvataksesi tämän laitteen pakat AnkiWebin versiolla. Menetät kaikki muutokset, jotka olet tehnyt tällä laitteella viimeisimmän synkronoinnin jälkeen.
    - Valitse **{ sync-upload-to-ankiweb }** korvataksesi AnkiWebin versiot tämän laitteen pakoilla ja poista kaikki AnkiWebissä tehdyt muutokset.
    
    Kun ristiriita on ratkaistu, synkronointi toimii jälleen normaalisti.
sync-ankiweb-id-label = AnkiWeb-käyttäjätunnus:
sync-password-label = Salasana:
sync-account-required =
    <h1>Käyttäjätili vaaditaan</h1>
    Tarvitset ilmaisen käyttäjätilin, että voi pitää kokoelmasi synkronoituna. <a href="{ $link }">Perusta</a> käyttäjätili ja syötä sitten tietosi alle.
sync-sanity-check-failed = Käytä Tarkista tietokanta -toimintoa ja synkronoi sitten uudelleen. Jos ongelmat jatkuvat, pakota täydellinen synkronointi asetusnäkymästä.
sync-clock-off = Synkronointi ei onnistu – kelloa ei ole asetettu oikeaan aikaan.
sync-upload-too-large =
    Kokoelmatiedostosi on liian suuri lähetettäväksi AnkiWebiin. Voit pienentää sen
    kokoa poistamalla kaikki ei-toivotut pakat (mahdollisesti viemällä ne ensin tiedostoihin), ja
    sitten käyttämällä Tarkista tietokanta -toimintoa tiedoston koon pienentämiseksi. ({ $details })
sync-sign-in = Kirjaudu sisään
sync-ankihub-dialog-heading = AnkiHubiin kirjautuminen
sync-ankihub-username-label = Käyttäjätunnus tai sähköpostiosoite:
sync-ankihub-login-failed = AnkiHubiin kirjautuminen ei onnistunut annetuilla tiedoilla.
sync-ankihub-addon-installation = AnkiHub-lisäosan asennus

## Buttons

sync-media-log-button = Median loki
sync-abort-button = Keskeytä
sync-download-from-ankiweb = Lataa AnkiWebistä
sync-upload-to-ankiweb = Lähetä AnkiWebiin
sync-cancel-button = Peruuta

## Normal sync progress

sync-downloading-from-ankiweb = Ladataan AnkiWebistä...
sync-uploading-to-ankiweb = Lähetetään AnkiWebiin...
sync-syncing = Synkronoidaan...
sync-checking = Tarkistetaan...
sync-connecting = Yhdistetään...
sync-added-updated-count = Lisätty/muokattu: { $up }↑ { $down }↓
sync-log-in-button = Kirjaudu
sync-log-out-button = Kirjaudu ulos
sync-collection-complete = Kokoelman synkronointi valmis.
