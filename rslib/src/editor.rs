// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::path::Path;
use std::time::Duration;

use percent_encoding_iri::percent_decode_str;
use reqwest::Client;
use reqwest::Url;

use crate::error::AnkiError;
use crate::error::Result;
use crate::invalid_input;

/// Download file from URL.
/// Returns (filename, file_contents) tuple.
pub async fn retrieve_url(url: &str) -> Result<(String, Vec<u8>)> {
    let is_local = url.to_lowercase().starts_with("file://");
    let (file_contents, content_type) = if is_local {
        download_local_file(url).await?
    } else {
        download_remote_file(url).await?
    };

    let mut parsed_url = match Url::parse(url) {
        Ok(url) => url,
        Err(e) => invalid_input!("Invalid URL: {}", e),
    };
    parsed_url.set_query(None);
    let mut filename = parsed_url
        .path_segments()
        .and_then(|mut segments| segments.next_back())
        .unwrap_or("")
        .to_string();

    filename = match percent_decode_str(&filename).decode_utf8() {
        Ok(decoded) => decoded.to_string(),
        Err(e) => invalid_input!("Failed to decode filename: {}", e),
    };

    if filename.trim().is_empty() {
        filename = "paste".to_string();
    }

    if let Some(mime_type) = content_type {
        filename = add_extension_based_on_mime(&filename, &mime_type);
    }

    Ok((filename.to_string(), file_contents))
}

async fn download_local_file(url: &str) -> Result<(Vec<u8>, Option<String>)> {
    let decoded_url = match percent_decode_str(url).decode_utf8() {
        Ok(url) => url,
        Err(e) => invalid_input!("Failed to decode file URL: {}", e),
    };

    let parsed_url = match Url::parse(&decoded_url) {
        Ok(url) => url,
        Err(e) => invalid_input!("Invalid file URL: {}", e),
    };

    let file_path = match parsed_url.to_file_path() {
        Ok(path) => path,
        Err(_) => invalid_input!("Invalid file path in URL"),
    };

    let file_contents = std::fs::read(&file_path).map_err(|e| AnkiError::FileIoError {
        source: anki_io::FileIoError {
            path: file_path.clone(),
            op: anki_io::FileOp::Read,
            source: e,
        },
    })?;

    Ok((file_contents, None))
}

async fn download_remote_file(url: &str) -> Result<(Vec<u8>, Option<String>)> {
    let client = Client::builder()
        .timeout(Duration::from_secs(30))
        .user_agent("Mozilla/5.0 (compatible; Anki)")
        .build()?;

    let response = client.get(url).send().await?.error_for_status()?;
    let content_type = response
        .headers()
        .get("content-type")
        .and_then(|ct| ct.to_str().ok())
        .map(|s| s.to_string());

    let file_contents = response.bytes().await?.to_vec();

    Ok((file_contents, content_type))
}

fn add_extension_based_on_mime(filename: &str, content_type: &str) -> String {
    let mut extension = "";
    if Path::new(filename).extension().is_none() {
        extension = match content_type {
            "audio/mpeg" => ".mp3",
            "audio/ogg" => ".oga",
            "audio/opus" => ".opus",
            "audio/wav" => ".wav",
            "audio/webm" => ".weba",
            "audio/aac" => ".aac",
            "image/jpeg" => ".jpg",
            "image/png" => ".png",
            "image/svg+xml" => ".svg",
            "image/webp" => ".webp",
            "image/avif" => ".avif",
            _ => "",
        };
    };

    filename.to_string() + extension
}
