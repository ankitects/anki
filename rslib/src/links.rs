// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use strum::{Display, EnumIter, EnumString};

static HELP_SITE: &'static str = "https://docs.ankiweb.net/";

#[derive(Debug, PartialEq, Clone, Copy, Display, EnumIter, EnumString)]
pub enum HelpPage {
    #[strum(serialize = "getting-started#note-types")]
    Notetype,
    #[strum(serialize = "browsing")]
    Browsing,
    #[strum(serialize = "browsing#find-and-replace")]
    BrowsingFindAndReplace,
    #[strum(serialize = "browsing#notes")]
    BrowsingNotesMenu,
    #[strum(serialize = "studying#keyboard-shortcuts")]
    KeyboardShortcuts,
    #[strum(serialize = "editing")]
    Editing,
    #[strum(serialize = "editing#adding-cards-and-notes")]
    AddingCardAndNote,
    #[strum(serialize = "editing#adding-a-note-type")]
    AddingNotetype,
    #[strum(serialize = "math#latex")]
    Latex,
    #[strum(serialize = "preferences")]
    Preferences,
    #[strum(serialize = "")]
    Index,
    #[strum(serialize = "templates/intro")]
    Templates,
    #[strum(serialize = "filtered-decks")]
    FilteredDeck,
    #[strum(serialize = "importing")]
    Importing,
    #[strum(serialize = "editing#customizing-fields")]
    CustomizingFields,
    #[strum(serialize = "deck-options")]
    DeckOptions,
    #[strum(serialize = "editing#features")]
    EditingFeatures,
}

pub fn help_page_link(page: HelpPage) -> String {
    format!("{}{}", HELP_SITE, page)
}

pub fn help_page_link_from_str(page: &str) -> String {
    format!("{}{}", HELP_SITE, page)
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
        match Url::parse(&help_page_link(page)) {
            Ok(url) => match check_web(&url, ctx).await {
                Ok(()) => Outcome::Valid,
                Err(Reason::Dom) => {
                    Outcome::Invalid(format!("'{}' not found on '{}'", page, HELP_SITE))
                }
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
            iter::once(&format!("{} links could not be validated:", self.0.len()))
                .chain(self.0.iter())
                .join("\n  - ")
        }
    }
}
