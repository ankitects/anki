## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = חיפוש שגוי: { $reason }
search-misplaced-and = נמצא ביטוי "AND" שאינו מחבר שני ביטויי חיפוש. אם ברצונך לחפש את המילה and, הקף אותה בגרשיים: "and".
search-misplaced-or = נמצא ביטוי "OR" שאינו מחבר שני ביטויי חיפוש. אם ברצונך לחפש את המילה or, הקף אותה בגרשיים: "or".
# Here, the ellipsis "..." may be localised.
search-empty-group = נמצאה קבוצה '(...)', אך אין דבר בתוך הסוגריים. אם ברצונך לחפש טסקט הכולל סוגריים הקף אותם בגרשיים: "()".
search-unopened-group = נמצאו סוגריים סוגרים '(', ללא סוגריים פותחים ')'. אם ברצונך לחפש טקסט הכולל סוגריים הקף אותם בגרשיים: "(" או ")".
search-unclosed-group = נמצאו סוגריים פותחים ')', ללא סוגריים סוגרים '('. אם ברצונך לחפש טקסט הכולל סוגריים הקף אותם בגרשיים: "(" או ")".
search-empty-quote = נמצאו זוג גרשיים, אך ללא ביטוי חיפוש. אם ברצונך לחפש טקסט הכולל גרשיים, הקדם להם לוכסן הפוך: `\"\"`
search-unclosed-quote = נמצאו גרשיים פותחים "  ללא גרשיים סוגרים ". אם ברצונך לחפש טקסט הכולל גרשיים, הקדם להם לוכסן הפוך: `\"`
search-missing-key = נמצאו נקודתיים ללא מילת מפתח. אם ברצונך לחפש טקסט הכולל נקודתיים, הקדם להם לוכסן הפוך: `\:`
search-unknown-escape = הביטוי '{ $val }' אינו מוכר. אם ברצונך לחפש טקסט הכולל לוכסן הפוך '\', הקדם אליו לוכסן נוסף '\\'.
search-invalid-argument = בעבור '{ $term }' התקבל ערך שגוי '{ $argument }'.
search-invalid-flag-2 = `דגל:` חייב להיות עם מספר דגל תקף: `1` (אדום),` 2` (כתום), `3` (ירוק),` 4` (כחול), `5` (ורוד),` 6 `(טורקיז),` 7` (סגול) או `0` (ללא דגל).
search-invalid-prop-operator = לאחר `prop:{ $val }` חייב להופיע אחד מהביטויים הבאים: `=`، `!=`، `<`، `>`، `<=`، או `>=`.
search-invalid-other = נא לבדוק שגיאות הקלדה

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = אמור להיות מספר ב "'{ $context }'", אך נמצא "'{ $provided }'".
search-invalid-whole-number = אמור להיות מספר שלם ב "'{ $context }'", אך נמצא "'{ $provided }'".
search-invalid-positive-whole-number = אמור להיות מספר חיובי ב "'{ $context }'", אך נמצא "'{ $provided }'".
search-invalid-negative-whole-number = אמור להיות מספר שלם קטן או שווה ל0, ב"'{ $context }'", אך נמצא "'{ $provided }'".
search-invalid-answer-button = אמור להיות כפתור תשובה בין 1-4 ב"'{ $context }'", אך נמצא "'{ $provided }'".

## Column labels in browse screen

search-note-modified = רשומה השתנתה
search-card-modified = כרטיס השתנה

##

# Tooltip for search lines outside browser
search-view-in-browser = הצג בדפדפן
