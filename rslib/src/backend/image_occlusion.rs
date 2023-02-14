// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Backend;
pub(super) use crate::pb::image_occlusion::imageocclusion_service::Service as ImageOcclusionService;
use crate::pb::{self as pb,};
use crate::prelude::*;

impl ImageOcclusionService for Backend {
    fn get_image_cloze_metadata(
        &self,
        input: pb::image_occlusion::ImageClozeMetadataRequest,
    ) -> Result<pb::image_occlusion::ImageClozeMetadata> {
        self.with_col(|col| col.get_image_cloze_metadata(&input.path))
    }

    fn add_image_occlusion_notes(
        &self,
        input: pb::image_occlusion::AddImageOcclusionNotesRequest,
    ) -> Result<pb::collection::OpChanges> {
        self.with_col(|col| {
            col.add_image_occlusion_notes(
                &input.image_path,
                &input.occlusions,
                &input.header,
                &input.back_extra,
                input.tags,
            )
        })
        .map(Into::into)
    }

    fn get_image_cloze_notes(
        &self,
        input: pb::image_occlusion::GetImageOcclusionNotesRequest,
    ) -> Result<pb::image_occlusion::ImageClozeNote> {
        self.with_col(|col| col.get_image_cloze_notes(input.note_id.into()))
    }

    fn update_image_occlusion_notes(
        &self,
        input: pb::image_occlusion::UpdateImageOcclusionNotesRequest,
    ) -> Result<pb::collection::OpChanges> {
        self.with_col(|col| {
            col.update_image_occlusion_notes(
                input.note_id.into(),
                &input.occlusions,
                &input.header,
                &input.back_extra,
                input.tags,
            )
        })
        .map(Into::into)
    }
}
