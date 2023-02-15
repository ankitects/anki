// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashSet;
use std::fmt::Write;

use fluent_syntax::ast::Entry;
use fluent_syntax::ast::Expression;
use fluent_syntax::ast::InlineExpression;
use fluent_syntax::ast::Pattern;
use fluent_syntax::ast::PatternElement;
use fluent_syntax::parser::parse;
use serde::Serialize;

use crate::gather::TranslationsByLang;
#[derive(Debug, PartialOrd, Ord, PartialEq, Eq, Serialize)]
pub struct Module {
    pub name: String,
    pub translations: Vec<Translation>,
    pub index: usize,
}

#[derive(Debug, PartialOrd, Ord, PartialEq, Eq, Serialize)]
pub struct Translation {
    pub key: String,
    pub text: String,
    pub variables: Vec<Variable>,
    pub index: usize,
}

#[derive(Debug, PartialOrd, Ord, PartialEq, Eq, Serialize)]
pub struct Variable {
    pub name: String,
    pub kind: VariableKind,
}

#[derive(Debug, PartialOrd, Ord, PartialEq, Eq, Serialize)]
pub enum VariableKind {
    Int,
    Float,
    String,
    Any,
}

pub fn get_modules(data: &TranslationsByLang) -> Vec<Module> {
    let mut output = vec![];

    for (module, text) in &data["templates"] {
        output.push(Module {
            name: module.to_string(),
            translations: extract_metadata(text),
            index: 0,
        });
    }

    output.sort_unstable();

    for (module_idx, module) in output.iter_mut().enumerate() {
        module.index = module_idx;
        for (entry_idx, entry) in module.translations.iter_mut().enumerate() {
            entry.index = entry_idx;
        }
    }

    output
}

fn extract_metadata(ftl_text: &str) -> Vec<Translation> {
    let res = parse(ftl_text).unwrap();
    let mut output = vec![];

    for entry in res.body {
        if let Entry::Message(m) = entry {
            if let Some(pattern) = m.value {
                let mut visitor = Visitor::default();
                visitor.visit_pattern(&pattern);
                let key = m.id.name.to_string();

                // special case translations that were ported from gettext, and use embedded
                // terms that reference other variables that aren't visible to our visitor
                if key == "statistics-studied-today" {
                    visitor.variables.push("amount".to_string());
                    visitor.variables.push("cards".to_string());
                } else if key == "statistics-average-answer-time" {
                    visitor.variables.push("cards-per-minute".to_string());
                }

                let (text, variables) = visitor.into_output();

                output.push(Translation {
                    key,
                    text,
                    variables,
                    index: 0,
                })
            }
        }
    }

    output.sort_unstable();

    output
}

/// Gather variable names and (rough) text from Fluent AST.
#[derive(Default)]
struct Visitor {
    text: String,
    variables: Vec<String>,
}

impl Visitor {
    fn into_output(self) -> (String, Vec<Variable>) {
        // make unique, preserving order
        let mut seen = HashSet::new();
        let vars: Vec<_> = self
            .variables
            .into_iter()
            .filter(|v| {
                if seen.contains(v) {
                    false
                } else {
                    seen.insert(v.clone())
                }
            })
            .map(Into::into)
            .collect();
        (self.text, vars)
    }

    fn visit_pattern(&mut self, pattern: &Pattern<&str>) {
        for element in &pattern.elements {
            match element {
                PatternElement::TextElement { value } => self.text.push_str(value),
                PatternElement::Placeable { expression } => self.visit_expression(expression),
            }
        }
    }

    fn visit_inline_expression(&mut self, expr: &InlineExpression<&str>, in_select: bool) {
        match expr {
            InlineExpression::VariableReference { id } => {
                if !in_select {
                    write!(self.text, "{{${}}}", id.name).unwrap();
                }
                self.variables.push(id.name.to_string());
            }
            InlineExpression::Placeable { expression } => {
                self.visit_expression(expression);
            }
            _ => {}
        }
    }

    fn visit_expression(&mut self, expression: &Expression<&str>) {
        match expression {
            Expression::Select { selector, variants } => {
                self.visit_inline_expression(selector, true);
                self.visit_pattern(&variants.last().unwrap().value)
            }
            Expression::Inline(expr) => self.visit_inline_expression(expr, false),
        }
    }
}

impl From<String> for Variable {
    fn from(name: String) -> Self {
        // rather than adding more items here as we add new strings, we should probably
        // try to either reuse existing ones, or consider some sort of Hungarian
        // notation
        let kind = match name.as_str() {
            "cards" | "notes" | "count" | "amount" | "reviews" | "total" | "selected"
            | "kilobytes" | "daysStart" | "daysEnd" | "days" | "secs-per-card" | "remaining"
            | "hourStart" | "hourEnd" | "correct" | "decks" | "changed" => VariableKind::Int,
            "average-seconds" | "cards-per-minute" => VariableKind::Float,
            "val" | "found" | "expected" | "part" | "percent" | "day" | "number" | "up"
            | "down" | "seconds" | "megs" => VariableKind::Any,
            term => {
                let term = term.to_ascii_lowercase();
                if term.ends_with("count") {
                    VariableKind::Int
                } else if term.starts_with("num") {
                    VariableKind::Any
                } else {
                    VariableKind::String
                }
            }
        };
        Variable { name, kind }
    }
}
