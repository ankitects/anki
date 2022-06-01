// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{borrow::Cow, fs::File, io::Write};

use itertools::Itertools;
use lazy_static::lazy_static;
use regex::Regex;

use super::metadata::Delimiter;
use crate::{
    import_export::{ExportProgress, IncrementableProgress},
    notetype::RenderCardOutput,
    prelude::*,
    search::SortMode,
    template::RenderedNode,
    text::{html_to_text_line, CowMapping},
};

const DELIMITER: Delimiter = Delimiter::Tab;

impl Collection {
    pub fn export_card_csv(
        &mut self,
        path: &str,
        search: impl TryIntoSearch,
        with_html: bool,
        progress_fn: impl 'static + FnMut(ExportProgress, bool) -> bool,
    ) -> Result<usize> {
        let mut progress = IncrementableProgress::new(progress_fn);
        progress.call(ExportProgress::File)?;
        let mut incrementor = progress.incrementor(ExportProgress::Cards);

        let mut writer = file_writer_with_header(path)?;
        let mut cards = self.search_cards(search, SortMode::NoOrder)?;
        cards.sort_unstable();
        for &card in &cards {
            incrementor.increment()?;
            writer.write_record(self.card_record(card, with_html)?)?;
        }
        writer.flush()?;

        Ok(cards.len())
    }

    pub fn export_note_csv(
        &mut self,
        path: &str,
        search: impl TryIntoSearch,
        with_html: bool,
        with_tags: bool,
        progress_fn: impl 'static + FnMut(ExportProgress, bool) -> bool,
    ) -> Result<usize> {
        let mut progress = IncrementableProgress::new(progress_fn);
        progress.call(ExportProgress::File)?;
        let mut incrementor = progress.incrementor(ExportProgress::Notes);

        let mut writer = file_writer_with_header(path)?;
        self.search_notes_into_table(search)?;
        self.storage.for_each_note_in_search(|note| {
            incrementor.increment()?;
            writer.write_record(note_record(&note, with_html, with_tags))?;
            Ok(())
        })?;
        writer.flush()?;
        self.storage.clear_searched_notes_table()?;

        Ok(incrementor.count())
    }

    fn card_record(&mut self, card: CardId, with_html: bool) -> Result<[String; 2]> {
        let RenderCardOutput { qnodes, anodes, .. } = self.render_existing_card(card, false)?;
        Ok([
            rendered_nodes_to_record_field(&qnodes, with_html, false),
            rendered_nodes_to_record_field(&anodes, with_html, true),
        ])
    }
}

fn file_writer_with_header(path: &str) -> Result<csv::Writer<File>> {
    let mut file = File::create(path)?;
    write_header(&mut file)?;
    Ok(csv::WriterBuilder::new()
        .delimiter(DELIMITER.byte())
        .flexible(true)
        .from_writer(file))
}

fn write_header(writer: &mut impl Write) -> Result<()> {
    write!(writer, "#separator:{}\n#html:true\n", DELIMITER.name())?;
    Ok(())
}

fn rendered_nodes_to_record_field(
    nodes: &[RenderedNode],
    with_html: bool,
    answer_side: bool,
) -> String {
    let text = rendered_nodes_to_str(nodes);
    let mut text = strip_redundant_sections(&text);
    if answer_side {
        text = text.map_cow(strip_answer_side_question);
    }
    if !with_html {
        text = text.map_cow(|t| html_to_text_line(t, false));
    }
    text.into()
}

fn rendered_nodes_to_str(nodes: &[RenderedNode]) -> String {
    nodes
        .iter()
        .map(|node| match node {
            RenderedNode::Text { text } => text,
            RenderedNode::Replacement { current_text, .. } => current_text,
        })
        .join("")
}

fn note_record(note: &Note, with_html: bool, with_tags: bool) -> Vec<String> {
    let mut fields: Vec<_> = note
        .fields()
        .iter()
        .map(|f| field_to_record_field(f, with_html))
        .collect();
    if with_tags {
        fields.push(note.tags.join(" "));
    }
    fields
}

fn field_to_record_field(field: &str, with_html: bool) -> String {
    let mut text = strip_redundant_sections(field);
    if !with_html {
        text = text.map_cow(|t| html_to_text_line(t, false));
    }
    text.into()
}

fn strip_redundant_sections(text: &str) -> Cow<str> {
    lazy_static! {
        static ref RE: Regex = Regex::new(
            r"(?isx)
            <style>.*?</style>          # style elements
            |
            \[\[type:[^]]+\]\]          # type replacements
            "
        )
        .unwrap();
    }
    RE.replace_all(text.as_ref(), "")
}

fn strip_answer_side_question(text: &str) -> Cow<str> {
    lazy_static! {
        static ref RE: Regex = Regex::new(r"(?is)^.*<hr id=answer>\n*").unwrap();
    }
    RE.replace_all(text.as_ref(), "")
}
