// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::card_rendering::ExtractClozeForTypingRequest;
use anki_proto::generic;

use crate::card::CardId;
use crate::card_rendering::extract_av_tags;
use crate::card_rendering::strip_av_tags;
use crate::cloze::extract_cloze_for_typing;
use crate::collection::Collection;
use crate::error::OrInvalid;
use crate::error::Result;
use crate::latex::extract_latex;
use crate::latex::extract_latex_expanding_clozes;
use crate::latex::ExtractedLatex;
use crate::markdown::render_markdown;
use crate::notetype::CardTemplateSchema11;
use crate::notetype::RenderCardOutput;
use crate::template::RenderedNode;
use crate::text::decode_iri_paths;
use crate::text::encode_iri_paths;
use crate::text::html_to_text_line;
use crate::text::sanitize_html_no_images;
use crate::text::strip_html;
use crate::text::strip_html_preserving_media_filenames;
use crate::typeanswer::compare_answer;

/// While the majority of these methods do not actually require a collection,
/// they are unlikely to be executed without one, so we only bother implementing
/// them for the collection.
impl crate::services::CardRenderingService for Collection {
    fn extract_av_tags(
        &mut self,
        input: anki_proto::card_rendering::ExtractAvTagsRequest,
    ) -> Result<anki_proto::card_rendering::ExtractAvTagsResponse> {
        let out = extract_av_tags(input.text, input.question_side, &self.tr);
        Ok(anki_proto::card_rendering::ExtractAvTagsResponse {
            text: out.0,
            av_tags: out.1,
        })
    }

    fn extract_latex(
        &mut self,
        input: anki_proto::card_rendering::ExtractLatexRequest,
    ) -> Result<anki_proto::card_rendering::ExtractLatexResponse> {
        let func = if input.expand_clozes {
            extract_latex_expanding_clozes
        } else {
            extract_latex
        };
        let (text, extracted) = func(&input.text, input.svg);

        Ok(anki_proto::card_rendering::ExtractLatexResponse {
            text: text.into_owned(),
            latex: extracted
                .into_iter()
                .map(
                    |e: ExtractedLatex| anki_proto::card_rendering::ExtractedLatex {
                        filename: e.fname,
                        latex_body: e.latex,
                    },
                )
                .collect(),
        })
    }

    fn get_empty_cards(&mut self) -> Result<anki_proto::card_rendering::EmptyCardsReport> {
        let mut empty = self.empty_cards()?;
        let report = self.empty_cards_report(&mut empty)?;

        let mut outnotes = vec![];
        for (_ntid, notes) in empty {
            outnotes.extend(notes.into_iter().map(|e| {
                anki_proto::card_rendering::empty_cards_report::NoteWithEmptyCards {
                    note_id: e.nid.0,
                    will_delete_note: e.empty.len() == e.current_count,
                    card_ids: e.empty.into_iter().map(|(_ord, id)| id.0).collect(),
                }
            }))
        }
        Ok(anki_proto::card_rendering::EmptyCardsReport {
            report,
            notes: outnotes,
        })
    }

    fn render_existing_card(
        &mut self,
        input: anki_proto::card_rendering::RenderExistingCardRequest,
    ) -> Result<anki_proto::card_rendering::RenderCardResponse> {
        self.render_existing_card(CardId(input.card_id), input.browser, input.partial_render)
            .map(Into::into)
    }

    fn render_uncommitted_card(
        &mut self,
        input: anki_proto::card_rendering::RenderUncommittedCardRequest,
    ) -> Result<anki_proto::card_rendering::RenderCardResponse> {
        let template = input.template.or_invalid("missing template")?.into();
        let mut note = input.note.or_invalid("missing note")?.into();
        let ord = input.card_ord as u16;
        let fill_empty = input.fill_empty;

        self.render_uncommitted_card(&mut note, &template, ord, fill_empty, input.partial_render)
            .map(Into::into)
    }

