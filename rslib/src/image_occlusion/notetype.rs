// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::sync::Arc;

use anki_proto::notetypes::stock_notetype::OriginalStockKind;
use anki_proto::notetypes::ImageOcclusionField;

use crate::notetype::stock::empty_stock;
use crate::notetype::Notetype;
use crate::notetype::NotetypeKind;
use crate::prelude::*;

impl Collection {
    pub fn add_image_occlusion_notetype(&mut self) -> Result<OpOutput<()>> {
        self.transact(Op::UpdateNotetype, |col| {
            col.add_image_occlusion_notetype_inner()
        })
    }

    pub fn add_image_occlusion_notetype_inner(&mut self) -> Result<()> {
        if self.get_first_io_notetype()?.is_none() {
            let mut nt = image_occlusion_notetype(&self.tr);
            let usn = self.usn()?;
            nt.set_modified(usn);
            let current_id = self.get_current_notetype_id();
            self.add_notetype_inner(&mut nt, usn, false)?;
            if let Some(current_id) = current_id {
                // preserve previous default
                self.set_current_notetype_id(current_id)?;
            }
        }
        Ok(())
    }

    /// Returns the I/O notetype with the provided id, checking to make sure it
    /// is valid.
    pub(crate) fn get_io_notetype_by_id(
        &mut self,
        notetype_id: NotetypeId,
    ) -> Result<Arc<Notetype>> {
        let nt = self.get_notetype(notetype_id)?.or_not_found(notetype_id)?;
        io_notetype_if_valid(nt)
    }

    pub(crate) fn get_first_io_notetype(&mut self) -> Result<Option<Arc<Notetype>>> {
        for nt in self.get_all_notetypes()? {
            if nt.config.original_stock_kind() == OriginalStockKind::ImageOcclusion {
                if let Ok(nt) = io_notetype_if_valid(nt) {
                    return Ok(Some(nt));
                }
            }
        }

        Ok(None)
    }
}

pub(crate) fn image_occlusion_notetype(tr: &I18n) -> Notetype {
    const IMAGE_CLOZE_CSS: &str = include_str!("notetype.css");
    let mut nt = empty_stock(
        NotetypeKind::Cloze,
        OriginalStockKind::ImageOcclusion,
        tr.notetypes_image_occlusion_name(),
    );
    nt.config.css = IMAGE_CLOZE_CSS.to_string();

    let occlusion = tr.notetypes_occlusion();
    let mut config = nt.add_field(occlusion.as_ref());
    config.tag = Some(ImageOcclusionField::Occlusions as u32);
    config.prevent_deletion = true;

    let image = tr.notetypes_image();
    config = nt.add_field(image.as_ref());
    config.tag = Some(ImageOcclusionField::Image as u32);
    config.prevent_deletion = true;

    let header = tr.notetypes_header();
    config = nt.add_field(header.as_ref());
    config.tag = Some(ImageOcclusionField::Header as u32);
    config.prevent_deletion = true;

    let back_extra = tr.notetypes_back_extra_field();
    config = nt.add_field(back_extra.as_ref());
    config.tag = Some(ImageOcclusionField::BackExtra as u32);
    config.prevent_deletion = true;

    let comments = tr.notetypes_comments_field();
    config = nt.add_field(comments.as_ref());
    config.tag = Some(ImageOcclusionField::Comments as u32);
    config.prevent_deletion = false;

    let err_loading = tr.notetypes_error_loading_image_occlusion();
    let qfmt = format!(
        r#"{{{{#{header}}}}}<div>{{{{{header}}}}}</div>{{{{/{header}}}}}
<div style="display: none">{{{{cloze:{occlusion}}}}}</div>
<div id="err"></div>
<div id="image-occlusion-container">
    {{{{{image}}}}}
    <canvas id="image-occlusion-canvas"></canvas>
</div>
<script>
try {{
    anki.imageOcclusion.setup();
}} catch (exc) {{
    document.getElementById("err").innerHTML = `{err_loading}<br><br>${{exc}}`;
}}
</script>
"#
    );

    let toggle_masks = tr.notetypes_toggle_masks();
    let afmt = format!(
        r#"{qfmt}
<div><button id="toggle">{toggle_masks}</button></div>
{{{{#{back_extra}}}}}<div>{{{{{back_extra}}}}}</div>{{{{/{back_extra}}}}}
"#,
    );
    nt.add_template(nt.name.clone(), qfmt, afmt);
    nt
}

fn io_notetype_if_valid(nt: Arc<Notetype>) -> Result<Arc<Notetype>> {
    if nt.config.original_stock_kind() != OriginalStockKind::ImageOcclusion {
        invalid_input!("Not an image occlusion notetype");
    }
    if nt.fields.len() < 4 {
        return Err(AnkiError::TemplateError {
            info: "IO notetype must have 4+ fields".to_string(),
        });
    }
    Ok(nt)
}
