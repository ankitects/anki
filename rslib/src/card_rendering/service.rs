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
    use anki_proto::card_rendering::CompareAnswerRequest;
    use anki_proto::card_rendering::ExtractAvTagsRequest;
    use anki_proto::card_rendering::ExtractClozeForTypingRequest;
    use anki_proto::card_rendering::ExtractLatexRequest;
    use anki_proto::card_rendering::HtmlToTextLineRequest;
    use anki_proto::card_rendering::RenderExistingCardRequest;
    use anki_proto::card_rendering::RenderMarkdownRequest;
    use anki_proto::card_rendering::RenderUncommittedCardLegacyRequest;
    use anki_proto::card_rendering::RenderUncommittedCardRequest;
    use anki_proto::card_rendering::RenderedTemplateNode;
    use anki_proto::card_rendering::RenderedTemplateReplacement;
    use anki_proto::card_rendering::StripHtmlRequest;
    use anki_proto::generic;

    use crate::error::AnkiError;
    use crate::notetype::CardTemplateSchema11;
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

    /// The text of a node list that rendered to a single text node, or `None`
    /// otherwise (an empty list, multiple nodes, or a replacement node — i.e.
    /// partially rendered output).
    fn text_of(nodes: &[anki_proto::card_rendering::RenderedTemplateNode]) -> Option<&str> {
        match nodes {
            [node] => match node.value.as_ref() {
                Some(Value::Text(text)) => Some(text),
                _ => None,
            },
            _ => None,
        }
    }

    // Question/answer rendering.

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

    // Template filters.

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

        assert_eq!(
            resp.question_nodes,
            vec![RenderedTemplateNode {
                value: Some(Value::Replacement(RenderedTemplateReplacement {
                    field_name: "Front".into(),
                    current_text: "front".into(),
                    filters: vec!["foo".into()],
                })),
            }]
        );
    }

    #[test]
    fn render_uncommitted_card_preserves_text_before_replacement_when_partial() {
        let mut col = Collection::new();
        let req = basic_request(&col, &["front", "back"], Some("pre {{foo:Front}}"), true);

        let resp = CardRenderingService::render_uncommitted_card(&mut col, req).unwrap();

        assert_eq!(
            resp.question_nodes,
            vec![
                RenderedTemplateNode {
                    value: Some(Value::Text("pre ".into())),
                },
                RenderedTemplateNode {
                    value: Some(Value::Replacement(RenderedTemplateReplacement {
                        field_name: "Front".into(),
                        current_text: "front".into(),
                        filters: vec!["foo".into()],
                    })),
                },
            ]
        );
    }

    // Edge cases (empty fields and cloze).

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

    // Error handling (structured errors, no panic).

    #[test]
    fn render_uncommitted_card_errors_when_template_missing() {
        let mut col = Collection::new();
        let mut req = basic_request(&col, &["front", "back"], None, false);
        req.template = None;

        let err = CardRenderingService::render_uncommitted_card(&mut col, req).unwrap_err();

        match err {
            AnkiError::InvalidInput { source } => {
                assert_eq!(source.message(), "missing template");
            }
            other => panic!("expected InvalidInput, got {other:?}"),
        }
    }

    #[test]
    fn render_uncommitted_card_errors_when_note_missing() {
        let mut col = Collection::new();
        let mut req = basic_request(&col, &["front", "back"], None, false);
        req.note = None;

        let err = CardRenderingService::render_uncommitted_card(&mut col, req).unwrap_err();

        match err {
            AnkiError::InvalidInput { source } => {
                assert_eq!(source.message(), "missing note");
            }
            other => panic!("expected InvalidInput, got {other:?}"),
        }
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

        let err = CardRenderingService::render_uncommitted_card_legacy(&mut col, req).unwrap_err();

        assert!(
            matches!(err, AnkiError::JsonError { .. }),
            "expected JsonError, got {err:?}"
        );
    }

    #[test]
    fn render_existing_card_errors_for_unknown_card_id() {
        let mut col = Collection::new();

        let err = CardRenderingService::render_existing_card(
            &mut col,
            RenderExistingCardRequest {
                card_id: 12345,
                browser: false,
                partial_render: false,
            },
        )
        .unwrap_err();

        match err {
            AnkiError::InvalidInput { source } => {
                assert_eq!(source.message(), "no such card");
            }
            other => panic!("expected InvalidInput, got {other:?}"),
        }
    }

    // Remaining delegating methods: assert the bridge contract
    // (proto conversion, mode/flag dispatch) rather than re-testing the
    // domain rules, which are covered by the free functions' own unit tests.

    #[test]
    fn extract_av_tags_moves_sound_into_av_tags() {
        use anki_proto::card_rendering::av_tag::Value as AvValue;
        let mut col = Collection::new();

        let resp = col
            .extract_av_tags(ExtractAvTagsRequest {
                text: "foo [sound:bar.mp3] baz".into(),
                question_side: true,
            })
            .unwrap();

        assert_eq!(resp.text, "foo [anki:play:q:0] baz");
        assert_eq!(resp.av_tags.len(), 1);
        assert!(matches!(
            &resp.av_tags[0].value,
            Some(AvValue::SoundOrVideo(name)) if name == "bar.mp3"
        ));
    }

    #[test]
    fn extract_latex_returns_extracted_expressions() {
        let mut col = Collection::new();

        let resp = col
            .extract_latex(ExtractLatexRequest {
                text: "a [latex]x^2[/latex] b".into(),
                svg: false,
                expand_clozes: false,
            })
            .unwrap();

        assert_eq!(resp.latex.len(), 1);
        assert_eq!(resp.latex[0].latex_body, "x^2");
        assert!(!resp.latex[0].filename.is_empty());
        // The inline latex is replaced by an <img> reference to the filename.
        assert!(resp.text.contains(&resp.latex[0].filename));
    }

    #[test]
    fn extract_latex_expands_clozes_when_requested() {
        let mut col = Collection::new();

        let resp = col
            .extract_latex(ExtractLatexRequest {
                text: "[latex]{{c1::x}}[/latex]".into(),
                svg: true,
                expand_clozes: true,
            })
            .unwrap();

        // Expanding the cloze yields one latex expression per rendered side
        // (deletion hidden and revealed).
        assert_eq!(resp.latex.len(), 2);
    }

    #[test]
    fn get_empty_cards_reports_note_with_empty_card() {
        let mut col = Collection::new();
        let nt = col.basic_rev_notetype();
        // Generate both cards with the fields filled, then blank the Back field:
        // the reverse card now renders empty while the forward card keeps the
        // note alive.
        let mut note = NoteAdder::new(&nt).fields(&["front", "back"]).add(&mut col);
        let reverse_card_id = col
            .storage
            .all_cards_of_note(note.id)
            .unwrap()
            .into_iter()
            .find(|card| card.template_idx == 1)
            .expect("expected a reverse card")
            .id
            .0;
        note.set_field(1, "").unwrap();
        col.update_note(&mut note).unwrap();

        let report = col.get_empty_cards().unwrap();

        assert_eq!(report.notes.len(), 1);
        assert_eq!(report.notes[0].note_id, note.id.0);
        assert_eq!(report.notes[0].card_ids, vec![reverse_card_id]);
        assert!(!report.notes[0].will_delete_note);
        assert!(!report.report.is_empty());
    }

    #[test]
    fn get_empty_cards_marks_note_for_deletion_when_all_cards_empty() {
        let mut col = Collection::new();
        let mut note = NoteAdder::basic(&mut col)
            .fields(&["front", "back"])
            .add(&mut col);
        let card_id = col.storage.all_cards_of_note(note.id).unwrap()[0].id.0;
        note.set_field(0, "").unwrap();
        note.set_field(1, "").unwrap();
        col.update_note(&mut note).unwrap();

        let report = col.get_empty_cards().unwrap();

        assert_eq!(report.notes.len(), 1);
        assert_eq!(report.notes[0].note_id, note.id.0);
        assert_eq!(report.notes[0].card_ids, vec![card_id]);
        assert!(report.notes[0].will_delete_note);
    }

    #[test]
    fn render_uncommitted_card_legacy_renders_question_and_answer() {
        let mut col = Collection::new();
        let nt = col.basic_notetype();
        let note = NoteAdder::new(&nt).fields(&["front", "back"]).note();
        let schema11: CardTemplateSchema11 = nt.templates[0].clone().into();
        let req = RenderUncommittedCardLegacyRequest {
            note: Some(note.into()),
            card_ord: 0,
            template: serde_json::to_vec(&schema11).unwrap(),
            fill_empty: false,
            partial_render: false,
        };

        let resp = CardRenderingService::render_uncommitted_card_legacy(&mut col, req).unwrap();

        assert_eq!(text_of(&resp.question_nodes), Some("front"));
        assert_eq!(
            text_of(&resp.answer_nodes),
            Some("front\n\n<hr id=answer>\n\nback")
        );
    }

    #[test]
    fn strip_av_tags_removes_sound_tags() {
        let mut col = Collection::new();

        let resp = col
            .strip_av_tags(generic::String {
                val: "foo [sound:bar] baz".into(),
            })
            .unwrap();

        assert_eq!(resp.val, "foo  baz");
    }

    #[test]
    fn render_markdown_converts_without_sanitizing() {
        let mut col = Collection::new();

        let resp = col
            .render_markdown(RenderMarkdownRequest {
                markdown: "# Title".into(),
                sanitize: false,
            })
            .unwrap();

        assert_eq!(resp.val, "<h1>Title</h1>\n");
    }

    #[test]
    fn render_markdown_sanitizes_disallowed_markup() {
        let mut col = Collection::new();

        let resp = col
            .render_markdown(RenderMarkdownRequest {
                markdown: "safe\n\n<script>alert(1)</script>".into(),
                sanitize: true,
            })
            .unwrap();

        assert!(resp.val.contains("safe"));
        assert!(!resp.val.contains("<script>"));
        assert!(!resp.val.contains("alert(1)"));
    }

    #[test]
    fn iri_paths_round_trip_through_encode_and_decode() {
        let mut col = Collection::new();
        let original = r#"<img src="a b.png">"#;

        let encoded = col
            .encode_iri_paths(generic::String {
                val: original.into(),
            })
            .unwrap();
        assert_ne!(encoded.val, original, "encoding should escape the space");

        let decoded = col.decode_iri_paths(encoded).unwrap();
        assert_eq!(decoded.val, original);
    }

    #[test]
    fn strip_html_normal_removes_all_tags() {
        use anki_proto::card_rendering::strip_html_request::Mode;
        let mut col = Collection::new();

        let resp = col
            .strip_html(StripHtmlRequest {
                text: r#"<b>hi</b> <img src="foo.jpg">"#.into(),
                mode: Mode::Normal as i32,
            })
            .unwrap();

        assert_eq!(resp.val, "hi ");
    }

    #[test]
    fn strip_html_preserve_mode_keeps_media_filename() {
        use anki_proto::card_rendering::strip_html_request::Mode;
        let mut col = Collection::new();

        let resp = col
            .strip_html(StripHtmlRequest {
                text: r#"<b>hi</b> <img src="foo.jpg">"#.into(),
                mode: Mode::PreserveMediaFilenames as i32,
            })
            .unwrap();

        assert_eq!(resp.val, "hi  foo.jpg ");
    }

    #[test]
    fn html_to_text_line_strips_markup() {
        let mut col = Collection::new();

        let resp = col
            .html_to_text_line(HtmlToTextLineRequest {
                text: "<b>hi</b>".into(),
                preserve_media_filenames: false,
            })
            .unwrap();

        assert_eq!(resp.val, "hi");
    }

    #[test]
    fn compare_answer_marks_correct_input() {
        let mut col = Collection::new();

        let resp = col
            .compare_answer(CompareAnswerRequest {
                expected: "foo".into(),
                provided: "foo".into(),
                combining: true,
            })
            .unwrap();

        assert_eq!(
            resp.val,
            "<code id=typeans><span class=typeGood>foo</span></code>"
        );
    }

    #[test]
    fn extract_cloze_for_typing_returns_answer_for_ordinal() {
        let mut col = Collection::new();

        let resp = col
            .extract_cloze_for_typing(ExtractClozeForTypingRequest {
                text: "{{c1::foo}} {{c2::bar}}".into(),
                ordinal: 1,
            })
            .unwrap();

        assert_eq!(resp.val, "foo");
    }
}
