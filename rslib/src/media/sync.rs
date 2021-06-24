// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    borrow::Cow,
    collections::HashMap,
    io,
    io::{Read, Write},
    path::Path,
    time,
};

use bytes::Bytes;
use reqwest::{multipart, Client, Response};
use serde_derive::{Deserialize, Serialize};
use serde_tuple::Serialize_tuple;
use slog::{debug, Logger};
use time::Duration;
use version::sync_client_version;

use crate::{
    error::{AnkiError, Result, SyncErrorKind},
    media::{
        changetracker::ChangeTracker,
        database::{MediaDatabaseContext, MediaDatabaseMetadata, MediaEntry},
        files::{
            add_file_from_ankiweb, data_for_file, mtime_as_i64, normalize_filename, AddedFile,
        },
        MediaManager,
    },
    sync::Timeouts,
    version,
};

static SYNC_MAX_FILES: usize = 25;
static SYNC_MAX_BYTES: usize = (2.5 * 1024.0 * 1024.0) as usize;
static SYNC_SINGLE_FILE_MAX_BYTES: usize = 100 * 1024 * 1024;

#[derive(Debug, Default, Clone, Copy)]
pub struct MediaSyncProgress {
    pub checked: usize,
    pub downloaded_files: usize,
    pub downloaded_deletions: usize,
    pub uploaded_files: usize,
    pub uploaded_deletions: usize,
}

pub struct MediaSyncer<'a, P>
where
    P: FnMut(MediaSyncProgress) -> bool,
{
    mgr: &'a MediaManager,
    ctx: MediaDatabaseContext<'a>,
    skey: Option<String>,
    client: Client,
    progress_cb: P,
    progress: MediaSyncProgress,
    endpoint: String,
    log: Logger,
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
    NotInDb,
    InDbNotPending,
    InDbAndPending,
}

#[derive(PartialEq, Debug)]
enum RequiredChange {
    // none also covers the case where we'll later upload
    None,
    Download,
    Delete,
    RemovePending,
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

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct ZipRequest<'a> {
    files: &'a [&'a String],
}

#[derive(Serialize_tuple)]
struct UploadEntry<'a> {
    fname: &'a str,
    in_zip_name: Option<String>,
}

#[derive(Deserialize, Debug)]
struct UploadResult {
    data: Option<UploadReply>,
    err: String,
}

#[derive(Deserialize, Debug)]
struct UploadReply {
    processed: usize,
    current_usn: i32,
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

fn media_sync_endpoint(host_number: u32) -> String {
    if let Ok(endpoint) = std::env::var("SYNC_ENDPOINT_MEDIA") {
        endpoint
    } else {
        let suffix = if host_number > 0 {
            format!("{}", host_number)
        } else {
            "".to_string()
        };
        format!("https://sync{}.ankiweb.net/msync/", suffix)
    }
}

impl<P> MediaSyncer<'_, P>
where
    P: FnMut(MediaSyncProgress) -> bool,
{
    pub fn new(
        mgr: &MediaManager,
        progress_cb: P,
        host_number: u32,
        log: Logger,
    ) -> MediaSyncer<'_, P> {
        let timeouts = Timeouts::new();
        let client = Client::builder()
            .connect_timeout(Duration::from_secs(timeouts.connect_secs))
            .timeout(Duration::from_secs(timeouts.request_secs))
            .io_timeout(Duration::from_secs(timeouts.io_secs))
            .build()
            .unwrap();
        let endpoint = media_sync_endpoint(host_number);
        let ctx = mgr.dbctx();
        MediaSyncer {
            mgr,
            ctx,
            skey: None,
            client,
            progress_cb,
            progress: Default::default(),
            endpoint,
            log,
        }
    }

    fn skey(&self) -> &str {
        self.skey.as_ref().unwrap()
    }

    pub async fn sync(&mut self, hkey: &str) -> Result<()> {
        self.sync_inner(hkey).await.map_err(|e| {
            debug!(self.log, "sync error: {:?}", e);
            e
        })
    }

