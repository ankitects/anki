// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;
use std::fmt::Write;
use std::ops::Deref;
use std::sync::LazyLock;

use anki_i18n::without_unicode_isolation;
use regex::Captures;
use regex::Match;
use regex::Regex;

use super::CardTemplate;
use crate::latex::LATEX;
use crate::prelude::*;
use crate::text::HTML_MEDIA_TAGS;
use crate::text::SOUND_TAG;

#[derive(Debug, PartialEq, Eq)]
struct Template<'a> {
    notetype: &'a str,
    card_type: &'a str,
    front: bool,
}

static FIELD_REPLACEMENT: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\{\{.+\}\}").unwrap());

impl Collection {
    pub fn report_media_field_referencing_templates(&mut self, buf: &mut String) -> Result<()> {
        let notetypes = self.get_all_notetypes()?;
        let templates = media_field_referencing_templates(notetypes.iter().map(Deref::deref));
        write_template_report(buf, &templates, &self.tr);
        Ok(())
    }
}

fn media_field_referencing_templates<'a>(
    notetypes: impl Iterator<Item = &'a Notetype>,
) -> Vec<Template<'a>> {
    notetypes
        .flat_map(|notetype| {
            notetype.templates.iter().flat_map(|card_type| {
                card_type
                    .sides()
                    .into_iter()
                    .filter(|&(format, _front)| references_media_field(format))
                    .map(|(_format, front)| Template::new(&notetype.name, &card_type.name, front))
            })
        })
        .collect()
}

fn references_media_field(format: &str) -> bool {
    for regex in [&*HTML_MEDIA_TAGS, &*SOUND_TAG, &*LATEX] {
        if regex
            .captures_iter(format)
            .any(captures_contain_field_replacement)
        {
            return true;
        }
    }
    false
}

fn captures_contain_field_replacement(caps: Captures) -> bool {
    caps.iter()
        .skip(1)
        .any(|opt| opt.is_some_and(match_contains_field_replacement))
}

fn match_contains_field_replacement(m: Match) -> bool {
    FIELD_REPLACEMENT.is_match(m.as_str())
}

fn write_template_report(buf: &mut String, templates: &[Template], tr: &I18n) {
    if templates.is_empty() {
        return;
    }
    writeln!(
        buf,
        "\n{}",
        &tr.media_check_template_references_field_header()
    )
    .unwrap();
    for template in templates {
        writeln!(buf, "{}", template.as_str(tr)).unwrap();
    }
}

impl<'a> Template<'a> {
    fn new(notetype: &'a str, card_type: &'a str, front: bool) -> Self {
        Template {
            notetype,
            card_type,
            front,
        }
    }

    fn as_str(&self, tr: &I18n) -> String {
        without_unicode_isolation(&tr.media_check_notetype_template(
            self.notetype,
            self.card_type,
            self.side_name(tr),
        ))
    }

    fn side_name<'tr>(&self, tr: &'tr I18n) -> Cow<'tr, str> {
        if self.front {
            tr.card_templates_front_template()
        } else {
            tr.card_templates_back_template()
        }
    }
}

impl CardTemplate {
    fn sides(&self) -> [(&str, bool); 2] {
        [
            (&self.config.q_format, true),
            (&self.config.a_format, false),
        ]
    }
}

#[cfg(test)]
mod test {
    use std::iter::once;

    use super::*;

    #[test]
    fn should_report_media_field_referencing_template() {
        let notetype = "foo";
        let card_type = "bar";
        let mut nt = Notetype {
            name: notetype.into(),
            ..Default::default()
        };
        nt.add_field("baz");
        nt.add_template(card_type, "<img src=baz>", "<img src={{baz}}>");

        let templates = media_field_referencing_templates(once(&nt));

        let expected = Template {
            notetype,
            card_type,
            front: false,
        };
        assert_eq!(templates, &[expected]);
    }
}
