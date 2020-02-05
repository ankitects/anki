// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::err::{AnkiError, Result, SyncErrorKind};
use crate::media::database::{MediaDatabaseContext, MediaDatabaseMetadata, MediaEntry};
use crate::media::files::{
    add_file_from_ankiweb, data_for_file, mtime_as_i64, normalize_filename, remove_files, AddedFile,
};
use crate::media::{register_changes, MediaManager};
use crate::version;
use bytes::Bytes;
use log::debug;
use reqwest;
use reqwest::{multipart, Client, Response};
use serde_derive::{Deserialize, Serialize};
use serde_tuple::Serialize_tuple;
use std::borrow::Cow;
use std::collections::HashMap;
use std::io::{Read, Write};
use std::path::Path;
use std::{io, time};

static SYNC_MAX_FILES: usize = 25;
static SYNC_MAX_BYTES: usize = (2.5 * 1024.0 * 1024.0) as usize;
static SYNC_SINGLE_FILE_MAX_BYTES: usize = 100 * 1024 * 1024;

// fixme: concurrent modifications during upload step

/// The counts are not cumulative - the progress hook should accumulate them.
#[derive(Debug)]
pub enum Progress {
    DownloadedChanges(usize),
    DownloadedFiles(usize),
    Uploaded { files: usize, deletions: usize },
    RemovedFiles(usize),
}

struct SyncContext<'a, P>
where
    P: Fn(Progress) -> bool,
{
    mgr: &'a MediaManager,
    ctx: MediaDatabaseContext<'a>,
    skey: Option<String>,
    client: Client,
    progress_cb: P,
    endpoint: &'a str,
}

