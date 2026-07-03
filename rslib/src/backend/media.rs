// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::media::AddMediaFromUrlRequest;
use anki_proto::media::AddMediaFromUrlResponse;

use crate::backend::Backend;
use crate::editor::retrieve_url;
use crate::error;

impl crate::services::BackendMediaService for Backend {
    fn add_media_from_url(
        &self,
        input: AddMediaFromUrlRequest,
    ) -> error::Result<AddMediaFromUrlResponse> {
        let rt = self.runtime_handle();
        let mut guard = self.col.lock().unwrap();
        let col = guard.as_mut().unwrap();
        let media = col.media()?;
        let fut = async move {
            let response = match retrieve_url(&input.url).await {
                Ok((filename, data)) => {
                    media
                        .add_file(&filename, &data)
                        .map(|fname| fname.to_string())?;
                    AddMediaFromUrlResponse {
                        filename: Some(filename),
                        error: None,
                    }
                }
                Err(e) => AddMediaFromUrlResponse {
                    filename: None,
                    error: Some(e.message(col.tr())),
                },
            };
            Ok(response)
        };
        rt.block_on(fut)
    }
}
