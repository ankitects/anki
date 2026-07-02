## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = بحث غير صالح: { $reason }
search-misplaced-and = وجدت كلمة `and` لكنها لا تصل عبارتي بحث. إذا كنت تريد البحث عن الكلمة نفسها، أحطها بعلامات اقتباس مزدوجة: `"and"`.
search-misplaced-or = وجدت كلمة `or` لكنها لا تصل عبارتي بحث. إذا كنت تريد البحث عن الكلمة نفسها، أحطها بعلامات اقتباس مزدوجة: `"or"`.
# Here, the ellipsis "..." may be localised.
search-empty-group = وجدت مجموعة `(...)`، لكن ليس هناك شيء بين الأقواس. إذا كنت تريد البحث عن الأقواس نفسها، أحطها بعلامات اقتباس مزدوجة: `"( )"`.
search-unopened-group = وجد قوس إغلاق `)`، لكن ليس هناك قوس فتح `(` يسبقه. إذا كنت تريد البحث عن حرف `)` نفسه، أحطه بعلامات اقتباس مزدوجة أو اسبقه بإشارة مائلة معاكسة: `")"` أو `\)`.
search-unclosed-group = وجد قوس فتح `(`، لكن ليس هناك قوس فتح `(` يسبقه. إذا كنت تريد البحث عن حرف `(` نفسه، أحطه بعلامات اقتباس مزدوجة أو اسبقه بإشارة مائلة معاكسة: `"("` أو `\(`.
search-empty-quote = وجد زوج علامات اقتباس مزدوجة `""`، لكن ليس هناك شيء بينها للبحث عنه. إذا كنت تريد البحث عن علامات الاقتباس المزدوجة نفسها، اسبقها بإشارة مائلة معاكسة: `\"\"`.
search-unclosed-quote = وجدت علامة اقتباس مزدوجة `"`، لكن ليس هناك علامة أخرى لإغلاقها. إذا كنت تريد البحث عن علامة `"` نفسها، اسبقها بإشارة مائلة معاكسة: `\"`.
search-missing-key = وجدت نقطتان رأسيتان `:`، لكن ليس هناك كلمة مفتاحية تسبقها. إذا كنت تريد البحث عن حرف `:` نفسه، اسبقه بإشارة مائلة معاكسة: `\:`.
search-unknown-escape = سلسة تجاهل الحروف الخاصة `{ $val }` غير معرفة. إذا كنت تريد البحث عن حرف `\` نفسه، اسبقه بالحرف نفسه: `\\`.
search-invalid-argument = أعطي `{ $term }` مدخلًا غير صالح '`{ $argument }`'.
search-invalid-flag-2 = يجب أن تُلحَق `flag:` برقم مؤشر صالح: `1` (أحمر)، `2` (برتقالي)، `3` (أخضر)، `4` (أزرق)، `5` (زهري)، `6` (فيروزي)، `7` (بنفسجي)، `0` (لا مؤشر).
search-invalid-prop-operator = يجب أن تتبع `prop:{ $val }` بواحد من رموز عمليات المقارنة التالية: `=`، `!=`، `<`، `>`، `<=`، أو `>=`.
search-invalid-other = يرجى التحقق من عدم وجود أخطاء كتابية.

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = توقعت رقمًا في "`{ $context }`"، لكني وجدت "`{ $provided }`".
search-invalid-whole-number = توقعت رقمًا صحيحًا في "`{ $context }`"، لكني وجدت "`{ $provided }`".
search-invalid-positive-whole-number = توقعت رقمًا إيجابيًا صحيحًا في "`{ $context }`"، لكني وجدت "`{ $provided }`".
search-invalid-negative-whole-number = توقعت رقمًا صحيحًا أصغر أو يساوي 0 في "`{ $context }`"، لكني وجدت "`{ $provided }`".
search-invalid-answer-button = توقعت زر إجابة بين 1-4 في "`{ $context }`"، لكني وجدت "`{ $provided }`".

## Column labels in browse screen

search-note-modified = تاريخ تعديل الملحوظة
search-card-modified = تاريخ تعديل البطاقة

##

# Tooltip for search lines outside browser
search-view-in-browser = عرض في المتصفح
