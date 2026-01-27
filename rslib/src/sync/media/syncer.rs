// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::sync::Arc;

use futures::stream;
use futures::StreamExt;
use tokio::sync::Semaphore;
use tracing::debug;
use version::sync_client_version;

use crate::error::AnkiError;
use crate::error::Result;
use crate::error::SyncErrorKind;
use crate::media::files::mtime_as_i64;
use crate::media::files::AddedFile;
use crate::media::MediaManager;
use crate::prelude::*;
use crate::progress::ThrottlingProgressHandler;
use crate::sync::http_client::HttpSyncClient;
use crate::sync::media::begin::SyncBeginRequest;
use crate::sync::media::begin::SyncBeginResponse;
use crate::sync::media::changes;
use crate::sync::media::changes::MediaChangesRequest;
use crate::sync::media::database::client::changetracker::ChangeTracker;
use crate::sync::media::database::client::MediaDatabaseMetadata;
use crate::sync::media::database::client::MediaEntry;
use crate::sync::media::download;
use crate::sync::media::download::DownloadFilesRequest;
use crate::sync::media::progress::MediaSyncProgress;
use crate::sync::media::protocol::MediaSyncProtocol;
use crate::sync::media::sanity::MediaSanityCheckResponse;
use crate::sync::media::sanity::SanityCheckRequest;
use crate::sync::media::upload::gather_zip_data_for_upload;
use crate::sync::media::zip::zip_files_for_upload;
use crate::sync::media::MAX_MEDIA_FILES_IN_ZIP;
use crate::sync::media::SYNC_CONCURRENT_BATCHES;
use crate::sync::request::IntoSyncRequest;
use crate::version;

pub struct MediaSyncer {
    mgr: MediaManager,
    client: HttpSyncClient,
    progress: ThrottlingProgressHandler<MediaSyncProgress>,
}

impl MediaSyncer {
    pub fn new(
        mgr: MediaManager,
        progress: ThrottlingProgressHandler<MediaSyncProgress>,
        client: HttpSyncClient,
    ) -> Result<MediaSyncer> {
        Ok(MediaSyncer {
            mgr,
            client,
            progress,
        })
    }

    pub async fn sync(&mut self, server_usn: Option<Usn>) -> Result<()> {
        self.sync_inner(server_usn).await.map_err(|e| {
            debug!("sync error: {:?}", e);
            e
        })
    }

    #[allow(clippy::useless_let_if_seq)]
    async fn sync_inner(&mut self, server_usn: Option<Usn>) -> Result<()> {
        self.register_changes()?;

        let meta = self.mgr.db.get_meta()?;
        let client_usn = meta.last_sync_usn;
        let server_usn = if let Some(usn) = server_usn {
            usn
        } else {
            self.begin_sync().await?
        };

        let mut actions_performed = false;

        // need to fetch changes from server?
        if client_usn != server_usn {
            debug!("differs from local usn {}, fetching changes", client_usn);
            self.fetch_changes(meta).await?;
            actions_performed = true;
        }

        // need to send changes to server?
        let changes_pending = !self.mgr.db.get_pending_uploads(1)?.is_empty();
        if changes_pending {
            self.send_changes().await?;
            actions_performed = true;
        }

        if actions_performed {
            self.finalize_sync().await?;
        }

        debug!("media sync complete");

        Ok(())
    }

    async fn begin_sync(&mut self) -> Result<Usn> {
        debug!("begin media sync");
        let SyncBeginResponse {
            host_key: _,
            usn: server_usn,
        } = self
            .client
            .begin(
                SyncBeginRequest {
                    client_version: sync_client_version().into(),
                }
                .try_into_sync_request()?,
            )
            .await?
            .json_result()?;

        debug!("server usn was {}", server_usn);
        Ok(server_usn)
    }

    /// Make sure media DB is up to date.
    fn register_changes(&mut self) -> Result<()> {
        let progress_cb = |checked| self.progress.update(true, |p| p.checked = checked).is_ok();
        ChangeTracker::new(self.mgr.media_folder.as_path(), progress_cb)
            .register_changes(&self.mgr.db)
    }