    #[allow(clippy::useless_let_if_seq)]
    async fn sync_inner(&mut self, hkey: &str) -> Result<()> {
        self.register_changes()?;

        let meta = self.ctx.get_meta()?;
        let client_usn = meta.last_sync_usn;

        debug!(self.log, "begin media sync");
        let (sync_key, server_usn) = self.sync_begin(hkey).await?;
        self.skey = Some(sync_key);
        debug!(self.log, "server usn was {}", server_usn);

        let mut actions_performed = false;

        // need to fetch changes from server?
        if client_usn != server_usn {
            debug!(
                self.log,
                "differs from local usn {}, fetching changes", client_usn
            );
            self.fetch_changes(meta).await?;
            actions_performed = true;
        }

        // need to send changes to server?
        let changes_pending = !self.ctx.get_pending_uploads(1)?.is_empty();
        if changes_pending {
            self.send_changes().await?;
            actions_performed = true;
        }

        if actions_performed {
            self.finalize_sync().await?;
        }

        self.fire_progress_cb()?;

        debug!(self.log, "media sync complete");

        Ok(())
    }

    /// Make sure media DB is up to date.
    fn register_changes(&mut self) -> Result<()> {
        // make borrow checker happy
        let progress = &mut self.progress;
        let progress_cb = &mut self.progress_cb;

        let progress = |checked| {
            progress.checked = checked;
            (progress_cb)(*progress)
        };

        ChangeTracker::new(self.mgr.media_folder.as_path(), progress, &self.log)
            .register_changes(&mut self.ctx)
    }

    async fn sync_begin(&self, hkey: &str) -> Result<(String, i32)> {
        let url = format!("{}begin", self.endpoint);

        let resp = self
            .client
            .get(&url)
            .query(&[("k", hkey), ("v", sync_client_version())])
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
            debug!(
                self.log,
                "fetching record batch";
                 "start_usn"=>last_usn
            );

            let batch = self.fetch_record_batch(last_usn).await?;
            if batch.is_empty() {
                debug!(self.log, "empty batch, done");
                break;
            }
            last_usn = batch.last().unwrap().usn;

            self.progress.checked += batch.len();
            self.fire_progress_cb()?;

            let (to_download, to_delete, to_remove_pending) =
                determine_required_changes(&mut self.ctx, &batch, &self.log)?;

            // file removal
            self.mgr.remove_files(&mut self.ctx, to_delete.as_slice())?;
            self.progress.downloaded_deletions += to_delete.len();
            self.fire_progress_cb()?;

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
                let download_batch = extract_into_media_folder(
                    self.mgr.media_folder.as_path(),
                    zip_data,
                    &self.log,
                )?
                .into_iter();
                let len = download_batch.len();
                dl_fnames = &dl_fnames[len..];
                downloaded.extend(download_batch);

                self.progress.downloaded_files += len;
                self.fire_progress_cb()?;
            }

            // then update the DB
            let dirmod = mtime_as_i64(&self.mgr.media_folder)?;
            let log = &self.log;
            self.ctx.transact(|ctx| {
                record_clean(ctx, &to_remove_pending, log)?;
                record_removals(ctx, &to_delete, log)?;
                record_additions(ctx, downloaded, log)?;

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

            let zip_data = zip_files(&mut self.ctx, &self.mgr.media_folder, &pending, &self.log)?;
            if zip_data.is_none() {
                self.progress.checked += pending.len();
                self.fire_progress_cb()?;
                // discard zip info and retry batch - not particularly efficient,
                // but this is a corner case
                continue;
            }

            let reply = self.send_zip_data(zip_data.unwrap()).await?;

            let (processed_files, processed_deletions): (Vec<_>, Vec<_>) = pending
                .iter()
                .take(reply.processed)
                .partition(|e| e.sha1.is_some());

            self.progress.uploaded_files += processed_files.len();
            self.progress.uploaded_deletions += processed_deletions.len();
            self.fire_progress_cb()?;

            let fnames: Vec<_> = processed_files
                .iter()
                .chain(processed_deletions.iter())
                .map(|e| &e.fname)
                .collect();
            let fname_cnt = fnames.len() as i32;
            let log = &self.log;
            self.ctx.transact(|ctx| {
                record_clean(ctx, fnames.as_slice(), log)?;
                let mut meta = ctx.get_meta()?;
                if meta.last_sync_usn + fname_cnt == reply.current_usn {
                    meta.last_sync_usn = reply.current_usn;
                    ctx.set_meta(&meta)?;
                } else {
                    debug!(
                        log,
                        "server usn {} is not {}, skipping usn update",
                        reply.current_usn,
                        meta.last_sync_usn + fname_cnt
                    );
                }
                Ok(())
            })?;
        }

        Ok(())
    }

