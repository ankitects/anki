// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

// copied from https://github.com/projectfluent/fluent-rs/pull/241

use std::fmt;
use std::fmt::Error;
use std::fmt::Write;

use fluent_syntax::ast::*;
use fluent_syntax::parser::Slice;

pub fn serialize<'s, S: Slice<'s>>(resource: &Resource<S>) -> String {
    serialize_with_options(resource, Options::default())
}

pub fn serialize_with_options<'s, S: Slice<'s>>(
    resource: &Resource<S>,
    options: Options,
) -> String {
    let mut ser = Serializer::new(options);

    ser.serialize_resource(resource)
        .expect("Writing to an in-memory buffer never fails");

    ser.into_serialized_text()
}

#[derive(Debug)]
pub struct Serializer {
    writer: TextWriter,
    options: Options,
    state: State,
}

impl Serializer {
    pub fn new(options: Options) -> Self {
        Serializer {
            writer: TextWriter::default(),
            options,
            state: State::default(),
        }
    }

    pub fn serialize_resource<'s, S: Slice<'s>>(&mut self, res: &Resource<S>) -> Result<(), Error> {
        for entry in &res.body {
            match entry {
                Entry::Message(msg) => self.serialize_message(msg)?,
                Entry::Term(term) => self.serialize_term(term)?,
                Entry::Comment(comment) => self.serialize_free_comment(comment, "#")?,
                Entry::GroupComment(comment) => self.serialize_free_comment(comment, "##")?,
                Entry::ResourceComment(comment) => self.serialize_free_comment(comment, "###")?,
                Entry::Junk { content } if self.options.with_junk => {
                    self.serialize_junk(content.as_ref())?
                }
                Entry::Junk { .. } => continue,
            }

            self.state.has_entries = true;
        }

        Ok(())
    }

    pub fn into_serialized_text(self) -> String {
        self.writer.buffer
    }

    fn serialize_junk(&mut self, junk: &str) -> Result<(), Error> {
        self.writer.write_literal(junk)
    }

    fn serialize_free_comment<'s, S: Slice<'s>>(
        &mut self,
        comment: &Comment<S>,
        prefix: &str,
    ) -> Result<(), Error> {
        if self.state.has_entries {
            self.writer.newline();
        }
        self.serialize_comment(comment, prefix)?;
        self.writer.newline();

        Ok(())
    }

    fn serialize_comment<'s, S: Slice<'s>>(
        &mut self,
        comment: &Comment<S>,
        prefix: &str,
    ) -> Result<(), Error> {
        for line in &comment.content {
            self.writer.write_literal(prefix)?;

            if !line.as_ref().trim().is_empty() {
                self.writer.write_literal(" ")?;
                self.writer.write_literal(line.as_ref())?;
            }

            self.writer.newline();
        }

        Ok(())
    }

    fn serialize_message<'s, S: Slice<'s>>(&mut self, msg: &Message<S>) -> Result<(), Error> {
        if let Some(comment) = msg.comment.as_ref() {
            self.serialize_comment(comment, "#")?;
        }

        self.writer.write_literal(msg.id.name.as_ref())?;
        self.writer.write_literal(" =")?;

        if let Some(value) = msg.value.as_ref() {
            self.serialize_pattern(value)?;
        }

        self.serialize_attributes(&msg.attributes)?;

        self.writer.newline();
        Ok(())
    }

    fn serialize_term<'s, S: Slice<'s>>(&mut self, term: &Term<S>) -> Result<(), Error> {
        if let Some(comment) = term.comment.as_ref() {
            self.serialize_comment(comment, "#")?;
        }

        self.writer.write_literal("-")?;
        self.writer.write_literal(term.id.name.as_ref())?;
        self.writer.write_literal(" =")?;
        self.serialize_pattern(&term.value)?;

        self.serialize_attributes(&term.attributes)?;

        self.writer.newline();

        Ok(())
    }

