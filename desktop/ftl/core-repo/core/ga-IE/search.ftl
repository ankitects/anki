## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = Cuardach neamhbhailí: { $reason }
search-misplaced-and = Bhí an focal `and` sa chuardach ach gan dhá théarma a bheith á nascadh aige. Más mian leat cuardach a dhéanamh ar an bhfocal 'and' féin, bíodh sé idir uaschamóga: `"and"`.
search-misplaced-or = Bhí an focal `or` sa chuardach ach gan dhá théarma a bheith á nascadh aige. Más mian leat cuardach a dhéanamh ar an bhfocal 'or' féin, bíodh sé idir uaschamóga: `"or"`.
# Here, the ellipsis "..." may be localised.
search-empty-group = Bhí grúpa `(...)` sa chuardach,  ach ní raibh aon rud idir na lúibíní sin arbh fhéidir a chuardach. Más mian leat cuardach a dhéanamh ar lúibíní mar dhea, bíodh siad idir uaschamóga: `"( )"`.
search-unopened-group = Bhí lúibín deiridh `)` sa chuardach,  ach ní raibh aon lúibín tosaigh `(` roimhe. Más mian leat cuardach a dhéanamh ar lúibíní deiridh mar dhea, bíodh an lúibín idir uaschamóga nó bíodh cúlslais roimhe: `")"` or `\)`.
search-unclosed-group = Bhí lúibín tosaigh `(` sa chuardach,  ach ní raibh aon lúibín deiridh `)` ina dhiaidh. Más mian leat cuardach a dhéanamh ar lúibíní tosaigh mar dhea, bíodh an lúibín idir uaschamóga nó bíodh cúlslais roimhe: `"("` or `\(`.
search-empty-quote = Bhí péire uaschamóg`""` sa chuardach,  ach ní raibh aon rud idir na huaschamóga sin arbh fhéidir a chuardach. Más mian leat cuardach a dhéanamh ar uaschamóga mar dhea, bíodh cúlslais rompu: `\"\"`.
search-unclosed-quote = Bhí uaschamóg `"` sa chuardach,  ach ní raibh aon dara huaschamóg léi. Más mian leat cuardach a dhéanamh ar uaschamóg mar dhea, bíodh cúlslais roimpi: `\"`.
search-missing-key = Bhí idirstad `:` sa chuardach,  ach ní raibh aon eochairfhocal roimhe. Más mian leat cuardach a dhéanamh ar idirstad mar dhea, bíodh cúlslais roimhe: `\:`.
search-unknown-escape = Níor tuigeadh an seachamh éalaithe `{ $val }`.  Más mian leat cuardach a dhéanamh ar chúlslais mar dhea, bíodh cúlslais eile roimpi: `\\`.
search-invalid-argument = Ní bailí '`{ $argument }`' mar argóint tar éis `{ $term }`.
search-invalid-flag-2 = Teastaíonn uimhir bhrataí atá bailí tar éis`bratach:` .i. : `1` (dearg), `2` (buí), `3` (glas uaine), `4` (gorm), `5` (bándearg), `6` (gormghlas), `7` (corcra) nó `0` (gan bhratach).
search-invalid-prop-operator = Ní mór oibritheoir comparáide a lua tar éis `prop:{ $val }`, mar atá: `=`, `!=`, `<`, `>`, `<=` or `>=`.
search-invalid-other = féach an bhfuil aon bhotún cló ann.

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = bítear ag súil le huimhir i "`{ $context }`", ach is éard a bhí ann ná "`{ $provided }`".
search-invalid-whole-number = bítear ag súil le slánuimhir i "`{ $context }`", ach is éard a bhí ann ná "`{ $provided }`".
search-invalid-positive-whole-number = bítear ag súil le slánuimhir dheimhneach i "`{ $context }`", ach is éard a bhí ann ná "`{ $provided }`".
search-invalid-negative-whole-number = bítear ag súil le slánuimhir níos lú ná nó cothrom le 0 i "`{ $context }`", ach is éard a bhí ann ná "`{ $provided }`".
search-invalid-answer-button = bítear ag súil le cnaipe freagartha idir 1-4 i "`{ $context }`", ach is éard a bhí ann ná "`{ $provided }`".

## Column labels in browse screen

search-note-modified = Nóta Athraithe
search-card-modified = Cárta Athraithe

##

# Tooltip for search lines outside browser
search-view-in-browser = Breathnaigh sa bhrabhsálaí
