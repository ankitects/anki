// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::fs::File;
use std::io::Write;

use anki_proto::card_rendering::all_tts_voices_response::TtsVoice;
use futures::executor::block_on;
use windows::core::HSTRING;
use windows::Media::SpeechSynthesis::SpeechSynthesisStream;
use windows::Media::SpeechSynthesis::SpeechSynthesizer;
use windows::Media::SpeechSynthesis::VoiceInformation;
use windows::Storage::Streams::DataReader;

use crate::error::windows::WindowsErrorDetails;
use crate::error::windows::WindowsSnafu;
use crate::prelude::*;

const MAX_BUFFER_SIZE: usize = 128 * 1024;

pub(super) fn all_voices(validate: bool) -> Result<Vec<TtsVoice>> {
    SpeechSynthesizer::AllVoices()?
        .into_iter()
        .map(|info| tts_voice_from_information(info, validate))
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
    let stream = block_on(async_op).context(WindowsSnafu {
        details: WindowsErrorDetails::Synthesizing,
    })?;
    Ok(stream)
}

fn write_stream_to_path(stream: SpeechSynthesisStream, path: &str) -> Result<()> {
    let input_stream = stream.GetInputStreamAt(0)?;
    let date_reader = DataReader::CreateDataReader(&input_stream)?;
    let stream_size = stream.Size()?.try_into().or_invalid("stream too large")?;
    date_reader.LoadAsync(stream_size)?;
    let mut file = File::create(path)?;
    write_reader_to_file(date_reader, &mut file, stream_size as usize)
}

fn write_reader_to_file(reader: DataReader, file: &mut File, stream_size: usize) -> Result<()> {
    let mut bytes_remaining = stream_size;
    let mut buf = [0u8; MAX_BUFFER_SIZE];
    while bytes_remaining > 0 {
        let chunk_size = bytes_remaining.min(MAX_BUFFER_SIZE);
        reader.ReadBytes(&mut buf[..chunk_size])?;
        file.write_all(&buf[..chunk_size])?;
        bytes_remaining -= chunk_size;
    }
    Ok(())
}

fn tts_voice_from_information(info: VoiceInformation, validate: bool) -> Result<TtsVoice> {
    Ok(TtsVoice {
        id: info.Id()?.to_string_lossy(),
        name: info.DisplayName()?.to_string_lossy(),
        language: info.Language()?.to_string_lossy(),
        // Windows lists voices that fail when actually trying to use them. This has been
        // observed with voices from an uninstalled language pack.
        // Validation is optional because it may be slow.
        available: validate.then(|| synthesize_stream(&info, 1.0, "").is_ok()),
    })
}
