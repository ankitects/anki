## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = Nederīga meklēšana: { $reason }
search-misplaced-and = tika atrasts `and`, bet tas nesavieno divas meklēšanas vārdkopas. Ja ir vēlēšanās meklēt pašu vārdu, tas ir jāievieto divkāršajās pēdiņās: `"and"`.
search-misplaced-or = tika atrasts `or`, bet tas nesavieno divas meklēšanas vārdkopas. Ja ir vēlēšanās meklēt pašu vārdu, tas ir jāievieto divkāršajās pēdiņās: `"or"`.
# Here, the ellipsis "..." may be localised.
search-empty-group = tika atrasta kopa `(...)`, bet starp iekavām nav nekā, ko meklēt. Ja ir vēlēšanās meklēt burtiskas iekavas, tās ir jāievieto divkāršajās pēdiņās: `"()"`.
search-unopened-group = tika atrasta aizverošā iekava `)`, bet pirms tās nav atverošās iekavas `(`. Ja ir vēlēšanās meklēt burtisku `)`, tā ir jāievieto divkāršajās pēdiņās vai pirms tās jāizmanto atpakaļslīpsvītra: `")"` vai `\)`.
search-unclosed-group = tika atrasta atverošā iekava `(`, bet nebija tai sekojošas aizverošās iekavas `)`. Ja ir vēlēšanās meklēt burtisku `(`, tā ir jāiekļauj pēdiņās vai pirms tās ir jāpievieno atpakaļslīpsvītra: `"("` vai `\(`.
search-empty-quote = tika atrasts pēdiņu pāris `""`, bet starp tām nekā nebija, ko meklēt. Ja ir vēlēšanās meklēt burtiskas pēdiņas, pirms tām jāizmanto atpakaļslīpsvītras: `\"\"`.
search-unclosed-quote = tika atrasta atverošās pēdiņas `"`, bet nebija otra pāra, kas tās noslēgtu. Ja ir vēlēšanās meklēt burtisku `"`, priekšā jāpievieno atpakaļslīpsvītra: `\"`.
search-missing-key = tika atrasts kols `:`, bet pirms tā nebija neviena atslēgvārda. Ja ir vēlēšanās meklēt burtisku `:`, pirms tā ir jāizmanto atpakaļslīpsvītra: `\:`.
search-unknown-escape = izvairīšanās virkne `{ $val }` ir nezināma. Ja ir vēlēšanās meklēt burtisku atpakaļslīpsvītru `\`, pirms tās ir jāpievieno vēl viena: `\\`.
search-invalid-argument = `{ $term }` tika sniegts nederīga mainīgā vērtība "`{ $argument }`".
search-invalid-flag-2 = aiz `flag:` ir jābūt derīgam karoga skaitlim: `1` (sarkans), `2` (oranžs), `3` (zaļš), `4` (zils), `5` (rozā), `6` (tirkīza), `7` (violets) vai `0` (nav karoga).
search-invalid-prop-operator = aiz `prop:{ $val }` ir jābūt kādam no šiem salīdzinātājiem: `=`, `!=`, `<`, `>`, `<=` vai `>=`.
search-invalid-other = lūgums pārbaudīt, vai nav rakstības kļūdu.

## eg. expected a number in "due>5x", but found "5x"


## Column labels in browse screen

search-note-modified = Piezīme mainīta
search-card-modified = Karte mainīta

##

# Tooltip for search lines outside browser
search-view-in-browser = Skatīt pārlūkā