    async fn fetch_changes(&mut self, mut meta: MediaDatabaseMetadata) -> Result<()> {
        let mut last_usn = meta.last_sync_usn;
        loop {
            debug!(start_usn = ?last_usn, "fetching record batch");

            let batch = self
                .client
                .media_changes(MediaChangesRequest { last_usn }.try_into_sync_request()?)
                .await?
                .json_result()?;
            if batch.is_empty() {
                debug!("empty batch, done");
                break;
            }
            last_usn = batch.last().unwrap().usn;

            self.progress.update(false, |p| p.checked += batch.len())?;

            let (to_download, to_delete, to_remove_pending) =
                changes::determine_required_changes(&self.mgr.db, batch)?;

            // file removal
            self.mgr.remove_files(to_delete.as_slice())?;
            self.progress
                .update(false, |p| p.downloaded_deletions += to_delete.len())?;

            // file download - concurrent batches
            let mut downloaded = vec![];
            if !to_download.is_empty() {
                let concurrent_batches = *SYNC_CONCURRENT_BATCHES;

                // When concurrent_batches is 1, maintain original serial behavior for compatibility
                if concurrent_batches <= 1 {
                    let mut dl_fnames = to_download.as_slice();
                    while !dl_fnames.is_empty() {
                        let batch: Vec<_> = dl_fnames
                            .iter()
                            .take(*MAX_MEDIA_FILES_IN_ZIP)
                            .map(ToOwned::to_owned)
                            .collect();
                        let zip_data = self
                            .client
                            .download_files(
                                DownloadFilesRequest { files: batch }.try_into_sync_request()?,
                            )
                            .await?
                            .data;
                        let download_batch = download::extract_into_media_folder(
                            self.mgr.media_folder.as_path(),
                            zip_data,
                        )?;
                        let len = download_batch.len();
                        dl_fnames = &dl_fnames[len..];
                        downloaded.extend(download_batch);
                        self.progress.update(false, |p| p.downloaded_files += len)?;
                    }
                } else {
                    // Concurrent download mode
                    let batches: Vec<Vec<String>> = to_download
                        .chunks(*MAX_MEDIA_FILES_IN_ZIP)
                        .map(|chunk| chunk.to_vec())
                        .collect();

                    let semaphore = Arc::new(Semaphore::new(concurrent_batches));
                    let client = self.client.clone();
                    let media_folder = self.mgr.media_folder.clone();

                    let results: Vec<Result<Vec<AddedFile>>> = stream::iter(batches)
                        .map(|batch| {
                            let sem = semaphore.clone();
                            let client = client.clone();
                            let media_folder = media_folder.clone();
                            async move {
                                let _permit =
                                    sem.acquire().await.map_err(|_| AnkiError::Interrupted)?;
                                let zip_data = client
                                    .download_files(
                                        DownloadFilesRequest { files: batch }
                                            .try_into_sync_request()?,
                                    )
                                    .await?
                                    .data;
                                download::extract_into_media_folder(
                                    media_folder.as_path(),
                                    zip_data,
                                )
                            }
                        })
                        .buffer_unordered(concurrent_batches)
                        .collect()
                        .await;

                    for result in results {
                        let batch_downloaded = result?;
                        let len = batch_downloaded.len();
                        downloaded.extend(batch_downloaded);
                        self.progress.update(false, |p| p.downloaded_files += len)?;
                    }
                }
            }

            // then update the DB
            let dirmod = mtime_as_i64(&self.mgr.media_folder)?;
            self.mgr.db.transact(|ctx| {
                ctx.record_clean(&to_remove_pending)?;
                ctx.record_removals(&to_delete)?;
                ctx.record_additions(downloaded)?;

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
            let pending: Vec<MediaEntry> = self
                .mgr
                .db
                .get_pending_uploads(*MAX_MEDIA_FILES_IN_ZIP as u32)?;
            if pending.is_empty() {
                break;
            }

            let data_for_zip =
                gather_zip_data_for_upload(&self.mgr.db, &self.mgr.media_folder, &pending)?;
            let zip_bytes = match data_for_zip {
                None => {
                    // discard zip info and retry batch - not particularly efficient,
                    // but this is a corner case
                    self.progress
                        .update(false, |p| p.checked += pending.len())?;
                    continue;
                }
                Some(data) => zip_files_for_upload(data)?,
            };

            let reply = self
                .client
                .upload_changes(zip_bytes.try_into_sync_request()?)
                .await?
                .json_result()?;

            let (processed_files, processed_deletions): (Vec<_>, Vec<_>) = pending
                .into_iter()
                .take(reply.processed)
                .partition(|e| e.sha1.is_some());

            self.progress.update(false, |p| {
                p.uploaded_files += processed_files.len();
                p.uploaded_deletions += processed_deletions.len();
            })?;

            let fnames: Vec<_> = processed_files
                .into_iter()
                .chain(processed_deletions.into_iter())
                .map(|e| e.fname)
                .collect();
            let fname_cnt = fnames.len() as i32;
            self.mgr.db.transact(|ctx| {
                ctx.record_clean(fnames.as_slice())?;
                let mut meta = ctx.get_meta()?;
                if meta.last_sync_usn.0 + fname_cnt == reply.current_usn.0 {
                    meta.last_sync_usn = reply.current_usn;
                    ctx.set_meta(&meta)?;
                } else {
                    debug!(
                        "server usn {} is not {}, skipping usn update",
                        reply.current_usn,
                        meta.last_sync_usn.0 + fname_cnt
                    );
                }
                Ok(())
            })?;
        }

        Ok(())
    }

    async fn finalize_sync(&mut self) -> Result<()> {
        let local = self.mgr.db.count()?;
        let msg = self
            .client
            .media_sanity_check(SanityCheckRequest { local }.try_into_sync_request()?)
            .await?
            .json_result()?;
        if msg == MediaSanityCheckResponse::Ok {
            Ok(())
        } else {
            self.mgr.db.transact(|ctx| ctx.force_resync())?;
            Err(AnkiError::sync_error("", SyncErrorKind::ResyncRequired))
        }
    }
}
