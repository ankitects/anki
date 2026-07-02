### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = Curtha leis: { $up }↑ { $down }↓
sync-media-removed-count = Scriosta: { $up }↑ { $down }↓
sync-media-checked-count =
    Seiceáilte: { $count ->
        [one] { $count } cheann amháin
        [two] { $count } cheann
        [few] { $count } cinn
        [many] { $count } gcinn
       *[other] { $count } ceann
    }
sync-media-starting = Sioncronú meán ag tosú..
sync-media-complete = Sioncronú meán críochnaithe.
sync-media-failed = Theip ar shioncronú meán.
sync-media-aborting = Sioncronú meán á thobscoir...
sync-media-aborted = Sioncronú meán tobscortha.
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = Sioncronú meán díchumasaithe.
# Title of the screen that shows syncing progress history
sync-media-log-title = Loga sioncronú meán

## Error messages / dialogs

sync-conflict = Ní féidir sioncronú a dhéanamh ach le cóip amháin d'Anki ag an am. Fan cúpla nóiméad, agus bain triail as arís.
sync-server-error = Tá fadhb ag AnkiWeb.  Bain triail as arís i gceann cúpla nóiméad.
sync-client-too-old = Tá an leagan seo de Anki róshean. Déan nuashonrú chun sioncronú a dhéanamh.
sync-wrong-pass = Ainm úsáideora nó pasfhocal AnkiWeb mícheart: bain triail arís as
sync-resync-required = Déan sioncronú arís. Má fheictear an teachtaireacht seo go minic, déan póstáil ar an láithreán tacaíochta.
sync-must-wait-for-end = Tá Anki ag sioncronú. Fan go mbeidh sé críochnaithe roimh triail eile a bhaint.
sync-confirm-empty-download = Níl aon chárta sa chnuasach logánta.  Íoslódáil ó AnkiWeb?
sync-conflict-explanation =
    De bharr an tsaghais difríochta idir na pacaí anseo agus ar AnkiWeb, ní féidir iad a chumasc.  Caithfear na pacaí ar thaobh amháin a fhorscríobh leis na pacaí ón taobh eile.
    
    Má roghnaíonn tú 'íoslódáil', déanfaidh Anki an cnuasach a Íoslódáil ó AnkiWeb, agus caillfear pé athruithe a rinne tú ar do ríomhaire tar éis an tsioncronaithe is déanaí.
    
    Má roghnaíonn tú 'úaslódáil', déanfaidh Anki an cnuasach a uaslódáil chuig AnkiWeb, agus caillfear pé athruithe a rinne tú ar AnkiWeb (nó ar ghléas ar bith eile) tar éis an tsioncronaithe is déanaí.
    
    A luaithe is atá gach gléas sioncronaithe, féadfar cumasc a dhéanamh go huathoibreach ar athbhreithnite agus ar chártaí nua as sin amach.
sync-ankiweb-id-label = Ainm Úsáideora AnkiWeb:
sync-password-label = Pasfhocal:
sync-account-required =
    <h1>Cuntas de dhíth</h1>
    Teastaíonn cuntas saor in aisce chun do chnuasach a choinneáil sioncronaithe.  <a href="{ $link }">Cruthaigh cuntas</a> duit féin, ansin cuir isteach do chuid sonraí thíos.
sync-sanity-check-failed = Úsáid an fheidhm 'Seiceáil Bunachar Sonraí', ansin déan sioncronú arís.  Mura réitíonn sé seo an fhadhb, déan sioncronú iomlán faoi 'Roghanna'
sync-clock-off = Ní féidir sioncronú a dhéanamh - tá an t-am mícheart ar an gclog.
sync-upload-too-large =
    Tá comhad do chnuasaigh rómhór chun é a uaslódáil chuig AnkiWeb.
    Chun a mhéid a laghdú, bain aon phaca nach dteastaíonn (d'fhéadfá
    iad a easpórtáil ar dtús), agus ansin roghnaigh 'Seiceáil an Bunachar
    Sonraí' chun méid an chomhaid a nuashonrú. ({ $details })

## Buttons

sync-media-log-button = Loga Meán
sync-abort-button = Tobscoir
sync-download-from-ankiweb = Íoslódáil ó AnkiWeb
sync-upload-to-ankiweb = Uaslódáil chuig AnkiWeb
sync-cancel-button = Cealaigh

## Normal sync progress

sync-downloading-from-ankiweb = Ag íoslódáil ó AnkiWeb ...
sync-uploading-to-ankiweb = Ag Uaslódáil Chuig AnkiWeb ...
sync-syncing = Ag sioncronú...
sync-checking = Ag seiceáil...
sync-connecting = Ag ceangal...
sync-added-updated-count = Curtha leis/athraithe: { $up }↑ { $down }↓
sync-log-out-button = Logáil Amach
sync-collection-complete = Críochnaigh sioncronú an chnuasaigh.
