// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#![cfg(test)]

use std::borrow::Cow;
use std::env;
use std::iter;
use std::sync::LazyLock;
use std::time::Duration;

use anki::links::help_page_link_suffix;
use anki::links::help_page_to_link;
use anki::links::HelpPage;
use futures::StreamExt;
use itertools::Itertools;
use linkcheck::validation::check_web;
use linkcheck::validation::Context;
use linkcheck::validation::Reason;
use linkcheck::BasicContext;
use regex::Regex;
use reqwest::Url;
use strum::IntoEnumIterator;

const WEB_TIMEOUT: Duration = Duration::from_secs(60);

/// Aggregates [`Outcome`]s by collecting the error messages of the invalid
/// ones.
#[derive(Default)]
struct Outcomes(Vec<String>);

#[derive(Debug)]
enum Outcome {
    Valid,
    Invalid(String),
}

#[derive(Clone)]
enum CheckableUrl {
    HelpPage(HelpPage),
    String(&'static str),
}

impl CheckableUrl {
    fn url(&self) -> Cow<'_, str> {
        match *self {
            Self::HelpPage(page) => help_page_to_link(page).into(),
            Self::String(s) => s.into(),
        }
    }

    fn anchor(&self) -> Cow<'_, str> {
        match *self {
            Self::HelpPage(page) => help_page_link_suffix(page).into(),
            Self::String(s) => s.split('#').next_back().unwrap_or_default().into(),
        }
    }
}

impl From<HelpPage> for CheckableUrl {
    fn from(value: HelpPage) -> Self {
        Self::HelpPage(value)
    }
}

impl From<&'static str> for CheckableUrl {
    fn from(value: &'static str) -> Self {
        Self::String(value)
    }
}

fn ts_help_pages() -> impl Iterator<Item = &'static str> {
    static QUOTED_URL: LazyLock<Regex> = LazyLock::new(|| Regex::new("\"(http.+)\"").unwrap());

    QUOTED_URL
        .captures_iter(include_str!("../../../ts/lib/tslib/help-page.ts"))
        .map(|caps| caps.get(1).unwrap().as_str())
}

#[tokio::test]
async fn check_links() {
    if env::var("ONLINE_TESTS").is_err() {
        println!("test disabled; ONLINE_TESTS not set");
        return;
    }
    let ctx = BasicContext::default();
    let result = futures::stream::iter(
        HelpPage::iter()
            .map(CheckableUrl::from)
            .chain(ts_help_pages().map(CheckableUrl::from)),
    )
    .map(|page| check_url(page, &ctx))
    .buffer_unordered(ctx.concurrency())
    .collect::<Outcomes>()
    .await;
    if !result.0.is_empty() {
        panic!("{}", result.message());
    }
}

async fn check_url(page: CheckableUrl, ctx: &BasicContext) -> Outcome {
    let link = page.url();
    match Url::parse(&link) {
        Ok(url) if url.as_str() == link => {
            let future = check_web(&url, ctx);
            let timeout = tokio::time::timeout(WEB_TIMEOUT, future);
            match timeout.await {
                Err(_) => Outcome::Invalid(format!("Timed out: {link}")),
                Ok(Ok(())) => Outcome::Valid,
                Ok(Err(Reason::Dom)) => Outcome::Invalid(format!(
                    "'#{}' not found on '{}{}'",
                    url.fragment().unwrap(),
                    url.domain().unwrap(),
                    url.path(),
                )),
                Ok(Err(Reason::Web(err))) => Outcome::Invalid(err.to_string()),
                _ => unreachable!(),
            }
        }
        Ok(_) => Outcome::Invalid(format!("'{}' is not a valid URL part", page.anchor(),)),
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
