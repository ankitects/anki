// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::card_rendering::all_tts_voices_response::TtsVoice;

use crate::prelude::*;

pub(super) fn all_voices(_validate: bool) -> Result<Vec<TtsVoice>> {
    invalid_input!("not implemented for this OS");
}

pub(super) fn write_stream(_path: &str, _voice_id: &str, _speed: f32, _text: &str) -> Result<()> {
    invalid_input!("not implemented for this OS");
}
