// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Backend;
use crate::card_rendering::extract_av_tags;
use crate::card_rendering::strip_av_tags;
use crate::card_rendering::tts;
use crate::cloze::extract_cloze_for_typing;
use crate::latex::extract_latex;
use crate::latex::extract_latex_expanding_clozes;
use crate::latex::ExtractedLatex;
use crate::markdown::render_markdown;
use crate::notetype::CardTemplateSchema11;
use crate::notetype::RenderCardOutput;
use crate::pb;
pub(super) use crate::pb::card_rendering::cardrendering_service::Service as CardRenderingService;
use crate::pb::card_rendering::ExtractClozeForTypingRequest;
use crate::prelude::*;
use crate::template::RenderedNode;
use crate::text::decode_iri_paths;
use crate::text::encode_iri_paths;
use crate::text::sanitize_html_no_images;
use crate::text::strip_html;
use crate::text::strip_html_preserving_media_filenames;
use crate::typeanswer::compare_answer;

impl CardRenderingService for Backend {
    fn extract_av_tags(
        &self,
        input: pb::card_rendering::ExtractAvTagsRequest,
    ) -> Result<pb::card_rendering::ExtractAvTagsResponse> {
        let out = extract_av_tags(input.text, input.question_side, self.i18n());
        Ok(pb::card_rendering::ExtractAvTagsResponse {
            text: out.0,
            av_tags: out.1,
        })
    }

    fn extract_latex(
        &self,
        input: pb::card_rendering::ExtractLatexRequest,
    ) -> Result<pb::card_rendering::ExtractLatexResponse> {
        let func = if input.expand_clozes {
            extract_latex_expanding_clozes
        } else {
            extract_latex
        };
        let (text, extracted) = func(&input.text, input.svg);

        Ok(pb::card_rendering::ExtractLatexResponse {
            text,
            latex: extracted
                .into_iter()
                .map(|e: ExtractedLatex| pb::card_rendering::ExtractedLatex {
                    filename: e.fname,
                    latex_body: e.latex,
                })
                .collect(),
        })
    }

    fn get_empty_cards(
        &self,
        _input: pb::generic::Empty,
    ) -> Result<pb::card_rendering::EmptyCardsReport> {
        self.with_col(|col| {
            let mut empty = col.empty_cards()?;
            let report = col.empty_cards_report(&mut empty)?;

            let mut outnotes = vec![];
            for (_ntid, notes) in empty {
                outnotes.extend(notes.into_iter().map(|e| {
                    pb::card_rendering::empty_cards_report::NoteWithEmptyCards {
                        note_id: e.nid.0,
                        will_delete_note: e.empty.len() == e.current_count,
                        card_ids: e.empty.into_iter().map(|(_ord, id)| id.0).collect(),
                    }
                }))
            }
            Ok(pb::card_rendering::EmptyCardsReport {
                report,
                notes: outnotes,
            })
        })
    }

    fn render_existing_card(
        &self,
        input: pb::card_rendering::RenderExistingCardRequest,
    ) -> Result<pb::card_rendering::RenderCardResponse> {
        self.with_col(|col| {
            col.render_existing_card(CardId(input.card_id), input.browser)
                .map(Into::into)
        })
    }

    fn render_uncommitted_card(
        &self,
        input: pb::card_rendering::RenderUncommittedCardRequest,
    ) -> Result<pb::card_rendering::RenderCardResponse> {
        let template = input.template.or_invalid("missing template")?.into();
        let mut note = input.note.or_invalid("missing note")?.into();
        let ord = input.card_ord as u16;
        let fill_empty = input.fill_empty;
        self.with_col(|col| {
            col.render_uncommitted_card(&mut note, &template, ord, fill_empty)
                .map(Into::into)
        })
    }

