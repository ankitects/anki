// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use snafu::Snafu;

use super::AnkiError;

#[derive(Debug, PartialEq, Snafu)]
#[snafu(visibility(pub))]
pub struct WindowsError {
    details: WindowsErrorDetails,
    source: windows::core::Error,
}

#[derive(Debug, PartialEq)]
pub enum WindowsErrorDetails {
    SettingVoice(windows::Media::SpeechSynthesis::VoiceInformation),
    SettingRate(f32),
    Synthesizing,
    Other,
}

impl From<windows::core::Error> for AnkiError {
    fn from(source: windows::core::Error) -> Self {
        AnkiError::WindowsError {
            source: WindowsError {
                source,
                details: WindowsErrorDetails::Other,
            },
        }
    }
}
