# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import anki.lang


def test_no_collection_i18n():
    anki.lang.set_lang("zz")
    tr = anki.lang.tr_legacyglobal
    no_uni = anki.lang.without_unicode_isolation
    assert no_uni(tr.statistics_reviews(reviews=2)) == "2 reviews"

    anki.lang.set_lang("ja")
    assert no_uni(tr.statistics_reviews(reviews=2)) == "2枚"


def test_legacy_enum():
    anki.lang.set_lang("ja")
    TR = anki.lang.TR
    tr = anki.lang.tr_legacyglobal
    no_uni = anki.lang.without_unicode_isolation

    assert no_uni(tr(TR.STATISTICS_REVIEWS, reviews=2)) == "2枚"
