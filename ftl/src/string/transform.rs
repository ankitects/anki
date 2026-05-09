// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;

use anki_io::paths_in_dir;
use anyhow::Result;
use camino::Utf8Path;
use camino::Utf8PathBuf;
use clap::Args;
use clap::ValueEnum;
use fluent_syntax::ast::Entry;
use fluent_syntax::ast::Expression;
use fluent_syntax::ast::InlineExpression;
use fluent_syntax::ast::Message;
use fluent_syntax::ast::Pattern;
use fluent_syntax::ast::PatternElement;
use fluent_syntax::ast::Resource;
use regex::Regex;

use crate::string::parse_file;
use crate::string::serialize_file;

#[derive(Args)]
pub struct TransformArgs {
    /// The folder which contains the different languages as subfolders, e.g.
    /// ftl/core-repo/core
    lang_folder: Utf8PathBuf,
    // What should be replaced.
    target: TransformTarget,
    regex: String,
    replacement: String,
    // limit replacement to a single key
    // #[clap(long)]
    // key: Option<String>,
}

#[derive(ValueEnum, Clone, PartialEq, Eq)]
pub enum TransformTarget {
    Text,
    Variable,
}

pub fn transform(args: TransformArgs) -> Result<()> {
    let regex = Regex::new(&args.regex)?;
    for lang in super::all_langs(&args.lang_folder)? {
        for ftl in paths_in_dir(&lang)? {
            transform_ftl(&ftl, &regex, &args)?;
        }
    }
    if let Some(template_dir) = super::additional_template_folder(&args.lang_folder) {
        // Our templates are also stored in the source tree, and need to be updated too.
        for ftl in paths_in_dir(template_dir)? {
            transform_ftl(&ftl, &regex, &args)?;
        }
    }

    Ok(())
}

fn transform_ftl(ftl: &Utf8Path, regex: &Regex, args: &TransformArgs) -> Result<()> {
    let mut resource = parse_file(ftl)?;
    if transform_ftl_inner(&mut resource, regex, args) {
        println!("Updating {ftl}");
        serialize_file(ftl, &resource)?;
    }
    Ok(())
}

fn transform_ftl_inner(
    resource: &mut Resource<String>,
    regex: &Regex,
    args: &TransformArgs,
) -> bool {
    let mut changed = false;
    for entry in &mut resource.body {
        if let Entry::Message(Message {
            value: Some(value), ..
        }) = entry
        {
            changed |= transform_pattern(value, regex, args);
        }
    }
    changed
}

/// True if changed.
fn transform_pattern(pattern: &mut Pattern<String>, regex: &Regex, args: &TransformArgs) -> bool {
    let mut changed = false;
    for element in &mut pattern.elements {
        match args.target {
            TransformTarget::Text => {
                changed |= transform_text(element, regex, args);
            }
            TransformTarget::Variable => {
                changed |= transform_variable(element, regex, args);
            }
        }
    }
    changed
}

fn transform_variable(
    pattern: &mut PatternElement<String>,
    regex: &Regex,
    args: &TransformArgs,
) -> bool {
    let mut changed = false;
    let mut maybe_update = |val: &mut String| {
        if let Cow::Owned(new_val) = regex.replace_all(val, &args.replacement) {
            changed = true;
            *val = new_val;
        }
    };
    if let PatternElement::Placeable { expression } = pattern {
        match expression {
            Expression::Select { selector, variants } => {
                if let InlineExpression::VariableReference { id } = selector {
                    maybe_update(&mut id.name)
                }
                for variant in variants {
                    changed |= transform_pattern(&mut variant.value, regex, args);
                }
            }
            Expression::Inline(expression) => {
                if let InlineExpression::VariableReference { id } = expression {
                    maybe_update(&mut id.name)
                }
            }
        }
    }
    changed
}

fn transform_text(
    pattern: &mut PatternElement<String>,
    regex: &Regex,
    args: &TransformArgs,
) -> bool {
    let mut changed = false;
    let mut maybe_update = |val: &mut String| {
        if let Cow::Owned(new_val) = regex.replace_all(val, &args.replacement) {
            changed = true;
            *val = new_val;
        }
    };
    match pattern {
        PatternElement::TextElement { value } => {
            maybe_update(value);
        }
        PatternElement::Placeable { expression } => match expression {
            Expression::Inline(val) => match val {
                InlineExpression::StringLiteral { value } => maybe_update(value),
                InlineExpression::NumberLiteral { value } => maybe_update(value),
                InlineExpression::FunctionReference { .. } => {}
                InlineExpression::MessageReference { .. } => {}
                InlineExpression::TermReference { .. } => {}
                InlineExpression::VariableReference { .. } => {}
                InlineExpression::Placeable { .. } => {}
            },
            Expression::Select { variants, .. } => {
                for variant in variants {
                    changed |= transform_pattern(&mut variant.value, regex, args);
                }
            }
        },
    }
    changed
}

#[cfg(test)]
mod tests {
    use fluent_syntax::parser::parse;

    use super::*;
    use crate::serialize::serialize;

    #[test]
    fn transform() -> Result<()> {
        let mut resource = parse(
            r#"sample-1 = This is a sample
sample-2 =
    { $sample ->
        [one] { $sample } sample done
       *[other] { $sample } samples done
    }"#
            .to_string(),
        )
        .unwrap();

        let mut args = TransformArgs {
            lang_folder: Default::default(),
            target: TransformTarget::Text,
            regex: "".to_string(),
            replacement: "replaced".to_string(),
        };
        // no changes
        assert!(!transform_ftl_inner(
            &mut resource,
            &Regex::new("aoeu").unwrap(),
            &args
        ));
        // text change
        let regex = Regex::new("sample").unwrap();
        let mut resource2 = resource.clone();
        assert!(transform_ftl_inner(&mut resource2, &regex, &args));
        assert_eq!(
            &serialize(&resource2),
            r#"sample-1 = This is a replaced
sample-2 =
    { $sample ->
        [one] { $sample } replaced done
       *[other] { $sample } replaceds done
    }
"#
        );
        // variable change
        let mut resource2 = resource.clone();
        args.target = TransformTarget::Variable;
        assert!(transform_ftl_inner(&mut resource2, &regex, &args));
        assert_eq!(
            &serialize(&resource2),
            r#"sample-1 = This is a sample
sample-2 =
    { $replaced ->
        [one] { $replaced } sample done
       *[other] { $replaced } samples done
    }
"#
        );

        Ok(())
    }
}
