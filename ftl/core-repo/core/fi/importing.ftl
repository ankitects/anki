importing-failed-debug-info = Tuonti epäonnistui. Vianmääritystietoa:
importing-aborted = Keskeytetty: { $val }
importing-added-duplicate-with-first-field = Lisätty ensimmäisen kentän kaksoiskappale: { $val }
importing-all-supported-formats = Kaikki tuetut tiedostotyypit { $val }
importing-allow-html-in-fields = Salli HTML kentissä
importing-anki-files-are-from-a-very = .anki-tiedostot ovat todella vanhasta Ankin versiosta. Voit tuoda ne Anki 2.0 -versiolla, jonka voit ladata Ankin verkkosivulta.
importing-anki2-files-are-not-directly-importable = .anki2-tiedostoja ei voida tuoda suoraan – tuo sen sijaan saamasi .apkg- tai .zip-tiedosto.
importing-appeared-twice-in-file = Esiintyi kahdesti tiedostossa: { $val }
importing-by-default-anki-will-detect-the = Anki yrittää tunnistaa erotinmerkin automaattisesti. Jos menee pieleen, voit itse syöttää erottimen tähän (pilkku, puolipiste, jne.). Tab on \t
importing-cannot-merge-notetypes-of-different-kinds = Aukkotehtävätyyppejä ei voi yhdistää tavallisiin muistiinpanotyyppeihin. Voit silti tuoda tämän tiedoston, kun '{ importing-merge-notetypes }' on kytketty pois päältä.
importing-change = Muuta
importing-colon = Kaksoispiste
importing-comma = Pilkku
importing-empty-first-field = Tyhjä ensimmäinen kenttä: { $val }
importing-field-separator = Kenttien erotin
importing-field-separator-guessed = Kenttien erotin (päätelty)
importing-field-mapping = Kenttäliitokset
importing-field-of-file-is = Tiedoston <b>{ $val }.</b> kenttä on:
importing-fields-separated-by = Kenttien erotin: { $val }
importing-file-must-contain-field-column = Tiedoston tulee sisältää vähintään yksi sarake, joka vastaa jotakin muistiinpanon kenttää.
importing-file-version-unknown-trying-import-anyway = Tuntematon tiedostoversio. Yritetään tuontia siitä huolimatta.
importing-first-field-matched = Ensimmäinen kenttä täsmää: { $val }
importing-identical = Identtinen
importing-ignore-field = Jätä kenttä huomioimatta
importing-ignore-lines-where-first-field-matches = Älä huomioi rivejä, joiden ensimmäinen kenttä vastaa olemassaolevaa muistiinpanoa
importing-ignored = <ei huomioitu>
importing-import-even-if-existing-note-has = Tuo vaikka olemassa olevassa muistiinpanossa on sama ensimmäinen kenttä
importing-import-options = Tuontiasetukset
importing-importing-complete = Tuonti valmis.
importing-invalid-file-please-restore-from-backup = Viallinen tiedosto. Palauta aikaisempi versio varmuuskopiosta.
importing-map-to = Liitä kenttään { $val }
importing-map-to-tags = Liitä tunnisteisiin
importing-mapped-to = liitetty kenttään <b>{ $val }</b>
importing-mapped-to-tags = liitetty <b>tunnisteisiin</b>
# the action of combining two existing note types to create a new one
importing-merge-notetypes = Yhdistä muistiinpanotyypit
importing-merge-notetypes-help =
    Jos tämä on käytössä, ja sinä tai pakan kirjoittaja muutatte muistiinpanotyypin skeemaa, Anki yhdistää nämä kaksi versiota sen sijaan, että säilyttäisi molemmat.
    
    Muistiinpanotyypin skeeman muuttaminen tarkoittaa kenttien tai mallien lisäämistä, poistamista tai uudelleenjärjestämistä tai lajittelukentän muuttamista.
    
    Vastaesimerkkinä voidaan todeta, että olemassa olevan mallin etupuolen muuttaminen *ei* ole
    skeeman muuttamista.
    
    Varoitus: Tämä edellyttää yksisuuntaista synkronointia ja saattaa merkitä olemassa olevat muistiinpanot muuttuneiksi.
