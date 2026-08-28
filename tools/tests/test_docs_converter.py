# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

from tools.docs_converter import (
    escape_text_preserve_html,
    format_page,
)

# ---------------------------------------------------------------------------
# Helper
# ---------------------------------------------------------------------------


def fmt(src: str, lang: str = "") -> str:
    """Wrap format_page with a minimal title so tests don't trip the WARN path."""
    return format_page(f"# T\n\n{src}", lang)


def body(result: str) -> str:
    """Strip the frontmatter block so assertions are less brittle."""
    # frontmatter ends at the first blank line after '---'
    lines = result.splitlines()
    for i, line in enumerate(lines):
        if i > 0 and line == "---":
            return "\n".join(lines[i + 1 :]).lstrip("\n")
    return result


# ===========================================================================
# escape_text_preserve_html
# ===========================================================================


class TestEscapeBracesAndDollars:
    def test_braces_escaped_in_plain_text(self) -> None:
        result = escape_text_preserve_html("Hello {world}!")
        assert "\\{world\\}" in result

    def test_dollar_escaped_in_plain_text(self) -> None:
        result = escape_text_preserve_html("Price is $5")
        assert "\\$5" in result

    def test_double_dollar_not_escaped(self) -> None:
        # $$ is a LaTeX display-math delimiter and must pass through unchanged.
        result = escape_text_preserve_html("Use $$x^2$$ here")
        assert "$$x^2$$" in result

    def test_braces_not_escaped_inside_fenced_code(self) -> None:
        src = "```text\n{{field}}\n```"
        result = escape_text_preserve_html(src)
        assert "{{field}}" in result
        assert "\\{" not in result

    def test_dollars_not_escaped_inside_fenced_code(self) -> None:
        src = "```text\n$1.00\n```"
        result = escape_text_preserve_html(src)
        assert "$1.00" in result
        assert "\\$" not in result

    def test_braces_not_escaped_inside_inline_code(self) -> None:
        result = escape_text_preserve_html("See `{field}` here")
        assert "`{field}`" in result

    def test_dollars_not_escaped_inside_inline_code(self) -> None:
        result = escape_text_preserve_html("Use `$var` in code")
        assert "`$var`" in result


class TestHtmlCommentConversion:
    def test_html_comment_converted_to_mdx(self) -> None:
        result = escape_text_preserve_html("<!-- note -->")
        assert "{/*" in result
        assert "*/" in result
        assert "<!--" not in result

    def test_comment_inside_fenced_code_untouched(self) -> None:
        src = "```html\n<!-- comment -->\n```"
        result = escape_text_preserve_html(src)
        assert "<!-- comment -->" in result


class TestAngleBracketEscaping:
    def test_angle_brackets_escaped_in_plain_text(self) -> None:
        result = escape_text_preserve_html("a < b and b > a")
        assert "&lt;" in result
        assert "&gt;" in result

    def test_angle_brackets_not_escaped_inside_fenced_code(self) -> None:
        src = "```text\na < b\n```"
        result = escape_text_preserve_html(src)
        assert "<" in result

    def test_angle_brackets_not_escaped_inside_inline_code(self) -> None:
        result = escape_text_preserve_html("See `a < b`")
        assert "`a < b`" in result


class TestVoidElementNormalization:
    def test_br_becomes_self_closing(self) -> None:
        result = escape_text_preserve_html("line<br>end")
        assert "<br />" in result

    def test_br_closing_tag_dropped(self) -> None:
        result = escape_text_preserve_html("line<br></br>end")
        assert "</br>" not in result

    def test_img_becomes_self_closing(self) -> None:
        result = escape_text_preserve_html('<img src="x.png">')
        assert "/>" in result

    def test_hr_becomes_self_closing(self) -> None:
        result = escape_text_preserve_html("<hr>")
        assert "<hr />" in result


class TestUnquotedAttributeQuoting:
    def test_unquoted_attr_gets_quoted(self) -> None:
        result = escape_text_preserve_html("<span style=color:red>")
        assert 'style="color:red"' in result


# ===========================================================================
# format_page — frontmatter
# ===========================================================================


class TestFrontmatter:
    def test_title_extracted(self) -> None:
        result = format_page("# My Page\n\nBody.\n")
        assert 'title: "My Page"' in result

    def test_title_quotes_escaped(self) -> None:
        result = format_page('# Say "hello"\n\nBody.\n')
        assert 'title: "Say \\"hello\\""' in result

    def test_original_heading_removed(self) -> None:
        result = format_page("# My Page\n\nBody.\n")
        lines = result.splitlines()
        heading_lines = [l for l in lines if l.startswith("# My Page")]
        assert not heading_lines


# ===========================================================================
# format_page — .md link stripping
# ===========================================================================


class TestMdLinkStripping:
    def test_md_extension_removed(self) -> None:
        result = body(fmt("[link](foo.md)"))
        assert "[link](foo)" in result
        assert ".md" not in result

    def test_md_extension_removed_with_anchor(self) -> None:
        result = body(fmt("[link](foo.md#bar)"))
        assert "[link](foo#bar)" in result

    def test_md_extension_removed_with_query(self) -> None:
        result = body(fmt("[link](foo.md?x=1)"))
        assert "[link](foo?x=1)" in result

    def test_non_md_link_unchanged(self) -> None:
        result = body(fmt("[link](foo.html)"))
        assert "[link](foo.html)" in result


