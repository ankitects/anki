### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Περισσότερες πληροφορίες
card-template-rendering-front-side-problem = Το πρότυπο της πρόσθιας πλευράς έχει πρόβλημα:
card-template-rendering-back-side-problem = Το οπίσθιο πρότυπο έχει πρόβλημα:
card-template-rendering-browser-front-side-problem = Το σχετιζόμενο με τον φυλλομετρητή (browser) πρότυπο της πρόσθιας πλευράς έχει πρόβλημα:
card-template-rendering-browser-back-side-problem = Το σχετιζόμενο με τον φυλλομετρητή (browser) πρότυπο της οπίσθιας πλευράς έχει πρόβλημα:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = Λείπει '{ $missing }' σε '{ $tag }'
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = Λείπει '{ $missing }'
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = Βρέθηκε '{ $found }', αλλά αναμένονταν '{ $expected }'
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = Βρέθηκε '{ $found }', αλλά λείπει '{ $missing1 }' ή '{ $missing2 }'
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = Βρέθηκε '{ $found }', αλλά δεν υπάρχει πεδίο με το όνομα '{ $field }'
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = Η πρόσθια πλευρά της κάρτας είναι κενή.
card-template-rendering-missing-cloze =
    Δεν βρέθηκε κενό { $number } στην κάρτα.
    Παρακαλώ εισάγετε κενά προς συμπλήρωση ή χρησιμοποιήστε το εργαλείο Εκκαθαριση καρτών.