importing-mnemosyne-20-deck-db = Mnemosyne 2.0 -pakka (*.db)
importing-multicharacter-separators-are-not-supported-please = Monimerkkisiä erottimia ei tueta. Anna erottimeksi vain yksi merkki.
importing-new-deck-will-be-created = Luodaan uusi pakka: { $name }
importing-notes-added-from-file = Muistiinpanoja lisätty tiedostosta: { $val }
importing-notes-found-in-file = Muistiinpanoja löydetty tiedostosta: { $val }
importing-notes-skipped-as-theyre-already-in = Muistiinpanoja, jotka ohitettiin, koska ne ovat jo kokoelmassasi: { $val }
importing-notes-skipped-update-due-to-notetype = Muistiinpanoja ei päivitetty, koska muistiinpanotyyppiä on muutettu sen jälkeen, kun muistiinpanot tuotiin ensimmäisen kerran: { $val }
importing-notes-updated-as-file-had-newer = Muistiinpanoja, jotka päivitettiin, koska tiedostossa oli uudempi versio: { $val }
importing-include-reviews = Sisällytä kertaukset
importing-also-import-progress = Tuo myös mahdollinen oppimisen edistyminen
importing-with-deck-configs = Tuo mahdolliset pakan esiasetukset
importing-updates = Päivitykset
importing-include-reviews-help =
    Jos tämä on käytössä, myös pakan jakajan mahdollisesti sisällyttämät aiemmat kertaukset tuodaan.
    Muussa tapauksessa kaikki kortit tuodaan uusina kortteina.
importing-with-deck-configs-help =
    Jos tämä on käytössä, myös pakan jakajan sisällyttämät pakan asetukset tuodaan.
    Muussa tapauksessa kaikille pakoille annetaan oletusasetukset.
importing-packaged-anki-deckcollection-apkg-colpkg-zip = Pakattu Anki-pakka/kokoelma (*.apkg *.colpkg *.zip)
# the '|' character
importing-pipe = Putki
# Warning displayed when the csv import preview table is clipped (some columns were hidden)
# $count is intended to be a large number (1000 and above)
importing-preview-truncated =
    { $count ->
        [one] Vain ensimmäinen sarake näytetään. Jos tulos näyttää väärältä, yritä muuttaa kenttien välisenä erottimena käytettävää merkkiä.
       *[other] Vain ensimmäiset { $count } saraketta näytetään. Jos tulos näyttää väärältä, yritä muuttaa kenttien välisenä erottimena käytettävää merkkiä.
    }
importing-rows-had-num1d-fields-expected-num2d = '{ $row }':ssa oli { $found } kenttää, pitäisi olla { $expected }
importing-selected-file-was-not-in-utf8 = Valittu tiedosto ei ollut UTF-8-muodossa. Katso käyttöohjeen tuonnista kertova osio.
importing-semicolon = Puolipiste
importing-skipped = Ohitettu
importing-tab = Sarkain
importing-tag-modified-notes = Merkitse muokatut muistiinpanot tunnisteella:
importing-text-separated-by-tabs-or-semicolons = Sarkaimilla tai puolipisteillä eroteltu teksti (*)
importing-the-first-field-of-the-note = Muistiinpanotyypin ensimmäinen kenttä on liitettävä.
importing-the-provided-file-is-not-a = Tarjottu tiedosto ei ole kelvollinen .apkg-tiedosto.
importing-this-file-does-not-appear-to = Tämä tiedosto ei vaikuta olevan kelvollinen .apkg-tiedosto. Jos saat tämän virheen AnkiWebistä ladatusta tiedostosta, voi olla, että latauksesi epännistui. Yritä uudelleen ja jos ongelma jatkuu, yritä uudelleen eri selaimella.
importing-this-will-delete-your-existing-collection = Olet poistamassa olemassa olevaa kokoelmaasi ja korvaamassa sitä tuotavassa tiedostossa olevalla tiedolla. Oletko varma?
importing-unable-to-import-from-a-readonly = Vain luku -tilassa olevaa tiedostoa ei voida tuoda.
importing-unknown-file-format = Tuntematon tiedostotyyppi
importing-update-existing-notes-when-first-field = Päivitä olemassa olevat muistiinpanot kun ensimmäinen kenttä täsmää
importing-updated = Päivitetty
importing-update-if-newer = Jos uudempi
importing-update-always = Aina
importing-update-never = Ei koskaan
importing-update-notes = Päivitä muistiinpanot
importing-update-notes-help = Määrittää, milloin kokoelmassa olevia muistiinpanoja päivitetään. Oletusarvoisesti tämä tehdään vain, jos vastaavaa tuotua muistiinpanoa on muutettu äskettäin.
importing-update-notetypes = Päivitä muistiinpanotyypit
importing-update-notetypes-help =
    Määrittää, milloin kokoelmassa olevia muistiinpanotyypejä päivitetään. Oletusarvoisesti tämä tehdään vain, 
    jos vastaavaa tuotua muistiinpanotyyppiä on muutettu äskettäin.
    Mallin tekstin ja muotoilun muutokset voidaan aina tuoda, mutta skeemamuutosten (esim. kenttien määrän tai järjestyksen muutokset)
    tuontia varten myös '{ importing-merge-notetypes }’ -valinta on otettava käyttöön.
