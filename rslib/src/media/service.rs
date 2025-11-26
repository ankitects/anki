// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashSet;

use anki_proto::generic;
use anki_proto::media::AddMediaFileRequest;
use anki_proto::media::CheckMediaResponse;
use anki_proto::media::TrashMediaFilesRequest;

use crate::collection::Collection;
use crate::error;
use crate::error::OrNotFound;
use crate::notes::service::to_i64s;
use crate::notetype::NotetypeId;

impl crate::services::MediaService for Collection {
    fn check_media(&mut self) -> error::Result<CheckMediaResponse> {
        self.transact_no_undo(|col| {
            let mut checker = col.media_checker()?;
            let mut output = checker.check()?;

            let mut report = checker.summarize_output(&mut output);
            col.report_media_field_referencing_templates(&mut report)?;

            Ok(CheckMediaResponse {
                unused: output.unused,
                missing: output.missing,
                missing_media_notes: to_i64s(output.missing_media_notes),
                report,
                have_trash: output.trash_count > 0,
            })
        })
    }

    fn add_media_file(&mut self, input: AddMediaFileRequest) -> error::Result<generic::String> {
        Ok(self
            .media()?
            .add_file(&input.desired_name, &input.data)?
            .to_string()
            .into())
    }

    fn trash_media_files(&mut self, input: TrashMediaFilesRequest) -> error::Result<()> {
        self.media()?.remove_files(&input.fnames)
    }

    fn empty_trash(&mut self) -> error::Result<()> {
        self.media_checker()?.empty_trash()
    }

    fn restore_trash(&mut self) -> error::Result<()> {
        self.media_checker()?.restore_trash()
    }

    fn extract_static_media_files(
        &mut self,
        ntid: anki_proto::notetypes::NotetypeId,
    ) -> error::Result<generic::StringList> {
        let ntid = NotetypeId::from(ntid);
        let notetype = self.storage.get_notetype(ntid)?.or_not_found(ntid)?;
        let mut files: HashSet<String> = HashSet::new();
        let mut inserter = |name: String| {
            files.insert(name);
        };
        notetype.gather_media_names(&mut inserter);

        Ok(files.into_iter().collect::<Vec<_>>().into())
    }

    fn get_media_constants(&mut self) -> error::Result<anki_proto::media::MediaConstants> {
        use crate::sync::media::{
            MAX_INDIVIDUAL_MEDIA_FILE_SIZE, MAX_MEDIA_FILENAME_LENGTH,
            MAX_MEDIA_FILENAME_LENGTH_SERVER,
        };

        Ok(anki_proto::media::MediaConstants {
            max_individual_file_size: MAX_INDIVIDUAL_MEDIA_FILE_SIZE as u64,
            max_filename_length: MAX_MEDIA_FILENAME_LENGTH as u32,
            max_filename_length_server: MAX_MEDIA_FILENAME_LENGTH_SERVER as u32,
        })
    }
}
