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

fn rendered_nodes_to_proto(
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

#[cfg(test)]
mod test {
    use anki_proto::card_rendering::rendered_template_node::Value;
    use anki_proto::card_rendering::RenderExistingCardRequest;
    use anki_proto::card_rendering::RenderUncommittedCardLegacyRequest;
    use anki_proto::card_rendering::RenderUncommittedCardRequest;

    use crate::error::AnkiError;
    use crate::prelude::*;
    use crate::services::CardRenderingService;
    use crate::tests::NoteAdder;

    /// Build a `RenderUncommittedCardRequest` for a Basic note whose fields are
    /// set to `fields`, optionally overriding the question format of the first
    /// template.
    fn basic_request(
        col: &Collection,
        fields: &[&str],
        q_format: Option<&str>,
        partial_render: bool,
    ) -> RenderUncommittedCardRequest {
        let nt = col.basic_notetype();
        let note = NoteAdder::new(&nt).fields(fields).note();
        let mut template = nt.templates[0].clone();
        if let Some(q_format) = q_format {
            template.config.q_format = q_format.into();
        }
        RenderUncommittedCardRequest {
            note: Some(note.into()),
            card_ord: 0,
            template: Some(template.into()),
            fill_empty: false,
            partial_render,
        }
    }

    /// The text of a fully rendered node list, or `None` when the nodes were
    /// left partially rendered (i.e. contain a replacement).
    fn text_of(nodes: &[anki_proto::card_rendering::RenderedTemplateNode]) -> Option<&str> {
        match nodes {
            [node] => match node.value.as_ref() {
                Some(Value::Text(text)) => Some(text),
                _ => None,
            },
            _ => None,
        }
    }

    // Area 1: question/answer rendering.

    #[test]
    fn render_uncommitted_card_returns_question_and_answer() {
        let mut col = Collection::new();
        let req = basic_request(&col, &["front", "back"], None, false);

        let resp = CardRenderingService::render_uncommitted_card(&mut col, req).unwrap();

        assert_eq!(text_of(&resp.question_nodes), Some("front"));
        assert_eq!(
            text_of(&resp.answer_nodes),
            Some("front\n\n<hr id=answer>\n\nback")
        );
        assert!(!resp.is_empty);
        assert!(!resp.css.is_empty());
    }

    #[test]
    fn render_existing_card_returns_saved_card_content() {
        let mut col = Collection::new();
        NoteAdder::basic(&mut col)
            .fields(&["front", "back"])
            .add(&mut col);
        let card_id = col.get_first_card().id.0;

        let resp = CardRenderingService::render_existing_card(
            &mut col,
            RenderExistingCardRequest {
                card_id,
                browser: false,
                partial_render: false,
            },
        )
        .unwrap();

        assert_eq!(text_of(&resp.question_nodes), Some("front"));
        assert_eq!(
            text_of(&resp.answer_nodes),
            Some("front\n\n<hr id=answer>\n\nback")
        );
    }

    // Area 2: template filters.

    #[test]
    fn render_uncommitted_card_applies_known_filter() {
        let mut col = Collection::new();
        // The `text` filter strips HTML from the field before rendering it.
        let req = basic_request(
            &col,
            &["<b>front</b>", "back"],
            Some("{{text:Front}}"),
            false,
        );

        let resp = CardRenderingService::render_uncommitted_card(&mut col, req).unwrap();

        assert_eq!(text_of(&resp.question_nodes), Some("front"));
    }

    #[test]
    fn render_uncommitted_card_ignores_unknown_filter_when_not_partial() {
        let mut col = Collection::new();
        let req = basic_request(&col, &["front", "back"], Some("{{foo:Front}}"), false);

        let resp = CardRenderingService::render_uncommitted_card(&mut col, req).unwrap();

        assert_eq!(text_of(&resp.question_nodes), Some("front"));
    }

    #[test]
    fn render_uncommitted_card_emits_replacement_node_when_partial() {
        let mut col = Collection::new();
        let req = basic_request(&col, &["front", "back"], Some("{{foo:Front}}"), true);

        let resp = CardRenderingService::render_uncommitted_card(&mut col, req).unwrap();

        let replacement = resp
            .question_nodes
            .iter()
            .find_map(|node| match node.value.as_ref() {
                Some(Value::Replacement(r)) => Some(r),
                _ => None,
            })
            .expect("expected a replacement node when partial rendering");
        assert_eq!(replacement.field_name, "Front");
        assert_eq!(replacement.filters, vec!["foo".to_string()]);
    }

    // Area 3: edge cases (empty fields and cloze).

    #[test]
    fn render_uncommitted_card_reports_empty_when_fields_blank() {
        let mut col = Collection::new();
        let req = basic_request(&col, &["", ""], None, false);

        let resp = CardRenderingService::render_uncommitted_card(&mut col, req).unwrap();

        assert!(resp.is_empty);
    }

    #[test]
    fn render_uncommitted_card_renders_cloze() {
        let mut col = Collection::new();
        let nt = col.cloze_notetype();
        let note = NoteAdder::new(&nt).fields(&["{{c1::foo}}", ""]).note();
        let req = RenderUncommittedCardRequest {
            note: Some(note.into()),
            card_ord: 0,
            template: Some(nt.templates[0].clone().into()),
            fill_empty: false,
            partial_render: false,
        };

        let resp = CardRenderingService::render_uncommitted_card(&mut col, req).unwrap();

        let question = text_of(&resp.question_nodes).expect("cloze question fully rendered");
        let answer = text_of(&resp.answer_nodes).expect("cloze answer fully rendered");
        // The question hides the deletion behind the `[...]` placeholder (the
        // value is only carried in a data attribute), and the answer reveals it.
        assert_eq!(
            question,
            r#"<span class="cloze" data-cloze="foo" data-ordinal="1">[...]</span>"#
        );
        assert_eq!(
            answer,
            "<span class=\"cloze\" data-ordinal=\"1\">foo</span><br>\n"
        );
    }

    // Area 4: error handling (structured errors, no panic).

    #[test]
    fn render_uncommitted_card_errors_when_template_missing() {
        let mut col = Collection::new();
        let mut req = basic_request(&col, &["front", "back"], None, false);
        req.template = None;

        let err = CardRenderingService::render_uncommitted_card(&mut col, req).unwrap_err();

        assert!(matches!(err, AnkiError::InvalidInput { .. }));
    }

    #[test]
    fn render_uncommitted_card_errors_when_note_missing() {
        let mut col = Collection::new();
        let mut req = basic_request(&col, &["front", "back"], None, false);
        req.note = None;

        let err = CardRenderingService::render_uncommitted_card(&mut col, req).unwrap_err();

        assert!(matches!(err, AnkiError::InvalidInput { .. }));
    }

    #[test]
    fn render_uncommitted_card_legacy_errors_on_invalid_template_bytes() {
        let mut col = Collection::new();
        let nt = col.basic_notetype();
        let note = NoteAdder::new(&nt).fields(&["front", "back"]).note();
        let req = RenderUncommittedCardLegacyRequest {
            note: Some(note.into()),
            card_ord: 0,
            template: b"not valid json".to_vec(),
            fill_empty: false,
            partial_render: false,
        };

        assert!(CardRenderingService::render_uncommitted_card_legacy(&mut col, req).is_err());
    }

    #[test]
    fn render_existing_card_errors_for_unknown_card_id() {
        let mut col = Collection::new();

        let result = CardRenderingService::render_existing_card(
            &mut col,
            RenderExistingCardRequest {
                card_id: 12345,
                browser: false,
                partial_render: false,
            },
        );

        assert!(result.is_err());
    }
}