importing-note-added =
    { $count ->
        [one] { $count } muistiinpano lisätty
       *[other] { $count } muistiinpanoa lisätty
    }
importing-note-imported =
    { $count ->
        [one] { $count } muistiinpano tuotu
       *[other] { $count } muistiinpanoa tuotu
    }
importing-note-unchanged =
    { $count ->
        [one] { $count } muistiinpano säilyi muuttumattomana
       *[other] { $count } muistiinpanoa säilyi muuttumattomana
    }
importing-note-updated =
    { $count ->
        [one] { $count } muistiinpano päivitetty
       *[other] { $count } muistiinpanoa päivitetty
    }
importing-processed-media-file =
    { $count ->
        [one] Tuotiin { $count } mediatiedosto
       *[other] Tuotiin { $count } mediatiedostoa
    }
importing-importing-file = Tuodaan tiedostoa...
importing-extracting = Puretaan dataa...
importing-gathering = Kerätään dataa...
importing-failed-to-import-media-file = Mediatiedoston tuominen ei onnistunut: { $debugInfo }
importing-processed-notes =
    { $count ->
        [one] { $count } muistiinpano käsitelty...
       *[other] { $count } muistiinpanoa käsitelty...
    }
importing-processed-cards =
    { $count ->
        [one] { $count } kortti käsitelty...
       *[other] { $count } korttia käsitelty...
    }
importing-existing-notes = Olemassa olevat muistiinpanot
# "Existing notes: Duplicate" (verb)
importing-duplicate = Monista
# "Existing notes: Preserve" (verb)
importing-preserve = Säilytä
# "Existing notes: Update" (verb)
importing-update = Päivitä
importing-tag-all-notes = Merkitse kaikki muistiinpanot tunnisteella
importing-tag-updated-notes = Merkitse päivitetyt muistiinpanot tunnisteella
importing-file = Tiedosto
# "Match scope: notetype / notetype and deck". Controls how duplicates are matched.
importing-match-scope = Täsmää laajuus
# Used with the 'match scope' option
importing-notetype-and-deck = Muistiinpanotyyppi ja pakka
importing-cards-added =
    { $count ->
        [one] { $count } kortti lisätty.
       *[other] { $count } korttia lisätty.
    }
importing-file-empty = Valitsemasi tiedosto on tyhjä.
importing-notes-added =
    { $count ->
        [one] { $count } uusi muistiinpano tuotu.
       *[other] { $count } uutta muistiinpanoa tuotu.
    }
importing-notes-updated =
    { $count ->
        [one] { $count } muistiinpanoa käytettiin olemassa olevien päivittämiseen.
       *[other] { $count } muistiinpanoa käytettiin olemassa olevien päivittämiseen.
    }
importing-existing-notes-skipped =
    { $count ->
        [one] { $count } muistiinpano on jo kokoelmassasi.
       *[other] { $count } muistiinpanoa on jo kokoelmassasi.
    }
importing-notes-failed =
    { $count ->
        [one] { $count } muistiinpanon tuonti ei onnistunut.
       *[other] { $count } muistiinpanon tuonti ei onnistunut.
    }
importing-conflicting-notes-skipped =
    { $count ->
        [one] { $count } muistiinpanoa ei tuotu, koska sen muistiinpanotyyppi on muuttunut.
       *[other] { $count } muistiinpanoa ei tuotu, koska niiden muistiinpanotyypit ovat muuttuneet.
    }
importing-conflicting-notes-skipped2 =
    { $count ->
        [one] { $count } muistiinpanoa ei tuotu, koska sen muistiinpanotyyppi on muuttunut ja '{ importing-merge-notetypes }' ei ollut käytössä.
       *[other] { $count } muistiinpanoa ei tuotu, koska niiden muistiinpanotyyppi on muuttunut ja '{ importing-merge-notetypes }' ei ollut käytössä.
    }
