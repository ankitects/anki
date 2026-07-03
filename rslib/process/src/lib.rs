// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::ffi::OsStr;
use std::iter::once;
use std::process::Command;
use std::string::FromUtf8Error;

use itertools::Itertools;
use snafu::ensure;
use snafu::ResultExt;
use snafu::Snafu;

#[derive(Debug)]
pub struct CodeDisplay(Option<i32>);

impl std::fmt::Display for CodeDisplay {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self.0 {
            Some(code) => write!(f, "{code}"),
            None => write!(f, "?"),
        }
    }
}

impl From<Option<i32>> for CodeDisplay {
    fn from(code: Option<i32>) -> Self {
        CodeDisplay(code)
    }
}

#[derive(Debug, Snafu)]
pub enum Error {
    #[snafu(display("Failed to execute: {cmdline}"))]
    DidNotExecute {
        cmdline: String,
        source: std::io::Error,
    },
    #[snafu(display("Failed to run ({code}): {cmdline}"))]
    ReturnedError { cmdline: String, code: CodeDisplay },
    #[snafu(display("Failed to run ({code}): {cmdline}: {stdout}{stderr}"))]
    ReturnedWithOutputError {
        cmdline: String,
        code: CodeDisplay,
        stdout: String,
        stderr: String,
    },
    #[snafu(display("Couldn't decode stdout/stderr as utf8"))]
    InvalidUtf8 {
        cmdline: String,
        source: FromUtf8Error,
    },
}

pub type Result<T> = std::result::Result<T, Error>;

pub struct Utf8Output {
    pub stdout: String,
    pub stderr: String,
}

pub trait CommandExt {
    /// A shortcut for when the command and its args are known up-front and have
    /// no spaces in them.
    fn run(cmd_and_args: impl AsRef<str>) -> Result<()> {
        let mut all_args = cmd_and_args.as_ref().split(' ');
        Command::new(all_args.next().unwrap())
            .args(all_args)
            .ensure_success()?;
        Ok(())
    }
    fn run_with_output<I, S>(cmd_and_args: I) -> Result<Utf8Output>
    where
        I: IntoIterator<Item = S>,
        S: AsRef<OsStr>,
    {
        let mut all_args = cmd_and_args.into_iter();
        Command::new(all_args.next().unwrap())
            .args(all_args)
            .utf8_output()
    }

    fn ensure_success(&mut self) -> Result<&mut Self>;
    fn utf8_output(&mut self) -> Result<Utf8Output>;
    fn ensure_spawn(&mut self) -> Result<std::process::Child>;
    #[cfg(unix)]
    fn ensure_exec(&mut self) -> Result<()>;
}

impl CommandExt for Command {
    fn ensure_success(&mut self) -> Result<&mut Self> {
        let status = self.status().with_context(|_| DidNotExecuteSnafu {
            cmdline: get_cmdline(self),
        })?;
        ensure!(
            status.success(),
            ReturnedSnafu {
                cmdline: get_cmdline(self),
                code: CodeDisplay::from(status.code()),
            }
        );
        Ok(self)
    }

    fn utf8_output(&mut self) -> Result<Utf8Output> {
        let cmdline = get_cmdline(self);
        let output = self.output().with_context(|_| DidNotExecuteSnafu {
            cmdline: cmdline.clone(),
        })?;

        let stdout = String::from_utf8(output.stdout).with_context(|_| InvalidUtf8Snafu {
            cmdline: cmdline.clone(),
        })?;
        let stderr = String::from_utf8(output.stderr).with_context(|_| InvalidUtf8Snafu {
            cmdline: cmdline.clone(),
        })?;

        ensure!(
            output.status.success(),
            ReturnedWithOutputSnafu {
                cmdline,
                code: CodeDisplay::from(output.status.code()),
                stdout: stdout.clone(),
                stderr: stderr.clone(),
            }
        );

        Ok(Utf8Output { stdout, stderr })
    }

    fn ensure_spawn(&mut self) -> Result<std::process::Child> {
        self.spawn().with_context(|_| DidNotExecuteSnafu {
            cmdline: get_cmdline(self),
        })
    }

    #[cfg(unix)]
    fn ensure_exec(&mut self) -> Result<()> {
        use std::os::unix::process::CommandExt as UnixCommandExt;
        let cmdline = get_cmdline(self);
        let error = self.exec();
        Err(Error::DidNotExecute {
            cmdline,
            source: error,
        })
    }
}

fn get_cmdline(arg: &mut Command) -> String {
    once(arg.get_program().to_string_lossy())
        .chain(arg.get_args().map(|arg| arg.to_string_lossy()))
        .join(" ")
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn test_run() {
        assert_eq!(
            Command::run("fakefake 1 2").unwrap_err().to_string(),
            "Failed to execute: fakefake 1 2"
        );
        #[cfg(not(windows))]
        assert!(matches!(
            Command::new("false").ensure_success(),
            Err(Error::ReturnedError {
                code: CodeDisplay(_),
                ..
            })
        ));
    }
}
