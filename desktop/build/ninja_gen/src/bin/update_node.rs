// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::error::Error;
use std::fs;
use std::path::Path;

use regex::Regex;
use reqwest::blocking::Client;
use serde_json::Value;

#[derive(Debug)]
struct NodeRelease {
    version: String,
    files: Vec<NodeFile>,
}

#[derive(Debug)]
struct NodeFile {
    filename: String,
    url: String,
}

fn main() -> Result<(), Box<dyn Error>> {
    let release_info = fetch_node_release_info()?;
    let new_text = generate_node_archive_function(&release_info)?;
    update_node_text(&new_text)?;
    println!("Node.js archive function updated successfully!");
    Ok(())
}

fn fetch_node_release_info() -> Result<NodeRelease, Box<dyn Error>> {
    let client = Client::new();

    // Get the Node.js release info
    let response = client
        .get("https://nodejs.org/dist/index.json")
        .header("User-Agent", "anki-build-updater")
        .send()?;

    let releases: Vec<Value> = response.json()?;

    // Find the latest LTS release
    let latest = releases
        .iter()
        .find(|release| {
            // LTS releases have a non-false "lts" field
            release["lts"].as_str().is_some() && release["lts"] != false
        })
        .ok_or("No LTS releases found")?;

    let version = latest["version"]
        .as_str()
        .ok_or("Version not found")?
        .to_string();

    let files = latest["files"]
        .as_array()
        .ok_or("Files array not found")?
        .iter()
        .map(|f| f.as_str().unwrap_or(""))
        .collect::<Vec<_>>();

    let lts_name = latest["lts"].as_str().unwrap_or("unknown");
    println!("Found Node.js LTS version: {version} ({lts_name})");

    // Map platforms to their expected file keys and full filenames
    let platform_mapping = vec![
        (
            "linux-x64",
            "linux-x64",
            format!("node-{version}-linux-x64.tar.xz"),
        ),
        (
            "linux-arm64",
            "linux-arm64",
            format!("node-{version}-linux-arm64.tar.xz"),
        ),
        (
            "darwin-x64",
            "osx-x64-tar",
            format!("node-{version}-darwin-x64.tar.xz"),
        ),
        (
            "darwin-arm64",
            "osx-arm64-tar",
            format!("node-{version}-darwin-arm64.tar.xz"),
        ),
        (
            "win-x64",
            "win-x64-zip",
            format!("node-{version}-win-x64.zip"),
        ),
        (
            "win-arm64",
            "win-arm64-zip",
            format!("node-{version}-win-arm64.zip"),
        ),
    ];

    let mut node_files = Vec::new();

    for (platform, file_key, filename) in platform_mapping {
        // Check if this file exists in the release
        if files.contains(&file_key) {
            let url = format!("https://nodejs.org/dist/{version}/{filename}");
            node_files.push(NodeFile {
                filename: filename.clone(),
                url,
            });
            println!("Found file for {platform}: {filename} (key: {file_key})");
        } else {
            return Err(
                format!("File not found for {platform} (key: {file_key}): {filename}").into(),
            );
        }
    }

    Ok(NodeRelease {
        version,
        files: node_files,
    })
}