impl<P> SyncContext<'_, P>
where
    P: Fn(Progress) -> bool,
{
    fn new<'a>(mgr: &'a MediaManager, progress_cb: P, endpoint: &'a str) -> SyncContext<'a, P> {
        let client = Client::builder()
            .connect_timeout(time::Duration::from_secs(30))
            .build()
            .unwrap();
        let ctx = mgr.dbctx();

        SyncContext {
            mgr,
            ctx,
            skey: None,
            client,
            progress_cb,
            endpoint,
        }
    }

    fn skey(&self) -> &str {
        self.skey.as_ref().unwrap()
    }

    async fn sync_begin(&self, hkey: &str) -> Result<(String, i32)> {
        let url = format!("{}begin", self.endpoint);

        let resp = self
            .client
            .get(&url)
            .query(&[("k", hkey), ("v", &version_string())])
            .send()
            .await?
            .error_for_status()?;

        let reply: SyncBeginResult = resp.json().await?;

        if let Some(data) = reply.data {
            Ok((data.sync_key, data.usn))
        } else {
            Err(AnkiError::server_message(reply.err))
        }
    }

    async fn fetch_changes(&mut self, mut meta: MediaDatabaseMetadata) -> Result<()> {
        let mut last_usn = meta.last_sync_usn;
        loop {
            debug!("fetching record batch starting from usn {}", last_usn);

            let batch = self.fetch_record_batch(last_usn).await?;
            if batch.is_empty() {
                debug!("empty batch, done");
                break;
            }
            last_usn = batch.last().unwrap().usn;

            self.progress(Progress::DownloadedChanges(batch.len()))?;

            let (to_download, to_delete, to_remove_pending) =
                determine_required_changes(&mut self.ctx, &batch)?;

            // file removal
            remove_files(self.mgr.media_folder.as_path(), to_delete.as_slice())?;
            self.progress(Progress::RemovedFiles(to_delete.len()))?;

            // file download
            let mut downloaded = vec![];
            let mut dl_fnames = to_download.as_slice();
            while !dl_fnames.is_empty() {
                let batch: Vec<_> = dl_fnames
                    .iter()
                    .take(SYNC_MAX_FILES)
                    .map(ToOwned::to_owned)
                    .collect();
                let zip_data = self.fetch_zip(batch.as_slice()).await?;
                let download_batch =
                    extract_into_media_folder(self.mgr.media_folder.as_path(), zip_data)?
                        .into_iter();
                let len = download_batch.len();
                dl_fnames = &dl_fnames[len..];
                downloaded.extend(download_batch);
                self.progress(Progress::DownloadedFiles(len))?;
            }

            // then update the DB
            let dirmod = mtime_as_i64(&self.mgr.media_folder)?;
            self.ctx.transact(|ctx| {
                record_removals(ctx, &to_delete)?;
                record_additions(ctx, downloaded)?;
                record_clean(ctx, &to_remove_pending)?;

                // update usn
                meta.last_sync_usn = last_usn;
                meta.folder_mtime = dirmod;
                ctx.set_meta(&meta)?;

                Ok(())
            })?;
        }
        Ok(())
    }

    async fn send_changes(&mut self) -> Result<()> {
        loop {
            let pending: Vec<MediaEntry> = self.ctx.get_pending_uploads(SYNC_MAX_FILES as u32)?;
            if pending.is_empty() {
                break;
            }

            let file_count = pending.iter().filter(|e| e.sha1.is_some()).count();

            let zip_data = zip_files(&self.mgr.media_folder, &pending)?;
            self.send_zip_data(zip_data).await?;

            self.progress(Progress::Uploaded {
                files: file_count,
                deletions: pending.len() - file_count,
            })?;

            let fnames: Vec<_> = pending.iter().map(|e| &e.fname).collect();
            self.ctx
                .transact(|ctx| record_clean(ctx, fnames.as_slice()))?;
        }

        Ok(())
    }

    async fn finalize_sync(&mut self) -> Result<()> {
        let url = format!("{}mediaSanity", self.endpoint);
        let local = self.ctx.count()?;

        let obj = FinalizeRequest { local };
        let resp = ankiweb_json_request(&self.client, &url, &obj, self.skey()).await?;
        let resp: FinalizeResponse = resp.json().await?;

        if let Some(data) = resp.data {
            if data == "OK" {
                Ok(())
            } else {
                self.ctx.transact(|ctx| ctx.force_resync())?;
                Err(AnkiError::SyncError {
                    info: "".into(),
                    kind: SyncErrorKind::ResyncRequired,
                })
            }
        } else {
            Err(AnkiError::server_message(resp.err))
        }
    }

    fn progress(&self, progress: Progress) -> Result<()> {
        if (self.progress_cb)(progress) {
            Ok(())
        } else {
            Err(AnkiError::Interrupted)
        }
    }

    async fn fetch_record_batch(&self, last_usn: i32) -> Result<Vec<ServerMediaRecord>> {
        let url = format!("{}mediaChanges", self.endpoint);

        let req = RecordBatchRequest { last_usn };
        let resp = ankiweb_json_request(&self.client, &url, &req, self.skey()).await?;
        let res: RecordBatchResult = resp.json().await?;

        if let Some(batch) = res.data {
            Ok(batch)
        } else {
            Err(AnkiError::server_message(res.err))
        }
    }

    async fn fetch_zip(&self, files: &[&String]) -> Result<Bytes> {
        let url = format!("{}downloadFiles", self.endpoint);

        debug!("requesting files: {:?}", files);

        let req = ZipRequest { files };
        let resp = ankiweb_json_request(&self.client, &url, &req, self.skey()).await?;
        resp.bytes().await.map_err(Into::into)
    }

    async fn send_zip_data(&self, data: Vec<u8>) -> Result<()> {
        let url = format!("{}uploadChanges", self.endpoint);

        ankiweb_bytes_request(&self.client, &url, data, self.skey()).await?;

        Ok(())
    }
}

#[allow(clippy::useless_let_if_seq)]
pub async fn sync_media<F>(
    mgr: &mut MediaManager,
    hkey: &str,
    progress_cb: F,
    endpoint: &str,
) -> Result<()>
where
    F: Fn(Progress) -> bool,
{
    let mut sctx = SyncContext::new(mgr, progress_cb, endpoint);

    // make sure media DB is up to date
    register_changes(&mut sctx.ctx, mgr.media_folder.as_path())?;

    let meta = sctx.ctx.get_meta()?;
    let client_usn = meta.last_sync_usn;

    debug!("beginning media sync");
    let (sync_key, server_usn) = sctx.sync_begin(hkey).await?;
    sctx.skey = Some(sync_key);
    debug!("server usn was {}", server_usn);

    let mut actions_performed = false;

    // need to fetch changes from server?
    if client_usn != server_usn {
        debug!("differs from local usn {}, fetching changes", client_usn);
        sctx.fetch_changes(meta).await?;
        actions_performed = true;
    }

    // need to send changes to server?
    let changes_pending = !sctx.ctx.get_pending_uploads(1)?.is_empty();
    if changes_pending {
        sctx.send_changes().await?;
        actions_performed = true;
    }

    if actions_performed {
        sctx.finalize_sync().await?;
    }

    debug!("media sync complete");

    Ok(())
}

