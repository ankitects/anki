# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Basic text to speech support.

Users can use the following in their card template:

{{tts en_US:Field}}

or

{{tts ja_JP voices=Kyoko,Otoya,Another_name:Field}}

The first argument must be an underscored language code, eg en_US.

If provided, voices is a comma-separated list of one or more voices that
the user would prefer. Spaces must not be included. Underscores will be
converted to spaces.

AVPlayer decides which TTSPlayer to use based on the returned rank.
In the default implementation, the TTS player is chosen based on the order
of voices the user has specified. When adding new TTS players, your code
can either expose the underlying names the TTS engine provides, or simply
expose the name of the engine, which would mean the user could write
{{tts en_AU voices=MyEngine}} to prioritize your engine.
"""

from __future__ import annotations

import os
import re
import subprocess
from concurrent.futures import Future
from dataclasses import dataclass
from operator import attrgetter
from typing import Any, cast

import anki
import anki.template
import aqt
from anki import hooks
from anki.collection import TtsVoice as BackendVoice
from anki.sound import AVTag, TTSTag
from anki.utils import checksum, is_win, tmpdir
from aqt import gui_hooks
from aqt.sound import OnDoneCallback, SimpleProcessPlayer
from aqt.utils import tooltip, tr


@dataclass
class TTSVoice:
    name: str
    lang: str

    def __str__(self) -> str:
        out = f"{{{{tts {self.lang} voices={self.name}}}}}"
        if self.unavailable():
            out += " (unavailable)"
        return out

    def unavailable(self) -> bool:
        return False


@dataclass
class TTSVoiceMatch:
    voice: TTSVoice
    rank: int


class TTSPlayer:
    default_rank = 0
    _available_voices: list[TTSVoice] | None = None

    def get_available_voices(self) -> list[TTSVoice]:
        return []

    def voices(self) -> list[TTSVoice]:
        if self._available_voices is None:
            self._available_voices = self.get_available_voices()
        return self._available_voices

    def voice_for_tag(self, tag: TTSTag) -> TTSVoiceMatch | None:
        avail_voices = self.voices()

        rank = self.default_rank

        # any requested voices match?
        for requested_voice in tag.voices:
            for avail in avail_voices:
                if avail.name == requested_voice and avail.lang == tag.lang:
                    return TTSVoiceMatch(voice=avail, rank=rank)

            rank -= 1

        # if no preferred voices match, we fall back on language
        # with a rank of -100
        for avail in avail_voices:
            if avail.lang == tag.lang:
                return TTSVoiceMatch(voice=avail, rank=-100)

        return None

    def temp_file_for_tag_and_voice(self, tag: AVTag, voice: TTSVoice) -> str:
        """Return a hashed filename, to allow for caching generated files.

        No file extension is included."""
        assert isinstance(tag, TTSTag)
        buf = f"{voice.name}-{voice.lang}-{tag.field_text}"
        return os.path.join(tmpdir(), f"tts-{checksum(buf)}")


class TTSProcessPlayer(SimpleProcessPlayer, TTSPlayer):
    # mypy gets confused if rank_for_tag is defined in TTSPlayer
    def rank_for_tag(self, tag: AVTag) -> int | None:
        if not isinstance(tag, TTSTag):
            return None

        match = self.voice_for_tag(tag)
        if match:
            return match.rank
        else:
            return None


# tts-voices filter
##########################################################################


def all_tts_voices() -> list[TTSVoice]:
    from aqt.sound import av_player

    all_voices: list[TTSVoice] = []
    for p in av_player.players:
        getter = getattr(p, "validated_voices", getattr(p, "voices", None))
        if getter:
            all_voices.extend(getter())
    return all_voices


def on_tts_voices(
    text: str, field: str, filter: str, ctx: anki.template.TemplateRenderContext
) -> str:
    if filter != "tts-voices":
        return text
    voices = all_tts_voices()
    voices.sort(key=attrgetter("lang", "name"))

    buf = "<div style='font-size: 14px; text-align: left;'>TTS voices available:<br>"
    buf += "<br>".join(map(str, voices))
    if any(v.unavailable() for v in voices):
        buf += "<div>One or more voices are unavailable."
        buf += " Installing a Windows language pack may help.</div>"
    return f"{buf}</div>"


hooks.field_filter.append(on_tts_voices)

# Mac support
##########################################################################


@dataclass
class MacVoice(TTSVoice):
    original_name: str


# pylint: disable=no-member
class MacTTSPlayer(TTSProcessPlayer):
    "Invokes a process to play the audio in the background."

    VOICE_HELP_LINE_RE = re.compile(r"^(.+)\s+(\S+)\s+#.*$")

    def _play(self, tag: AVTag) -> None:
        assert isinstance(tag, TTSTag)
        match = self.voice_for_tag(tag)
        assert match
        voice = match.voice
        assert isinstance(voice, MacVoice)

        default_wpm = 170
        words_per_min = str(int(default_wpm * tag.speed))

        self._process = subprocess.Popen(
            ["say", "-v", voice.original_name, "-r", words_per_min, "-f", "-"],
            stdin=subprocess.PIPE,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        # write the input text to stdin
        self._process.stdin.write(tag.field_text.encode("utf8"))
        self._process.stdin.close()
        self._wait_for_termination(tag)

    def get_available_voices(self) -> list[TTSVoice]:
        cmd = subprocess.run(
            ["say", "-v", "?"], capture_output=True, check=True, encoding="utf8"
        )

        voices = []
        for line in cmd.stdout.splitlines():
            voice = self._parse_voice_line(line)
            if voice:
                voices.append(voice)
        return voices

    def _parse_voice_line(self, line: str) -> TTSVoice | None:
        m = self.VOICE_HELP_LINE_RE.match(line)
        if not m:
            return None

        original_name = m.group(1).strip()
        tidy_name = f"Apple_{original_name.replace(' ', '_')}"
        return MacVoice(name=tidy_name, original_name=original_name, lang=m.group(2))


class MacTTSFilePlayer(MacTTSPlayer):
    "Generates an .aiff file, which is played using av_player."

    tmppath = os.path.join(tmpdir(), "tts.aiff")

    def _play(self, tag: AVTag) -> None:
        assert isinstance(tag, TTSTag)
        match = self.voice_for_tag(tag)
        assert match
        voice = match.voice
        assert isinstance(voice, MacVoice)

        default_wpm = 170
        words_per_min = str(int(default_wpm * tag.speed))

        self._process = subprocess.Popen(
            [
                "say",
                "-v",
                voice.original_name,
                "-r",
                words_per_min,
                "-f",
                "-",
                "-o",
                self.tmppath,
            ],
            stdin=subprocess.PIPE,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        # write the input text to stdin
        self._process.stdin.write(tag.field_text.encode("utf8"))
        self._process.stdin.close()
        self._wait_for_termination(tag)

    def _on_done(self, ret: Future, cb: OnDoneCallback) -> None:
        ret.result()

        # inject file into the top of the audio queue
        from aqt.sound import av_player

        av_player.current_player = None
        av_player.insert_file(self.tmppath)


# Windows support
##########################################################################


@dataclass
class WindowsVoice(TTSVoice):
    handle: Any


if is_win:
    # language ID map from https://github.com/sindresorhus/lcid/blob/master/lcid.json
    LCIDS = {
        "4": "zh_CHS",
        "1025": "ar_SA",
        "1026": "bg_BG",
        "1027": "ca_ES",
        "1028": "zh_TW",
        "1029": "cs_CZ",
        "1030": "da_DK",
        "1031": "de_DE",
        "1032": "el_GR",
        "1033": "en_US",
        "1034": "es_ES",
        "1035": "fi_FI",
        "1036": "fr_FR",
        "1037": "he_IL",
        "1038": "hu_HU",
        "1039": "is_IS",
        "1040": "it_IT",
        "1041": "ja_JP",
        "1042": "ko_KR",
        "1043": "nl_NL",
        "1044": "nb_NO",
        "1045": "pl_PL",
        "1046": "pt_BR",
        "1047": "rm_CH",
        "1048": "ro_RO",
        "1049": "ru_RU",
        "1050": "hr_HR",
        "1051": "sk_SK",
        "1052": "sq_AL",
        "1053": "sv_SE",
        "1054": "th_TH",
        "1055": "tr_TR",
        "1056": "ur_PK",
        "1057": "id_ID",
        "1058": "uk_UA",
        "1059": "be_BY",
        "1060": "sl_SI",
        "1061": "et_EE",
        "1062": "lv_LV",
        "1063": "lt_LT",
        "1064": "tg_TJ",
        "1065": "fa_IR",
        "1066": "vi_VN",
        "1067": "hy_AM",
        "1069": "eu_ES",
        "1070": "wen_DE",
        "1071": "mk_MK",
        "1074": "tn_ZA",
        "1076": "xh_ZA",
        "1077": "zu_ZA",
        "1078": "af_ZA",
        "1079": "ka_GE",
        "1080": "fo_FO",
        "1081": "hi_IN",
        "1082": "mt_MT",
        "1083": "se_NO",
        "1086": "ms_MY",
        "1087": "kk_KZ",
        "1088": "ky_KG",
        "1089": "sw_KE",
        "1090": "tk_TM",
        "1092": "tt_RU",
        "1093": "bn_IN",
        "1094": "pa_IN",
        "1095": "gu_IN",
        "1096": "or_IN",
        "1097": "ta_IN",
        "1098": "te_IN",
        "1099": "kn_IN",
        "1100": "ml_IN",
        "1101": "as_IN",
        "1102": "mr_IN",
        "1103": "sa_IN",
        "1104": "mn_MN",
        "1105": "bo_CN",
        "1106": "cy_GB",
        "1107": "kh_KH",
        "1108": "lo_LA",
        "1109": "my_MM",
        "1110": "gl_ES",
        "1111": "kok_IN",
        "1114": "syr_SY",
        "1115": "si_LK",
        "1118": "am_ET",
        "1121": "ne_NP",
        "1122": "fy_NL",
        "1123": "ps_AF",
        "1124": "fil_PH",
        "1125": "div_MV",
        "1128": "ha_NG",
        "1130": "yo_NG",
        "1131": "quz_BO",
        "1132": "ns_ZA",
        "1133": "ba_RU",
        "1134": "lb_LU",
        "1135": "kl_GL",
        "1144": "ii_CN",
        "1146": "arn_CL",
        "1148": "moh_CA",
        "1150": "br_FR",
        "1152": "ug_CN",
        "1153": "mi_NZ",
        "1154": "oc_FR",
        "1155": "co_FR",
        "1156": "gsw_FR",
        "1157": "sah_RU",
        "1158": "qut_GT",
        "1159": "rw_RW",
        "1160": "wo_SN",
        "1164": "gbz_AF",
        "2049": "ar_IQ",
        "2052": "zh_CN",
        "2055": "de_CH",
        "2057": "en_GB",
        "2058": "es_MX",
        "2060": "fr_BE",
        "2064": "it_CH",
        "2067": "nl_BE",
        "2068": "nn_NO",
        "2070": "pt_PT",
        "2077": "sv_FI",
        "2080": "ur_IN",
        "2092": "az_AZ",
        "2094": "dsb_DE",
        "2107": "se_SE",
        "2108": "ga_IE",
        "2110": "ms_BN",
        "2115": "uz_UZ",
        "2128": "mn_CN",
        "2129": "bo_BT",
        "2141": "iu_CA",
        "2143": "tmz_DZ",
        "2155": "quz_EC",
        "3073": "ar_EG",
        "3076": "zh_HK",
        "3079": "de_AT",
        "3081": "en_AU",
        "3082": "es_ES",
        "3084": "fr_CA",
        "3098": "sr_SP",
        "3131": "se_FI",
        "3179": "quz_PE",
        "4097": "ar_LY",
        "4100": "zh_SG",
        "4103": "de_LU",
        "4105": "en_CA",
        "4106": "es_GT",
        "4108": "fr_CH",
        "4122": "hr_BA",
        "4155": "smj_NO",
        "5121": "ar_DZ",
        "5124": "zh_MO",
        "5127": "de_LI",
        "5129": "en_NZ",
        "5130": "es_CR",
        "5132": "fr_LU",
        "5179": "smj_SE",
        "6145": "ar_MA",
        "6153": "en_IE",
        "6154": "es_PA",
        "6156": "fr_MC",
        "6203": "sma_NO",
        "7169": "ar_TN",
        "7177": "en_ZA",
        "7178": "es_DO",
        "7194": "sr_BA",
        "7227": "sma_SE",
        "8193": "ar_OM",
        "8201": "en_JA",
        "8202": "es_VE",
        "8218": "bs_BA",
        "8251": "sms_FI",
        "9217": "ar_YE",
        "9225": "en_CB",
        "9226": "es_CO",
        "9275": "smn_FI",
        "10241": "ar_SY",
        "10249": "en_BZ",
        "10250": "es_PE",
        "11265": "ar_JO",
        "11273": "en_TT",
        "11274": "es_AR",
        "12289": "ar_LB",
        "12297": "en_ZW",
        "12298": "es_EC",
        "13313": "ar_KW",
        "13321": "en_PH",
        "13322": "es_CL",
        "14337": "ar_AE",
        "14346": "es_UR",
        "15361": "ar_BH",
        "15370": "es_PY",
        "16385": "ar_QA",
        "16394": "es_BO",
        "17417": "en_MY",
        "17418": "es_SV",
        "18441": "en_IN",
        "18442": "es_HN",
        "19466": "es_NI",
        "20490": "es_PR",
        "21514": "es_US",
        "31748": "zh_CHT",
    }

    def lcid_hex_str_to_lang_codes(hex_codes: str) -> list[str]:
        return [
            LCIDS.get(str(int(code, 16)), "unknown") for code in hex_codes.split(";")
        ]

    class WindowsTTSPlayer(TTSProcessPlayer):
        default_rank = -1
        try:
            import win32com.client  # pylint: disable=import-error

            speaker = win32com.client.Dispatch("SAPI.SpVoice")
        except Exception as exc:
            print("unable to activate sapi:", exc)
            speaker = None

        def get_available_voices(self) -> list[TTSVoice]:
            if self.speaker is None:
                return []
            return [
                obj
                for voice in self.speaker.GetVoices()
                for obj in self._voice_to_objects(voice)
            ]

        def _voice_to_objects(self, voice: Any) -> list[WindowsVoice]:
            try:
                langs = voice.GetAttribute("language")
            except Exception:
                # no associated language; ignore
                return []
            langs = lcid_hex_str_to_lang_codes(langs)
            try:
                name = voice.GetAttribute("name")
            except Exception:
                # some voices may not have a name
                name = "unknown"
            name = self._tidy_name(name)
            return [WindowsVoice(name=name, lang=lang, handle=voice) for lang in langs]

        def _play(self, tag: AVTag) -> None:
            assert isinstance(tag, TTSTag)
            match = self.voice_for_tag(tag)
            assert match
            voice = cast(WindowsVoice, match.voice)

            try:
                native_voice = voice.handle
                self.speaker.Voice = native_voice
                self.speaker.Rate = self._rate_for_speed(tag.speed)
                self.speaker.Speak(tag.field_text, 1)
                gui_hooks.av_player_did_begin_playing(self, tag)

                # wait 100ms
                while not self.speaker.WaitUntilDone(100):
                    if self._terminate_flag:
                        # stop playing
                        self.speaker.Skip("Sentence", 2**15)
                        return
            finally:
                self._terminate_flag = False

        def _tidy_name(self, name: str) -> str:
            "eg. Microsoft Haruka Desktop -> Microsoft_Haruka."
            return re.sub(r"^Microsoft (.+) Desktop$", "Microsoft_\\1", name).replace(
                " ", "_"
            )

        def _rate_for_speed(self, speed: float) -> int:
            "eg. 1.5 -> 15, 0.5 -> -5"
            speed = (speed * 10) - 10
            return int(max(-10, min(10, speed)))

    @dataclass
    class WindowsRTVoice(TTSVoice):
        id: str
        available: bool | None = None

        def unavailable(self) -> bool:
            return self.available is False

        @classmethod
        def from_backend_voice(cls, voice: BackendVoice) -> WindowsRTVoice:
            return cls(
                id=voice.id,
                name=voice.name.replace(" ", "_"),
                lang=voice.language.replace("-", "_"),
                available=voice.available,
            )

    class WindowsRTTTSFilePlayer(TTSProcessPlayer):
        tmppath = os.path.join(tmpdir(), "tts.wav")

        def validated_voices(self) -> list[TTSVoice]:
            self._available_voices = self._get_available_voices(validate=True)
            return self._available_voices

        @classmethod
        def get_available_voices(cls) -> list[TTSVoice]:
            return cls._get_available_voices(validate=False)

        @staticmethod
        def _get_available_voices(validate: bool) -> list[TTSVoice]:
            assert aqt.mw
            voices = aqt.mw.backend.all_tts_voices(validate=validate)
            return list(map(WindowsRTVoice.from_backend_voice, voices))

        def _play(self, tag: AVTag) -> None:
            assert aqt.mw
            assert isinstance(tag, TTSTag)
            match = self.voice_for_tag(tag)
            assert match
            voice = cast(WindowsRTVoice, match.voice)

            self._taskman.run_on_main(
                lambda: gui_hooks.av_player_did_begin_playing(self, tag)
            )
            aqt.mw.backend.write_tts_stream(
                path=self.tmppath,
                voice_id=voice.id,
                speed=tag.speed,
                text=tag.field_text,
            )

        def _on_done(self, ret: Future, cb: OnDoneCallback) -> None:
            if exception := ret.exception():
                print(str(exception))
                tooltip(tr.errors_windows_tts_runtime_error())
                cb()
                return

            # inject file into the top of the audio queue
            from aqt.sound import av_player

            av_player.current_player = None
            av_player.insert_file(self.tmppath)
