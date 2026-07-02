### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = Pievienots: { $up }↑ { $down }↓
sync-media-removed-count = Noņemts: { $up }↑ { $down }↓
sync-media-checked-count = Pārbaudīts: { $count }
sync-media-starting = Sākas multivides sinhronizācija...
sync-media-complete = Multivides sinhronizācija pabeigta.
sync-media-failed = Informācijas nesēju sinhronizēšana neizdevās.

## Error messages / dialogs

sync-conflict = Tikai viena Anki kopija var vienlaicīgi sinhronizēt Tavā kontā. Lūgums uzgaidīt dažas minūtes, tad mēģināt vēlreiz.
sync-server-error = AnkiWeb gadījās sarežģījums. Lūgums pēc dažām minūtēm vēlreiz mēģināt vēlreiz.
sync-wrong-pass = E-pasta adrese vai parole bija nepareiza; lūgums mēģināt vēlreiz.
sync-resync-required = Lūgums sinhronizēt vēlreiz. Ja šis ziņojums turpina parādīties, lūgums vērsties atbalsta vietnē.
sync-must-wait-for-end = Anki pašlaik sinhronizē. Lūgums uzgaidīt, līdz sinhronizēšana tiek pabeigta, tad jāmēģina vēlreiz.
sync-confirm-empty-download = Vietējā krājumā nav kartīšu. Lejupielādēt no AnkiWeb?
sync-confirm-empty-upload = AnkiWeb krājumā nav kartīšu. Aizstāt to ar vietējo krājumu?
sync-conflict-explanation =
    Kavas šeit un AnkiWeb atšķiras tādā veidā, ka tās nevar apvienot, tādēļ vienā pusē ir nepieciešams pārrakstīt kavas ar kavām no otras puses.
    
    Ja izvēlas lejupielādēt, Anki iegūs krājumu no AnkiWeb, un jebkuras izmaiņas, kas šajā ierīcē tika veiktas kopš pēdējās sinhronizēšanas reizes, tiks zaudētas.
    
    Ja izvēlas augšupielādēt, Anki nosūtīts datus no šīs ierīces uz AnkiWeb, un jebkuras izmaiņas, kas gaida AnkiWeb, tiks zaudētas.
    
    Kad visas ierīces būs vienādotas, turpmākās pārskatīšanas un pievienotās kartītes var tikt automātiski apvienotas.
sync-conflict-explanation2 =
    Ir nesaderība starp šajā ierīcē un AnkiWeb esošajām kavām. Jāizvēlas, kuru versiju paturēt:
    
    - atlasīt **{ sync-download-from-ankiweb }**, lai aizvietotu šeit esošās kavas ar AnkiWeb versiju; tiks zaudētas jebkādas izmaiņas, kas tika veiktas šajā ierīcē pēc pēdējās sinhronizēšanas;
    - atlasīt **{ sync-upload-to-ankiweb }**, lai pārrakstītu AnkiWeb versijas ar šajā ierīcē esošajām kavām un izdzēstu jebkādas AnkiWeb esošās izmaiņas.
    
    Tiklīdz nesaderība būs novērsta, sinhronizēšana darbosies kā ierasts.
sync-account-required =
    <h1>Nepieciešams konts</h1>
    Ir nepieciešams bezmaksas konts, lai turētu savu krājumu vienādotu. Lūgums <a href="{ $link }">izveidot</a> kontu, tad zemāk ievadīt savu informāciju.
sync-sanity-check-failed = Lūgums izmantot "Pārbaudīt datubāzi", tad sinhronizēt vēlreiz. Ja sarežģījumi turpinās, lūgums iestatījumu ekrānā veikt uzspiestu vienvirziena sinhronizēšanu.
# “details” expands to a string such as “300.14 MB > 300.00 MB”
sync-upload-too-large =
    Krājuma datne ir pārāk liela, lai to nosūtītu AnkiWeb. Tās izmēru var
    samazināt ar nevajadzīgo kavu noņemšanu (pēc izvēles pirms tam tās izgūstot)
    un tad izmantot "Pārbaudīt datubāzi", lai samazinātu datnes lielumu. ({ $details })
sync-ankihub-login-failed = Ar norādītajiem pieteikšanās datiem nevarēja pieteikties AnkiHub.

## Buttons


## Normal sync progress

sync-collection-complete = Krājuma vienādošana pabeigta.
