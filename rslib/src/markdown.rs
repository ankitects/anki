// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use pulldown_cmark::html;
use pulldown_cmark::Parser;

pub(crate) fn render_markdown(markdown: &str) -> String {
    let mut buf = String::with_capacity(markdown.len());
    let parser = Parser::new(markdown);
    html::push_html(&mut buf, parser);
    buf
}
