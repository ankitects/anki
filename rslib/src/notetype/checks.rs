// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{borrow::Cow, fmt::Write, ops::Deref};

use anki_i18n::without_unicode_isolation;
use lazy_static::lazy_static;
use regex::Regex;

use super::CardTemplate;
use crate::prelude::*;

#[derive(Debug, PartialEq)]
struct Template<'a> {
    notetype: &'a str,
    card_type: &'a str,
    front: bool,
}

lazy_static! {
    static ref MEDIA_FIELD_REFERENCE: Regex = Regex::new(
        r#"(?xsi)
            # an image, audio, or object tag
            <\b(?:img|audio|object)\b[^>]+\b(?:src|data)\b=
            (?:
                    # 1: double-quoted
                    "
                    \{\{.+?\}\}
                    "
                    [^>]*>                    
                |
                    # 2: single-quoted
                    '
                    \{\{.+?\}\}
                    '
                    [^>]*>
                |
                    # 3: unquoted
                    \{\{.+?\}\}
                    (?:
                        # then either a space and the rest
                        \x20[^>]*>
                        |
                        # or the tag immediately ends
                        >
                    )
            )
        |
            # an Anki sound tag
            \[sound:\{\{.+?\}\}\]
        |
            # standard latex
            \[latex\]\{\{.+?\}\}\[/latex\]
        |
            # inline math
            \[\$\]\{\{.+?\}\}\[/\$\]
        |
            # math environment
            \[\$\$\]\{\{.+?\}\}\[/\$\$\]
        "#
    )
    .unwrap();
}

impl Collection {
    pub fn report_media_field_referencing_templates(&mut self, buf: &mut String) -> Result<()> {
        let notetypes = self.get_all_notetypes()?;
        let templates = media_field_referencing_templates(notetypes.values().map(Deref::deref));
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
                card_type.sides().into_iter().filter_map(|(format, front)| {
                    references_media_field(format)
                        .then(|| Template::new(&notetype.name, &card_type.name, front))
                })
            })
        })
        .collect()
}

fn references_media_field(format: &str) -> bool {
    MEDIA_FIELD_REFERENCE.is_match(format)
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
