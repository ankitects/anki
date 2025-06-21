// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;
use std::collections::HashSet;
use std::fmt::Write;

use anyhow::Result;
use camino::Utf8PathBuf;
use itertools::Itertools;

use crate::action::BuildAction;
use crate::archives::Platform;
use crate::configure::ConfigureBuild;
use crate::input::space_separated;
use crate::input::BuildInput;

#[derive(Debug)]
pub struct Build {
    pub variables: HashMap<&'static str, String>,
    pub buildroot: Utf8PathBuf,
    pub build_profile: BuildProfile,
    pub pools: Vec<(&'static str, usize)>,
    pub trailing_text: String,
    pub host_platform: Platform,
    pub have_n2: bool,

    pub(crate) output_text: String,
    action_names: HashSet<&'static str>,
    pub(crate) groups: HashMap<String, Vec<String>>,
}

impl Build {
    pub fn new() -> Result<Self> {
        let buildroot = if cfg!(windows) {
            Utf8PathBuf::from("out")
        } else {
            // on Unix systems we allow out to be a symlink to an external location
            Utf8PathBuf::from("out").canonicalize_utf8()?
        };

        let mut build = Build {
            buildroot,
            build_profile: BuildProfile::from_env(),
            host_platform: Platform::current(),
            variables: Default::default(),
            pools: Default::default(),
            trailing_text: Default::default(),
            output_text: Default::default(),
            action_names: Default::default(),
            groups: Default::default(),
            have_n2: which::which("n2").is_ok(),
        };

        build.add_action("build:configure", ConfigureBuild {})?;

        Ok(build)
    }

    pub fn variable(&mut self, name: &'static str, value: impl Into<String>) {
        self.variables.insert(name, value.into());
    }

    pub fn pool(&mut self, name: &'static str, size: usize) {
        self.pools.push((name, size));
    }

    /// Evaluate the provided closure only once, using `key` to determine
    /// uniqueness. This key should not match any build action name.
    pub fn once_only(
        &mut self,
        key: &'static str,
        block: impl FnOnce(&mut Build) -> Result<()>,
    ) -> Result<()> {
        if self.action_names.insert(key) {
            block(self)
        } else {
            Ok(())
        }
    }

    pub fn add_action(&mut self, group: impl AsRef<str>, action: impl BuildAction) -> Result<()> {
        let group = group.as_ref();
        let groups = split_groups(group);
        let group = groups[0];
        let command = action.command();

        let action_name = action.name();
        // first invocation?
        let mut first_invocation = false;
        self.once_only(action_name, |build| {
            action.on_first_instance(build)?;
            first_invocation = true;
            Ok(())
        })?;

        let action_name = action_name.to_string();

        // ensure separator is delivered to runner, not shell
        let command = if cfg!(windows) || action.bypass_runner() {
            command.into()
        } else {
            command.replace("&&", "\"&&\"")
        };

        let mut statement = BuildStatement::from_build_action(
            group,
            action,
            &self.groups,
            self.build_profile,
            self.have_n2,
        );

        if first_invocation {
            let command = statement.prepare_command(command)?;
            writeln!(
                &mut self.output_text,
                "\
rule {action_name}
  command = {command}",
            )
            .unwrap();
            for (k, v) in &statement.rule_variables {
                writeln!(&mut self.output_text, "  {k} = {v}").unwrap();
            }
            self.output_text.push('\n');
        }

        let (all_outputs, subgroups) = statement.render_into(&mut self.output_text);
        for group in groups {
            self.add_resolved_files_to_group(group, &all_outputs);
        }
        for (subgroup, outputs) in subgroups {
            let group_with_subgroup = format!("{group}:{subgroup}");
            self.add_resolved_files_to_group(&group_with_subgroup, &outputs);
        }

        Ok(())
    }

    /// Add one or more resolved files to a group. Does not add to the parent
    /// groups; that must be done by the caller.
    fn add_resolved_files_to_group<'a>(
        &mut self,
        group: &str,
        files: impl IntoIterator<Item = &'a String>,
    ) {
        let buf = self.groups.entry(group.to_owned()).or_default();
        buf.extend(files.into_iter().map(ToString::to_string));
    }

    /// Allows you to add dependencies on files or build steps that aren't
    /// required to build the group itself, but are required by consumers of
    /// that group. Can also be used to allow substitution of local binaries
    /// for downloaded ones (eg :node_binary).
    pub fn add_dependency(&mut self, group: &str, deps: BuildInput) {
        let files = self.expand_inputs(deps);
        let groups = split_groups(group);
        for group in groups {
            self.add_resolved_files_to_group(group, &files);
        }
    }

