// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Backend;
pub(super) use crate::backend_proto::cardrendering_service::Service as CardRenderingService;
use crate::{
    backend_proto as pb,
    latex::{extract_latex, extract_latex_expanding_clozes, ExtractedLatex},
    markdown::render_markdown,
    notetype::{CardTemplateSchema11, RenderCardOutput},
    prelude::*,
    template::RenderedNode,
    text::{
        decode_iri_paths, encode_iri_paths, extract_av_tags, sanitize_html_no_images,
        strip_av_tags, AvTag,
    },
};

impl CardRenderingService for Backend {
    fn extract_av_tags(
        &self,
        input: pb::ExtractAvTagsRequest,
    ) -> Result<pb::ExtractAvTagsResponse> {
        let (text, tags) = extract_av_tags(&input.text, input.question_side);
        let pt_tags = tags
            .into_iter()
            .map(|avtag| match avtag {
                AvTag::SoundOrVideo(file) => pb::AvTag {
                    value: Some(pb::av_tag::Value::SoundOrVideo(file)),
                },
                AvTag::TextToSpeech {
                    field_text,
                    lang,
                    voices,
                    other_args,
                    speed,
                } => pb::AvTag {
                    value: Some(pb::av_tag::Value::Tts(pb::TtsTag {
                        field_text,
                        lang,
                        voices,
                        speed,
                        other_args,
                    })),
                },
            })
            .collect();

        Ok(pb::ExtractAvTagsResponse {
            text: text.into(),
            av_tags: pt_tags,
        })
    }

    fn extract_latex(&self, input: pb::ExtractLatexRequest) -> Result<pb::ExtractLatexResponse> {
        let func = if input.expand_clozes {
            extract_latex_expanding_clozes
        } else {
            extract_latex
        };
        let (text, extracted) = func(&input.text, input.svg);

        Ok(pb::ExtractLatexResponse {
            text,
            latex: extracted
                .into_iter()
                .map(|e: ExtractedLatex| pb::ExtractedLatex {
                    filename: e.fname,
                    latex_body: e.latex,
                })
                .collect(),
        })
    }

    fn get_empty_cards(&self, _input: pb::Empty) -> Result<pb::EmptyCardsReport> {
        self.with_col(|col| {
            let mut empty = col.empty_cards()?;
            let report = col.empty_cards_report(&mut empty)?;

            let mut outnotes = vec![];
            for (_ntid, notes) in empty {
                outnotes.extend(notes.into_iter().map(|e| {
                    pb::empty_cards_report::NoteWithEmptyCards {
                        note_id: e.nid.0,
                        will_delete_note: e.empty.len() == e.current_count,
                        card_ids: e.empty.into_iter().map(|(_ord, id)| id.0).collect(),
                    }
                }))
            }
            Ok(pb::EmptyCardsReport {
                report,
                notes: outnotes,
            })
        })
    }

    fn render_existing_card(
        &self,
        input: pb::RenderExistingCardRequest,
    ) -> Result<pb::RenderCardResponse> {
        self.with_col(|col| {
            col.render_existing_card(CardId(input.card_id), input.browser)
                .map(Into::into)
        })
    }

    fn render_uncommitted_card(
        &self,
        input: pb::RenderUncommittedCardRequest,
    ) -> Result<pb::RenderCardResponse> {
        let template = input.template.ok_or(AnkiError::NotFound)?.into();
        let mut note = input
            .note
            .ok_or_else(|| AnkiError::invalid_input("missing note"))?
            .into();
        let ord = input.card_ord as u16;
        let fill_empty = input.fill_empty;
        self.with_col(|col| {
            col.render_uncommitted_card(&mut note, &template, ord, fill_empty)
                .map(Into::into)
        })
    }

    fn render_uncommitted_card_legacy(
        &self,
        input: pb::RenderUncommittedCardLegacyRequest,
    ) -> Result<pb::RenderCardResponse> {
        let schema11: CardTemplateSchema11 = serde_json::from_slice(&input.template)?;
        let template = schema11.into();
        let mut note = input
            .note
            .ok_or_else(|| AnkiError::invalid_input("missing note"))?
            .into();
        let ord = input.card_ord as u16;
        let fill_empty = input.fill_empty;
        self.with_col(|col| {
            col.render_uncommitted_card(&mut note, &template, ord, fill_empty)
                .map(Into::into)
        })
    }

    fn strip_av_tags(&self, input: pb::String) -> Result<pb::String> {
        Ok(pb::String {
            val: strip_av_tags(&input.val).into(),
        })
    }

    fn render_markdown(&self, input: pb::RenderMarkdownRequest) -> Result<pb::String> {
        let mut text = render_markdown(&input.markdown);
        if input.sanitize {
            // currently no images
            text = sanitize_html_no_images(&text);
        }
        Ok(text.into())
    }

    fn encode_iri_paths(&self, input: pb::String) -> Result<pb::String> {
        Ok(encode_iri_paths(&input.val).to_string().into())
    }

    fn decode_iri_paths(&self, input: pb::String) -> Result<pb::String> {
        Ok(decode_iri_paths(&input.val).to_string().into())
    }
}

fn rendered_nodes_to_proto(nodes: Vec<RenderedNode>) -> Vec<pb::RenderedTemplateNode> {
    nodes
        .into_iter()
        .map(|n| pb::RenderedTemplateNode {
            value: Some(rendered_node_to_proto(n)),
        })
        .collect()
}

fn rendered_node_to_proto(node: RenderedNode) -> pb::rendered_template_node::Value {
    match node {
        RenderedNode::Text { text } => pb::rendered_template_node::Value::Text(text),
        RenderedNode::Replacement {
            field_name,
            current_text,
            filters,
        } => pb::rendered_template_node::Value::Replacement(pb::RenderedTemplateReplacement {
            field_name,
            current_text,
            filters,
        }),
    }
}

impl From<RenderCardOutput> for pb::RenderCardResponse {
    fn from(o: RenderCardOutput) -> Self {
        pb::RenderCardResponse {
            question_nodes: rendered_nodes_to_proto(o.qnodes),
            answer_nodes: rendered_nodes_to_proto(o.anodes),
            css: o.css,
            latex_svg: o.latex_svg,
        }
    }
}
