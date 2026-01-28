// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;

use pulldown_cmark::html;
use pulldown_cmark::Options;
use pulldown_cmark::Parser;

/// Render markdown to HTML with GitHub Flavored Markdown extensions.
///
/// Enabled extensions:
/// - Tables (GFM)
/// - Strikethrough (~~text~~)
/// - Task lists (- [ ] and - [x])
/// - Footnotes
///
/// # Example
/// ```
/// let html = render_markdown("| A | B |\n|---|---|\n| 1 | 2 |");
/// // Returns: <table>...</table>
/// ```
pub(crate) fn render_markdown(markdown: &str) -> Cow<'_, str> {
    // Return early if input is empty or contains no markdown-like syntax
    if markdown.is_empty() {
        return Cow::Borrowed(markdown);
    }

    let mut options = Options::empty();
    // Enable GFM tables: | col1 | col2 |
    options.insert(Options::ENABLE_TABLES);
    // Enable strikethrough: ~~deleted~~
    options.insert(Options::ENABLE_STRIKETHROUGH);
    // Enable task lists: - [ ] todo, - [x] done
    options.insert(Options::ENABLE_TASKLISTS);
    // Enable footnotes: [^1] and [^1]: footnote text
    options.insert(Options::ENABLE_FOOTNOTES);
    // Enable smart punctuation: "quotes" -> curly quotes, -- -> en-dash, --- -> em-dash
    options.insert(Options::ENABLE_SMART_PUNCTUATION);

    let mut buf = String::with_capacity(markdown.len() * 2);
    let parser = Parser::new_ext(markdown, options);
    html::push_html(&mut buf, parser);

    Cow::Owned(buf)
}

/// Render markdown without wrapping paragraphs in <p> tags.
/// Useful for inline content where paragraph tags would break layout.
pub(crate) fn render_markdown_inline(markdown: &str) -> Cow<'_, str> {
    let rendered = render_markdown(markdown);

    // Strip outer <p>...</p> tags if present (for single-paragraph content)
    if let Cow::Owned(s) = rendered {
        let trimmed = s.trim();
        if trimmed.starts_with("<p>") && trimmed.ends_with("</p>") && trimmed.matches("<p>").count() == 1 {
            let inner = &trimmed[3..trimmed.len() - 4];
            return Cow::Owned(inner.to_string());
        }
        Cow::Owned(s)
    } else {
        rendered
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_basic_markdown() {
        assert!(render_markdown("**bold**").contains("<strong>bold</strong>"));
        assert!(render_markdown("*italic*").contains("<em>italic</em>"));
        assert!(render_markdown("`code`").contains("<code>code</code>"));
    }

    #[test]
    fn test_tables() {
        let table = "| Header 1 | Header 2 |\n|----------|----------|\n| Cell 1   | Cell 2   |";
        let html = render_markdown(table);
        assert!(html.contains("<table>"));
        assert!(html.contains("<th>Header 1</th>"));
        assert!(html.contains("<td>Cell 1</td>"));
    }

    #[test]
    fn test_strikethrough() {
        let result = render_markdown("~~deleted~~");
        assert!(result.contains("<del>deleted</del>"));
    }

    #[test]
    fn test_task_lists() {
        let tasks = "- [ ] Todo\n- [x] Done";
        let html = render_markdown(tasks);
        assert!(html.contains("type=\"checkbox\""));
        assert!(html.contains("checked"));
    }

    #[test]
    fn test_footnotes() {
        let text = "Text with footnote[^1].\n\n[^1]: Footnote content.";
        let html = render_markdown(text);
        assert!(html.contains("footnote"));
    }

    #[test]
    fn test_code_blocks() {
        let code = "```rust\nfn main() {}\n```";
        let html = render_markdown(code);
        assert!(html.contains("<pre>"));
        assert!(html.contains("<code"));
    }

    #[test]
    fn test_empty_input() {
        assert_eq!(render_markdown(""), "");
    }

    #[test]
    fn test_inline_rendering() {
        let result = render_markdown_inline("**bold**");
        assert_eq!(result, "<strong>bold</strong>");
        assert!(!result.contains("<p>"));
    }

    #[test]
    fn test_multiline_keeps_paragraphs() {
        let text = "First paragraph.\n\nSecond paragraph.";
        let result = render_markdown_inline(text);
        // Multiple paragraphs should keep their structure
        assert!(result.contains("<p>"));
    }
}
