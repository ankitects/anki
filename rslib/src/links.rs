// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::backend_proto::links::help_page_link_request::HelpPage;

static HELP_SITE: &'static str = "https://docs.ankiweb.net/";

impl HelpPage {
    pub fn to_link(self) -> String {
        format!("{}{}", HELP_SITE, self.to_link_suffix())
    }

    fn to_link_suffix(self) -> &'static str {
        match self {
            HelpPage::NoteType => "getting-started#note-types",
            HelpPage::Browsing => "browsing",
            HelpPage::BrowsingFindAndReplace => "browsing#find-and-replace",
            HelpPage::BrowsingNotesMenu => "browsing#notes",
            HelpPage::KeyboardShortcuts => "studying#keyboard-shortcuts",
            HelpPage::Editing => "editing",
            HelpPage::AddingCardAndNote => "editing#adding-cards-and-notes",
            HelpPage::AddingANoteType => "editing#adding-a-note-type",
            HelpPage::Latex => "math#latex",
            HelpPage::Preferences => "preferences",
            HelpPage::Index => "",
            HelpPage::Templates => "templates/intro",
            HelpPage::FilteredDeck => "filtered-decks",
            HelpPage::Importing => "importing",
            HelpPage::CustomizingFields => "editing#customizing-fields",
            HelpPage::DeckOptions => "deck-options",
            HelpPage::EditingFeatures => "editing#features",
        }
    }
}

#[cfg(test)]
mod test {
    use super::*;

    use futures::StreamExt;
    use itertools::Itertools;
    use linkcheck::{
        validation::{check_web, Context, Reason},
        BasicContext,
    };
    use reqwest::Url;
    use std::iter;
    use strum::IntoEnumIterator;

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
        if result.0.len() > 0 {
            panic!(result.message());
        }
    }

    async fn check_page(page: HelpPage, ctx: &BasicContext) -> Outcome {
        match Url::parse(&page.to_link()) {
            Ok(url) => match check_web(&url, ctx).await {
                Ok(()) => Outcome::Valid,
                Err(Reason::Dom) => Outcome::Invalid(format!(
                    "'#{}' not found on '{}{}'",
                    url.fragment().unwrap(),
                    url.domain().unwrap(),
                    url.path(),
                )),
                Err(Reason::Web(err)) => Outcome::Invalid(err.to_string()),
                _ => unreachable!(),
            },
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
