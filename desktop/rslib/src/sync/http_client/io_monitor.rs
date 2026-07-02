// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::io::Cursor;
use std::io::ErrorKind;
use std::sync::Arc;
use std::sync::Mutex;
use std::time::Duration;

use bytes::Bytes;
use futures::Stream;
use futures::StreamExt;
use futures::TryStreamExt;
use reqwest::header::CONTENT_TYPE;
use reqwest::header::LOCATION;
use reqwest::Body;
use reqwest::RequestBuilder;
use reqwest::Response;
use reqwest::StatusCode;
use tokio::io::AsyncReadExt;
use tokio::select;
use tokio::time::interval;
use tokio::time::Instant;
use tokio_util::io::ReaderStream;
use tokio_util::io::StreamReader;

use crate::error::Result;
use crate::sync::error::HttpError;
use crate::sync::error::HttpResult;
use crate::sync::error::OrHttpErr;
use crate::sync::request::header_and_stream::decode_zstd_body_stream_for_client;
use crate::sync::request::header_and_stream::encode_zstd_body_stream;
use crate::sync::response::ORIGINAL_SIZE;

/// Serves two purposes:
/// - allows us to monitor data sending/receiving and abort if the transfer
///   stalls
/// - allows us to monitor amount of data moving, to provide progress reporting
#[derive(Clone)]
pub struct IoMonitor(pub Arc<Mutex<IoMonitorInner>>);

impl IoMonitor {
    pub fn new() -> Self {
        Self(Arc::new(Mutex::new(IoMonitorInner {
            last_activity: Instant::now(),
            bytes_sent: 0,
            total_bytes_to_send: 0,
            bytes_received: 0,
            total_bytes_to_receive: 0,
        })))
    }

    pub fn wrap_stream<S, E>(
        &self,
        sending: bool,
        total_bytes: u32,
        stream: S,
    ) -> impl Stream<Item = HttpResult<Bytes>> + Send + Sync + 'static
    where
        S: Stream<Item = Result<Bytes, E>> + Send + Sync + 'static,
        E: std::error::Error + Send + Sync + 'static,
    {
        let inner = self.0.clone();
        {
            let mut inner = inner.lock().unwrap();
            inner.last_activity = Instant::now();
            if sending {
                inner.total_bytes_to_send += total_bytes
            } else {
                inner.total_bytes_to_receive += total_bytes
            }
        }
        stream.map(move |res| match res {
            Ok(bytes) => {
                let mut inner = inner.lock().unwrap();
                inner.last_activity = Instant::now();
                if sending {
                    inner.bytes_sent += bytes.len() as u32;
                } else {
                    inner.bytes_received += bytes.len() as u32;
                }
                Ok(bytes)
            }
            err => err.or_http_err(StatusCode::SEE_OTHER, "stream failure"),
        })
    }

    /// Returns if no I/O activity observed for `stall_time`.
    pub async fn timeout(&self, stall_time: Duration) {
        let poll_interval = Duration::from_millis(if cfg!(test) { 10 } else { 1000 });
        let mut interval = interval(poll_interval);
        loop {
            let now = interval.tick().await;
            let last_activity = self.0.lock().unwrap().last_activity;
            if now.duration_since(last_activity) > stall_time {
                return;
            }
        }
    }

    /// Takes care of encoding provided request data and setting content type to
    /// binary, and returns the decompressed response body.
    pub async fn zstd_request_with_timeout(
        &self,
        request: RequestBuilder,
        request_body: Vec<u8>,
        stall_duration: Duration,
    ) -> HttpResult<Vec<u8>> {
        let request_total = request_body.len() as u32;
        let request_body_stream = encode_zstd_body_stream(self.wrap_stream(
            true,
            request_total,
            ReaderStream::new(Cursor::new(request_body)),
        ));
        let response_body_stream = async move {
            let resp = request
                .header(CONTENT_TYPE, "application/octet-stream")
                .body(Body::wrap_stream(request_body_stream))
                .send()
                .await?
                .error_for_status()?;
            map_redirect_to_error(&resp)?;
            let response_total = resp
                .headers()
                .get(&ORIGINAL_SIZE)
                .and_then(|v| v.to_str().ok())
                .and_then(|v| v.parse::<u32>().ok())
                .or_bad_request("missing original size")?;
            let response_stream = self.wrap_stream(
                false,
                response_total,
                decode_zstd_body_stream_for_client(resp.bytes_stream()),
            );
            let mut reader =
                StreamReader::new(response_stream.map_err(|e| {
                    std::io::Error::new(ErrorKind::ConnectionAborted, format!("{e}"))
                }));
            let mut buf = Vec::with_capacity(response_total as usize);
            reader
                .read_to_end(&mut buf)
                .await
                .or_http_err(StatusCode::SEE_OTHER, "reading stream")?;
            Ok::<_, HttpError>(buf)
        };
        select! {
            // happy path
            data = response_body_stream => Ok(data?),
            // timeout
            _ = self.timeout(stall_duration) => {
                Err(HttpError {
                    code: StatusCode::REQUEST_TIMEOUT,
                    context: "timeout monitor".into(),
                    source: None,
                })
            }
        }
    }
}