fn generate_node_archive_function(release: &NodeRelease) -> Result<String, Box<dyn Error>> {
    let client = Client::new();

    // Fetch the SHASUMS256.txt file once
    println!("Fetching SHA256 checksums...");
    let shasums_url = format!("https://nodejs.org/dist/{}/SHASUMS256.txt", release.version);
    let shasums_response = client
        .get(&shasums_url)
        .header("User-Agent", "anki-build-updater")
        .send()?;
    let shasums_text = shasums_response.text()?;

    // Create a mapping from filename patterns to platform names - using the exact
    // patterns we stored in files
    let platform_mapping = vec![
        ("linux-x64.tar.xz", "LinuxX64"),
        ("linux-arm64.tar.xz", "LinuxArm"),
        ("darwin-x64.tar.xz", "MacX64"),
        ("darwin-arm64.tar.xz", "MacArm"),
        ("win-x64.zip", "WindowsX64"),
        ("win-arm64.zip", "WindowsArm"),
    ];

    let mut platform_blocks = Vec::new();

    for (file_pattern, platform_name) in platform_mapping {
        // Find the file that ends with this pattern
        if let Some(file) = release
            .files
            .iter()
            .find(|f| f.filename.ends_with(file_pattern))
        {
            // Find the SHA256 for this file
            let sha256 = shasums_text
                .lines()
                .find(|line| line.contains(&file.filename))
                .and_then(|line| line.split_whitespace().next())
                .ok_or_else(|| format!("SHA256 not found for {}", file.filename))?;

            println!(
                "Found SHA256 for {}: {} => {}",
                platform_name, file.filename, sha256
            );

            let block = format!(
                "        Platform::{} => OnlineArchive {{\n            url: \"{}\",\n            sha256: \"{}\",\n        }},",
                platform_name, file.url, sha256
            );
            platform_blocks.push(block);
        } else {
            return Err(format!(
                "File not found for platform {platform_name}: no file ending with {file_pattern}"
            )
            .into());
        }
    }

    let function = format!(
        "pub fn node_archive(platform: Platform) -> OnlineArchive {{\n    match platform {{\n{}\n    }}\n}}",
        platform_blocks.join("\n")
    );

    Ok(function)
}

fn update_node_text(new_function: &str) -> Result<(), Box<dyn Error>> {
    let node_rs_content = read_node_rs()?;

    // Regex to match the entire node_archive function with proper multiline
    // matching
    let re = Regex::new(
        r"(?s)pub fn node_archive\(platform: Platform\) -> OnlineArchive \{.*?\n\s*\}\s*\n\s*\}",
    )?;

    let updated_content = re.replace(&node_rs_content, new_function);

    write_node_rs(&updated_content)?;
    Ok(())
}

fn read_node_rs() -> Result<String, Box<dyn Error>> {
    // Use CARGO_MANIFEST_DIR to get the crate root, then find src/node.rs
    let manifest_dir =
        std::env::var("CARGO_MANIFEST_DIR").map_err(|_| "CARGO_MANIFEST_DIR not set")?;
    let path = Path::new(&manifest_dir).join("src").join("node.rs");
    Ok(fs::read_to_string(path)?)
}

fn write_node_rs(content: &str) -> Result<(), Box<dyn Error>> {
    // Use CARGO_MANIFEST_DIR to get the crate root, then find src/node.rs
    let manifest_dir =
        std::env::var("CARGO_MANIFEST_DIR").map_err(|_| "CARGO_MANIFEST_DIR not set")?;
    let path = Path::new(&manifest_dir).join("src").join("node.rs");
    fs::write(path, content)?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_regex_replacement() {
        let sample_content = r#"Some other code
pub fn node_archive(platform: Platform) -> OnlineArchive {
    match platform {
        Platform::LinuxX64 => OnlineArchive {
            url: "https://nodejs.org/dist/v20.11.0/node-v20.11.0-linux-x64.tar.xz",
            sha256: "old_hash",
        },
        Platform::MacX64 => OnlineArchive {
            url: "https://nodejs.org/dist/v20.11.0/node-v20.11.0-darwin-x64.tar.xz",
            sha256: "old_hash",
        },
    }
}

More code here"#;

        let new_function = r#"pub fn node_archive(platform: Platform) -> OnlineArchive {
    match platform {
        Platform::LinuxX64 => OnlineArchive {
            url: "https://nodejs.org/dist/v21.0.0/node-v21.0.0-linux-x64.tar.xz",
            sha256: "new_hash",
        },
        Platform::MacX64 => OnlineArchive {
            url: "https://nodejs.org/dist/v21.0.0/node-v21.0.0-darwin-x64.tar.xz",
            sha256: "new_hash",
        },
    }
}"#;

        let re = Regex::new(
            r"(?s)pub fn node_archive\(platform: Platform\) -> OnlineArchive \{.*?\n\s*\}\s*\n\s*\}"
        ).unwrap();

        let result = re.replace(sample_content, new_function);
        assert!(result.contains("v21.0.0"));
        assert!(result.contains("new_hash"));
        assert!(!result.contains("old_hash"));
        assert!(result.contains("Some other code"));
        assert!(result.contains("More code here"));
    }
}