#[derive(Debug, Deserialize)]
struct SyncBeginResult {
    data: Option<SyncBeginResponse>,
    err: String,
}

#[derive(Debug, Deserialize)]
struct SyncBeginResponse {
    #[serde(rename = "sk")]
    sync_key: String,
    usn: i32,
}

#[derive(Debug, Clone, Copy)]
enum LocalState {
    NotInDB,
    InDBNotPending,
    InDBAndPending,
}

#[derive(PartialEq, Debug)]
enum RequiredChange {
    // no also covers the case where we'll later upload
    None,
    Download,
    Delete,
    RemovePending,
}

fn determine_required_change(
    local_sha1: &str,
    remote_sha1: &str,
    local_state: LocalState,
) -> RequiredChange {
    use LocalState as L;
    use RequiredChange as R;

    match (local_sha1, remote_sha1, local_state) {
        // both deleted, not in local DB
        ("", "", L::NotInDB) => R::None,
        // both deleted, in local DB
        ("", "", _) => R::Delete,
        // added on server, add even if local deletion pending
        ("", _, _) => R::Download,
        // deleted on server but added locally; upload later
        (_, "", L::InDBAndPending) => R::None,
        // deleted on server and not pending sync
        (_, "", _) => R::Delete,
        // if pending but the same as server, don't need to upload
        (lsum, rsum, L::InDBAndPending) if lsum == rsum => R::RemovePending,
        (lsum, rsum, _) => {
            if lsum == rsum {
                // not pending and same as server, nothing to do
                R::None
            } else {
                // differs from server, favour server
                R::Download
            }
        }
    }
}

/// Get a list of server filenames and the actions required on them.
/// Returns filenames in (to_download, to_delete).
fn determine_required_changes<'a>(
    ctx: &mut MediaDatabaseContext,
    records: &'a [ServerMediaRecord],
) -> Result<(Vec<&'a String>, Vec<&'a String>, Vec<&'a String>)> {
    let mut to_download = vec![];
    let mut to_delete = vec![];
    let mut to_remove_pending = vec![];

    for remote in records {
        let (local_sha1, local_state) = match ctx.get_entry(&remote.fname)? {
            Some(entry) => (
                match entry.sha1 {
                    Some(arr) => hex::encode(arr),
                    None => "".to_string(),
                },
                if entry.sync_required {
                    LocalState::InDBAndPending
                } else {
                    LocalState::InDBNotPending
                },
            ),
            None => ("".to_string(), LocalState::NotInDB),
        };

        let req_change = determine_required_change(&local_sha1, &remote.sha1, local_state);
        debug!(
            "for {}, lsha={} rsha={} lstate={:?} -> {:?}",
            remote.fname,
            local_sha1.chars().take(8).collect::<String>(),
            remote.sha1.chars().take(8).collect::<String>(),
            local_state,
            req_change
        );
        match req_change {
            RequiredChange::Download => to_download.push(&remote.fname),
            RequiredChange::Delete => to_delete.push(&remote.fname),
            RequiredChange::RemovePending => to_remove_pending.push(&remote.fname),
            RequiredChange::None => (),
        };
    }

    Ok((to_download, to_delete, to_remove_pending))
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct RecordBatchRequest {
    last_usn: i32,
}

#[derive(Debug, Deserialize)]
struct RecordBatchResult {
    data: Option<Vec<ServerMediaRecord>>,
    err: String,
}

#[derive(Debug, Deserialize)]
struct ServerMediaRecord {
    fname: String,
    usn: i32,
    sha1: String,
}

async fn ankiweb_json_request<T>(
    client: &Client,
    url: &str,
    json: &T,
    skey: &str,
) -> Result<Response>
where
    T: serde::Serialize,
{
    let req_json = serde_json::to_string(json)?;
    let part = multipart::Part::text(req_json);
    ankiweb_request(client, url, part, skey).await
}

async fn ankiweb_bytes_request(
    client: &Client,
    url: &str,
    bytes: Vec<u8>,
    skey: &str,
) -> Result<Response> {
    let part = multipart::Part::bytes(bytes);
    ankiweb_request(client, url, part, skey).await
}

async fn ankiweb_request(
    client: &Client,
    url: &str,
    data_part: multipart::Part,
    skey: &str,
) -> Result<Response> {
    let data_part = data_part.file_name("data");

    let form = multipart::Form::new()
        .part("data", data_part)
        .text("sk", skey.to_string());

    client
        .post(url)
        .multipart(form)
        .send()
        .await?
        .error_for_status()
        .map_err(Into::into)
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct ZipRequest<'a> {
    files: &'a [&'a String],
}