    fn render_uncommitted_card_legacy(
        &mut self,
        input: anki_proto::card_rendering::RenderUncommittedCardLegacyRequest,
    ) -> Result<anki_proto::card_rendering::RenderCardResponse> {
        let schema11: CardTemplateSchema11 = serde_json::from_slice(&input.template)?;
        let template = schema11.into();
        let mut note = input.note.or_invalid("missing note")?.into();
        let ord = input.card_ord as u16;
        let fill_empty = input.fill_empty;

        self.render_uncommitted_card(&mut note, &template, ord, fill_empty, input.partial_render)
            .map(Into::into)
    }

    fn strip_av_tags(&mut self, input: generic::String) -> Result<generic::String> {
        Ok(strip_av_tags(input.val).into())
    }

    fn render_markdown(
        &mut self,
        input: anki_proto::card_rendering::RenderMarkdownRequest,
    ) -> Result<generic::String> {
        let mut text = render_markdown(&input.markdown);
        if input.sanitize {
            // currently no images
            text = sanitize_html_no_images(&text);
        }
        Ok(text.into())
    }

    fn encode_iri_paths(&mut self, input: generic::String) -> Result<generic::String> {
        Ok(encode_iri_paths(&input.val).to_string().into())
    }

    fn decode_iri_paths(&mut self, input: generic::String) -> Result<generic::String> {
        Ok(decode_iri_paths(&input.val).to_string().into())
    }

    fn strip_html(
        &mut self,
        input: anki_proto::card_rendering::StripHtmlRequest,
    ) -> Result<generic::String> {
        strip_html_proto(input)
    }

    fn html_to_text_line(
        &mut self,
        input: anki_proto::card_rendering::HtmlToTextLineRequest,
    ) -> Result<generic::String> {
        Ok(
            html_to_text_line(&input.text, input.preserve_media_filenames)
                .to_string()
                .into(),
        )
    }

    fn compare_answer(
        &mut self,
        input: anki_proto::card_rendering::CompareAnswerRequest,
    ) -> Result<generic::String> {
        Ok(compare_answer(&input.expected, &input.provided, input.combining).into())
    }

    fn extract_cloze_for_typing(
        &mut self,
        input: ExtractClozeForTypingRequest,
    ) -> Result<generic::String> {
        Ok(extract_cloze_for_typing(&input.text, input.ordinal as u16)
            .to_string()
            .into())
    }
}

pub(crate) fn rendered_nodes_to_proto(
    nodes: Vec<RenderedNode>,
) -> Vec<anki_proto::card_rendering::RenderedTemplateNode> {
    nodes
        .into_iter()
        .map(|n| anki_proto::card_rendering::RenderedTemplateNode {
            value: Some(rendered_node_to_proto(n)),
        })
        .collect()
}

fn rendered_node_to_proto(
    node: RenderedNode,
) -> anki_proto::card_rendering::rendered_template_node::Value {
    match node {
        RenderedNode::Text { text } => {
            anki_proto::card_rendering::rendered_template_node::Value::Text(text)
        }
        RenderedNode::Replacement {
            field_name,
            current_text,
            filters,
        } => anki_proto::card_rendering::rendered_template_node::Value::Replacement(
            anki_proto::card_rendering::RenderedTemplateReplacement {
                field_name,
                current_text,
                filters,
            },
        ),
    }
}

impl From<RenderCardOutput> for anki_proto::card_rendering::RenderCardResponse {
    fn from(o: RenderCardOutput) -> Self {
        anki_proto::card_rendering::RenderCardResponse {
            question_nodes: rendered_nodes_to_proto(o.qnodes),
            answer_nodes: rendered_nodes_to_proto(o.anodes),
            css: o.css,
            latex_svg: o.latex_svg,
            is_empty: o.is_empty,
        }
    }
}

pub(crate) fn strip_html_proto(
    input: anki_proto::card_rendering::StripHtmlRequest,
) -> Result<generic::String> {
    Ok(match input.mode() {
        anki_proto::card_rendering::strip_html_request::Mode::Normal => strip_html(&input.text),
        anki_proto::card_rendering::strip_html_request::Mode::PreserveMediaFilenames => {
            strip_html_preserving_media_filenames(&input.text)
        }
    }
    .to_string()
    .into())
}