    async fn finalize_sync(&mut self) -> Result<()> {
        let url = format!("{}mediaSanity", self.endpoint);
        let local = self.ctx.count()?;

        let obj = FinalizeRequest { local };
        let resp = ankiweb_json_request(&self.client, &url, &obj, self.skey(), false).await?;
        let resp: FinalizeResponse = resp.json().await?;

        if let Some(data) = resp.data {
            if data == "OK" {
                Ok(())
            } else {
                self.ctx.transact(|ctx| ctx.force_resync())?;
                Err(AnkiError::sync_error("", SyncErrorKind::ResyncRequired))
            }
        } else {
            Err(AnkiError::server_message(resp.err))
        }
    }

    fn fire_progress_cb(&mut self) -> Result<()> {
        if (self.progress_cb)(self.progress) {
            Ok(())
        } else {
            Err(AnkiError::Interrupted)
        }
    }

    async fn fetch_record_batch(&self, last_usn: i32) -> Result<Vec<ServerMediaRecord>> {
        let url = format!("{}mediaChanges", self.endpoint);

        let req = RecordBatchRequest { last_usn };
        let resp = ankiweb_json_request(&self.client, &url, &req, self.skey(), false).await?;
        let res: RecordBatchResult = resp.json().await?;

        if let Some(batch) = res.data {
            Ok(batch)
        } else {
            Err(AnkiError::server_message(res.err))
        }
    }

    async fn fetch_zip(&self, files: &[&String]) -> Result<Bytes> {
        let url = format!("{}downloadFiles", self.endpoint);

        debug!(self.log, "requesting files: {:?}", files);

        let req = ZipRequest { files };
        let resp = ankiweb_json_request(&self.client, &url, &req, self.skey(), true).await?;
        resp.bytes().await.map_err(Into::into)
    }

