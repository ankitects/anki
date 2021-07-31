// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki::links::HelpPage;
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
            panic!("{}", result.message());
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
            iter::once("invalid links found:")
                .chain(self.0.iter().map(String::as_str))
                .join("\n  - ")
        }
    }
}