# ===========================================================================
# format_page — heading anchors
# ===========================================================================


class TestHeadingAnchors:
    def test_heading_anchor_converted_to_a_tag(self) -> None:
        result = body(fmt("## My Section {#my-anchor}"))
        assert '<a id="my-anchor"></a>' in result
        assert "## My Section" in result
        assert "{#my-anchor}" not in result


# ===========================================================================
# format_page — quicklinks
# ===========================================================================


class TestQuicklinks:
    def test_url_autolink_expanded(self) -> None:
        result = body(fmt("<https://example.com>"))
        assert "[https://example.com](https://example.com)" in result

    def test_email_autolink_gets_mailto(self) -> None:
        result = body(fmt("<user@example.com>"))
        assert "mailto:" in result


# ===========================================================================
# format_page — admonish → callout
# ===========================================================================


class TestAdmonishConversion:
    def test_note_admonish_converted(self) -> None:
        src = "```admonish note\nSome note.\n```"
        result = body(fmt(src))
        assert "<Note>" in result
        assert "Some note." in result
        assert "admonish" not in result

    def test_warning_admonish_converted(self) -> None:
        src = "```admonish warning\nBe careful.\n```"
        result = body(fmt(src))
        assert "<Warning>" in result

    def test_danger_admonish_converted(self) -> None:
        src = "```admonish danger\nDangerous!\n```"
        result = body(fmt(src))
        assert "<Danger>" in result

    def test_summary_admonish_gets_heading(self) -> None:
        src = "```admonish summary\nContent.\n```"
        result = body(fmt(src))
        assert "**Summary**" in result
        assert "<Note>" in result

    def test_info_admonish_converted(self) -> None:
        src = "```admonish info\nFYI.\n```"
        result = body(fmt(src))
        assert "<Info>" in result

    def test_caution_admonish_maps_to_warning(self) -> None:
        src = "```admonish caution\nWatch out.\n```"
        result = body(fmt(src))
        assert "<Warning>" in result

    def test_example_admonish_maps_to_info(self) -> None:
        src = "```admonish example\nE.g. this.\n```"
        result = body(fmt(src))
        assert "<Info>" in result


# ===========================================================================
# format_page — relative link replacement
# ===========================================================================


class TestRelativeLinkReplacement:
    def test_docs_ankiweb_net_converted(self) -> None:
        result = body(fmt("[x](https://docs.ankiweb.net/foo.html)"))
        assert "/manual/foo" in result
        assert "docs.ankiweb.net" not in result

    def test_faqs_domain_converted(self) -> None:
        result = body(fmt("[x](https://faqs.ankiweb.net/bar.html)"))
        assert "/faqs/bar" in result

    def test_ankimobile_domain_converted(self) -> None:
        result = body(fmt("[x](https://docs.ankimobile.net/intro.html)"))
        assert "/ankimobile/intro" in result

    def test_addon_docs_domain_converted(self) -> None:
        result = body(fmt("[x](https://addon-docs.ankiweb.net/intro.html)"))
        assert "/addons/intro" in result

    def test_anchor_preserved_after_conversion(self) -> None:
        result = body(fmt("[x](https://docs.ankiweb.net/foo.html#section)"))
        assert "/manual/foo#section" in result

    def test_language_prefix_prepended(self) -> None:
        result = body(fmt("[x](https://docs.ankiweb.net/foo.html)", lang="ar"))
        assert "/ar/manual/foo" in result

    def test_autolink_url_also_converted(self) -> None:
        # Regression: suffix regex was eating ](url) and leaving href unconverted.
        result = body(
            fmt(
                "<https://docs.ankiweb.net/templates/fields.html#text-to-speech-for-individual-fields>",
                lang="ar",
            )
        )
        assert (
            "/ar/manual/templates/fields#text-to-speech-for-individual-fields" in result
        )
        assert "docs.ankiweb.net" not in result

    def test_unrecognised_domain_not_converted(self) -> None:
        result = body(fmt("[x](https://example.com/foo.html)"))
        assert "example.com/foo.html" in result


# ===========================================================================
# format_page — toc comment removal
# ===========================================================================


class TestTocRemoval:
    def test_toc_comment_removed(self) -> None:
        result = body(fmt("<!-- toc -->\n\nBody."))
        assert "toc" not in result

    def test_toc_comment_case_insensitive(self) -> None:
        result = body(fmt("<!-- TOC -->\n\nBody."))
        assert "TOC" not in result


# ===========================================================================
# format_page — indented code blocks (angle brackets & braces inside)
# ===========================================================================


class TestIndentedCodeBlocks:
    def test_angle_brackets_escaped_in_indented_block(self) -> None:
        result = body(fmt("    <span>"))
        assert "&lt;span&gt;" in result

    def test_braces_escaped_in_indented_block(self) -> None:
        result = body(fmt("    {{field}}"))
        assert "\\{\\{field\\}\\}" in result

    def test_dollars_escaped_in_indented_block(self) -> None:
        result = body(fmt("    $1.00"))
        assert "\\$1.00" in result