    fn serialize_pattern<'s, S: Slice<'s>>(&mut self, pattern: &Pattern<S>) -> Result<(), Error> {
        let start_on_newline = pattern.elements.iter().any(|elem| match elem {
            PatternElement::TextElement { value } => value.as_ref().contains('\n'),
            PatternElement::Placeable { expression } => is_select_expr(expression),
        });

        if start_on_newline {
            self.writer.newline();
            self.writer.indent();
        } else {
            self.writer.write_literal(" ")?;
        }

        for element in &pattern.elements {
            self.serialize_element(element)?;
        }

        if start_on_newline {
            self.writer.dedent();
        }

        Ok(())
    }

    fn serialize_attributes<'s, S: Slice<'s>>(
        &mut self,
        attrs: &[Attribute<S>],
    ) -> Result<(), Error> {
        if attrs.is_empty() {
            return Ok(());
        }

        self.writer.indent();

        for attr in attrs {
            self.writer.newline();
            self.serialize_attribute(attr)?;
        }

        self.writer.dedent();

        Ok(())
    }

    fn serialize_attribute<'s, S: Slice<'s>>(&mut self, attr: &Attribute<S>) -> Result<(), Error> {
        self.writer.write_literal(".")?;
        self.writer.write_literal(attr.id.name.as_ref())?;
        self.writer.write_literal(" =")?;

        self.serialize_pattern(&attr.value)?;

        Ok(())
    }

    fn serialize_element<'s, S: Slice<'s>>(
        &mut self,
        elem: &PatternElement<S>,
    ) -> Result<(), Error> {
        match elem {
            PatternElement::TextElement { value } => self.writer.write_literal(value.as_ref()),
            PatternElement::Placeable { expression } => match expression {
                Expression::Inline(InlineExpression::Placeable { expression }) => {
                    // A placeable inside a placeable is a special case because we
                    // don't want the braces to look silly (e.g. "{ { Foo() } }").
                    self.writer.write_literal("{{ ")?;
                    self.serialize_expression(expression)?;
                    self.writer.write_literal(" }}")?;
                    Ok(())
                }
                Expression::Select { .. } => {
                    // select adds its own newline and indent, emit the brace
                    // *without* a space so we don't get 5 spaces instead of 4
                    self.writer.write_literal("{ ")?;
                    self.serialize_expression(expression)?;
                    self.writer.write_literal("}")?;
                    Ok(())
                }
                Expression::Inline(_) => {
                    self.writer.write_literal("{ ")?;
                    self.serialize_expression(expression)?;
                    self.writer.write_literal(" }")?;
                    Ok(())
                }
            },
        }
    }

    fn serialize_expression<'s, S: Slice<'s>>(
        &mut self,
        expr: &Expression<S>,
    ) -> Result<(), Error> {
        match expr {
            Expression::Inline(inline) => self.serialize_inline_expression(inline),
            Expression::Select { selector, variants } => {
                self.serialize_select_expression(selector, variants)
            }
        }
    }

    fn serialize_inline_expression<'s, S: Slice<'s>>(
        &mut self,
        expr: &InlineExpression<S>,
    ) -> Result<(), Error> {
        match expr {
            InlineExpression::StringLiteral { value } => {
                self.writer.write_literal("\"")?;
                self.writer.write_literal(value.as_ref())?;
                self.writer.write_literal("\"")?;
                Ok(())
            }
            InlineExpression::NumberLiteral { value } => self.writer.write_literal(value.as_ref()),
            InlineExpression::VariableReference {
                id: Identifier { name: value },
            } => {
                self.writer.write_literal("$")?;
                self.writer.write_literal(value.as_ref())?;
                Ok(())
            }
            InlineExpression::FunctionReference { id, arguments } => {
                self.writer.write_literal(id.name.as_ref())?;
                self.serialize_call_arguments(arguments)?;

                Ok(())
            }
            InlineExpression::MessageReference { id, attribute } => {
                self.writer.write_literal(id.name.as_ref())?;

                if let Some(attr) = attribute.as_ref() {
                    self.writer.write_literal(".")?;
                    self.writer.write_literal(attr.name.as_ref())?;
                }

                Ok(())
            }
            InlineExpression::TermReference {
                id,
                attribute,
                arguments,
            } => {
                self.writer.write_literal("-")?;
                self.writer.write_literal(id.name.as_ref())?;

                if let Some(attr) = attribute.as_ref() {
                    self.writer.write_literal(".")?;
                    self.writer.write_literal(attr.name.as_ref())?;
                }
                if let Some(args) = arguments.as_ref() {
                    self.serialize_call_arguments(args)?;
                }

                Ok(())
            }
            InlineExpression::Placeable { expression } => {
                self.writer.write_literal("{")?;
                self.serialize_expression(expression)?;
                self.writer.write_literal("}")?;

                Ok(())
            }
        }
    }