/// Reqwest can't retry a redirected request as the body has been consumed, so
/// we need to bubble it up to the sync driver to retry.
fn map_redirect_to_error(resp: &Response) -> HttpResult<()> {
    if resp.status() == StatusCode::PERMANENT_REDIRECT {
        let location = resp
            .headers()
            .get(LOCATION)
            .or_bad_request("missing location header")?;
        let location = String::from_utf8(location.as_bytes().to_vec())
            .or_bad_request("location was not in utf8")?;
        None.or_permanent_redirect(location)?;
    }
    Ok(())
}

#[derive(Debug)]
pub struct IoMonitorInner {
    last_activity: Instant,
    pub bytes_sent: u32,
    pub total_bytes_to_send: u32,
    pub bytes_received: u32,
    pub total_bytes_to_receive: u32,
}

impl IoMonitor {}

#[cfg(test)]
mod test {
    use async_stream::stream;
    use futures::pin_mut;
    use futures::StreamExt;
    use tokio::select;
    use tokio::time::sleep;
    use wiremock::matchers::method;
    use wiremock::matchers::path;
    use wiremock::Mock;
    use wiremock::MockServer;
    use wiremock::ResponseTemplate;

    use super::*;
    use crate::sync::error::HttpError;

    /// The delays in the tests are aggressively short, and false positives slip
    /// through on a loaded system - especially on Windows. Fix by applying
    /// a universal multiplier.
    fn millis(millis: u64) -> Duration {
        Duration::from_millis(millis * if cfg!(windows) { 10 } else { 5 })
    }

    #[tokio::test]
    async fn can_fail_before_any_bytes() {
        let monitor = IoMonitor::new();
        let stream = monitor.wrap_stream(
            true,
            0,
            stream! {
                sleep(millis(2000)).await;
                yield Ok::<_, HttpError>(Bytes::from("1"))
            },
        );
        pin_mut!(stream);
        select! {
            _ = stream.next() => panic!("expected failure"),
            _ = monitor.timeout(millis(100)) => ()
        };
    }

    #[tokio::test]
    async fn fails_when_data_stops_moving() {
        let monitor = IoMonitor::new();
        let stream = monitor.wrap_stream(
            true,
            0,
            stream! {
                for _ in 0..10 {
                    sleep(millis(10)).await;
                    yield Ok::<_, HttpError>(Bytes::from("1"))
                }
                sleep(millis(50)).await;
                yield Ok::<_, HttpError>(Bytes::from("1"))
            },
        );
        pin_mut!(stream);
        for _ in 0..10 {
            select! {
                _ = stream.next() => (),
                _ = monitor.timeout(millis(20)) => panic!("expected success")
            };
        }
        select! {
            _ = stream.next() => panic!("expected timeout"),
            _ = monitor.timeout(millis(20)) => ()
        };
    }

    #[tokio::test]
    async fn connect_timeout_works() {
        let monitor = IoMonitor::new();
        let req = monitor.zstd_request_with_timeout(
            reqwest::Client::new().post("http://0.0.0.1"),
            vec![],
            millis(50),
        );
        req.await.unwrap_err();
    }

    #[tokio::test]
    async fn http_success() {
        let mock_server = MockServer::start().await;
        Mock::given(method("POST"))
            .and(path("/"))
            .respond_with(ResponseTemplate::new(200).insert_header(ORIGINAL_SIZE.as_str(), "0"))
            .mount(&mock_server)
            .await;
        let monitor = IoMonitor::new();
        let req = monitor.zstd_request_with_timeout(
            reqwest::Client::new().post(mock_server.uri()),
            vec![],
            millis(10),
        );
        req.await.unwrap();
    }

    #[tokio::test]
    async fn delay_before_reply_fails() {
        let mock_server = MockServer::start().await;
        Mock::given(method("POST"))
            .and(path("/"))
            .respond_with(ResponseTemplate::new(200).set_delay(millis(50)))
            .mount(&mock_server)
            .await;
        let monitor = IoMonitor::new();
        let req = monitor.zstd_request_with_timeout(
            reqwest::Client::new().post(mock_server.uri()),
            vec![],
            millis(10),
        );
        req.await.unwrap_err();
    }
}
