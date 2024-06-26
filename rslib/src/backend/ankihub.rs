// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Backend;
use crate::ankihub::login::ankihub_login;
use crate::ankihub::login::ankihub_logout;
use crate::ankihub::login::LoginResponse;
use crate::prelude::*;

impl From<LoginResponse> for anki_proto::ankihub::LoginResponse {
    fn from(value: LoginResponse) -> Self {
        anki_proto::ankihub::LoginResponse {
            token: value.token.unwrap_or_default(),
        }
    }
}

impl crate::services::BackendAnkiHubService for Backend {
    fn ankihub_login(
        &self,
        input: anki_proto::ankihub::LoginRequest,
    ) -> Result<anki_proto::ankihub::LoginResponse> {
        let rt = self.runtime_handle();
        let fut = ankihub_login(input.id, input.password, self.web_client());

        rt.block_on(fut).map(|a| a.into())
    }

    fn ankihub_logout(&self, input: anki_proto::ankihub::LogoutRequest) -> Result<()> {
        let rt = self.runtime_handle();
        let fut = ankihub_logout(input.token, self.web_client());
        rt.block_on(fut)
    }
}