importing-import-log = Tuonnin loki
importing-no-notes-in-file = Tiedostosta ei löytynyt muistiinpanoja.
importing-notes-found-in-file2 =
    { $notes ->
        [one] Tiedostosta löytyi { $notes } muistiinpano. Niiden joukosta:
       *[other] Tiedostosta löytyi { $notes } muistiinpanoa. Niiden joukosta:
    }
importing-show = Näytä
importing-details = Tiedot
importing-status = Tila
importing-duplicate-note-added = Muistiinpanon kaksoiskappale lisätty
importing-added-new-note = Uusi muistiinpano lisätty
importing-existing-note-skipped = Muistiinpano ohitettu, koska ajantasainen kopio siitä on jo kokoelmassasi
importing-note-skipped-update-due-to-notetype = Muistiinpanoa ei päivitetty, koska muistiinpanotyyppiä on muutettu sen jälkeen, kun muistiinpano tuotiin ensimmäisen kerran
importing-note-skipped-update-due-to-notetype2 = Muistiinpanoa ei päivitetty, koska muistiinpanon tyyppiä on muutettu sen jälkeen, kun olet tuonut muistiinpanon ensimmäistä kertaa, eikä asetus '{ importing-merge-notetypes }' ollut käytössä.
importing-note-updated-as-file-had-newer = Muistiinpano päivitetty, koska tiedoston sisältämä versio oli uudempi
importing-note-skipped-due-to-missing-notetype = Muistiinpano ohitettu, koska sen muistiinpanotyyppi puuttuu
importing-note-skipped-due-to-missing-deck = Muistiinpano ohitettu, koska sen pakka puuttuu
importing-note-skipped-due-to-empty-first-field = Muistiinpano ohitettu, koska sen ensimmäinen kenttä on tyhjä
importing-field-separator-help =
    Merkki, joka erottaa tekstitiedoston kentät toisistaan. Voit tarkistaa esikatselun avulla, onko kentät erotettu oikein.
    
    Huomaa, että jos tämä merkki esiintyy jossakin kentässä, kenttään on lisättävä lainausmerkit CSV-standardin mukaisesti. Taulukkolaskentaohjelmat, kuten LibreOffice, tekevät tämän automaattisesti.
importing-allow-html-in-fields-help = Ota tämä käyttöön, jos tiedosto sisältää HTML-muotoilua. Jos tiedosto esimerkiksi sisältää merkkijonon '&lt;br&gt;', se näkyy rivinvaihtona kortissasi. Jos taas tämä on pois käytöstä, merkit '&lt;br&gt;’ näytetään sellaisenaan.
importing-notetype-help =
    Äskettäin tuoduilla muistiinpanoilla on tämä muistiinpanotyyppi, ja vain olemassa olevat muistiinpanot, joilla on tämä muistiinpanotyyppi, päivitetään.
    
    Voit valita, mitkä tiedoston kentät vastaavat mitäkin muistiinpanotyypin kenttiä liitostyökalulla.
importing-deck-help = Tuodut kortit sijoitetaan tähän pakkaan.
importing-existing-notes-help =
    Mitä tehdä, jos tuotu muistiinpano vastaa olemassa olevaa muistiinpanoa.
    
    - `{ importing-update }`: Päivitä olemassa oleva muistiinpano.
    - `{ importing-preserve }`: Älä tee mitään.
    - `{ importing-duplicate }`: Luo uusi muistiinpano.
importing-match-scope-help =
    Vain olemassa olevat muistiinpanot, joilla on sama muistiinpanotyyppi, tarkistetaan kaksoiskappaleiden varalta.
    Tämä voidaan lisäksi rajoittaa koskemaan muistiinpanoja, joiden kortit ovat samassa pakassa.
importing-tag-all-notes-help = Nämä tunnisteet lisätään sekä uusiin että päivitettyihin muistiinpanoihin.
importing-tag-updated-notes-help = Nämä tunnisteet lisätään päivitettyihin muistiinpanoihin.
importing-overview = Yleiskatsaus

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

importing-importing-collection = Tuodaan kokoelmaa...
importing-unable-to-import-filename = Tiedoston { $filename } tuonti ei onnistunut: tiedostotyyppiä ei tueta
importing-notes-that-could-not-be-imported = Muistiinpanoja, joita ei voitu tuoda, koska muistiinpanotyyppi on muuttunut: { $val }
importing-added = Lisätty
importing-pauker-18-lesson-paugz = Pauker 1.8 oppitunti (*.pau.gz)
importing-supermemo-xml-export-xml = Supermemo XML -vienti (*.xml)
