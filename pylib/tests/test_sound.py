# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os

from anki.sound import AV_REF_RE, SoundOrVideoTag, strip_av_refs


def test_sound_tag_path_relative_joins_media_folder():
    tag = SoundOrVideoTag(filename="audio.mp3")
    assert tag.path("/media") == "/media/audio.mp3"


def test_sound_tag_path_absolute_ignores_media_folder():
    abs_path = os.path.abspath("/some/absolute/audio.mp3")
    tag = SoundOrVideoTag(filename=abs_path)
    assert tag.path("/media") == abs_path


def test_strip_av_refs_removes_play_tag():
    assert strip_av_refs("Hello [anki:play:q:0] world") == "Hello  world"


def test_strip_av_refs_leaves_unrelated_text_unchanged():
    assert strip_av_refs("No audio here") == "No audio here"


def test_strip_av_refs_removes_multiple_tags():
    assert strip_av_refs("[anki:play:q:0]front[anki:play:a:1]back") == "frontback"


def test_av_ref_re_matches_question_side():
    m = AV_REF_RE.search("[anki:play:q:0]")
    assert m is not None
    assert m.group(2) == "q"
    assert m.group(3) == "0"


def test_av_ref_re_matches_answer_side():
    m = AV_REF_RE.search("[anki:play:a:2]")
    assert m is not None
    assert m.group(2) == "a"
    assert m.group(3) == "2"


def test_av_ref_re_does_not_match_legacy_sound_tag():
    assert AV_REF_RE.search("[sound:foo.mp3]") is None
