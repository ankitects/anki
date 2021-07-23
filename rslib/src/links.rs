// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::backend_proto::links::help_page_link_request::HelpPage;

static HELP_SITE: &str = "https://docs.ankiweb.net/";

impl HelpPage {
    pub fn to_link(self) -> String {
        format!("{}{}", HELP_SITE, self.to_link_suffix())
    }

    fn to_link_suffix(self) -> &'static str {
        match self {
            HelpPage::NoteType => "getting-started.html#note-types",
            HelpPage::Browsing => "browsing.html",
            HelpPage::BrowsingFindAndReplace => "browsing.html#find-and-replace",
            HelpPage::BrowsingNotesMenu => "browsing.html#notes",
            HelpPage::KeyboardShortcuts => "studying.html#keyboard-shortcuts",
            HelpPage::Editing => "editing.html",
            HelpPage::AddingCardAndNote => "editing.html#adding-cards-and-notes",
            HelpPage::AddingANoteType => "editing.html#adding-a-note-type",
            HelpPage::Latex => "math.html#latex",
            HelpPage::Preferences => "preferences.html",
            HelpPage::Index => "",
            HelpPage::Templates => "templates/intro.html",
            HelpPage::FilteredDeck => "filtered-decks.html",
            HelpPage::Importing => "importing.html",
            HelpPage::CustomizingFields => "editing.html#customizing-fields",
            HelpPage::DeckOptions => "deck-options.html",
            HelpPage::EditingFeatures => "editing.html#features",
        }
    }
}

#[cfg(test)]
mod test {
    use std::iter;

    use futures::StreamExt;
    use itertools::Itertools;
    use linkcheck::{
        validation::{check_web, Context, Reason},
        BasicContext,
    };
    use reqwest::Url;
    use strum::IntoEnumIterator;

    use super::*;

    /// Aggregates [`Outcome`]s by collecting the error messages of the invalid ones.
    #[derive(Default)]
    struct Outcomes(Vec<String>);

    enum Outcome {
        Valid,
        Invalid(String),
    }

    #[tokio::test]
    async fn check_links() {
        let ctx = BasicContext::default();
        let result = futures::stream::iter(HelpPage::iter())
            .map(|page| check_page(page, &ctx))
            .buffer_unordered(ctx.concurrency())
            .collect::<Outcomes>()
            .await;
        if !result.0.is_empty() {
            panic!("invalid links found: {}", result.message());
        }
    }

    async fn check_page(page: HelpPage, ctx: &BasicContext) -> Outcome {
        let link = page.to_link();
        match Url::parse(&link) {
            Ok(url) => {
                if url.as_str() == link {
                    match check_web(&url, ctx).await {
                        Ok(()) => Outcome::Valid,
                        Err(Reason::Dom) => Outcome::Invalid(format!(
                            "'#{}' not found on '{}{}'",
                            url.fragment().unwrap(),
                            url.domain().unwrap(),
                            url.path(),
                        )),
                        Err(Reason::Web(err)) => Outcome::Invalid(err.to_string()),
                        _ => unreachable!(),
                    }
                } else {
                    Outcome::Invalid(format!(
                        "'{}' is not a valid URL part",
                        page.to_link_suffix(),
                    ))
                }
            }
            Err(err) => Outcome::Invalid(err.to_string()),
        }
    }

    impl Extend<Outcome> for Outcomes {
        fn extend<T: IntoIterator<Item = Outcome>>(&mut self, items: T) {
            for outcome in items {
                match outcome {
                    Outcome::Valid => (),
                    Outcome::Invalid(err) => self.0.push(err),
                }
            }
        }
    }

    impl Outcomes {
        fn message(&self) -> String {
            iter::once(&format!("{} link(s) could not be validated:", self.0.len()))
                .chain(self.0.iter())
                .join("\n  - ")
        }
    }
}
