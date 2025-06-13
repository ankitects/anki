// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::error::Error;
use std::fs;
use std::path::Path;

use regex::Regex;
use reqwest::blocking::Client;
use serde_json::Value;

fn fetch_uv_release_info() -> Result<String, Box<dyn Error>> {
    let client = Client::new();

    println!("Fetching latest uv release info from GitHub...");
    // Fetch latest release info
    let response = client
        .get("https://api.github.com/repos/astral-sh/uv/releases/latest")
        .header("User-Agent", "Anki-Build-Script")
        .send()?;

    let release_info: Value = response.json()?;
    let assets = release_info["assets"]
        .as_array()
        .expect("assets should be an array");

    // Map platform names to their corresponding asset patterns
    let platform_patterns = [
        ("LinuxX64", "x86_64-unknown-linux-gnu"),
        ("LinuxArm", "aarch64-unknown-linux-gnu"),
        ("MacX64", "x86_64-apple-darwin"),
        ("MacArm", "aarch64-apple-darwin"),
        ("WindowsX64", "x86_64-pc-windows-msvc"),
        ("WindowsArm", "aarch64-pc-windows-msvc"),
    ];

    let mut match_blocks = Vec::new();

    for (platform, pattern) in platform_patterns {
        // Find the asset matching the platform pattern (the binary)
        let asset = assets.iter().find(|asset| {
            let name = asset["name"].as_str().unwrap_or("");
            name.contains(pattern) && (name.ends_with(".tar.gz") || name.ends_with(".zip"))
        });
        if asset.is_none() {
            eprintln!("No asset found for platform {platform} pattern {pattern}");
            continue;
        }
        let asset = asset.unwrap();
        let download_url = asset["browser_download_url"].as_str().unwrap();
        let asset_name = asset["name"].as_str().unwrap();

        // Find the corresponding .sha256 or .sha256sum asset
        let sha_asset = assets.iter().find(|a| {
            let name = a["name"].as_str().unwrap_or("");
            name == format!("{}.sha256", asset_name) || name == format!("{}.sha256sum", asset_name)
        });
        if sha_asset.is_none() {
            eprintln!("No sha256 asset found for {asset_name}");
            continue;
        }
        let sha_asset = sha_asset.unwrap();
        let sha_url = sha_asset["browser_download_url"].as_str().unwrap();
        println!("Fetching SHA256 for {platform}...");
        let sha_text = client
            .get(sha_url)
            .header("User-Agent", "Anki-Build-Script")
            .send()?
            .text()?;
        // The sha file is usually of the form: "<sha256>  <filename>"
        let sha256 = sha_text.split_whitespace().next().unwrap_or("");

        match_blocks.push(format!(
            "        Platform::{} => {{\n            OnlineArchive {{\n                url: \"{}\",\n                sha256: \"{}\",\n            }}\n        }}",
            platform, download_url, sha256
        ));
    }

    Ok(format!(
        "pub fn uv_archive(platform: Platform) -> OnlineArchive {{\n    match platform {{\n{}\n    }}",
        match_blocks.join(",\n")
    ))
}

fn read_python_rs() -> Result<String, Box<dyn Error>> {
    let manifest_dir = std::env::var("CARGO_MANIFEST_DIR").unwrap_or_else(|_| ".".to_string());
    let path = Path::new(&manifest_dir).join("src/python.rs");
    println!("Reading {}", path.display());
    let content = fs::read_to_string(path)?;
    Ok(content)
}

fn update_uv_text(old_text: &str, new_uv_text: &str) -> Result<String, Box<dyn Error>> {
    let re = Regex::new(r"(?ms)^pub fn uv_archive\(platform: Platform\) -> OnlineArchive \{.*?\n\s*\}\s*\n\s*\}\s*\n\s*\}").unwrap();
    if !re.is_match(old_text) {
        return Err("Could not find uv_archive function block to replace".into());
    }
    let new_content = re.replace(old_text, new_uv_text).to_string();
    println!("Original lines: {}", old_text.lines().count());
    println!("Updated lines: {}", new_content.lines().count());
    Ok(new_content)
}

fn write_python_rs(content: &str) -> Result<(), Box<dyn Error>> {
    let manifest_dir = std::env::var("CARGO_MANIFEST_DIR").unwrap_or_else(|_| ".".to_string());
    let path = Path::new(&manifest_dir).join("src/python.rs");
    println!("Writing to {}", path.display());
    fs::write(path, content)?;
    Ok(())
}

fn main() -> Result<(), Box<dyn Error>> {
    let new_uv_archive = fetch_uv_release_info()?;
    let content = read_python_rs()?;
    let updated_content = update_uv_text(&content, &new_uv_archive)?;
    write_python_rs(&updated_content)?;
    println!("Successfully updated uv_archive function in python.rs");
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_update_uv_text_with_actual_file() {
        let content = fs::read_to_string("src/python.rs").unwrap();
        let original_lines = content.lines().count();

        const EXPECTED_LINES_REMOVED: usize = 38;

        let updated = update_uv_text(&content, "").unwrap();
        let updated_lines = updated.lines().count();

        assert_eq!(
            updated_lines,
            original_lines - EXPECTED_LINES_REMOVED,
            "Expected line count to decrease by exactly {} lines (original: {}, updated: {})",
            EXPECTED_LINES_REMOVED,
            original_lines,
            updated_lines
        );
    }
}
