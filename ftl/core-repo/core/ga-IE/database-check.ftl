database-check-corrupt = Tá comhad an chnuasaigh truaillithe. Déan aischur ó chúltaca uathoibríoch.
database-check-rebuilt = Bunachar sonraí atógtha agus optamaithe.
database-check-card-properties =
    { $count ->
        [one] Deisíodh { $count } airí cárta neamhbhailí.
        [two] Deisíodh { $count } airí cárta neamhbhailí.
        [few] Deisíodh { $count } airí cárta neamhbhailí.
        [many] Deisíodh { $count } n-airí cárta neamhbhailí.
       *[other] Deisíodh { $count } airí cárta neamhbhailí.
    }
database-check-missing-templates =
    Scriosadh 
    { $count ->
        [one] { $count } chárta amháin a raibh a theimpléad ar iarraidh
        [two] { $count } chárta a raibh a dteimpléad ar iarraidh
        [few] { $count } chárta a raibh a dteimpléad ar iarraidh
        [many] { $count } gcárta a raibh a dteimpléad ar iarraidh
       *[other] { $count } cárta a raibh a dteimpléad ar iarraidh
    }
database-check-field-count =
    { $count ->
        [one] Deisíodh { $count } nóta amháin agus líon mícheart de réimsí aige.
        [two] Deisíodh { $count } nóta agus líon mícheart de réimsí acu.
        [few] Deisíodh { $count } nóta agus líon mícheart de réimsí acu.
        [many] Deisíodh { $count } nóta agus líon mícheart de réimsí acu.
       *[other] Deisíodh { $count } nóta agus líon mícheart de réimsí acu.
    }
database-check-new-card-high-due =
    Aimsíodh 
       { $count ->
        [one] { $count } chárta amháin nua a bhfuil uimhir 'le staidéar' >= 1,000,000 aige - d'fhéadfaí é
        [two] { $count } chárta nua a bhfuil uimhir 'le staidéar' >= 1,000,000 acu - d'fhéadfaí iad
        [few] { $count } chárta nua a bhfuil uimhir 'le staidéar' >= 1,000,000 acu - d'fhéadfaí iad
        [many] { $count } gcárta nua a bhfuil uimhir 'le staidéar' >= 1,000,000 acu - d'fhéadfaí iad
       *[other] { $count } cárta nua a bhfuil uimhir 'le staidéar' >= 1,000,000 acu - d'fhéadfaí iad
    } a bhogadh faoi 'Brabhsáil'.
database-check-card-missing-note =
    { $count ->
        [one] Scriosadh { $count } chárta amháin a raibh a nóta ar iarraidh
        [two] Scriosadh { $count } chárta a raibh a nóta ar iarraidh
        [few] Scriosadh { $count } chárta a raibh a nóta ar iarraidh
        [many] Scriosadh { $count } gcárta a raibh a nóta ar iarraidh
       *[other] Scriosadh { $count } cárta a raibh a nóta ar iarraidh
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] Scriosadh { $count } chárta amháin a raibh teimpléad dúblach aige.
        [two] Scriosadh { $count } chárta a raibh teimpléad dúblach acu.
        [few] Scriosadh { $count } chárta a raibh teimpléad dúblach acu.
        [many] Scriosadh { $count } gcárta a raibh teimpléad dúblach acu.
       *[other] Scriosadh { $count } cárta a raibh teimpléad dúblach acu.
    }
database-check-missing-decks =
    { $count ->
        [one] Deisíodh { $count } phaca amháin a bhí ar iarraidh.
        [two] Deisíodh { $count } phaca a bhí ar iarraidh.
        [few] Deisíodh { $count } phaca a bhí ar iarraidh.
        [many] Deisíodh { $count } bpaca a bhí ar iarraidh.
       *[other] Deisíodh { $count } paca a bhí ar iarraidh.
    }
database-check-revlog-properties =
    { $count ->
        [one] Deisíodh { $count } iontráil athbhreithnithe amháin a raibh a airí neamhbhailí.
        [two] Deisíodh { $count } iontráil athbhreithnithe a raibh a n-airí neamhbhailí.
        [few] Deisíodh { $count } iontráil athbhreithnithe a raibh a n-airí neamhbhailí.
        [many] Deisíodh { $count } n-iontráil athbhreithnithe a raibh a n-airí neamhbhailí.
       *[other] Deisíodh { $count } iontráil athbhreithnithe a raibh a n-airí neamhbhailí.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] Deisíodh { $count } nóta amháin a raibh carachtair neamhbhailí utf8 ann.
        [two] Deisíodh { $count } nóta a raibh carachtair neamhbhailí utf8 iontu.
        [few] Deisíodh { $count } nóta a raibh carachtair neamhbhailí utf8 iontu.
        [many] Deisíodh { $count } nóta a raibh carachtair neamhbhailí utf8 iontu.
       *[other] Deisíodh { $count } nóta a raibh carachtair neamhbhailí utf8 iontu.
    }
# "db-check" is always in English
database-check-notetypes-recovered = Bhí cineál nóta amháin (ar a laghad) ar iarraidh. Tugadh cineálacha nóta nua dar tosadh "db-check" do na nótaí a bhí i gceist, ach cailleadh ainmneacha na réimsí agus dearadh na gcartaí.  Seans gurbh fhearr a d'fheilfeadh sé aischur a dhéanamh ó chúltaca uathoibríoch.

## Progress info

database-check-checking-integrity = An cnuasach á sheiceáil...
database-check-rebuilding = Atógáil...
database-check-checking-cards = Cártaí á seiceáil...
database-check-checking-notes = Nótaí á seiceáil...
database-check-checking-history = Stair á seiceáil...
database-check-title = Seiceáil an Bunachar Sonraí
