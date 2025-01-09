// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::path::Path;
use std::path::PathBuf;

use anki_io::metadata;
use anki_io::read_file;
use anki_proto::image_occlusion::get_image_occlusion_note_response::ImageOcclusionNote;
use anki_proto::image_occlusion::get_image_occlusion_note_response::Value;
use anki_proto::image_occlusion::AddImageOcclusionNoteRequest;
use anki_proto::image_occlusion::GetImageForOcclusionResponse;
use anki_proto::image_occlusion::GetImageOcclusionNoteResponse;
use anki_proto::image_occlusion::ImageOcclusionFieldIndexes;
use anki_proto::notetypes::ImageOcclusionField;
use regex::Regex;

use crate::cloze::parse_image_occlusions;
use crate::media::MediaManager;
use crate::prelude::*;

impl Collection {
    pub fn get_image_for_occlusion(&mut self, path: &str) -> Result<GetImageForOcclusionResponse> {
        let mut metadata = GetImageForOcclusionResponse {
            ..Default::default()
        };
        metadata.data = read_file(path)?;
        Ok(metadata)
    }

    pub fn add_image_occlusion_note(
        &mut self,
        req: AddImageOcclusionNoteRequest,
    ) -> Result<OpOutput<()>> {
        // image file
        let image_bytes = read_file(&req.image_path)?;
        let image_filename = Path::new(&req.image_path)
            .file_name()
            .or_not_found("expected filename")?
            .to_str()
            .unwrap()
            .to_string();

        let mgr = MediaManager::new(&self.media_folder, &self.media_db)?;
        let actual_image_name_after_adding = mgr.add_file(&image_filename, &image_bytes)?;

        let image_tag = format!(r#"<img src="{}">"#, &actual_image_name_after_adding);

        let current_deck = self.get_current_deck()?;
        let notetype_id: NotetypeId = req.notetype_id.into();
        self.transact(Op::ImageOcclusion, |col| {
            let nt = if notetype_id.0 == 0 {
                // when testing via .html page, use first available notetype
                col.add_image_occlusion_notetype_inner()?;
                col.get_first_io_notetype()?
                    .or_invalid("expected an i/o notetype to exist")?
            } else {
                col.get_io_notetype_by_id(notetype_id)?
            };

            let mut note = nt.new_note();
            let idxs = nt.get_io_field_indexes()?;
            note.set_field(idxs.occlusions as usize, req.occlusions)?;
            note.set_field(idxs.image as usize, image_tag)?;
            note.set_field(idxs.header as usize, req.header)?;
            note.set_field(idxs.back_extra as usize, req.back_extra)?;
            note.tags = req.tags;
            col.add_note_inner(&mut note, current_deck.id)?;

            Ok(())
        })
    }

    pub fn get_image_occlusion_note(
        &mut self,
        note_id: NoteId,
    ) -> Result<GetImageOcclusionNoteResponse> {
        let value = match self.get_image_occlusion_note_inner(note_id) {
            Ok(note) => Value::Note(note),
            Err(err) => Value::Error(format!("{:?}", err)),
        };
        Ok(GetImageOcclusionNoteResponse { value: Some(value) })
    }

    pub fn get_image_occlusion_note_inner(
        &mut self,
        note_id: NoteId,
    ) -> Result<ImageOcclusionNote> {
        let note = self.storage.get_note(note_id)?.or_not_found(note_id)?;
        let mut cloze_note = ImageOcclusionNote::default();

        let fields = note.fields();

        let nt = self
            .get_notetype(note.notetype_id)?
            .or_not_found(note.notetype_id)?;
        let idxs = nt.get_io_field_indexes()?;

        cloze_note.occlusions = parse_image_occlusions(fields[idxs.occlusions as usize].as_str());
        cloze_note.occlude_inactive = cloze_note.occlusions.iter().any(|oc| {
            oc.shapes.iter().any(|sh| {
                sh.properties
                    .iter()
                    .find(|p| p.name == "oi")
                    .is_some_and(|p| p.value == "1")
            })
        });
        cloze_note.header.clone_from(&fields[idxs.header as usize]);
        cloze_note
            .back_extra
            .clone_from(&fields[idxs.back_extra as usize]);
        cloze_note.image_data = "".into();
        cloze_note.tags.clone_from(&note.tags);

        let image_file_name = &fields[idxs.image as usize];
        let src = self
            .extract_img_src(image_file_name)
            .unwrap_or_else(|| "".to_owned());
        let final_path = self.media_folder.join(src);

        if self.is_image_file(&final_path)? {
            cloze_note.image_data = read_file(&final_path)?;
            cloze_note.image_file_name = final_path
                .file_name()
                .or_not_found("expected filename")?
                .to_str()
                .unwrap()
                .to_string();
        }

        Ok(cloze_note)
    }

    pub fn update_image_occlusion_note(
        &mut self,
        note_id: NoteId,
        occlusions: &str,
        header: &str,
        back_extra: &str,
        tags: Vec<String>,
    ) -> Result<OpOutput<()>> {
        let mut note = self.storage.get_note(note_id)?.or_not_found(note_id)?;
        self.transact(Op::ImageOcclusion, |col| {
            let nt = col
                .get_notetype(note.notetype_id)?
                .or_not_found(note.notetype_id)?;
            let idxs = nt.get_io_field_indexes()?;
            note.set_field(idxs.occlusions as usize, occlusions)?;
            note.set_field(idxs.header as usize, header)?;
            note.set_field(idxs.back_extra as usize, back_extra)?;
            note.tags = tags;
            col.update_note_inner(&mut note)?;
            Ok(())
        })
    }

    fn extract_img_src(&mut self, html: &str) -> Option<String> {
        let re = Regex::new(r#"<img\s+[^>]*src\s*=\s*"([^"]+)"#).unwrap();
        re.captures(html).map(|cap| cap[1].to_owned())
    }

    fn is_image_file(&mut self, path: &PathBuf) -> Result<bool> {
        let file_path = Path::new(&path);
        let supported_extensions = ["jpg", "jpeg", "png", "gif", "svg", "webp", "ico", "avif"];

        if file_path.exists() {
            let meta = metadata(file_path)?;
            if meta.is_file() {
                if let Some(ext_osstr) = file_path.extension() {
                    if let Some(ext_str) = ext_osstr.to_str() {
                        if supported_extensions.contains(&ext_str.to_lowercase().as_str()) {
                            return Ok(true);
                        }
                    }
                }
            }
        }

        Ok(false)
    }
}

impl Notetype {
    pub(crate) fn get_io_field_indexes(&self) -> Result<ImageOcclusionFieldIndexes> {
        get_field_indexes_by_tag(self).or_else(|_| {
            if self.fields.len() < 4 {
                return Err(AnkiError::DatabaseCheckRequired);
            }
            Ok(ImageOcclusionFieldIndexes {
                occlusions: 0,
                image: 1,
                header: 2,
                back_extra: 3,
            })
        })
    }
}

fn get_field_indexes_by_tag(nt: &Notetype) -> Result<ImageOcclusionFieldIndexes> {
    Ok(ImageOcclusionFieldIndexes {
        occlusions: get_field_index(nt, ImageOcclusionField::Occlusions)?,
        image: get_field_index(nt, ImageOcclusionField::Image)?,
        header: get_field_index(nt, ImageOcclusionField::Header)?,
        back_extra: get_field_index(nt, ImageOcclusionField::BackExtra)?,
    })
}

fn get_field_index(nt: &Notetype, field: ImageOcclusionField) -> Result<u32> {
    nt.fields
        .iter()
        .enumerate()
        .find(|(_idx, f)| f.config.tag == Some(field as u32))
        .map(|(idx, _)| idx as u32)
        .ok_or(AnkiError::DatabaseCheckRequired)
}
