// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use anyhow::Result;

use crate::action::BuildAction;
use crate::build::FilesHandle;
use crate::glob;
use crate::hash::simple_hash;
use crate::input::BuildInput;
use crate::inputs;
use crate::Build;

pub struct CogRender<'a> {
    pub inputs: &'a BuildInput,
    pub deps: &'a BuildInput,
    pub check_only: bool,
}

impl BuildAction for CogRender<'_> {
    fn command(&self) -> &str {
        "$cog -I . --markers \"<<<<cog >>>> <<<<end>>>>\" $mode $in"
    }

    fn files(&mut self, build: &mut impl FilesHandle) {
        build.add_inputs("", self.deps);
        build.add_inputs("in", self.inputs);
        build.add_inputs("cog", inputs![":pyenv:cog"]);
        build.add_variable(
            "mode",
            if self.check_only {
                "--check --diff"
            } else {
                "-r $in"
            },
        );

        let hash = simple_hash(self.inputs);
        build.add_output_stamp(format!(
            "tests/cog_check.{}.{hash}",
            if self.check_only { "check" } else { "render" }
        ));
    }
}

pub fn cog_render(
    build: &mut Build,
    group: &str,
    inputs: BuildInput,
    deps: BuildInput,
) -> Result<()> {
    build.add_action(
        format!("check:format:cog:{group}"),
        CogRender {
            inputs: &inputs,
            deps: &deps,
            check_only: true,
        },
    )?;
    build.add_action(
        format!("format:cog:{group}"),
        CogRender {
            inputs: &inputs,
            deps: &deps,
            check_only: false,
        },
    )?;
    Ok(())
}

pub struct DocsCopy<'a> {
    pub locale: &'a str,
    pub check_only: bool,
}

impl BuildAction for DocsCopy<'_> {
    fn command(&self) -> &str {
        "$pyenv_bin $script copy $locale $args"
    }

    fn files(&mut self, build: &mut impl FilesHandle) {
        build.add_inputs("pyenv_bin", inputs![":pyenv:bin"]);
        build.add_inputs("script", inputs!["tools/docs.py"]);
        build.add_variable("locale", self.locale);
        build.add_variable("args", if self.check_only { "--dry-run" } else { "" });

        let inputs = inputs![
            glob!["docs-site/*.mdx"],
            glob!["docs-site/addons/**/*.mdx"],
            glob!["docs-site/ankimobile/**/*.mdx"],
            glob!["docs-site/developers/**/*.mdx"],
            glob!["docs-site/faqs/**/*.mdx"],
            glob!["docs-site/manual/**/*.mdx"],
            glob!["docs-site/releases/**/*.mdx"],
            glob!["docs-site/translators/**/*.mdx"],
        ];
        // Vec (Locale, Vec<Output>) to allow for multiple locales
        let outputs: Vec<(String, Vec<String>)> = self
            .locale
            .split(',')
            .map(|locale| {
                let locale = locale.trim().to_string();
                let files = build
                    .expand_inputs(&inputs)
                    .into_iter()
                    .map(|p| {
                        let p = p.replace('\\', "/");
                        let relative = p.strip_prefix("docs-site/").unwrap_or(&p);
                        format!("../docs-site/{locale}/{relative}")
                    })
                    .collect();
                (locale, files)
            })
            .collect();

        if self.check_only {
            for (_, output) in outputs {
                let hash = simple_hash(inputs![output]);
                build.add_output_stamp(format!("tests/docs_copy_check_{hash}.stamp"));
            }
        } else {
            for (locale, output) in outputs {
                build.add_inputs("", &inputs);
                build.add_outputs_ext(locale, output, true);
            }
        }
    }
}