fn extract_into_media_folder(media_folder: &Path, zip: Bytes) -> Result<Vec<AddedFile>> {
    let reader = io::Cursor::new(zip);
    let mut zip = zip::ZipArchive::new(reader)?;

    let meta_file = zip.by_name("_meta")?;
    let fmap: HashMap<String, String> = serde_json::from_reader(meta_file)?;
    let mut output = Vec::with_capacity(fmap.len());

    for i in 0..zip.len() {
        let mut file = zip.by_index(i)?;
        let name = file.name();
        if name == "_meta" {
            continue;
        }

        let real_name = fmap
            .get(name)
            .ok_or_else(|| AnkiError::sync_misc("malformed zip"))?;

        let mut data = Vec::with_capacity(file.size() as usize);
        file.read_to_end(&mut data)?;

        debug!("writing {}", real_name);

        let added = add_file_from_ankiweb(media_folder, real_name, &data)?;

        output.push(added);
    }

    Ok(output)
}

fn record_removals(ctx: &mut MediaDatabaseContext, removals: &[&String]) -> Result<()> {
    for &fname in removals {
        debug!("marking removed: {}", fname);
        ctx.remove_entry(fname)?;
    }

    Ok(())
}

fn record_additions(ctx: &mut MediaDatabaseContext, additions: Vec<AddedFile>) -> Result<()> {
    for file in additions {
        if let Some(renamed) = file.renamed_from {
            // the file AnkiWeb sent us wasn't normalized, so we need to record
            // the old file name as a deletion
            debug!("marking non-normalized file as deleted: {}", renamed);
            let mut entry = MediaEntry {
                fname: renamed,
                sha1: None,
                mtime: 0,
                sync_required: true,
            };
            ctx.set_entry(&entry)?;
            // and upload the new filename to ankiweb
            debug!("marking renamed file as needing upload: {}", file.fname);
            entry = MediaEntry {
                fname: file.fname.to_string(),
                sha1: Some(file.sha1),
                mtime: file.mtime,
                sync_required: true,
            };
            ctx.set_entry(&entry)?;
        } else {
            // a normal addition
            let entry = MediaEntry {
                fname: file.fname.to_string(),
                sha1: Some(file.sha1),
                mtime: file.mtime,
                sync_required: false,
            };
            debug!(
                "marking added: {} {}",
                entry.fname,
                hex::encode(entry.sha1.as_ref().unwrap())
            );
            ctx.set_entry(&entry)?;
        }
    }

    Ok(())
}

fn record_clean(ctx: &mut MediaDatabaseContext, clean: &[&String]) -> Result<()> {
    for &fname in clean {
        if let Some(mut entry) = ctx.get_entry(fname)? {
            if entry.sync_required {
                entry.sync_required = false;
                debug!("marking clean: {}", entry.fname);
                ctx.set_entry(&entry)?;
            }
        }
    }

    Ok(())
}

#[derive(Serialize_tuple)]
struct UploadEntry<'a> {
    fname: &'a str,
    in_zip_name: Option<String>,
}

