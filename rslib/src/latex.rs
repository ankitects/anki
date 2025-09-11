// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;
use std::sync::LazyLock;

use regex::Captures;
use regex::Regex;

use crate::cloze::expand_clozes_to_reveal_latex;
use crate::media::files::sha1_of_data;
use crate::text::strip_html;

pub(crate) static LATEX: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(
        r"(?xsi)
            \[latex\](.+?)\[/latex\]     # 1 - standard latex
            |
            \[\$\](.+?)\[/\$\]           # 2 - inline math
            |
            \[\$\$\](.+?)\[/\$\$\]       # 3 - math environment
            ",
    )
    .unwrap()
});
static LATEX_NEWLINES: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(
        r#"(?xi)
            <br( /)?>
            |
            <div>
        "#,
    )
    .unwrap()
});

pub(crate) fn contains_latex(text: &str) -> bool {
    LATEX.is_match(text)
}

#[derive(Debug, PartialEq, Eq)]
pub struct ExtractedLatex {
    pub fname: String,
    pub latex: String,
}

/// Expand any cloze deletions, then extract LaTeX.
pub(crate) fn extract_latex_expanding_clozes(
    text: &str,
    svg: bool,
) -> (Cow<'_, str>, Vec<ExtractedLatex>) {
    if text.contains("{{c") {
        let expanded = expand_clozes_to_reveal_latex(text);
        let (text, extracts) = extract_latex(&expanded, svg);
        (text.into_owned().into(), extracts)
    } else {
        extract_latex(text, svg)
    }
}

/// Extract LaTeX from the provided text.
/// Expects cloze deletions to already be expanded.
pub fn extract_latex(text: &str, svg: bool) -> (Cow<'_, str>, Vec<ExtractedLatex>) {
    let mut extracted = vec![];

    let new_text = LATEX.replace_all(text, |caps: &Captures| {
        // [latex] blocks preserve newlines between <br>/<div>
        // [$] and [$$] blocks remove <br>/<div> completely so that
        // no blank lines are introduced inside math environments.
        let latex = match (caps.get(1), caps.get(2), caps.get(3)) {
            (Some(m), _, _) => strip_html_for_latex(m.as_str()).into(),
            (_, Some(m), _) => format!("${}$", strip_html_for_latex_math(m.as_str())),
            (_, _, Some(m)) => format!(
                r"\begin{{displaymath}}{}\end{{displaymath}}",
                strip_html_for_latex_math(m.as_str())
            ),
            _ => unreachable!(),
        };

        let fname = fname_for_latex(&latex, svg);
        let img_link = image_link_for_fname(&latex, &fname);
        extracted.push(ExtractedLatex { fname, latex });

        img_link
    });

    (new_text, extracted)
}

fn strip_html_for_latex(html: &str) -> Cow<'_, str> {
    let mut out: Cow<str> = html.into();
    if let Cow::Owned(o) = LATEX_NEWLINES.replace_all(html, "\n") {
        out = o.into();
    }
    if let Cow::Owned(o) = strip_html(out.as_ref()) {
        out = o.into();
    }

    out
}

/// Removes HTML breaks (<br>, <div>) from math blocks instead of
/// converting them to newlines. This prevents LaTeX environments
/// from being broken by unintended blank lines.
fn strip_html_for_latex_math(html: &str) -> Cow<'_, str> {
    let mut out: Cow<str> = html.into();
    if let Cow::Owned(o) = LATEX_NEWLINES.replace_all(html, "") {
        out = o.into();
    }
    if let Cow::Owned(o) = strip_html(out.as_ref()) {
        out = o.into();
    }
    out
}

fn fname_for_latex(latex: &str, svg: bool) -> String {
    let ext = if svg { "svg" } else { "png" };
    let csum = hex::encode(sha1_of_data(latex.as_bytes()));

    format!("latex-{csum}.{ext}")
}

fn image_link_for_fname(src: &str, fname: &str) -> String {
    format!(
        "<img class=latex alt=\"{}\" src=\"{}\">",
        htmlescape::encode_attribute(src),
        fname
    )
}

#[cfg(test)]
mod test {
    use crate::latex::extract_latex;
    use crate::latex::ExtractedLatex;

    #[test]
    fn latex() {
        let fname = "latex-ef30b3f4141c33a5bf7044b0d1961d3399c05d50.png";
        assert_eq!(
            extract_latex("a[latex]one<br>and<div>two[/latex]b", false),
            (
                format!("a<img class=latex alt=\"one&#x0A;and&#x0A;two\" src=\"{fname}\">b").into(),
                vec![ExtractedLatex {
                    fname: fname.into(),
                    latex: "one\nand\ntwo".into()
                }]
            )
        );

        assert_eq!(
            extract_latex("[$]<b>hello</b>&nbsp; world[/$]", true).1,
            vec![ExtractedLatex {
                fname: "latex-060219fbf3ddb74306abddaf4504276ad793b029.svg".to_string(),
                latex: "$hello  world$".to_string()
            }]
        );

        assert_eq!(
            extract_latex("[$$]math &amp; stuff[/$$]", false).1,
            vec![ExtractedLatex {
                fname: "latex-8899f3f849ffdef6e4e9f2f34a923a1f608ebc07.png".to_string(),
                latex: r"\begin{displaymath}math & stuff\end{displaymath}".to_string()
            }]
        );
    }

    /// Ensures that math blocks do not contain unintended blank lines
    /// when <br> or <div> elements are present in the HTML.
    #[test]
    fn no_blank_lines_in_math() {
        let input = "[$$]\\begin{tikzcd}x & y\\end{tikzcd}[/$$]";
        let (_, extracts) = extract_latex(input, false);
        let extracted = &extracts[0].latex;

        assert!(
            !extracted.contains("\n\n"),
            "Should not contain blank lines inside math"
        );
    }
}