    /// Outputs from a given build statement group. An error if no files have
    /// been registered yet.
    pub fn group_outputs(&self, group_name: &'static str) -> &[String] {
        self.groups
            .get(group_name)
            .unwrap_or_else(|| panic!("expected files in {group_name}"))
    }

    /// Single output from a given build statement group. An error if no files
    /// have been registered yet, or more than one file has been registered.
    pub fn group_output(&self, group_name: &'static str) -> String {
        let outputs = self.group_outputs(group_name);
        assert_eq!(outputs.len(), 1);
        outputs.first().unwrap().into()
    }

    pub fn expand_inputs(&self, inputs: impl AsRef<BuildInput>) -> Vec<String> {
        expand_inputs(inputs, &self.groups)
    }

    /// Expand inputs, the return a filtered subset.
    pub fn filter_inputs<F>(&self, inputs: impl AsRef<BuildInput>, func: F) -> Vec<String>
    where
        F: FnMut(&String) -> bool,
    {
        self.expand_inputs(inputs)
            .into_iter()
            .filter(func)
            .collect()
    }

    pub fn inputs_with_suffix(&self, inputs: impl AsRef<BuildInput>, ext: &str) -> Vec<String> {
        self.filter_inputs(inputs, |f| f.ends_with(ext))
    }
}

fn split_groups(group: &str) -> Vec<&str> {
    let mut rest = group;
    let mut groups = vec![group];
    while let Some((head, _tail)) = rest.rsplit_once(':') {
        groups.push(head);
        rest = head;
    }
    groups
}

struct BuildStatement<'a> {
    /// Cache of outputs by already-evaluated build rules, allowing later rules
    /// to more easily consume the outputs of previous rules.
    existing_outputs: &'a HashMap<String, Vec<String>>,
    rule_name: &'static str,
    // implicit refers to files that are not automatically assigned to $in and $out by Ninja,
    implicit_inputs: Vec<String>,
    implicit_outputs: Vec<String>,
    explicit_inputs: Vec<String>,
    explicit_outputs: Vec<String>,
    order_only_inputs: Vec<String>,
    output_subsets: Vec<(String, Vec<String>)>,
    variables: Vec<(String, String)>,
    rule_variables: Vec<(String, String)>,
    output_stamp: bool,
    env_vars: Vec<String>,
    working_dir: Option<String>,
    create_dirs: Vec<String>,
    build_profile: BuildProfile,
    bypass_runner: bool,
}

impl BuildStatement<'_> {
    fn from_build_action<'a>(
        group: &str,
        mut action: impl BuildAction,
        existing_outputs: &'a HashMap<String, Vec<String>>,
        build_profile: BuildProfile,
        have_n2: bool,
    ) -> BuildStatement<'a> {
        let mut stmt = BuildStatement {
            existing_outputs,
            rule_name: action.name(),
            implicit_inputs: Default::default(),
            implicit_outputs: Default::default(),
            explicit_inputs: Default::default(),
            explicit_outputs: Default::default(),
            order_only_inputs: Default::default(),
            variables: Default::default(),
            rule_variables: Default::default(),
            output_subsets: Default::default(),
            output_stamp: false,
            env_vars: Default::default(),
            working_dir: None,
            create_dirs: Default::default(),
            build_profile,
            bypass_runner: action.bypass_runner(),
        };
        action.files(&mut stmt);

        if stmt.explicit_outputs.is_empty() && stmt.implicit_outputs.is_empty() {
            panic!("{} must generate at least one output", action.name());
        }
        stmt.variables.push(("description".into(), group.into()));
        stmt.rule_variables.push((
            "restat".into(),
            (action.check_output_timestamps() as u32).to_string(),
        ));
        if action.generator() {
            stmt.rule_variables.push(("generator".into(), "1".into()));
        }
        if let Some(pool) = action.concurrency_pool() {
            stmt.rule_variables.push(("pool".into(), pool.into()));
        }
        if have_n2 {
            if action.hide_success() {
                stmt.rule_variables
                    .push(("hide_success".into(), "1".into()));
            }
            if action.hide_progress() {
                stmt.rule_variables
                    .push(("hide_progress".into(), "1".into()));
            }
        }

        stmt
    }

    /// Returns a list of all output files, which `Build` will add to
    /// `existing_outputs`, and any subgroups.
    fn render_into(mut self, buf: &mut String) -> (Vec<String>, Vec<(String, Vec<String>)>) {
        let action_name = self.rule_name;
        self.implicit_inputs.sort();
        self.implicit_outputs.sort();
        let inputs_str = to_ninja_target_string(
            &self.explicit_inputs,
            &self.implicit_inputs,
            &self.order_only_inputs,
        );
        let outputs_str =
            to_ninja_target_string(&self.explicit_outputs, &self.implicit_outputs, &[]);

        writeln!(buf, "build {outputs_str}: {action_name} {inputs_str}").unwrap();
        for (key, value) in self.variables.iter().sorted() {
            writeln!(buf, "  {key} = {}", value).unwrap();
        }
        writeln!(buf).unwrap();

        let outputs_vec = {
            self.implicit_outputs.extend(self.explicit_outputs);
            self.implicit_outputs
        };
        (outputs_vec, self.output_subsets)
    }

    fn prepare_command(&mut self, command: String) -> Result<String> {
        if self.bypass_runner {
            return Ok(command);
        }
        if command.starts_with("$runner") {
            self.implicit_inputs.push("$runner".into());
            return Ok(command);
        }
        let mut buf = String::from("$runner run ");
        if self.output_stamp {
            write!(&mut buf, "--stamp=$stamp ")?;
        }
        for var in &self.env_vars {
            write!(&mut buf, "--env=\"{var}\" ")?;
        }
        for dir in &self.create_dirs {
            write!(&mut buf, "--mkdir={dir} ")?;
        }
        if let Some(working_dir) = &self.working_dir {
            write!(&mut buf, "--cwd={working_dir} ")?;
        }
        buf.push_str(&command);
        Ok(buf)
    }
}

