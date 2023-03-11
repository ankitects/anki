// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::path::Path;
use std::path::PathBuf;
use std::sync::Arc;

use regex::Regex;

use crate::io::metadata;
use crate::io::read_file;
use crate::media::MediaManager;
use crate::notetype::CardGenContext;
use crate::notetype::Notetype;
use crate::notetype::NotetypeConfig;
use crate::pb::image_occlusion::image_cloze_note_response::Value;
use crate::pb::image_occlusion::ImageClozeNote;
use crate::pb::image_occlusion::ImageClozeNoteResponse;
pub use crate::pb::image_occlusion::ImageData;
use crate::prelude::*;

impl Collection {
    pub fn get_image_for_occlusion(&mut self, path: &str) -> Result<ImageData> {
        let mut metadata = ImageData {
            ..Default::default()
        };
        metadata.data = read_file(path)?;
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
        let image_bytes = read_file(image_path)?;
        let image_filename = Path::new(&image_path)
            .file_name()
            .or_not_found("expected filename")?
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
                if let Some(nt) = self.get_notetype_by_name(&name)? {
                    nt
                } else {
                    return Err(AnkiError::TemplateError {
                        info: "IO notetype not found".to_string(),
                    });
                }
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
        let mut nt = self.image_occlusion_notetype();
        self.add_notetype_inner(&mut nt, usn, false)?;
        Ok(())
    }

    pub fn get_image_cloze_note(&mut self, note_id: NoteId) -> Result<ImageClozeNoteResponse> {
        let value = match self.get_image_cloze_note_inner(note_id) {
            Ok(note) => Value::Note(note),
            Err(err) => Value::Error(format!("{:?}", err)),
        };
        Ok(ImageClozeNoteResponse { value: Some(value) })
    }

    pub fn get_image_cloze_note_inner(&mut self, note_id: NoteId) -> Result<ImageClozeNote> {
        let note = self.storage.get_note(note_id)?.or_not_found(note_id)?;
        let mut cloze_note = ImageClozeNote::default();

        let fields = note.fields();
        if fields.len() < 4 {
            invalid_input!("Note does not have 4 fields");
        }

        cloze_note.occlusions = fields[0].clone();
        cloze_note.header = fields[2].clone();
        cloze_note.back_extra = fields[3].clone();
        cloze_note.image_data = "".into();
        cloze_note.tags = note.tags.clone();

        let image_file_name = fields[1].clone();
        let src = self
            .extract_img_src(&image_file_name)
            .unwrap_or_else(|| "".to_owned());
        let final_path = self.media_folder.join(src);

        if self.is_image_file(&final_path)? {
            cloze_note.image_data = read_file(&final_path)?;
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

    fn is_image_file(&mut self, path: &PathBuf) -> Result<bool> {
        let file_path = Path::new(&path);
        let supported_extensions = vec![
            "jpg", "jpeg", "png", "tif", "tiff", "gif", "svg", "webp", "ico",
        ];

        if file_path.exists() {
            let meta = metadata(file_path)?;
            if meta.is_file() {
                if let Some(ext_osstr) = file_path.extension() {
                    if let Some(ext_str) = ext_osstr.to_str() {
                        if supported_extensions.contains(&ext_str) {
                            return Ok(true);
                        }
                    }
                }
            }
        }

        Ok(false)
    }

    fn image_occlusion_notetype(&mut self) -> Notetype {
        let tr = &self.tr;
        const IMAGE_CLOZE_CSS: &str = include_str!("image_occlusion_styling.css");
        let mut nt = Notetype {
            name: tr.notetypes_image_occlusion_name().into(),
            config: NotetypeConfig::new_cloze(),
            ..Default::default()
        };
        nt.config.css = IMAGE_CLOZE_CSS.to_string();
        let occlusion = tr.notetypes_occlusion();
        nt.add_field(occlusion.as_ref());
        let image = tr.notetypes_image();
        nt.add_field(image.as_ref());
        let header = tr.notetypes_header();
        nt.add_field(header.as_ref());
        let back_extra = tr.notetypes_back_extra_field();
        nt.add_field(back_extra.as_ref());
        let comments = tr.notetypes_comments_field();
        nt.add_field(comments.as_ref());
        let qfmt = format!(
            "<div style=\"display: none\">{{{{cloze:{}}}}}</div>
<div id=container>
    {{{{{}}}}}
    <canvas id=\"canvas\" class=\"image-occlusion-canvas\"></canvas>
</div>
<div id=\"err\"></div>
<script>
try {{
    anki.setupImageCloze();
}} catch (exc) {{
    document.getElementById(\"err\").innerHTML = `{}<br><br>${{exc}}`;
}}
</script>
",
            occlusion,
            image,
            tr.notetypes_error_loading_image_occlusion(),
        );
        let afmt = format!(
            "{{{{{}}}}}
{}
<button id=\"toggle\">{}</button>
<br>
{{{{{}}}}}
<br>
{{{{{}}}}}",
            header,
            qfmt,
            tr.notetypes_toggle_masks(),
            back_extra,
            comments,
        );
        nt.add_template(nt.name.clone(), qfmt, afmt);
        nt
    }
}
