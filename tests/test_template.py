from anki.template import Template


def test_remove_formatting_from_mathjax():
    t = Template('')
    assert t._removeFormattingFromMathjax(r'\(2^{{c3::2}}\)', 3) == r'\(2^{{C3::2}}\)'

    txt = (r'{{c1::ok}} \(2^2\) {{c2::not ok}} \(2^{{c3::2}}\) \(x^3\) '
           r'{{c4::blah}} {{c5::text with \(x^2\) jax}}')
    # Cloze 2 is not in MathJax, so it should not get protected against
    # formatting.
    assert t._removeFormattingFromMathjax(txt, 2) == txt

    # TODO: r'\(a\) {{c1::b}} \[ {{c1::c}} \]', ord=1 should return
    # r'\(a\) {{c1::b}} \[ {{C1::c}} \]', but actually fails to mark the cloze
    # as not-to-be-formatted.
