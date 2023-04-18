// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::sync::Arc;

use crate::notetype::stock::empty_stock;
use crate::notetype::Notetype;
use crate::notetype::NotetypeKind;
use crate::pb::notetypes::stock_notetype::OriginalStockKind;
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
            self.add_notetype_inner(&mut nt, self.usn()?, false)?;
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
        for (_, nt) in self.get_all_notetypes()? {
            if nt.config.original_stock_kind() == OriginalStockKind::ImageOcclusion {
                return Some(io_notetype_if_valid(nt)).transpose();
            }
        }

        Ok(None)
    }
}

pub(crate) fn image_occlusion_notetype(tr: &I18n) -> Notetype {
    const IMAGE_CLOZE_CSS: &str = include_str!("image_occlusion_styling.css");
    let mut nt = empty_stock(
        NotetypeKind::Cloze,
        OriginalStockKind::ImageOcclusion,
        tr.notetypes_image_occlusion_name(),
    );
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
