// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::fs::read;
use std::path::Path;
use std::path::PathBuf;
use std::sync::Arc;

use regex::Regex;

use crate::media::MediaManager;
use crate::notetype;
use crate::notetype::CardGenContext;
use crate::notetype::Notetype;
use crate::pb::image_occlusion::ImageClozeNote;
use crate::pb::image_occlusion::ImageClozeNoteResponse;
pub use crate::pb::image_occlusion::ImageData;
use crate::prelude::*;

impl Collection {
    pub fn get_image_for_occlusion(&mut self, path: &str) -> Result<ImageData> {
        let mut metadata = ImageData {
            ..Default::default()
        };
        metadata.data = read(path)?;
        Ok(metadata)
    }

    #[allow(clippy::too_many_arguments)]
    pub fn add_image_occlusion_note(
        &mut self,
        image_path: &str,
        occlusions: &str,
        header: &str,
        back_extra: &str,
        tags: Vec<String>,
    ) -> Result<OpOutput<()>> {
        // image file
        let image_bytes = read(image_path)?;
        let image_filename = Path::new(&image_path)
            .file_name()
            .unwrap()
            .to_str()
            .unwrap()
            .to_string();

        let mgr = MediaManager::new(&self.media_folder, &self.media_db)?;
        let actual_image_name_after_adding = mgr.add_file(&image_filename, &image_bytes)?;

        let image_tag = format!(
            "<img id='img' src='{}'></img>",
            &actual_image_name_after_adding
        );

        let current_deck = self.get_current_deck()?;
        self.transact(Op::ImageOcclusion, |col| {
            let nt = col.get_or_create_io_notetype()?;

            let mut note = nt.new_note();
            note.set_field(0, occlusions)?;
            note.set_field(1, &image_tag)?;
            note.set_field(2, header)?;
            note.set_field(3, back_extra)?;
            note.tags = tags;

            let last_deck = col.get_last_deck_added_to_for_notetype(note.notetype_id);
            let ctx = CardGenContext::new(nt.as_ref(), last_deck, col.usn()?);
            let norm = col.get_config_bool(BoolKey::NormalizeNoteText);
            col.add_note_inner(&ctx, &mut note, current_deck.id, norm)?;

            Ok(())
        })
    }

    fn get_or_create_io_notetype(&mut self) -> Result<Arc<Notetype>> {
        let tr = &self.tr;
        let name = format!("{}", tr.notetypes_image_occlusion_name());
        let nt = match self.get_notetype_by_name(&name)? {
            Some(nt) => nt,
            None => {
                self.add_io_notetype()?;
                self.get_notetype_by_name(&name).unwrap().unwrap() // need to handle exception
            }
        };
        if nt.fields.len() < 4 {
            Err(AnkiError::TemplateError {
                info: "IO notetype must have 4+ fields".to_string(),
            })
        } else {
            Ok(nt)
        }
    }

    fn add_io_notetype(&mut self) -> Result<()> {
        let usn = self.usn()?;
        let tr = &self.tr;
        let mut nt = notetype::stock::image_occlusion(tr);
        self.add_notetype_inner(&mut nt, usn, false)?;
        Ok(())
    }

    pub fn get_image_cloze_note(&mut self, note_id: NoteId) -> Result<ImageClozeNoteResponse> {
        let mut cloze_note = ImageClozeNote::default();
        let note = self.storage.get_note(note_id);
        let mut response = ImageClozeNoteResponse::default();
        match note {
            Ok(note) => {
                let mut note = note.unwrap();
                let original = note.clone();
                let fields = note.fields_mut();

                if fields.len() < 4 {
                    response.error = "Note does not have 4 fields".into();
                    return Ok(response);
                }

                cloze_note.occlusions = fields.get(0).unwrap().into();
                cloze_note.header = fields.get(2).unwrap().into();
                cloze_note.back_extra = fields.get(3).unwrap().into();
                cloze_note.image_data = "".into();

                let tags = original.tags;
                cloze_note.tags = tags.to_vec();

                let image_file_name = fields.get(1).unwrap();
                let src = self
                    .extract_img_src(image_file_name)
                    .unwrap_or_else(|| "".to_owned());
                let final_path = self.media_folder.join(src);

                if self.is_image_file(&final_path) {
                    cloze_note.image_data = read(&final_path).unwrap();
                }

                response.note = cloze_note.into();
                Ok(response)
            }
            Err(_) => {
                response.error = "Note not found".into();
                Ok(response)
            }
        }
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
            note.set_field(0, occlusions)?;
            note.set_field(2, header)?;
            note.set_field(3, back_extra)?;
            note.tags = tags;
            col.update_note_inner(&mut note)?;
            Ok(())
        })
    }

    fn extract_img_src(&mut self, html: &str) -> Option<String> {
        let re = Regex::new(r#"<img\s+[^>]*src\s*=\s*"([^"]+)"#).unwrap();
        re.captures(html).map(|cap| cap[1].to_owned())
    }

    fn is_image_file(&mut self, path: &PathBuf) -> bool {
        let file_path = Path::new(&path);
        let supported_extensions = vec![
            "jpg", "jpeg", "png", "tif", "tiff", "gif", "svg", "webp", "ico",
        ];

        if file_path.exists() {
            let metadata = std::fs::metadata(file_path).unwrap();
            if metadata.is_file() {
                if let Some(ext_osstr) = file_path.extension() {
                    if let Some(ext_str) = ext_osstr.to_str() {
                        if supported_extensions.contains(&ext_str) {
                            return true;
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }

        false
    }
}
