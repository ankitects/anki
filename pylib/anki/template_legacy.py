# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
This file contains code that is no longer used by Anki. It is left around
for now as an example of how the stock field filters can be implemented using
the legacy addHook() API.
"""

# from __future__ import annotations
#
# import re
# from typing import Any, Callable
#
# from anki.lang import _
# from anki.template import (
#     CLOZE_REGEX_MATCH_GROUP_CONTENT,
#     CLOZE_REGEX_MATCH_GROUP_HINT,
#     CLOZE_REGEX_MATCH_GROUP_TAG,
#     clozeReg,
# )
# from anki.utils import stripHTML
#
# # Cloze filter
# ##########################################################################
#
#
# def _clozeText(txt: str, ord: str, type: str) -> str:
#     """Process the given Cloze deletion within the given template."""
#     reg = clozeReg
#     currentRegex = clozeReg % ord
#     if not re.search(currentRegex, txt):
#         # No Cloze deletion was found in txt.
#         return ""
#     txt = _removeFormattingFromMathjax(txt, ord)
#
#     def repl(m):
#         # replace chosen cloze with type
#         if type == "q":
#             if m.group(CLOZE_REGEX_MATCH_GROUP_HINT):
#                 buf = "[%s]" % m.group(CLOZE_REGEX_MATCH_GROUP_HINT)
#             else:
#                 buf = "[...]"
#         else:
#             buf = m.group(CLOZE_REGEX_MATCH_GROUP_CONTENT)
#         # uppercase = no formatting
#         if m.group(CLOZE_REGEX_MATCH_GROUP_TAG) == "c":
#             buf = "<span class=cloze>%s</span>" % buf
#         return buf
#
#     txt = re.sub(currentRegex, repl, txt)
#     # and display other clozes normally
#     return re.sub(reg % r"\d+", "\\2", txt)
#
#
# def _removeFormattingFromMathjax(txt, ord) -> str:
#     """Marks all clozes within MathJax to prevent formatting them.
#
#     Active Cloze deletions within MathJax should not be wrapped inside
#     a Cloze <span>, as that would interfere with MathJax.
#
#     This method finds all Cloze deletions number `ord` in `txt` which are
#     inside MathJax inline or display formulas, and replaces their opening
#     '{{c123' with a '{{C123'. The clozeText method interprets the upper-case
#     C as "don't wrap this Cloze in a <span>".
#     """
#     creg = clozeReg.replace("(?si)", "")
#
#     # Scan the string left to right.
#     # After a MathJax opening - \( or \[ - flip in_mathjax to True.
#     # After a MathJax closing - \) or \] - flip in_mathjax to False.
#     # When a Cloze pattern number `ord` is found and we are in MathJax,
#     # replace its '{{c' with '{{C'.
#     #
#     # TODO: Report mismatching opens/closes - e.g. '\(\]'
#     # TODO: Report errors in this method better than printing to stdout.
#     # flags in middle of expression deprecated
#     in_mathjax = False
#
#     def replace(match):
#         nonlocal in_mathjax
#         if match.group("mathjax_open"):
#             if in_mathjax:
#                 print("MathJax opening found while already in MathJax")
#             in_mathjax = True
#         elif match.group("mathjax_close"):
#             if not in_mathjax:
#                 print("MathJax close found while not in MathJax")
#             in_mathjax = False
#         elif match.group("cloze"):
#             if in_mathjax:
#                 return match.group(0).replace(
#                     "{{c{}::".format(ord), "{{C{}::".format(ord)
#                 )
#         else:
#             print("Unexpected: no expected capture group is present")
#         return match.group(0)
#
#     # The following regex matches one of:
#     #  -  MathJax opening
#     #  -  MathJax close
#     #  -  Cloze deletion number `ord`
#     return re.sub(
#         r"(?si)"
#         r"(?P<mathjax_open>\\[([])|"
#         r"(?P<mathjax_close>\\[\])])|"
#         r"(?P<cloze>" + (creg % ord) + ")",
#         replace,
#         txt,
#     )
#
# def test_remove_formatting_from_mathjax():
#     assert _removeFormattingFromMathjax(r"\(2^{{c3::2}}\)", 3) == r"\(2^{{C3::2}}\)"
#
#     txt = (
#         r"{{c1::ok}} \(2^2\) {{c2::not ok}} \(2^{{c3::2}}\) \(x^3\) "
#         r"{{c4::blah}} {{c5::text with \(x^2\) jax}}"
#     )
#     # Cloze 2 is not in MathJax, so it should not get protected against
#     # formatting.
#     assert _removeFormattingFromMathjax(txt, 2) == txt
#
#     txt = r"\(a\) {{c1::b}} \[ {{c1::c}} \]"
#     assert _removeFormattingFromMathjax(txt, 1) == (r"\(a\) {{c1::b}} \[ {{C1::c}} \]")
#
#
#
# def _cloze_filter(field_text: str, filter_args: str, q_or_a: str):
#     return _clozeText(field_text, filter_args, q_or_a)
#
#
# def cloze_qfilter(field_text: str, filter_args: str, *args):
#     return _cloze_filter(field_text, filter_args, "q")
#
#
# def cloze_afilter(field_text: str, filter_args: str, *args):
#     return _cloze_filter(field_text, filter_args, "a")
#
#
# addHook("fmod_cq", cloze_qfilter)
# addHook("fmod_ca", cloze_afilter)
#
# # Other filters
# ##########################################################################
#
#
# def hint_filter(txt: str, args, context, tag: str, fullname) -> str:
#     if not txt.strip():
#         return ""
#     # random id
#     domid = "hint%d" % id(txt)
#     return """
# <a class=hint href="#"
# onclick="this.style.display='none';document.getElementById('%s').style.display='block';return false;">
# %s</a><div id="%s" class=hint style="display: none">%s</div>
# """ % (
#         domid,
#         _("Show %s") % tag,
#         domid,
#         txt,
#     )
#
#
# FURIGANA_RE = r" ?([^ >]+?)\[(.+?)\]"
# RUBY_REPL = r"<ruby><rb>\1</rb><rt>\2</rt></ruby>"
#
#
# def replace_if_not_audio(repl: str) -> Callable[[Any], Any]:
#     def func(match):
#         if match.group(2).startswith("sound:"):
#             # return without modification
#             return match.group(0)
#         else:
#             return re.sub(FURIGANA_RE, repl, match.group(0))
#
#     return func
#
#
# def without_nbsp(s: str) -> str:
#     return s.replace("&nbsp;", " ")
#
#
# def kanji_filter(txt: str, *args) -> str:
#     return re.sub(FURIGANA_RE, replace_if_not_audio(r"\1"), without_nbsp(txt))
#
#
# def kana_filter(txt: str, *args) -> str:
#     return re.sub(FURIGANA_RE, replace_if_not_audio(r"\2"), without_nbsp(txt))
#
#
# def furigana_filter(txt: str, *args) -> str:
#     return re.sub(FURIGANA_RE, replace_if_not_audio(RUBY_REPL), without_nbsp(txt))
#
#
# def text_filter(txt: str, *args) -> str:
#     return stripHTML(txt)
#
#
# def type_answer_filter(txt: str, filter_args: str, context, tag: str, dummy) -> str:
#     # convert it to [[type:...]] for the gui code to process
#     if filter_args:
#         return f"[[type:{filter_args}:{tag}]]"
#     else:
#         return f"[[type:{tag}]]"
#
#
# addHook("fmod_text", text_filter)
# addHook("fmod_type", type_answer_filter)
# addHook("fmod_hint", hint_filter)
# addHook("fmod_kanji", kanji_filter)
# addHook("fmod_kana", kana_filter)
# addHook("fmod_furigana", furigana_filter)
