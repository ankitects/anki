import anki.lang


def test_no_collection_i18n():
    anki.lang.set_lang("zz")
    tr = anki.lang.tr_legacyglobal
    no_uni = anki.lang.without_unicode_isolation
    assert no_uni(tr.statistics_reviews(reviews=2)) == "2 reviews"

    anki.lang.set_lang("ja")
    assert no_uni(tr.statistics_reviews(reviews=2)) == "2 枚の復習カード"
