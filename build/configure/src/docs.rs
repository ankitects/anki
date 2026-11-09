// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use anyhow::Result;
use ninja_gen::docs::cog_render;
use ninja_gen::docs::CogRender;
use ninja_gen::docs::DocsCopy;
use ninja_gen::glob;
use ninja_gen::inputs;
use ninja_gen::node::Prettier;
use ninja_gen::Build;

pub fn check_docs(build: &mut Build) -> Result<()> {
    check_copy(build)?;
    check_cog(build)?;
    Ok(())
}

const LOCALES: &str = "ar,de,es,fr,id,it,ja,pl,pt,ru,uk,uz,zh-Hans";

fn check_copy(build: &mut Build) -> Result<()> {
    build.add_action(
        "docs:release:copy",
        DocsCopy {
            locale: LOCALES,
            check_only: false,
        },
    )?;
    // So we don't get a huge diff if we manually edit the mintlify-docs branch
    build.add_action(
        "docs:release:copy:prettier",
        Prettier {
            inputs: inputs![glob!["docs-site/**/*.mdx"], ":docs:release:copy"],
            pattern: "docs-site/**/*.mdx",
            check_only: false,
        },
    )?;
    // Disabled until we have enough translation interest to justify pre-filling all
    // the untranslated pages
    /* build.add_action(
        "check:format:docs:copy",
        DocsCopy {
            locale: LOCALES,
            check_only: true,
        },
    )?; */
    Ok(())
}

fn check_cog(build: &mut Build) -> Result<()> {
    cog_render(
        build,
        "docs",
        inputs![glob!["docs/*.md"], glob!["docs-site/*.mdx"],],
        inputs![
            "pylib/tools/genhooks.py",
            "tools/mintlify_hooks.py",
            "tools/auto_update_untranslated.py",
            "docs/cogdocs.py",
        ],
    )?;

    // Split to prevent command too long errors
    for locale in LOCALES.split(',') {
        // For the mintlify-docs branch
        build.add_action(
            format!("docs:release:cog:{locale}"),
            CogRender {
                inputs: &inputs![format!(":docs:release:copy:{locale}")],
                deps: &inputs![
                    glob!["docs-site/*.mdx"],
                    glob!["docs-site/addons/**.mdx"],
                    glob!["docs-site/ankimobile/**.mdx"],
                    glob!["docs-site/developers/**.mdx"],
                    glob!["docs-site/faqs/**.mdx"],
                    glob!["docs-site/manual/**.mdx"],
                    glob!["docs-site/releases/**.mdx"],
                    glob!["docs-site/translators/**.mdx"],
                ],
                check_only: false,
            },
        )?;
    }
    Ok(())
}
