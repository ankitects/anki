// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use anyhow::Result;
use ninja_gen::cog::cog_render;
use ninja_gen::glob;
use ninja_gen::inputs;
use ninja_gen::Build;

pub fn check_cog(build: &mut Build) -> Result<()> {
    cog_render(
        build,
        "docs",
        inputs![
            glob!["docs/*.md"],
            glob!["docs-site/*.mdx"],
            glob!["docs-site/{ar,de,es,fa,fr,id,it,ja,pl,pt,ru,uk,uz,zh-Hans}/**/*.mdx"],
        ],
        inputs![
            "pylib/tools/genhooks.py",
            "tools/mintlify_hooks.py",
            "tools/auto_update_untranslated.py",
            "docs/cogdocs.py",
            glob!["docs-site/*.mdx"],
            glob!["docs-site/addons/**.mdx"],
            glob!["docs-site/ankimobile/**.mdx"],
            glob!["docs-site/developers/**.mdx"],
            glob!["docs-site/faqs/**.mdx"],
            glob!["docs-site/manual/**.mdx"],
            glob!["docs-site/releases/**.mdx"],
            glob!["docs-site/translators/**.mdx"],
        ],
    )
}
