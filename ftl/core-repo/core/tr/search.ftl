## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = Geçersiz arama:{ $reason }
search-misplaced-and = Bir `and` bulundu, ancak iki arama terimini birbirine bağlamıyor. Kelimenin kendisini aramak istiyorsanız, tırnak işareti içine alın: `"and"`.
search-misplaced-or = Bir `or` bulundu, ancak iki arama terimini birbirine bağlamıyor. Kelimenin kendisini aramak istiyorsanız, tırnak işareti içine alın: `"or"`.
# Here, the ellipsis "..." may be localised.
search-empty-group = `(...)` grubu bulundu, ancak parantezler arasında aranacak hiçbir şey yoktu. Parantezlerin kendisini aramak istiyorsanız, onları tırnak işareti içine alın: `"( )"`.
search-unopened-group = Bir kapanış parantezi `)` bulundu, ancak öncesinde bir açılış parantezi `(` yoktu. Eğer `)`nin kendisini aramak istiyorsanız, onu tırnak işareti içine alın veya önüne ters eğik çizgi ekleyin: `")"` veya `\)`.
search-unclosed-group = Bir açılış parantezi `(` bulundu, ancak öncesinde bir kapanış parantezi `)` yoktu. Eğer `(`nin kendisini aramak istiyorsanız, onu tırnak işareti içine alın veya önüne ters eğik çizgi ekleyin: `"("` veya `\(`.
search-empty-quote = Bir çift tırnak işareti `""` bulundu, ancak aralarında aranacak bir şey yoktu. Eğer tırnak işaretlerinin kendisini aramak istiyorsanız, önlerine ters eğik çizgiler ekleyin: `\"\"`.
search-unclosed-quote = Bir tırnak işareti `"` bulundu, ancak onu kapatacak ikinci bir tırnak işareti yoktu. Eğer `"`nin kendisini aramak istiyorsanız, önüne bir ters eğik çizgi ekleyin: `\"`.
search-missing-key = İki nokta `:` bulundu, ancak öncesinde herhangi bir anahtar kelime yoktu. Eğer `:`nın kendisini aramak istiyorsanız, önüne bir ters eğik çizgi ekleyin: `\:`.
search-invalid-flag-2 = `flag:`dan sonra geçerli bir bayrak numarası gelmeli. Örneğin: `1` (kırmızı), `2` (turuncu), `3` (yeşil), `4` (mavi), `5` (pembe), `6` (turkuaz), `7` (mor) veya `0` (bayrak yok).
search-invalid-other = Lütfen yazı hataları için kontrol edin.

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = "`{ $context }`" içinde bir sayı beklendi, ama "`{ $provided }`" bulundu.
search-invalid-whole-number = "`{ $context }`" içinde bir tam sayı beklendi, ama "`{ $provided }`" bulundu.
search-invalid-positive-whole-number = "`{ $context }`" içinde pozitif bir tam sayı beklendi, ama "`{ $provided }`" bulundu.
search-invalid-negative-whole-number = "`{ $context }`" içinde 0'dan küçük veya eşit bir tam sayı beklendi, ama "`{ $provided }`" bulundu.
search-invalid-answer-button = "`{ $context }`" içinde 1-4 arasında cevap düğmesi beklendi, ama "`{ $provided }`" bulundu.

## Column labels in browse screen

search-note-modified = Not Düzenlendi
search-card-modified = Kart Düzenlendi

##

# Tooltip for search lines outside browser
search-view-in-browser = Tarayıcıda görüntüle
