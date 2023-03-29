// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Backend;
pub(super) use crate::pb::image_occlusion::imageocclusion_service::Service as ImageOcclusionService;
use crate::pb::{self as pb,};
use crate::prelude::*;

impl ImageOcclusionService for Backend {
    fn get_image_for_occlusion(
        &self,
        input: pb::image_occlusion::GetImageForOcclusionRequest,
    ) -> Result<pb::image_occlusion::ImageData> {
        self.with_col(|col| col.get_image_for_occlusion(&input.path))
    }

    fn add_image_occlusion_note(
        &self,
        input: pb::image_occlusion::AddImageOcclusionNoteRequest,
    ) -> Result<pb::collection::OpChanges> {
        self.with_col(|col| {
            col.add_image_occlusion_note(
                &input.image_path,
                &input.occlusions,
                &input.header,
                &input.back_extra,
                input.tags,
            )
        })
        .map(Into::into)
    }

    fn get_image_cloze_note(
        &self,
        input: pb::image_occlusion::GetImageOcclusionNoteRequest,
    ) -> Result<pb::image_occlusion::ImageClozeNoteResponse> {
        self.with_col(|col| col.get_image_cloze_note(input.note_id.into()))
    }

    fn update_image_occlusion_note(
        &self,
        input: pb::image_occlusion::UpdateImageOcclusionNoteRequest,
    ) -> Result<pb::collection::OpChanges> {
        self.with_col(|col| {
            col.update_image_occlusion_note(
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
