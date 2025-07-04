// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::error::Error;
use std::fs;
use std::path::Path;

use regex::Regex;
use reqwest::blocking::Client;
use serde_json::Value;
use sha2::Digest;
use sha2::Sha256;

fn fetch_protoc_release_info() -> Result<String, Box<dyn Error>> {
    let client = Client::new();

    println!("Fetching latest protoc release info from GitHub...");
    // Fetch latest release info
    let response = client
        .get("https://api.github.com/repos/protocolbuffers/protobuf/releases/latest")
        .header("User-Agent", "Anki-Build-Script")
        .send()?;

    let release_info: Value = response.json()?;
    let assets = release_info["assets"]
        .as_array()
        .expect("assets should be an array");

    // Map platform names to their corresponding asset patterns
    let platform_patterns = [
        ("LinuxX64", "linux-x86_64"),
        ("LinuxArm", "linux-aarch_64"),
        ("MacX64", "osx-universal_binary"), // Mac uses universal binary for both
        ("MacArm", "osx-universal_binary"),
        ("WindowsX64", "win64"), // Windows uses x86 binary for both archs
        ("WindowsArm", "win64"),
    ];

    let mut match_blocks = Vec::new();

    for (platform, pattern) in platform_patterns {
        // Find the asset matching the platform pattern
        let asset = assets.iter().find(|asset| {
            let name = asset["name"].as_str().unwrap_or("");
            name.starts_with("protoc-") && name.contains(pattern) && name.ends_with(".zip")
        });

        if asset.is_none() {
            eprintln!("No asset found for platform {platform} pattern {pattern}");
            continue;
        }

        let asset = asset.unwrap();
        let download_url = asset["browser_download_url"].as_str().unwrap();
        let asset_name = asset["name"].as_str().unwrap();

        // Download the file and calculate SHA256 locally
        println!("Downloading and checksumming {asset_name} for {platform}...");
        let response = client
            .get(download_url)
            .header("User-Agent", "Anki-Build-Script")
            .send()?;

        let bytes = response.bytes()?;
        let mut hasher = Sha256::new();
        hasher.update(&bytes);
        let sha256 = format!("{:x}", hasher.finalize());

        // Handle platform-specific match patterns
        let match_pattern = match platform {
            "MacX64" => "Platform::MacX64 | Platform::MacArm",
            "MacArm" => continue, // Skip MacArm since it's handled with MacX64
            "WindowsX64" => "Platform::WindowsX64 | Platform::WindowsArm",
            "WindowsArm" => continue, // Skip WindowsArm since it's handled with WindowsX64
            _ => &format!("Platform::{platform}"),
        };

        match_blocks.push(format!(
            "        {match_pattern} => {{\n            OnlineArchive {{\n                url: \"{download_url}\",\n                sha256: \"{sha256}\",\n            }}\n        }}"
        ));
    }

    Ok(format!(
        "pub fn protoc_archive(platform: Platform) -> OnlineArchive {{\n    match platform {{\n{}\n    }}\n}}",
        match_blocks.join(",\n")
    ))
}

fn read_protobuf_rs() -> Result<String, Box<dyn Error>> {
    let manifest_dir = std::env::var("CARGO_MANIFEST_DIR").unwrap_or_else(|_| ".".to_string());
    let path = Path::new(&manifest_dir).join("src/protobuf.rs");
    println!("Reading {}", path.display());
    let content = fs::read_to_string(path)?;
    Ok(content)
}

fn update_protoc_text(old_text: &str, new_protoc_text: &str) -> Result<String, Box<dyn Error>> {
    let re =
        Regex::new(r"(?ms)^pub fn protoc_archive\(platform: Platform\) -> OnlineArchive \{.*?\n\}")
            .unwrap();
    if !re.is_match(old_text) {
        return Err("Could not find protoc_archive function block to replace".into());
    }
    let new_content = re.replace(old_text, new_protoc_text).to_string();
    println!("Original lines: {}", old_text.lines().count());
    println!("Updated lines: {}", new_content.lines().count());
    Ok(new_content)
}

fn write_protobuf_rs(content: &str) -> Result<(), Box<dyn Error>> {
    let manifest_dir = std::env::var("CARGO_MANIFEST_DIR").unwrap_or_else(|_| ".".to_string());
    let path = Path::new(&manifest_dir).join("src/protobuf.rs");
    println!("Writing to {}", path.display());
    fs::write(path, content)?;
    Ok(())
}

fn main() -> Result<(), Box<dyn Error>> {
    let new_protoc_archive = fetch_protoc_release_info()?;
    let content = read_protobuf_rs()?;
    let updated_content = update_protoc_text(&content, &new_protoc_archive)?;
    write_protobuf_rs(&updated_content)?;
    println!("Successfully updated protoc_archive function in protobuf.rs");
    Ok(())
}