    fn render_uncommitted_card_legacy(
        &self,
        input: pb::card_rendering::RenderUncommittedCardLegacyRequest,
    ) -> Result<pb::card_rendering::RenderCardResponse> {
        let schema11: CardTemplateSchema11 = serde_json::from_slice(&input.template)?;
        let template = schema11.into();
        let mut note = input.note.or_invalid("missing note")?.into();
        let ord = input.card_ord as u16;
        let fill_empty = input.fill_empty;
        self.with_col(|col| {
            col.render_uncommitted_card(&mut note, &template, ord, fill_empty)
                .map(Into::into)
        })
    }

    fn strip_av_tags(&self, input: pb::generic::String) -> Result<pb::generic::String> {
        Ok(strip_av_tags(input.val).into())
    }

    fn render_markdown(
        &self,
        input: pb::card_rendering::RenderMarkdownRequest,
    ) -> Result<pb::generic::String> {
        let mut text = render_markdown(&input.markdown);
        if input.sanitize {
            // currently no images
            text = sanitize_html_no_images(&text);
        }
        Ok(text.into())
    }

    fn encode_iri_paths(&self, input: pb::generic::String) -> Result<pb::generic::String> {
        Ok(encode_iri_paths(&input.val).to_string().into())
    }

    fn decode_iri_paths(&self, input: pb::generic::String) -> Result<pb::generic::String> {
        Ok(decode_iri_paths(&input.val).to_string().into())
    }

    fn strip_html(
        &self,
        input: pb::card_rendering::StripHtmlRequest,
    ) -> Result<pb::generic::String> {
        Ok(match input.mode() {
            pb::card_rendering::strip_html_request::Mode::Normal => strip_html(&input.text),
            pb::card_rendering::strip_html_request::Mode::PreserveMediaFilenames => {
                strip_html_preserving_media_filenames(&input.text)
            }
        }
        .to_string()
        .into())
    }

    fn compare_answer(
        &self,
        input: pb::card_rendering::CompareAnswerRequest,
    ) -> Result<pb::generic::String> {
        Ok(compare_answer(&input.expected, &input.provided).into())
    }

    fn extract_cloze_for_typing(
        &self,
        input: ExtractClozeForTypingRequest,
    ) -> Result<pb::generic::String> {
        Ok(extract_cloze_for_typing(&input.text, input.ordinal as u16)
            .to_string()
            .into())
    }

    fn all_tts_voices(
        &self,
        input: pb::card_rendering::AllTtsVoicesRequest,
    ) -> Result<pb::card_rendering::AllTtsVoicesResponse> {
        tts::all_voices(input.validate)
            .map(|voices| pb::card_rendering::AllTtsVoicesResponse { voices })
    }

    fn write_tts_stream(
        &self,
        request: pb::card_rendering::WriteTtsStreamRequest,
    ) -> Result<pb::generic::Empty> {
        tts::write_stream(
            &request.path,
            &request.voice_id,
            request.speed,
            &request.text,
        )
        .map(Into::into)
    }
}

fn rendered_nodes_to_proto(
    nodes: Vec<RenderedNode>,
) -> Vec<pb::card_rendering::RenderedTemplateNode> {
    nodes
        .into_iter()
        .map(|n| pb::card_rendering::RenderedTemplateNode {
            value: Some(rendered_node_to_proto(n)),
        })
        .collect()
}

fn rendered_node_to_proto(node: RenderedNode) -> pb::card_rendering::rendered_template_node::Value {
    match node {
        RenderedNode::Text { text } => {
            pb::card_rendering::rendered_template_node::Value::Text(text)
        }
        RenderedNode::Replacement {
            field_name,
            current_text,
            filters,
        } => pb::card_rendering::rendered_template_node::Value::Replacement(
            pb::card_rendering::RenderedTemplateReplacement {
                field_name,
                current_text,
                filters,
            },
        ),
    }
}

impl From<RenderCardOutput> for pb::card_rendering::RenderCardResponse {
    fn from(o: RenderCardOutput) -> Self {
        pb::card_rendering::RenderCardResponse {
            question_nodes: rendered_nodes_to_proto(o.qnodes),
            answer_nodes: rendered_nodes_to_proto(o.anodes),
            css: o.css,
            latex_svg: o.latex_svg,
        }
    }
}
