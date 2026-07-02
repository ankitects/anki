## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = Μη έγκυρη αναζήτηση: { $reason }
search-misplaced-and = ένα `and` βρέθηκε αλλά δεν συνδέει δύο όρους προς αναζήτηση. Αν θέλετε να αναζητήσετε την ίδια την λέξη, εισάγετέ την σε διπλά εισαγωγικά: `"and"`.
search-misplaced-or = ένα `or` βρέθηκε αλλά δεν συνδέει δύο όρους προς αναζήτηση. Αν θέλετε να αναζητήσετε την ίδια την λέξη, εισάγετέ την σε διπλά εισαγωγικά: `"or"`.
# Here, the ellipsis "..." may be localised.
search-empty-group = βρέθηκε μια ομάδα `(...)`, αλλά δεν υπήρχε τίποτα ανάμεσα στις αγκύλες για αναζήτηση. Αν θέλετε να αναζητήσετε τις παρενθέσεις, εισάγετέ τις σε διπλά εισαγωγικά: `«( )»`.
search-unopened-group = βρέθηκε μια κλειστή αγκύλη `)`, αλλά δεν υπήρχε καμία αγκύλη ανοίγματος `(` που να προηγείται. Αν θέλετε να αναζητήσετε το `)`, βάλτε το σε διπλά εισαγωγικά ή βάλτε μια backslash: `")"` ή `\)`.
search-unclosed-group = βρέθηκε μια εναρκτήρια αγκύλη `(`, αλλά δεν υπήρχε καμία κλειστή αγκύλη `)` που να την ακολουθεί. Αν θέλετε να αναζητήσετε το `(`, συμπεριλάβετε το σε διπλά εισαγωγικά ή βάλτε μια ανάστροφη κάθετο: `"("` ή `\(` .
search-empty-quote = βρέθηκε ένα ζεύγος διπλών εισαγωγικών `""`, αλλά δεν υπήρχε τίποτα μεταξύ τους για αναζήτηση. Αν θέλετε να αναζητήσετε διπλά εισαγωγικά, προσθέστε ανάστροφη κάθετο: `\"\"`.
search-unclosed-quote = βρέθηκε ένα διπλό εισαγωγικό `"`, αλλά δεν υπήρχε δεύτερο για να το κλείσει. Αν θέλετε να αναζητήσετε το `"`, προσθέστε μια ανάστροφη κάθετο: `\"`.
search-missing-key = βρέθηκε μια άνω και κάτω τελεία `:`, αλλά δεν υπήρχε καμία λέξη-κλειδί που να προηγείται. Αν θέλετε να αναζητήσετε το `:`, προσθέστε μια ανάστροφη κάθετο: `\:`.
search-invalid-argument = `{ $term }` έχει δωθεί μη έγκυρο επιχείρημα '`{ $argument }`'.
search-invalid-flag-2 = 'σημαία:' πρέπει να ακολουθείται από έναν έγκυρο αριθμό σημαίας: '1' (κόκκινο), '2' (πορτοκαλί), '3' (πράσινο), '4' (μπλε), '5' (ροζ), '6' (τυρκουάζ), '7' (μωβ) ή '0' (καμία σημαία).
search-invalid-prop-operator = Το `prop:{ $val }` πρέπει να ακολουθείται από έναν από τους παρακάτω συγκριτικούς τελεστές: `=`, `!=`, `<`, `>`, `<=` or `>=`.
search-invalid-other = παρακαλώ ελέγξτε για τυπογραφικά λάθη.

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = Αναμένονταν αριθμός στο "`{ $context }`", αλλά βρέθηκε "`{ $provided }`".
search-invalid-whole-number = αναμένονταν ένας ολόκληρος αριθμός στο "`{ $context }`", αλλά βρέθηκε "`{ $provided }`".
search-invalid-positive-whole-number = αναμένονταν ένας ολόκληρος θετικός αριθμός στο "`{ $context }`", αλλά βρέθηκε "`{ $provided }`".
search-invalid-negative-whole-number = αναμένονταν ένας ολόκληρος αριθμός, μικρότερος ή ίσος με το 0, στο "`{ $context }`", αλλά βρέθηκε "`{ $provided }`".
search-invalid-answer-button = Αναμένονταν ένα κουμπί απάντησης μεταξύ 1-4 στο "`{ $context }`", αλλά βρέθηκε "`{ $provided }`".

## Column labels in browse screen

search-note-modified = Τροποποιήθηκε (σημείωση)
search-card-modified = Τροποποιήθηκε (κάρτα)

##

# Tooltip for search lines outside browser
search-view-in-browser = Προβολή στον περιηγητή
