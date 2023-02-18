// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use axum::http::StatusCode;
use axum::response::IntoResponse;
use axum::response::Redirect;
use axum::response::Response;
use snafu::OptionExt;
use snafu::Snafu;

pub type HttpResult<T, E = HttpError> = std::result::Result<T, E>;

#[derive(Debug, Snafu)]
#[snafu(visibility(pub))]
pub struct HttpError {
    pub code: StatusCode,
    pub context: String,
    // snafu's automatic error conversion only supports Option if
    // the whatever trait is derived, and deriving whatever means we
    // can't have extra fields like `code`. Even without Option, the
    // error conversion requires us to manually box the error, so we end
    // up having to disable the default behaviour and add the error to the
    // snafu ourselves
    #[snafu(source(false))]
    pub source: Option<Box<dyn std::error::Error + Send + Sync>>,
}

impl HttpError {
    pub fn new_without_source(code: StatusCode, context: impl Into<String>) -> Self {
        Self {
            code,
            context: context.into(),
            source: None,
        }
    }

    /// Compatibility with ensure!() macro
    pub fn fail<T>(self) -> Result<T, Self> {
        Err(self)
    }
}

impl IntoResponse for HttpError {
    fn into_response(self) -> Response {
        let HttpError {
            code,
            context,
            source,
        } = self;
        if code.is_server_error() && code != StatusCode::NOT_IMPLEMENTED {
            tracing::error!(context, ?source, httpstatus = code.as_u16(),);
        } else {
            tracing::info!(context, ?source, httpstatus = code.as_u16(),);
        }
        if code == StatusCode::PERMANENT_REDIRECT {
            Redirect::permanent(&context).into_response()
        } else {
            (code, code.as_str().to_string()).into_response()
        }
    }
}

pub trait OrHttpErr {
    type Value;

    fn or_http_err(
        self,
        code: StatusCode,
        context: impl Into<String>,
    ) -> Result<Self::Value, HttpError>;

    fn or_bad_request(self, context: impl Into<String>) -> Result<Self::Value, HttpError>
    where
        Self: Sized,
    {
        self.or_http_err(StatusCode::BAD_REQUEST, context)
    }

    fn or_internal_err(self, context: impl Into<String>) -> Result<Self::Value, HttpError>
    where
        Self: Sized,
    {
        self.or_http_err(StatusCode::INTERNAL_SERVER_ERROR, context)
    }

    fn or_forbidden(self, context: impl Into<String>) -> Result<Self::Value, HttpError>
    where
        Self: Sized,
    {
        self.or_http_err(StatusCode::FORBIDDEN, context)
    }

    fn or_conflict(self, context: impl Into<String>) -> Result<Self::Value, HttpError>
    where
        Self: Sized,
    {
        self.or_http_err(StatusCode::CONFLICT, context)
    }

    fn or_not_found(self, context: impl Into<String>) -> Result<Self::Value, HttpError>
    where
        Self: Sized,
    {
        self.or_http_err(StatusCode::NOT_FOUND, context)
    }

    fn or_permanent_redirect(self, context: impl Into<String>) -> Result<Self::Value, HttpError>
    where
        Self: Sized,
    {
        self.or_http_err(StatusCode::PERMANENT_REDIRECT, context)
    }
}

impl<T, E> OrHttpErr for Result<T, E>
where
    E: Into<Box<dyn std::error::Error + Send + Sync + 'static>>,
{
    type Value = T;

    fn or_http_err(
        self,
        code: StatusCode,
        context: impl Into<String>,
    ) -> Result<Self::Value, HttpError> {
        self.map_err(|err| {
            HttpSnafu {
                code,
                context: context.into(),
                source: err.into(),
            }
            .build()
        })
    }
}

impl<T> OrHttpErr for Option<T> {
    type Value = T;

    fn or_http_err(
        self,
        code: StatusCode,
        context: impl Into<String>,
    ) -> Result<Self::Value, HttpError> {
        self.context(HttpSnafu {
            code,
            context,
            source: None,
        })
    }
}
