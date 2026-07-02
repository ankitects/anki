## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = Yaroqsiz qidiruv: { $reason }
search-misplaced-and = `and` topildi, lekin u ikkita qidiruv soʻzini bogʻlamaydi. Agar soʻzning oʻzini qidirmoqchi boʻlsangiz, uni qoʻsh tirnoq ichiga oʻrang: `"and"`.
search-misplaced-or = `or` topildi, lekin u ikkita qidiruv soʻzini bogʻlamaydi. Agar soʻzning oʻzini qidirmoqchi boʻlsangiz, uni qoʻsh tirnoq ichiga oʻrang: `"or"`.
# Here, the ellipsis "..." may be localised.
search-empty-group = `(...)` guruhi topildi, lekin qavslar orasida qidirish uchun hech narsa yoʻq edi. Agar aynan qavslarni oʻzini qidirmoqchi boʻlsangiz, ularni qoʻsh tirnoq ichiga oʻrang: `"( )"`.
search-unopened-group = `)` yopiluvchi qavs topildi, lekin ochiluvchi qavs `(` oldida yoʻq. Agar aynan `)` oʻzini qidirmoqchi boʻlsangiz, uni qoʻsh tirnoq ichiga oʻrang yoki oldiga teskari qiya chiziq qoʻying: `")"` yoki `\)`.
search-unclosed-group = `(` ochiluvchi qavs topildi, lekin ketidan yopiluvchi qavs `)` yoʻq. Agar aynan `(` oʻzini qidirmoqchi boʻlsangiz, uni qoʻsh tirnoq ichiga oʻrang yoki oldiga teskari qiya chiziq qoʻying: `"("` yoki `\(`.
search-empty-quote = bir juft qoʻsh tirnoq `""` topildi, lekin ular orasida qidirish uchun hech narsa yoʻq edi. Agar aynan qoʻshtirnoqlarni oʻzini qidirmoqchi boʻlsangiz, oldiga teskari qiya chiziq qoʻying: `\"\"`.
search-unclosed-quote = `"` ochiluvchi qoʻshtirnoq topildi, lekin uni yopish uchun ikkinchisi yoʻq. Agar aynan `"` belgini oʻzini qidirmoqchi boʻlsangiz, oldiga teskari qiya chiziq qoʻying: `\"`.
search-missing-key = ikki nuqta `:` topildi, lekin uning oldida hech qanday kalit soʻz yoʻq edi. Agar aynan `:` belgini oʻzini qidirmoqchi boʻlsangiz, oldiga teskari qiya chiziq qoʻying: `\:`.
search-unknown-escape = `{ $val }` escape ketma-ketligi aniqlanmagan. Agar aynan `\` teskari qiya chiziq belgini oʻzini qidirmoqchi boʻlsangiz, oldiga yana bittasini qoʻying: `\\`.
search-invalid-argument = '`{ $argument }`' argumenti `{ $term }` uchun yaroqsiz.
search-invalid-flag-2 = `flag:` dan keyin yaroqli bayroq raqami kelishi kerak: `1` (qizil), `2` (toʻq sariq), `3` (yashil), `4` (koʻk), `5` (pushti), `6` (moviy), `7` (binafsha) or `0` (bayroq yoʻq).
search-invalid-prop-operator = `prop:{ $val }`dan keyin quyidagi taqqoslash operatorlaridan biri kelishi kerak: `=`, `!=`, `<`, `>`, `<=` yoki `>=`.
search-invalid-other = kiritish xatolarini tekshiring.

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = "`{ $context }`" ichida son kutilgan edi, lekin "`{ $provided }`" topildi.
search-invalid-whole-number = "`{ $context }`" ichida butun son kutilgan edi, lekin "`{ $provided }`" topildi.
search-invalid-positive-whole-number = "`{ $context }`" ichida musbat son kutilgan edi, lekin "`{ $provided }`" topildi.
search-invalid-negative-whole-number = "`{ $context }`" ichida 0 ga teng yoki undan kichik son kutilgan edi, lekin "`{ $provided }`" topildi.
search-invalid-answer-button = "`{ $context }`" ichida 1 va 4 orasidagi javob tugmasi kutilgan edi, lekin "`{ $provided }`" topildi.

## Column labels in browse screen

search-note-modified = Qayd oʻzgartirildi
search-card-modified = Karta oʻzgartirildi

##

# Tooltip for search lines outside browser
search-view-in-browser = Karta brauzerida koʻrish
