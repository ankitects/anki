// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{action::BuildAction, hash::simple_hash, input::BuildInput, inputs, Build, Result};

pub struct PythonEnvironment<'a> {
    pub folder: &'static str,
    pub base_requirements_txt: BuildInput,
    pub requirements_txt: BuildInput,
    pub python_binary: &'a BuildInput,
    pub extra_binary_exports: &'static [&'static str],
}

impl BuildAction for PythonEnvironment<'_> {
    fn command(&self) -> &str {
        "$runner pyenv $python_binary $builddir/$pyenv_folder $system_pkgs $base_requirements $requirements"
    }

    fn files(&mut self, build: &mut impl crate::build::FilesHandle) {
        let bin_path = |binary: &str| -> Vec<String> {
            let folder = self.folder;
            let path = if cfg!(windows) {
                format!("{folder}/scripts/{binary}.exe")
            } else {
                format!("{folder}/bin/{binary}")
            };
            vec![path]
        };

        build.add_inputs("python_binary", self.python_binary);
        build.add_inputs("base_requirements", &self.base_requirements_txt);
        build.add_inputs("requirements", &self.requirements_txt);
        build.add_variable("pyenv_folder", self.folder);
        build.add_outputs_ext("bin", bin_path("python"), true);
        build.add_outputs_ext("pip", bin_path("pip"), true);
        for binary in self.extra_binary_exports {
            build.add_outputs_ext(*binary, bin_path(binary), true);
        }
    }

    fn check_output_timestamps(&self) -> bool {
        true
    }
}

pub struct PythonTypecheck {
    pub folders: &'static [&'static str],
    pub deps: BuildInput,
}

impl BuildAction for PythonTypecheck {
    fn command(&self) -> &str {
        "$mypy $folders"
    }

    fn files(&mut self, build: &mut impl crate::build::FilesHandle) {
        build.add_inputs("", &self.deps);
        build.add_inputs("mypy", inputs![":pyenv:mypy"]);
        build.add_inputs("", inputs![".mypy.ini"]);
        build.add_variable("folders", self.folders.join(" "));

        let hash = simple_hash(self.folders);
        build.add_output_stamp(format!("tests/python_typecheck.{hash}"));
    }
}

struct PythonFormatSingle<'a> {
    pub input: BuildInput,
    pub check_only: bool,
    pub isort_ini: &'a BuildInput,
}

impl BuildAction for PythonFormatSingle<'_> {
    fn command(&self) -> &str {
        "$black -t py39 -q $check --color $in && $
         $isort --color --settings-path $isort_ini $check $in"
    }

    fn files(&mut self, build: &mut impl crate::build::FilesHandle) {
        build.add_inputs("in", &self.input);
        build.add_inputs("black", inputs![":pyenv:black"]);
        build.add_inputs("isort", inputs![":pyenv:isort"]);
        build.add_inputs("isort_ini", self.isort_ini);
        build.add_variable(
            "check",
            if self.check_only {
                "--diff --check"
            } else {
                ""
            },
        );

        let hash = simple_hash(&self.input);
        build.add_output_stamp(format!(
            "tests/python_format.{}.{hash}",
            if self.check_only { "check" } else { "fix" }
        ));
    }
}

pub fn python_format(build: &mut Build, inputs: BuildInput, isort_ini: BuildInput) -> Result<()> {
    let input_files = build.expand_inputs(inputs);
    for file in input_files {
        build.add(
            "check:format:python",
            PythonFormatSingle {
                input: inputs![file.as_str()],
                check_only: true,
                isort_ini: &isort_ini,
            },
        )?;

        build.add(
            "format:python",
            PythonFormatSingle {
                input: inputs![file],
                check_only: false,
                isort_ini: &isort_ini,
            },
        )?;
    }
    Ok(())
}

pub struct PythonLint {
    pub folders: &'static [&'static str],
    pub pylint_ini: BuildInput,
    pub deps: BuildInput,
}

impl BuildAction for PythonLint {
    fn command(&self) -> &str {
        "$pylint --rcfile $pylint_ini -sn -j $cpus $folders"
    }

    fn files(&mut self, build: &mut impl crate::build::FilesHandle) {
        build.add_inputs("", &self.deps);
        build.add_inputs("pylint", inputs![":pyenv:pylint"]);
        build.add_inputs("pylint_ini", &self.pylint_ini);
        build.add_variable("folders", self.folders.join(" "));
        // On a 16 core system, values above 10 do not improve wall clock time,
        // but waste extra cores that could be working on other tests.
        build.add_variable("cpus", num_cpus::get().min(10).to_string());

        let hash = simple_hash(&self.deps);
        build.add_output_stamp(format!("tests/python_lint.{hash}"));
    }
}

pub struct PythonTest {
    pub folder: &'static str,
    pub python_path: &'static [&'static str],
    pub deps: BuildInput,
}

impl BuildAction for PythonTest {
    fn command(&self) -> &str {
        "$pytest $folder"
    }

    fn files(&mut self, build: &mut impl crate::build::FilesHandle) {
        build.add_inputs("", &self.deps);
        build.add_inputs("pytest", inputs![":pyenv:pytest"]);
        build.add_variable("folder", self.folder);
        build.add_variable(
            "pythonpath",
            &self.python_path.join(if cfg!(windows) { ";" } else { ":" }),
        );
        build.add_env_var("PYTHONPATH", "$pythonpath");
        build.add_env_var("ANKI_TEST_MODE", "1");
        let hash = simple_hash(self.folder);
        build.add_output_stamp(format!("tests/python_pytest.{hash}"));
    }
}
