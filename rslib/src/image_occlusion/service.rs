// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use anki_proto::image_occlusion::AddImageOcclusionNoteRequest;
use anki_proto::image_occlusion::GetImageForOcclusionRequest;
use anki_proto::image_occlusion::GetImageForOcclusionResponse;
use anki_proto::image_occlusion::GetImageOcclusionNoteRequest;
use anki_proto::image_occlusion::GetImageOcclusionNoteResponse;
use anki_proto::image_occlusion::UpdateImageOcclusionNoteRequest;

use crate::collection::Collection;
use crate::error;

impl crate::services::ImageOcclusionService for Collection {
    fn get_image_for_occlusion(
        &mut self,
        input: GetImageForOcclusionRequest,
    ) -> error::Result<GetImageForOcclusionResponse> {
        self.get_image_for_occlusion(&input.path)
    }

    fn add_image_occlusion_note(
        &mut self,
        input: AddImageOcclusionNoteRequest,
    ) -> error::Result<anki_proto::collection::OpChanges> {
        self.add_image_occlusion_note(
            input.notetype_id.into(),
            &input.image_path,
            &input.occlusions,
            &input.header,
            &input.back_extra,
            input.tags,
        )
        .map(Into::into)
    }

    fn get_image_occlusion_note(
        &mut self,
        input: GetImageOcclusionNoteRequest,
    ) -> error::Result<GetImageOcclusionNoteResponse> {
        self.get_image_occlusion_note(input.note_id.into())
    }

    fn update_image_occlusion_note(
        &mut self,
        input: UpdateImageOcclusionNoteRequest,
    ) -> error::Result<anki_proto::collection::OpChanges> {
        self.update_image_occlusion_note(
            input.note_id.into(),
            &input.occlusions,
            &input.header,
            &input.back_extra,
            input.tags,
        )
        .map(Into::into)
    }

    fn add_image_occlusion_notetype(&mut self) -> error::Result<anki_proto::collection::OpChanges> {
        self.add_image_occlusion_notetype().map(Into::into)
    }
}
