import anki.lang
from anki.lang import TR


def test_no_collection_i18n():
    anki.lang.set_lang("zz", "")
    tr2 = anki.lang.current_i18n.translate
    no_uni = anki.lang.without_unicode_isolation
    assert no_uni(tr2(TR.STATISTICS_REVIEWS, reviews=2)) == "2 reviews"

    anki.lang.set_lang("ja", "")
    tr2 = anki.lang.current_i18n.translate
    assert no_uni(tr2(TR.STATISTICS_REVIEWS, reviews=2)) == "2 枚の復習カード"
