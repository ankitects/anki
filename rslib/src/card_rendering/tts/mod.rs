// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::card_rendering::all_tts_voices_response::TtsVoice;

use crate::prelude::*;

#[cfg(windows)]
#[path = "windows.rs"]
mod inner;
#[cfg(not(windows))]
#[path = "other.rs"]
mod inner;

pub fn all_voices(validate: bool) -> Result<Vec<TtsVoice>> {
    inner::all_voices(validate)
}

pub fn write_stream(path: &str, voice_id: &str, speed: f32, text: &str) -> Result<()> {
    inner::write_stream(path, voice_id, speed, text)
}
