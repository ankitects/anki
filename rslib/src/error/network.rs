// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_i18n::I18n;
use reqwest::StatusCode;

use super::AnkiError;

#[derive(Debug, PartialEq)]
pub struct NetworkError {
    pub info: String,
    pub kind: NetworkErrorKind,
}

#[derive(Debug, PartialEq)]
pub enum NetworkErrorKind {
    Offline,
    Timeout,
    ProxyAuth,
    Other,
}

#[derive(Debug, PartialEq)]
pub struct SyncError {
    pub info: String,
    pub kind: SyncErrorKind,
}

#[derive(Debug, PartialEq)]
pub enum SyncErrorKind {
    Conflict,
    ServerError,
    ClientTooOld,
    AuthFailed,
    ServerMessage,
    ClockIncorrect,
    Other,
    ResyncRequired,
    DatabaseCheckRequired,
    SyncNotStarted,
    UploadTooLarge,
}

impl AnkiError {
    pub(crate) fn sync_error(info: impl Into<String>, kind: SyncErrorKind) -> Self {
        AnkiError::SyncError(SyncError {
            info: info.into(),
            kind,
        })
    }

    pub(crate) fn server_message<S: Into<String>>(msg: S) -> AnkiError {
        AnkiError::sync_error(msg, SyncErrorKind::ServerMessage)
    }
}

impl From<reqwest::Error> for AnkiError {
    fn from(err: reqwest::Error) -> Self {
        let url = err.url().map(|url| url.as_str()).unwrap_or("");
        let str_err = format!("{}", err);
        // strip url from error to avoid exposing keys
        let info = str_err.replace(url, "");

        if err.is_timeout() {
            AnkiError::NetworkError(NetworkError {
                info,
                kind: NetworkErrorKind::Timeout,
            })
        } else if err.is_status() {
            error_for_status_code(info, err.status().unwrap())
        } else {
            guess_reqwest_error(info)
        }
    }
}

fn error_for_status_code(info: String, code: StatusCode) -> AnkiError {
    use reqwest::StatusCode as S;
    match code {
        S::PROXY_AUTHENTICATION_REQUIRED => AnkiError::NetworkError(NetworkError {
            info,
            kind: NetworkErrorKind::ProxyAuth,
        }),
        S::CONFLICT => AnkiError::SyncError(SyncError {
            info,
            kind: SyncErrorKind::Conflict,
        }),
        S::FORBIDDEN => AnkiError::SyncError(SyncError {
            info,
            kind: SyncErrorKind::AuthFailed,
        }),
        S::NOT_IMPLEMENTED => AnkiError::SyncError(SyncError {
            info,
            kind: SyncErrorKind::ClientTooOld,
        }),
        S::INTERNAL_SERVER_ERROR | S::BAD_GATEWAY | S::GATEWAY_TIMEOUT | S::SERVICE_UNAVAILABLE => {
            AnkiError::SyncError(SyncError {
                info,
                kind: SyncErrorKind::ServerError,
            })
        }
        S::BAD_REQUEST => AnkiError::SyncError(SyncError {
            info,
            kind: SyncErrorKind::DatabaseCheckRequired,
        }),
        _ => AnkiError::NetworkError(NetworkError {
            info,
            kind: NetworkErrorKind::Other,
        }),
    }
}

fn guess_reqwest_error(mut info: String) -> AnkiError {
    if info.contains("dns error: cancelled") {
        return AnkiError::Interrupted;
    }
    let kind = if info.contains("unreachable") || info.contains("dns") {
        NetworkErrorKind::Offline
    } else if info.contains("timed out") {
        NetworkErrorKind::Timeout
    } else {
        if info.contains("invalid type") {
            info = format!(
                "{} {} {}\n\n{}",
                "Please force a full sync in the Preferences screen to bring your devices into sync.",
                "Then, please use the Check Database feature, and sync to your other devices.",
                "If problems persist, please post on the support forum.",
                info,
            );
        }

        NetworkErrorKind::Other
    };
    AnkiError::NetworkError(NetworkError { info, kind })
}

impl From<zip::result::ZipError> for AnkiError {
    fn from(err: zip::result::ZipError) -> Self {
        AnkiError::sync_error(err.to_string(), SyncErrorKind::Other)
    }
}

impl SyncError {
    pub fn localized_description(&self, tr: &I18n) -> String {
        match self.kind {
            SyncErrorKind::ServerMessage => self.info.clone().into(),
            SyncErrorKind::Other => self.info.clone().into(),
            SyncErrorKind::Conflict => tr.sync_conflict(),
            SyncErrorKind::ServerError => tr.sync_server_error(),
            SyncErrorKind::ClientTooOld => tr.sync_client_too_old(),
            SyncErrorKind::AuthFailed => tr.sync_wrong_pass(),
            SyncErrorKind::ResyncRequired => tr.sync_resync_required(),
            SyncErrorKind::ClockIncorrect => tr.sync_clock_off(),
            SyncErrorKind::DatabaseCheckRequired => tr.sync_sanity_check_failed(),
            SyncErrorKind::SyncNotStarted => "sync not started".into(),
            SyncErrorKind::UploadTooLarge => tr.sync_upload_too_large(&self.info),
        }
        .into()
    }
}

impl NetworkError {
    pub fn localized_description(&self, tr: &I18n) -> String {
        let summary = match self.kind {
            NetworkErrorKind::Offline => tr.network_offline(),
            NetworkErrorKind::Timeout => tr.network_timeout(),
            NetworkErrorKind::ProxyAuth => tr.network_proxy_auth(),
            NetworkErrorKind::Other => tr.network_other(),
        };
        let details = tr.network_details(self.info.as_str());
        format!("{}\n\n{}", summary, details)
    }
}
