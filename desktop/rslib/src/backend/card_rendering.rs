// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use anki_proto::card_rendering::StripHtmlRequest;

use crate::backend::Backend;
use crate::card_rendering::service::strip_html_proto;
use crate::card_rendering::tts;
use crate::prelude::*;
use crate::services::BackendCardRenderingService;

impl BackendCardRenderingService for Backend {
    fn strip_html(
        &self,
        input: StripHtmlRequest,
    ) -> crate::error::Result<anki_proto::generic::String> {
        strip_html_proto(input)
    }

    fn all_tts_voices(
        &self,
        input: anki_proto::card_rendering::AllTtsVoicesRequest,
    ) -> Result<anki_proto::card_rendering::AllTtsVoicesResponse> {
        tts::all_voices(input.validate)
            .map(|voices| anki_proto::card_rendering::AllTtsVoicesResponse { voices })
    }

    fn write_tts_stream(
        &self,
        request: anki_proto::card_rendering::WriteTtsStreamRequest,
    ) -> Result<()> {
        tts::write_stream(
            &request.path,
            &request.voice_id,
            request.speed,
            &request.text,
        )
    }
}
