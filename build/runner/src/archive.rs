// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::fs;
use std::io::Read;
use std::path::Path;
use std::path::PathBuf;

use anki_io::read_file;
use anki_io::write_file;
use anyhow::Result;
use camino::Utf8Path;
use clap::Args;
use clap::Subcommand;
use sha2::Digest;

#[derive(Subcommand)]
pub enum ArchiveArgs {
    Download(DownloadArgs),
    Extract(ExtractArgs),
}

#[derive(Args)]
pub struct DownloadArgs {
    archive_url: String,
    checksum: String,
    output_path: PathBuf,
}

#[derive(Args)]
pub struct ExtractArgs {
    archive_path: String,
    output_folder: String,
}

#[tokio::main]
pub async fn archive_command(args: ArchiveArgs) -> Result<()> {
    match args {
        ArchiveArgs::Download(args) => {
            download_and_check(&args.archive_url, &args.checksum, &args.output_path).await
        }
        ArchiveArgs::Extract(args) => extract_archive(&args.archive_path, &args.output_folder),
    }
}

async fn download_and_check(archive_url: &str, checksum: &str, output_path: &Path) -> Result<()> {
    // skip download if we already have a valid file
    if output_path.exists() && sha2_data(&read_file(output_path)?) == checksum {
        return Ok(());
    }

    let response = reqwest::get(archive_url).await?.error_for_status()?;
    let data = response.bytes().await?.to_vec();
    let actual_checksum = sha2_data(&data);
    if actual_checksum != checksum {
        println!("expected {checksum}, got {actual_checksum}");
        std::process::exit(1);
    }
    fs::write(output_path, data)?;

    Ok(())
}

fn sha2_data(data: &[u8]) -> String {
    let mut digest = sha2::Sha256::new();
    digest.update(data);
    let result = digest.finalize();
    format!("{result:x}")
}

enum CompressionKind {
    Zstd,
    Gzip,
    Lzma,
    /// handled by archive
    Internal,
}

enum ArchiveKind {
    Tar,
    Zip,
}

fn extract_archive(archive_path: &str, output_folder: &str) -> Result<()> {
    let archive_path = Utf8Path::new(archive_path);
    let archive_filename = archive_path.file_name().unwrap();
    let mut components = archive_filename.rsplit('.');
    let last_component = components.next().unwrap();
    let (compression, archive_suffix) = match last_component {
        "zst" | "zstd" => (CompressionKind::Zstd, components.next().unwrap()),
        "gz" => (CompressionKind::Gzip, components.next().unwrap()),
        "xz" => (CompressionKind::Lzma, components.next().unwrap()),
        "tgz" => (CompressionKind::Gzip, last_component),
        "zip" => (CompressionKind::Internal, last_component),
        other => panic!("unexpected compression: {other}"),
    };
    let archive = match archive_suffix {
        "tar" | "tgz" => ArchiveKind::Tar,
        "zip" => ArchiveKind::Zip,
        other => panic!("unexpected archive kind: {other}"),
    };

    let reader = fs::File::open(archive_path)?;
    let uncompressed_data = match compression {
        CompressionKind::Zstd => zstd::decode_all(&reader)?,
        CompressionKind::Gzip => {
            let mut buf = Vec::new();
            let mut decoder = flate2::read::GzDecoder::new(&reader);
            decoder.read_to_end(&mut buf)?;
            buf
        }
        CompressionKind::Lzma => {
            let mut buf = Vec::new();
            let mut decoder = xz2::read::XzDecoder::new(&reader);
            decoder.read_to_end(&mut buf)?;
            buf
        }
        CompressionKind::Internal => {
            vec![]
        }
    };

    let output_folder = Utf8Path::new(output_folder);
    if output_folder.exists() {
        fs::remove_dir_all(output_folder)?;
    }
    // extract into a temporary folder
    let output_tmp =
        output_folder.with_file_name(format!("{}.tmp", output_folder.file_name().unwrap()));
    match archive {
        ArchiveKind::Tar => {
            let mut archive = tar::Archive::new(&uncompressed_data[..]);
            archive.set_preserve_mtime(false);
            archive.unpack(&output_tmp)?;
        }
        ArchiveKind::Zip => {
            let mut archive = zip::ZipArchive::new(reader)?;
            archive.extract(&output_tmp)?;
        }
    }
    // if the output folder contains a single folder (eg foo-1.2), move it up a
    // level
    let mut entries: Vec<_> = output_tmp.read_dir_utf8()?.take(2).collect();
    let first_entry = entries.pop().unwrap()?;
    if entries.is_empty() && first_entry.metadata()?.is_dir() {
        fs::rename(first_entry.path(), output_folder)?;
        fs::remove_dir_all(output_tmp)?;
    } else {
        fs::rename(output_tmp, output_folder)?;
    }
    write_file(output_folder.with_extension("marker"), "")?;
    Ok(())
}