    fn serialize_select_expression<'s, S: Slice<'s>>(
        &mut self,
        selector: &InlineExpression<S>,
        variants: &[Variant<S>],
    ) -> Result<(), Error> {
        self.serialize_inline_expression(selector)?;
        self.writer.write_literal(" ->")?;

        self.writer.newline();
        self.writer.indent();

        for variant in variants {
            self.serialize_variant(variant)?;
            self.writer.newline();
        }

        self.writer.dedent();
        Ok(())
    }

    fn serialize_variant<'s, S: Slice<'s>>(&mut self, variant: &Variant<S>) -> Result<(), Error> {
        if variant.default {
            self.writer.write_char_into_indent('*');
        }

        self.writer.write_literal("[")?;
        self.serialize_variant_key(&variant.key)?;
        self.writer.write_literal("]")?;
        self.serialize_pattern(&variant.value)?;

        Ok(())
    }

    fn serialize_variant_key<'s, S: Slice<'s>>(
        &mut self,
        key: &VariantKey<S>,
    ) -> Result<(), Error> {
        match key {
            VariantKey::NumberLiteral { value } | VariantKey::Identifier { name: value } => {
                self.writer.write_literal(value.as_ref())
            }
        }
    }

    fn serialize_call_arguments<'s, S: Slice<'s>>(
        &mut self,
        args: &CallArguments<S>,
    ) -> Result<(), Error> {
        let mut argument_written = false;

        self.writer.write_literal("(")?;

        for positional in &args.positional {
            if argument_written {
                self.writer.write_literal(", ")?;
            }

            self.serialize_inline_expression(positional)?;
            argument_written = true;
        }

        for named in &args.named {
            if argument_written {
                self.writer.write_literal(", ")?;
            }

            self.writer.write_literal(named.name.name.as_ref())?;
            self.writer.write_literal(": ")?;
            self.serialize_inline_expression(&named.value)?;
            argument_written = true;
        }

        self.writer.write_literal(")")?;
        Ok(())
    }
}

fn is_select_expr<'s, S: Slice<'s>>(expr: &Expression<S>) -> bool {
    match expr {
        Expression::Select { .. } => true,
        Expression::Inline(InlineExpression::Placeable { expression }) => {
            is_select_expr(expression)
        }
        Expression::Inline(_) => false,
    }
}

#[derive(Debug, Default, Copy, Clone, PartialEq, Eq)]
pub struct Options {
    pub with_junk: bool,
}

#[derive(Debug, Default, PartialEq)]
struct State {
    has_entries: bool,
}

#[derive(Debug, Clone, Default)]
struct TextWriter {
    buffer: String,
    indent_level: usize,
}

impl TextWriter {
    fn indent(&mut self) {
        self.indent_level += 1;
    }

    fn dedent(&mut self) {
        self.indent_level = self
            .indent_level
            .checked_sub(1)
            .expect("Dedenting without a corresponding indent");
    }

    fn write_indent(&mut self) {
        for _ in 0..self.indent_level {
            self.buffer.push_str("    ");
        }
    }

    fn newline(&mut self) {
        self.buffer.push('\n');
    }

    fn write_literal(&mut self, mut item: &str) -> fmt::Result {
        if self.buffer.ends_with('\n') {
            // we've just added a newline, make sure it's properly indented
            self.write_indent();

            // we've just added indentation, so we don't care about leading
            // spaces
            item = item.trim_start_matches(' ');
        }

        write!(self.buffer, "{item}")
    }

    fn write_char_into_indent(&mut self, ch: char) {
        if self.buffer.ends_with('\n') {
            self.write_indent();
        }
        self.buffer.pop();
        self.buffer.push(ch);
    }
}