fn expand_inputs(
    input: impl AsRef<BuildInput>,
    existing_outputs: &HashMap<String, Vec<String>>,
) -> Vec<String> {
    let mut vec = vec![];
    input.as_ref().add_to_vec(&mut vec, existing_outputs);
    vec
}

#[derive(Debug, Eq, PartialEq, Clone, Copy)]
pub enum BuildProfile {
    Debug,
    Release,
    ReleaseWithLto,
}

impl BuildProfile {
    fn from_env() -> Self {
        match std::env::var("RELEASE").unwrap_or_default().as_str() {
            "1" => Self::Release,
            "2" => Self::ReleaseWithLto,
            _ => Self::Debug,
        }
    }
}

pub trait FilesHandle {
    /// Add inputs to the build statement. Can be called multiple times with
    /// different variables. This is a shortcut for calling .expand_inputs()
    /// and then .add_inputs_vec()
    /// - If the variable name is non-empty, a variable of the same name will be
    ///   created so the file list can be accessed in the command. By
    ///   convention, this is often `in`.
    fn add_inputs(&mut self, variable: &'static str, inputs: impl AsRef<BuildInput>);
    fn add_inputs_vec(&mut self, variable: &'static str, inputs: Vec<String>);
    fn add_order_only_inputs(&mut self, variable: &'static str, inputs: impl AsRef<BuildInput>);

    /// Add a variable that can be referenced in the command.
    fn add_variable(&mut self, name: impl Into<String>, value: impl Into<String>);

    fn expand_input(&self, input: &BuildInput) -> String;
    fn expand_inputs(&self, inputs: impl AsRef<BuildInput>) -> Vec<String>;

    /// Like [FilesHandle::add_outputs_ext], without adding a subgroup.
    fn add_outputs(
        &mut self,
        variable: &'static str,
        outputs: impl IntoIterator<Item = impl AsRef<str>>,
    ) {
        self.add_outputs_ext(variable, outputs, false);
    }

    /// Add outputs to the build statement. Can be called multiple times with
    /// different variables.
    /// - Each output automatically has $builddir/ prefixed to it if it does not
    ///   already start with it.
    /// - If the variable name is non-empty, a variable of the same name will be
    ///   created so the file list can be accessed in the command. By
    ///   convention, this is often `out`.
    /// - If subgroup is true, the files are also placed in a subgroup. Eg if a
    ///   rule `foo` exists and subgroup `bar` is provided, the files are
    ///   accessible via `:foo:bar`. The variable name must not be empty, or
    ///   called `out`.
    fn add_outputs_ext(
        &mut self,
        variable: impl Into<String>,
        outputs: impl IntoIterator<Item = impl AsRef<str>>,
        subgroup: bool,
    );

    /// Save an output stamp if the command completes successfully. Note that
    /// if you have bypassed the runner, you will need to create the file
    /// yourself.
    fn add_output_stamp(&mut self, path: impl Into<String>);
    /// Set an env var for the duration of the provided command(s).
    /// Note this is defined once for the rule, so if the value should change
    /// for each command, `constant_value` should reference a `$variable` you
    /// have defined.
    fn add_env_var(&mut self, key: &str, constant_value: &str);
    /// Set the current working dir for the provided command(s).
    /// Note this is defined once for the rule, so if the value should change
    /// for each command, `constant_value` should reference a `$variable` you
    /// have defined.
    fn set_working_dir(&mut self, constant_value: &str);
    /// Ensure provided folder and parent folders are created before running
    /// the command. Can be called multiple times. Defines a variable pointing
    /// at the folder.
    fn create_dir_all(&mut self, key: &str, path: impl Into<String>);

    fn build_profile(&self) -> BuildProfile;
}

impl FilesHandle for BuildStatement<'_> {
    fn add_inputs(&mut self, variable: &'static str, inputs: impl AsRef<BuildInput>) {
        self.add_inputs_vec(variable, FilesHandle::expand_inputs(self, inputs));
    }

    fn add_inputs_vec(&mut self, variable: &'static str, inputs: Vec<String>) {
        match variable {
            "in" => self.explicit_inputs.extend(inputs),
            other_key => {
                if !other_key.is_empty() {
                    self.add_variable(other_key, space_separated(&inputs));
                }
                self.implicit_inputs.extend(inputs);
            }
        }
    }

    fn add_order_only_inputs(&mut self, variable: &'static str, inputs: impl AsRef<BuildInput>) {
        let inputs = FilesHandle::expand_inputs(self, inputs);
        if !variable.is_empty() {
            self.add_variable(variable, space_separated(&inputs))
        }
        self.order_only_inputs.extend(inputs);
    }

    fn add_variable(&mut self, key: impl Into<String>, value: impl Into<String>) {
        self.variables.push((key.into(), value.into()));
    }

    fn expand_input(&self, input: &BuildInput) -> String {
        let mut vec = Vec::with_capacity(1);
        input.add_to_vec(&mut vec, self.existing_outputs);
        if vec.len() != 1 {
            panic!("expected {input:?} to resolve to a single file; got ${vec:?}");
        }
        vec.pop().unwrap()
    }

    fn add_outputs_ext(
        &mut self,
        variable: impl Into<String>,
        outputs: impl IntoIterator<Item = impl AsRef<str>>,
        subgroup: bool,
    ) {
        let outputs = outputs.into_iter().map(|v| {
            let v = v.as_ref();
            let v = if !v.starts_with("$builddir/") && !v.starts_with("$builddir\\") {
                format!("$builddir/{}", v)
            } else {
                v.to_owned()
            };
            if cfg!(windows) {
                v.replace('/', "\\")
            } else {
                v
            }
        });
        let variable = variable.into();
        match variable.as_str() {
            "out" => self.explicit_outputs.extend(outputs),
            other_key => {
                let outputs: Vec<_> = outputs.collect();
                if !other_key.is_empty() {
                    self.add_variable(other_key, space_separated(&outputs));
                }
                if subgroup {
                    assert!(!other_key.is_empty());
                    self.output_subsets
                        .push((other_key.to_owned(), outputs.to_owned()));
                }

                self.implicit_outputs.extend(outputs);
            }
        }
    }

    fn expand_inputs(&self, inputs: impl AsRef<BuildInput>) -> Vec<String> {
        expand_inputs(inputs, self.existing_outputs)
    }

    fn build_profile(&self) -> BuildProfile {
        self.build_profile
    }

    fn add_output_stamp(&mut self, path: impl Into<String>) {
        self.output_stamp = true;
        self.add_outputs("stamp", vec![path.into()]);
    }

    fn add_env_var(&mut self, key: &str, constant_value: &str) {
        self.env_vars.push(format!("{key}={constant_value}"));
    }

    fn set_working_dir(&mut self, constant_value: &str) {
        self.working_dir = Some(constant_value.to_owned());
    }

    fn create_dir_all(&mut self, key: &str, path: impl Into<String>) {
        let path = path.into();
        self.add_variable(key, &path);
        self.create_dirs.push(path);
    }
}

fn to_ninja_target_string(
    explicit: &[String],
    implicit: &[String],
    order_only: &[String],
) -> String {
    let mut joined = space_separated(explicit);
    if !implicit.is_empty() {
        joined.push_str(" | ");
        joined.push_str(&space_separated(implicit));
    }
    if !order_only.is_empty() {
        joined.push_str(" || ");
        joined.push_str(&space_separated(order_only));
    }
    joined
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn test_split_groups() {
        assert_eq!(&split_groups("foo"), &["foo"]);
        assert_eq!(&split_groups("foo:bar"), &["foo:bar", "foo"]);
        assert_eq!(
            &split_groups("foo:bar:baz"),
            &["foo:bar:baz", "foo:bar", "foo"]
        );
    }
}
