// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::time::Duration;

use anki_proto::ankiweb::CheckForUpdateRequest;
use anki_proto::ankiweb::CheckForUpdateResponse;
use anki_proto::ankiweb::GetAddonInfoRequest;
use anki_proto::ankiweb::GetAddonInfoResponse;
use prost::Message;

use super::Backend;
use crate::prelude::*;
use crate::services::BackendAnkiwebService;

fn service_url(service: &str) -> String {
    format!("https://ankiweb.net/svc/{service}")
}

impl Backend {
    fn post<I, O>(&self, service: &str, input: I) -> Result<O>
    where
        I: Message,
        O: Message + Default,
    {
        self.runtime_handle().block_on(async move {
            let out = self
                .web_client()
                .post(service_url(service))
                .body(input.encode_to_vec())
                .timeout(Duration::from_secs(60))
                .send()
                .await?
                .error_for_status()?
                .bytes()
                .await?;
            let out: O = O::decode(&out[..])?;
            Ok(out)
        })
    }
}

impl BackendAnkiwebService for Backend {
    fn get_addon_info(&self, input: GetAddonInfoRequest) -> Result<GetAddonInfoResponse> {
        self.post("desktop/addon-info", input)
    }

    fn check_for_update(&self, input: CheckForUpdateRequest) -> Result<CheckForUpdateResponse> {
        self.post("desktop/check-for-update", input)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn addon_info() -> Result<()> {
        if std::env::var("ONLINE_TESTS").is_err() {
            println!("test disabled; ONLINE_TESTS not set");
            return Ok(());
        }
        let backend = Backend::new(I18n::template_only(), false);
        let info = backend.get_addon_info(GetAddonInfoRequest {
            client_version: 30,
            addon_ids: vec![3918629684],
        })?;
        assert_eq!(info.info[0].min_version, 0);
        assert_eq!(info.info[0].max_version, 49);
        Ok(())
    }
}
