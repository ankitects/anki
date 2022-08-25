// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{borrow::Cow, fmt::Write, ops::Deref};

use anki_i18n::without_unicode_isolation;

use super::CardTemplate;
use crate::{prelude::*, text::contains_media_tag};

struct Template<'a> {
    notetype: &'a str,
    card_type: &'a str,
    front: bool,
}

impl Collection {
    pub fn report_media_referencing_templates(&mut self, buf: &mut String) -> Result<()> {
        let notetypes = self.get_all_notetypes()?;
        let templates = media_referencing_templates(notetypes.values().map(Deref::deref));
        write_template_report(buf, &templates, &self.tr);
        Ok(())
    }
}

fn media_referencing_templates<'a>(
    notetypes: impl Iterator<Item = &'a Notetype>,
) -> Vec<Template<'a>> {
    notetypes
        .flat_map(|notetype| {
            notetype.templates.iter().flat_map(|card_type| {
                card_type.sides().into_iter().filter_map(|(format, front)| {
                    contains_media_tag(format)
                        .then(|| Template::new(&notetype.name, &card_type.name, front))
                })
            })
        })
        .collect()
}

fn write_template_report(buf: &mut String, templates: &[Template], tr: &I18n) {
    if templates.is_empty() {
        return;
    }
    writeln!(buf, "\n{}", &tr.media_check_template_refs_header()).unwrap();
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
    use super::*;
    use crate::collection::open_test_collection;

    #[test]
    fn should_report_media_referencing_template() {
        let mut col = open_test_collection();
        let mut nt = Notetype {
            name: String::from("foo"),
            ..Default::default()
        };
        nt.add_field("baz");
        nt.add_template("bar", "{{baz}}", "<img src={{baz}}>");
        col.add_notetype(&mut nt, true).unwrap();

        let mut buf = String::new();
        col.report_media_referencing_templates(&mut buf).unwrap();

        let mut templates = buf.trim().lines().skip(1);
        assert_eq!(templates.next(), Some("foo: bar (Back Template)"));
        assert!(templates.next().is_none());
    }
}