    async fn send_zip_data(&self, data: Vec<u8>) -> Result<UploadReply> {
        let url = format!("{}uploadChanges", self.endpoint);

        let resp = ankiweb_bytes_request(&self.client, &url, data, self.skey(), true).await?;
        let res: UploadResult = resp.json().await?;

        if let Some(reply) = res.data {
            Ok(reply)
        } else {
            Err(AnkiError::server_message(res.err))
        }
    }
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
        ("", "", L::NotInDb) => R::None,
        // both deleted, in local DB
        ("", "", _) => R::Delete,
        // added on server, add even if local deletion pending
        ("", _, _) => R::Download,
        // deleted on server but added locally; upload later
        (_, "", L::InDbAndPending) => R::None,
        // deleted on server and not pending sync
        (_, "", _) => R::Delete,
        // if pending but the same as server, don't need to upload
        (lsum, rsum, L::InDbAndPending) if lsum == rsum => R::RemovePending,
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
    log: &Logger,
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
                    LocalState::InDbAndPending
                } else {
                    LocalState::InDbNotPending
                },
            ),
            None => ("".to_string(), LocalState::NotInDb),
        };

        let req_change = determine_required_change(&local_sha1, &remote.sha1, local_state);
        debug!(
            log,
            "determine action";
            "fname" => &remote.fname,
            "lsha" => local_sha1.chars().take(8).collect::<String>(),
            "rsha" => remote.sha1.chars().take(8).collect::<String>(),
            "state" => ?local_state,
            "action" => ?req_change
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

async fn ankiweb_json_request<T>(
    client: &Client,
    url: &str,
    json: &T,
    skey: &str,
    timeout_long: bool,
) -> Result<Response>
where
    T: serde::Serialize,
{
    let req_json = serde_json::to_string(json)?;
    let part = multipart::Part::text(req_json);
    ankiweb_request(client, url, part, skey, timeout_long).await
}

async fn ankiweb_bytes_request(
    client: &Client,
    url: &str,
    bytes: Vec<u8>,
    skey: &str,
    timeout_long: bool,
) -> Result<Response> {
    let part = multipart::Part::bytes(bytes);
    ankiweb_request(client, url, part, skey, timeout_long).await
}

async fn ankiweb_request(
    client: &Client,
    url: &str,
    data_part: multipart::Part,
    skey: &str,
    timeout_long: bool,
) -> Result<Response> {
    let data_part = data_part.file_name("data");

    let form = multipart::Form::new()
        .part("data", data_part)
        .text("sk", skey.to_string());

    let mut req = client.post(url).multipart(form);

    if timeout_long {
        req = req.timeout(Duration::from_secs(60 * 60));
    }

    req.send().await?.error_for_status().map_err(Into::into)
}

fn extract_into_media_folder(
    media_folder: &Path,
    zip: Bytes,
    log: &Logger,
) -> Result<Vec<AddedFile>> {
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
            .ok_or_else(|| AnkiError::sync_error("malformed zip", SyncErrorKind::Other))?;

        let mut data = Vec::with_capacity(file.size() as usize);
        file.read_to_end(&mut data)?;

        let added = add_file_from_ankiweb(media_folder, real_name, &data, log)?;

        output.push(added);
    }

    Ok(output)
}

fn record_removals(
    ctx: &mut MediaDatabaseContext,
    removals: &[&String],
    log: &Logger,
) -> Result<()> {
    for &fname in removals {
        debug!(log, "mark removed"; "fname" => fname);
        ctx.remove_entry(fname)?;
    }

    Ok(())
}

fn record_additions(
    ctx: &mut MediaDatabaseContext,
    additions: Vec<AddedFile>,
    log: &Logger,
) -> Result<()> {
    for file in additions {
        if let Some(renamed) = file.renamed_from {
            // the file AnkiWeb sent us wasn't normalized, so we need to record
            // the old file name as a deletion
            debug!(log, "marking non-normalized file as deleted: {}", renamed);
            let mut entry = MediaEntry {
                fname: renamed,
                sha1: None,
                mtime: 0,
                sync_required: true,
            };
            ctx.set_entry(&entry)?;
            // and upload the new filename to ankiweb
            debug!(
                log,
                "marking renamed file as needing upload: {}", file.fname
            );
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
            debug!(log, "mark added";
                "fname" => &entry.fname,
                "sha1" => hex::encode(&entry.sha1.as_ref().unwrap()[0..4])
            );
            ctx.set_entry(&entry)?;
        }
    }

    Ok(())
}

fn record_clean(ctx: &mut MediaDatabaseContext, clean: &[&String], log: &Logger) -> Result<()> {
    for &fname in clean {
        if let Some(mut entry) = ctx.get_entry(fname)? {
            if entry.sync_required {
                entry.sync_required = false;
                debug!(log, "mark clean"; "fname"=>&entry.fname);
                ctx.set_entry(&entry)?;
            }
        }
    }

    Ok(())
}

