### These messages are shown on the review screen, preview screen, and
### card template screen when the user has made a mistake in their card
### template, or the front of the card is empty.

# Label of link users can click on
card-template-rendering-more-info = Više informacija
card-template-rendering-front-side-problem = Prednji predložak ima problem:
card-template-rendering-back-side-problem = Stražnji predložak ima problem:
card-template-rendering-browser-front-side-problem = Predložak prednje strane specifičan za preglednik ima problem:
card-template-rendering-browser-back-side-problem = Predložak stražnje strane specifičan za preglednik ima problem:
# when the user forgot to close a field reference,
# eg, Missing '}}' in '{{Field'
card-template-rendering-no-closing-brackets = Nedostaje '{ $missing }' u '{ $tag }'
# when the user opened a conditional, but forgot to close it
# eg, Missing '{{/Conditional}}'
card-template-rendering-conditional-not-closed = Nedostaje '{ $missing }'
# when the user closed the wrong conditional
# eg, Found '{{/Something}}', but expected '{{/SomethingElse}}'
card-template-rendering-wrong-conditional-closed = Pronađeno '{ $found }', ali očekivano '{ $expected }'
# when the user closed a conditional that wasn't open
# eg, Found '{{/Something}}', but missing '{{#Something}}' or '{{^Something}}'
card-template-rendering-conditional-not-open = Pronađeno je '{ $found }', ali nedostaje '{ $missing1 }' ili '{ $missing2 }'
# when the user referenced a field that doesn't exist
# eg, Found '{{Field}}', but there is not field called 'Field'
card-template-rendering-no-such-field = Pronađeno je '{ $found }', ali ne postoji polje pod nazivom '{ $field }'
# This message is shown when the front side of the card is blank,
# either due to a badly-designed template, or because required fields
# are missing.
card-template-rendering-empty-front = Prednja strana ove kartice je prazna.
card-template-rendering-missing-cloze =
    Na kartici nije nađeno nadopunjavanje s brojem { $number }.
    Ili dodajte nadopunjavanje, ili pokrenite alat "Prazne kartice".