fn zip_files(media_folder: &Path, files: &[MediaEntry]) -> Result<Vec<u8>> {
    let buf = vec![];

    let w = std::io::Cursor::new(buf);
    let mut zip = zip::ZipWriter::new(w);

    let options =
        zip::write::FileOptions::default().compression_method(zip::CompressionMethod::Stored);

    let mut accumulated_size = 0;
    let mut entries = vec![];

    for (idx, file) in files.iter().enumerate() {
        if accumulated_size > SYNC_MAX_BYTES {
            break;
        }

        let normalized = normalize_filename(&file.fname);
        if let Cow::Owned(o) = normalized {
            debug!("media check required: {} should be {}", &file.fname, o);
            return Err(media_check_required());
        }

        let file_data = data_for_file(media_folder, &file.fname)?;

        if let Some(data) = &file_data {
            if data.is_empty() {
                return Err(media_check_required());
            }
            if data.len() > SYNC_SINGLE_FILE_MAX_BYTES {
                return Err(media_check_required());
            }
            accumulated_size += data.len();
            zip.start_file(format!("{}", idx), options)?;
            zip.write_all(data)?;
        }

        debug!(
            "will upload {} as {}",
            file.fname,
            if file_data.is_some() {
                "addition "
            } else {
                "removal"
            }
        );

        entries.push(UploadEntry {
            fname: &file.fname,
            in_zip_name: if file_data.is_some() {
                Some(format!("{}", idx))
            } else {
                None
            },
        });
    }

    let meta = serde_json::to_string(&entries)?;
    zip.start_file("_meta", options)?;
    zip.write_all(meta.as_bytes())?;

    let w = zip.finish()?;

    Ok(w.into_inner())
}

fn version_string() -> String {
    format!("anki,{},{}", version(), std::env::consts::OS)
}

fn media_check_required() -> AnkiError {
    AnkiError::SyncError {
        info: "".into(),
        kind: SyncErrorKind::MediaCheckRequired,
    }
}

#[derive(Serialize)]
struct FinalizeRequest {
    local: u32,
}

#[derive(Debug, Deserialize)]
struct FinalizeResponse {
    data: Option<String>,
    err: String,
}

#[cfg(test)]
mod test {
    use crate::err::Result;
    use crate::media::sync::{determine_required_change, sync_media, LocalState, RequiredChange};
    use crate::media::MediaManager;
    use tempfile::tempdir;
    use tokio::runtime::Runtime;

    async fn test_sync(hkey: &str) -> Result<()> {
        let dir = tempdir()?;
        let media_dir = dir.path().join("media");
        std::fs::create_dir(&media_dir)?;
        let media_db = dir.path().join("media.db");

        std::fs::write(media_dir.join("test.file").as_path(), "hello")?;

        let progress = |progress| {
            println!("got progress: {:?}", progress);
            true
        };

        let mut mgr = MediaManager::new(&media_dir, &media_db)?;

        sync_media(&mut mgr, hkey, progress, "https://sync.ankiweb.net/msync/").await?;

        Ok(())
    }

    #[test]
    fn sync() {
        env_logger::init();

        let hkey = match std::env::var("TEST_HKEY") {
            Ok(s) => s,
            Err(_) => {
                return;
            }
        };

        let mut rt = Runtime::new().unwrap();
        rt.block_on(test_sync(&hkey)).unwrap()
    }

    #[test]
    fn required_change() {
        use determine_required_change as d;
        use LocalState as L;
        use RequiredChange as R;
        assert_eq!(d("", "", L::NotInDB), R::None);
        assert_eq!(d("", "", L::InDBNotPending), R::Delete);
        assert_eq!(d("", "1", L::InDBAndPending), R::Download);
        assert_eq!(d("1", "", L::InDBAndPending), R::None);
        assert_eq!(d("1", "", L::InDBNotPending), R::Delete);
        assert_eq!(d("1", "1", L::InDBNotPending), R::None);
        assert_eq!(d("1", "1", L::InDBAndPending), R::RemovePending);
        assert_eq!(d("a", "b", L::InDBAndPending), R::Download);
        assert_eq!(d("a", "b", L::InDBNotPending), R::Download);
    }
}
