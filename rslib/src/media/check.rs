// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::err::{AnkiError, Result};
use crate::media::database::MediaDatabaseContext;
use crate::media::files::{
    data_for_file, filename_if_normalized, remove_files, MEDIA_SYNC_FILESIZE_LIMIT,
};
use crate::media::MediaManager;
use coarsetime::Instant;
use log::debug;
use std::borrow::Cow;

#[derive(Debug, PartialEq)]
pub struct MediaCheckOutput {
    files: Vec<String>,
    renamed: Vec<RenamedFile>,
    dirs: Vec<String>,
    oversize: Vec<String>,
}

/// A file that was renamed due to invalid chars or non-NFC encoding.
/// On Apple computers, files in NFD format are not renamed.
#[derive(Debug, PartialEq)]
pub struct RenamedFile {
    current_fname: String,
    original_fname: String,
}

pub struct MediaChecker<'a, P>
where
    P: FnMut(usize) -> bool,
{
    mgr: &'a MediaManager,
    progress_cb: P,
    checked: usize,
    progress_updated: Instant,
}

impl<P> MediaChecker<'_, P>
where
    P: FnMut(usize) -> bool,
{
    pub fn new(mgr: &MediaManager, progress_cb: P) -> MediaChecker<'_, P> {
        MediaChecker {
            mgr,
            progress_cb,
            checked: 0,
            progress_updated: Instant::now(),
        }
    }

    pub fn check(&mut self) -> Result<MediaCheckOutput> {
        // loop through on-disk files
        let mut dirs = vec![];
        let mut oversize = vec![];
        let mut all_files = vec![];
        let mut renamed_files = vec![];
        let mut ctx = self.mgr.dbctx();
        for dentry in self.mgr.media_folder.read_dir()? {
            let dentry = dentry?;

            self.checked += 1;
            if self.checked % 10 == 0 {
                self.maybe_fire_progress_cb()?;
            }

            // if the filename is not valid unicode, skip it
            let fname_os = dentry.file_name();
            let disk_fname = match fname_os.to_str() {
                Some(s) => s,
                None => continue,
            };

            // skip folders
            if dentry.file_type()?.is_dir() {
                dirs.push(disk_fname.to_string());
                continue;
            }

            // ignore large files and zero byte files
            let metadata = dentry.metadata()?;
            if metadata.len() > MEDIA_SYNC_FILESIZE_LIMIT as u64 {
                oversize.push(disk_fname.to_string());
                continue;
            }
            if metadata.len() == 0 {
                continue;
            }

            // rename if required
            let (norm_name, renamed) = self.normalize_and_maybe_rename(&mut ctx, &disk_fname)?;
            if renamed {
                renamed_files.push(RenamedFile {
                    current_fname: norm_name.to_string(),
                    original_fname: disk_fname.to_string(),
                })
            }

            all_files.push(norm_name.into_owned());
        }

        Ok(MediaCheckOutput {
            files: all_files,
            renamed: renamed_files,
            dirs,
            oversize,
        })
    }

    /// Returns (normalized_form, needs_rename)
    fn normalize_and_maybe_rename<'a>(
        &mut self,
        ctx: &mut MediaDatabaseContext,
        disk_fname: &'a str,
    ) -> Result<(Cow<'a, str>, bool)> {
        // already normalized?
        if let Some(fname) = filename_if_normalized(disk_fname) {
            return Ok((fname, false));
        }

        // add a copy of the file using the correct name
        let data = data_for_file(&self.mgr.media_folder, disk_fname)?.ok_or_else(|| {
            AnkiError::IOError {
                info: "file disappeared".into(),
            }
        })?;
        let fname = self.mgr.add_file(ctx, disk_fname, &data)?;
        debug!("renamed {} to {}", disk_fname, fname);
        assert_ne!(fname.as_ref(), disk_fname);

        // move the originally named file to the trash
        remove_files(&self.mgr.media_folder, &[disk_fname])?;

        Ok((fname, true))
    }

    fn fire_progress_cb(&mut self) -> Result<()> {
        if (self.progress_cb)(self.checked) {
            Ok(())
        } else {
            Err(AnkiError::Interrupted)
        }
    }

    fn maybe_fire_progress_cb(&mut self) -> Result<()> {
        let now = Instant::now();
        if now.duration_since(self.progress_updated).as_secs() < 1 {
            return Ok(());
        }
        self.progress_updated = now;
        self.fire_progress_cb()
    }
}

#[cfg(test)]
mod test {
    use crate::err::Result;
    use crate::media::check::{MediaCheckOutput, MediaChecker, RenamedFile};
    use crate::media::MediaManager;
    use std::fs;
    use tempfile::{tempdir, TempDir};

    fn common_setup() -> Result<(TempDir, MediaManager)> {
        let dir = tempdir()?;
        let media_dir = dir.path().join("media");
        fs::create_dir(&media_dir)?;
        let media_db = dir.path().join("media.db");

        let mgr = MediaManager::new(&media_dir, media_db)?;

        Ok((dir, mgr))
    }

    #[test]
    fn test_media_check() -> Result<()> {
        let (_dir, mgr) = common_setup()?;

        // add some test files
        fs::write(&mgr.media_folder.join("zerobytes"), "")?;
        fs::create_dir(&mgr.media_folder.join("folder"))?;
        fs::write(&mgr.media_folder.join("normal.jpg"), "normal")?;
        fs::write(&mgr.media_folder.join("con.jpg"), "con")?;

        let progress = |_n| true;
        let mut checker = MediaChecker::new(&mgr, progress);
        let output = checker.check()?;

        assert_eq!(
            output,
            MediaCheckOutput {
                files: vec!["con_.jpg".to_string(), "normal.jpg".to_string()],
                renamed: vec![RenamedFile {
                    current_fname: "con_.jpg".to_string(),
                    original_fname: "con.jpg".to_string()
                }],
                dirs: vec!["folder".to_string()],
                oversize: vec![]
            }
        );

        assert!(fs::metadata(&mgr.media_folder.join("con.jpg")).is_err());
        assert!(fs::metadata(&mgr.media_folder.join("con_.jpg")).is_ok());

        Ok(())
    }

    #[test]
    fn test_unicode_normalization() -> Result<()> {
        let (_dir, mgr) = common_setup()?;

        fs::write(&mgr.media_folder.join("ぱぱ.jpg"), "nfd encoding")?;

        let progress = |_n| true;
        let mut checker = MediaChecker::new(&mgr, progress);
        let output = checker.check()?;

        if cfg!(target_vendor = "apple") {
            // on a Mac, the file should not have been renamed, but the returned name
            // should be in NFC format
            assert_eq!(
                output,
                MediaCheckOutput {
                    files: vec!["ぱぱ.jpg".to_string()],
                    renamed: vec![],
                    dirs: vec![],
                    oversize: vec![]
                }
            );
            assert!(fs::metadata(&mgr.media_folder.join("ぱぱ.jpg")).is_ok());
        } else {
            // on other platforms, the file should have been renamed to NFC
            assert_eq!(
                output,
                MediaCheckOutput {
                    files: vec!["ぱぱ.jpg".to_string()],
                    renamed: vec![RenamedFile {
                        current_fname: "ぱぱ.jpg".to_string(),
                        original_fname: "ぱぱ.jpg".to_string()
                    }],
                    dirs: vec![],
                    oversize: vec![]
                }
            );
            assert!(fs::metadata(&mgr.media_folder.join("ぱぱ.jpg")).is_err());
            assert!(fs::metadata(&mgr.media_folder.join("ぱぱ.jpg")).is_ok());
        }

        Ok(())
    }
}
