// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::io::Write;

use futures::executor::block_on;
use windows::core::HSTRING;
use windows::Media::SpeechSynthesis::SpeechSynthesisStream;
use windows::Media::SpeechSynthesis::SpeechSynthesizer;
use windows::Media::SpeechSynthesis::VoiceInformation;
use windows::Storage::Streams::DataReader;

use crate::pb::card_rendering::all_tts_voices_response::TtsVoice;
use crate::prelude::*;

pub(super) fn all_voices() -> Result<Vec<TtsVoice>> {
    SpeechSynthesizer::AllVoices()?
        .into_iter()
        .map(TryFrom::try_from)
        .collect()
}

pub(super) fn write_stream(path: &str, voice_id: &str, speed: f32, text: &str) -> Result<()> {
    let stream = synthesize_stream(voice_id, speed, text)?;
    write_stream_to_path(stream, path)?;
    Ok(())
}

fn find_voice(voice_id: &str) -> Result<VoiceInformation, AnkiError> {
    SpeechSynthesizer::AllVoices()?
        .into_iter()
        .find(|info| {
            info.Id()
                .map(|id| id.to_string_lossy().eq(voice_id))
                .unwrap_or_default()
        })
        .or_invalid("voice id not found")
}

fn ssml(speed: f32, text: &str) -> Result<HSTRING> {
    let ssml: Vec<u16> = format!(
        concat!(
            r#"<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="en-US">"#,
            r#"    <prosody rate="{}">{}</prosody>"#,
            r#"</speak>"#,
        ),
        speed, text
    )
    .encode_utf16()
    .collect();
    HSTRING::from_wide(&ssml).map_err(Into::into)
}

fn synthesize_stream(voice_id: &str, speed: f32, text: &str) -> Result<SpeechSynthesisStream> {
    let synthesizer = SpeechSynthesizer::new()?;
    let voice = find_voice(voice_id)?;
    synthesizer.SetVoice(&voice)?;
    let async_op = synthesizer.SynthesizeSsmlToStreamAsync(&ssml(speed, text)?)?;
    let stream = block_on(async_op)?;
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