fn zip_files<'a>(
    ctx: &mut MediaDatabaseContext,
    media_folder: &Path,
    files: &'a [MediaEntry],
    log: &Logger,
) -> Result<Option<Vec<u8>>> {
    let buf = vec![];
    let mut invalid_entries = vec![];

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

        #[cfg(target_vendor = "apple")]
        {
            use unicode_normalization::is_nfc;
            if !is_nfc(&file.fname) {
                // older Anki versions stored non-normalized filenames in the DB; clean them up
                debug!(log, "clean up non-nfc entry"; "fname"=>&file.fname);
                invalid_entries.push(&file.fname);
                continue;
            }
        }

        let file_data = if file.sha1.is_some() {
            match data_for_file(media_folder, &file.fname) {
                Ok(data) => data,
                Err(e) => {
                    debug!(log, "error accessing {}: {}", &file.fname, e);
                    invalid_entries.push(&file.fname);
                    continue;
                }
            }
        } else {
            // uploading deletion
            None
        };

        if let Some(data) = &file_data {
            let normalized = normalize_filename(&file.fname);
            if let Cow::Owned(o) = normalized {
                debug!(log, "media check required: {} should be {}", &file.fname, o);
                invalid_entries.push(&file.fname);
                continue;
            }

            if data.is_empty() {
                invalid_entries.push(&file.fname);
                continue;
            }
            if data.len() > SYNC_SINGLE_FILE_MAX_BYTES {
                invalid_entries.push(&file.fname);
                continue;
            }
            accumulated_size += data.len();
            zip.start_file(format!("{}", idx), options)?;
            zip.write_all(data)?;
        }

        debug!(
            log,
            "will upload";
             "fname"=>&file.fname,
             "kind"=>if file_data.is_some() {
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

    if !invalid_entries.is_empty() {
        // clean up invalid entries; we'll build a new zip
        ctx.transact(|ctx| {
            for fname in invalid_entries {
                ctx.remove_entry(fname)?;
            }
            Ok(())
        })?;
        return Ok(None);
    }

    let meta = serde_json::to_string(&entries)?;
    zip.start_file("_meta", options)?;
    zip.write_all(meta.as_bytes())?;

    let w = zip.finish()?;

    Ok(Some(w.into_inner()))
}

#[cfg(test)]
mod test {
    use tempfile::tempdir;
    use tokio::runtime::Runtime;

    use crate::{
        error::Result,
        media::{
            sync::{determine_required_change, LocalState, MediaSyncProgress, RequiredChange},
            MediaManager,
        },
    };

    async fn test_sync(hkey: &str) -> Result<()> {
        let dir = tempdir()?;
        let media_dir = dir.path().join("media");
        std::fs::create_dir(&media_dir)?;
        let media_db = dir.path().join("media.db");

        std::fs::write(media_dir.join("test.file").as_path(), "hello")?;

        let progress = |progress: MediaSyncProgress| {
            println!("got progress: {:?}", progress);
            true
        };

        let log = crate::log::terminal();

        let mgr = MediaManager::new(&media_dir, &media_db)?;
        mgr.sync_media(progress, 0, hkey, log).await?;

        Ok(())
    }

    #[test]
    fn sync() {
        let hkey = match std::env::var("TEST_HKEY") {
            Ok(s) => s,
            Err(_) => {
                return;
            }
        };

        let rt = Runtime::new().unwrap();
        rt.block_on(test_sync(&hkey)).unwrap()
    }

    #[test]
    fn required_change() {
        use determine_required_change as d;
        use LocalState as L;
        use RequiredChange as R;
        assert_eq!(d("", "", L::NotInDb), R::None);
        assert_eq!(d("", "", L::InDbNotPending), R::Delete);
        assert_eq!(d("", "1", L::InDbAndPending), R::Download);
        assert_eq!(d("1", "", L::InDbAndPending), R::None);
        assert_eq!(d("1", "", L::InDbNotPending), R::Delete);
        assert_eq!(d("1", "1", L::InDbNotPending), R::None);
        assert_eq!(d("1", "1", L::InDbAndPending), R::RemovePending);
        assert_eq!(d("a", "b", L::InDbAndPending), R::Download);
        assert_eq!(d("a", "b", L::InDbNotPending), R::Download);
    }
}
