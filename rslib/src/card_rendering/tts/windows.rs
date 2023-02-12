// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::io::Write;

use futures::executor::block_on;
use windows::core::HSTRING;
use windows::Media::SpeechSynthesis::SpeechSynthesisStream;
use windows::Media::SpeechSynthesis::SpeechSynthesizer;
use windows::Media::SpeechSynthesis::VoiceInformation;
use windows::Storage::Streams::DataReader;

use crate::error::windows::WindowsErrorDetails;
use crate::error::windows::WindowsSnafu;
use crate::pb::card_rendering::all_tts_voices_response::TtsVoice;
use crate::prelude::*;

pub(super) fn all_voices() -> Result<Vec<TtsVoice>> {
    SpeechSynthesizer::AllVoices()?
        .into_iter()
        // Windows lists voices that fail when actually trying to use them. This has been observed
        // with voices from an uninstalled language pack.
        .filter(|voice| synthesize_stream(voice, 1.0, "").is_ok())
        .map(TryFrom::try_from)
        .collect()
}

pub(super) fn write_stream(path: &str, voice_id: &str, speed: f32, text: &str) -> Result<()> {
    let voice = find_voice(voice_id)?;
    let stream = synthesize_stream(&voice, speed, text)?;
    write_stream_to_path(stream, path)?;
    Ok(())
}

fn find_voice(voice_id: &str) -> Result<VoiceInformation> {
    SpeechSynthesizer::AllVoices()?
        .into_iter()
        .find(|info| {
            info.Id()
                .map(|id| id.to_string_lossy().eq(voice_id))
                .unwrap_or_default()
        })
        .or_invalid("voice id not found")
}

fn to_hstring(text: &str) -> HSTRING {
    let utf16: Vec<u16> = text.encode_utf16().collect();
    HSTRING::from_wide(&utf16).expect("Strings are valid Unicode")
}

fn synthesize_stream(
    voice: &VoiceInformation,
    speed: f32,
    text: &str,
) -> Result<SpeechSynthesisStream> {
    let synthesizer = SpeechSynthesizer::new()?;
    synthesizer.SetVoice(voice).with_context(|_| WindowsSnafu {
        details: WindowsErrorDetails::SettingVoice(voice.clone()),
    })?;
    synthesizer
        .Options()?
        .SetSpeakingRate(speed as f64)
        .context(WindowsSnafu {
            details: WindowsErrorDetails::SettingRate(speed),
        })?;
    let async_op = synthesizer.SynthesizeTextToStreamAsync(&to_hstring(text))?;
    let stream = block_on(async_op).with_context(|_| WindowsSnafu {
        details: WindowsErrorDetails::Synthesizing(text.to_string()),
    })?;
    Ok(stream)
}

fn write_stream_to_path(stream: SpeechSynthesisStream, path: &str) -> Result<()> {
    let input_stream = stream.GetInputStreamAt(0)?;
    let date_reader = DataReader::CreateDataReader(&input_stream)?;
    let stream_size = stream.Size()?.try_into().or_invalid("stream too large")?;
    date_reader.LoadAsync(stream_size)?;
    let mut file = std::fs::File::create(path)?;
    let mut buf = vec![0u8; stream_size as usize];
    date_reader.ReadBytes(&mut buf)?;
    file.write_all(&buf)?;
    Ok(())
}

impl TryFrom<VoiceInformation> for TtsVoice {
    type Error = AnkiError;

    fn try_from(info: VoiceInformation) -> Result<Self> {
        Ok(Self {
            id: info.Id()?.to_string_lossy(),
            name: info.DisplayName()?.to_string_lossy(),
            language: info.Language()?.to_string_lossy(),
        })
    }
}
