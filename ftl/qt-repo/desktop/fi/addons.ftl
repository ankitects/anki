addons-possibly-involved = Asiaan mahdollisesti liittyvät lisäosat: { $addons }
addons-failed-to-load =
    Asentamasi lisäosa ei latautunut. Jos ongelmat jatkuvat, siirry Työkalut>Lisäohjelmat-valikkoon ja poista lisäosa käytöstä tai poista se.
    
    Ladattaessa kohdetta '{ $name }':
    { $traceback }
addons-failed-to-load2 =
    Seuraavien lisäosien lataaminen epäonnistui:
    { $addons }
    
    Ne on ehkä päivitettävä tukemaan tätä versiota Ankista. Napsauta { addons-check-for-updates } -painiketta nähdäksesi, onko päivityksiä saatavilla.
    
    Voit käyttää { about-copy-debug-info } -painiketta saadaksesi vianmääritystietoja. Jos haluat lähettää lisäosan kehittäjälle virheraportin, voit liittää nämä tiedot siihen.
    
    Jos lisäosalle ei ole saatavilla päivitystä, voit poistaa lisäosan käytöstä tai kokonaan, jotta tämä viesti ei enää ilmestyisi.
addons-startup-failed = Lisäosan käynnistäminen epäonnistui
# Shown in the add-on configuration screen (Tools>Add-ons>Config), in the title bar
addons-config-window-title = Määritä '{ $name }'
addons-config-validation-error = Annetussa määrityksessä oli ongelma: { $problem }, polussa { $path }, koskien skeemaa { $schema }.
addons-window-title = Lisäosat
addons-addon-has-no-configuration = Lisäosalla ei ole määrityksiä.
addons-addon-installation-error = Lisäosan asennusvirhe
addons-browse-addons = Selaa lisäosia
addons-changes-will-take-effect-when-anki = Muutokset tulevat voimaan, kun Anki käynnistetään uudelleen.
addons-check-for-updates = Tarkista päivitykset
addons-checking = Tarkistetaan...
addons-code = Koodi:
addons-config = Määritys
addons-configuration = Määritykset
addons-corrupt-addon-file = Korruptoitunut lisäosatiedosto
addons-disabled = (ei käytössä)
addons-disabled2 = (ei käytössä)
addons-download-complete-please-restart-anki-to = Lataus on valmistunut. Käynnistä Anki uudelleen, jotta muutokset tulevat voimaan.
addons-downloaded-fnames = Ladattiin { $fname }
addons-downloading-adbd-kb02fkb = Ladataan { $part }/{ $total } ({ $kilobytes } Kt)...
addons-error-downloading-ids-errors = Virhe kohteen <i>{ $id }</i> lataamisen aikana: { $error }
addons-error-installing-bases-errors = Virhe kohteen <i>{ $base }</i> asentamisen aikana: { $error }
addons-get-addons = Hanki lisäosia...
addons-important-as-addons-are-programs-downloaded = <b>Tärkeää</b>: Koska lisäosat ovat internetistä ladattuja ohjelmia, ne voivat olla haitallisia. <b>Asenna vain sellaisia lisäosia, joihin luotat.</b><br><br>Haluatko varmasti asentaa Ankiin tämän lisäosan / nämä lisäosat?<br><br>%(names)s
addons-install-addon = Asenna lisäosa
addons-install-addons = Asenna lisäosia
addons-install-anki-addon = Asenna Ankin lisäosa
addons-install-from-file = Asenna tiedostosta...
addons-installation-complete = Asennus valmis
addons-installed-names = Asennettiin { $name }
addons-installed-successfully = Asennus onnistui.
addons-invalid-addon-manifest = Virheellinen lisäosan manifesti.
addons-invalid-code = Virheellinen koodi
addons-invalid-code-or-addon-not-available = Virheellinen koodi tai lisäosa ei ole saatavilla käyttämässäsi Anki-versiossa.
addons-invalid-configuration = Virheellinen määritys:
addons-invalid-configuration-top-level-object-must = Virheellinen määriys: ylätason objektin on oltava map-tyyppinen
addons-no-updates-available = Päivityksiä ei ole saatavilla.
addons-one-or-more-errors-occurred = Tapahtui yksi tai useampi virhe:
addons-packaged-anki-addon = Pakattu Anki-lisäosa
addons-please-check-your-internet-connection = Tarkista internet-yhteytesi.
addons-please-report-this-to-the-respective = Ilmoita tästä lisäosan tekijälle/tekijöille.
addons-please-restart-anki-to-complete-the = <b>Käynnistä Anki uudelleen saattaaksesi asennus päätökseen.</b>
addons-please-select-a-single-addon-first = Valitse ensin yksittäinen lisäosa.
addons-requires = (vaatii kohteen { $val })
addons-restored-defaults = Oletusmääritykset palautettiin
addons-the-following-addons-are-incompatible-with = Seuraavat lisäosat eivät ole yhteensopivia kohteen { $name } kanssa ja ne on poistettu käytöstä: { $found }
addons-the-following-addons-have-updates-available = Seuraaviin lisäosiin on saatavilla päivityksiä. Asennetaanko ne nyt?
addons-the-following-conflicting-addons-were-disabled = Seuraavat ristiriidassa olevat lisäosat poistettiin käytöstä:
addons-this-addon-is-not-compatible-with = Tämä lisäosa ei ole yhteensopiva käyttämäsi Anki-version kanssa.
addons-to-browse-addons-please-click-the = Voit selata lisäosia napsauttamalla alla olevaa Selaa-painiketta.<br><br>Kun olet löytänyt mieluisan lisäosan, liitä sen koodi alle. Voit liittää useita koodeja välilyönneillä erotettuna.
addons-toggle-enabled = Ota käyttöön/poista käytöstä
addons-unable-to-update-or-delete-addon = Lisäosaa ei voida päivittää tai poistaa. Käynnistä Anki ja pidä samalla vaihtonäppäintä pohjassa poistaaksesi lisäosat tilapäisesti käytöstä ja yritä sitten uudelleen. Vianmääritystiedot: { $val }
addons-unknown-error = Tuntematon virhe: { $val }
addons-view-addon-page = Näytä lisäosan sivu
addons-view-files = Näytä tiedostot
addons-delete-the-numd-selected-addon =
    { $count ->
        [one] Poistetaanko { $count } valittu lisäosa?
       *[other] Poistetaanko { $count } valittua lisäosaa?
    }
addons-choose-update-window-title = Päivitä lisäosia
addons-choose-update-update-all = Päivitä kaikki
