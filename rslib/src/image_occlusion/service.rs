// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use anki_proto::image_occlusion::AddImageOcclusionNoteRequest;
use anki_proto::image_occlusion::GetImageForOcclusionRequest;
use anki_proto::image_occlusion::GetImageForOcclusionResponse;
use anki_proto::image_occlusion::GetImageOcclusionFieldsRequest;
use anki_proto::image_occlusion::GetImageOcclusionFieldsResponse;
use anki_proto::image_occlusion::GetImageOcclusionNoteRequest;
use anki_proto::image_occlusion::GetImageOcclusionNoteResponse;
use anki_proto::image_occlusion::UpdateImageOcclusionNoteRequest;

use crate::collection::Collection;
use crate::error::Result;
use crate::prelude::*;

impl crate::services::ImageOcclusionService for Collection {
    fn get_image_for_occlusion(
        &mut self,
        input: GetImageForOcclusionRequest,
    ) -> Result<GetImageForOcclusionResponse> {
        self.get_image_for_occlusion(&input.path)
    }

    fn add_image_occlusion_note(
        &mut self,
        input: AddImageOcclusionNoteRequest,
    ) -> Result<anki_proto::collection::OpChanges> {
        self.add_image_occlusion_note(input).map(Into::into)
    }

    fn get_image_occlusion_note(
        &mut self,
        input: GetImageOcclusionNoteRequest,
    ) -> Result<GetImageOcclusionNoteResponse> {
        self.get_image_occlusion_note(input.note_id.into())
    }

    fn update_image_occlusion_note(
        &mut self,
        input: UpdateImageOcclusionNoteRequest,
    ) -> Result<anki_proto::collection::OpChanges> {
        self.update_image_occlusion_note(
            input.note_id.into(),
            &input.occlusions,
            &input.header,
            &input.back_extra,
            input.tags,
        )
        .map(Into::into)
    }

    fn add_image_occlusion_notetype(&mut self) -> Result<anki_proto::collection::OpChanges> {
        self.add_image_occlusion_notetype().map(Into::into)
    }

    fn get_image_occlusion_fields(
        &mut self,
        input: GetImageOcclusionFieldsRequest,
    ) -> Result<GetImageOcclusionFieldsResponse> {
        let ntid = NotetypeId::from(input.notetype_id);
        let nt = self.get_notetype(ntid)?.or_not_found(ntid)?;
        Ok(GetImageOcclusionFieldsResponse {
            fields: Some(nt.get_io_field_indexes()?),
        })
    }
}
