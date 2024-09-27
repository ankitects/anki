// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;
use std::fmt::Display;
use std::sync::LazyLock;

use camino::Utf8PathBuf;

#[derive(Debug, Clone, Hash, Default)]
pub enum BuildInput {
    Single(String),
    Multiple(Vec<String>),
    Glob(Glob),
    Inputs(Vec<BuildInput>),
    #[default]
    Empty,
}

impl AsRef<BuildInput> for BuildInput {
    fn as_ref(&self) -> &BuildInput {
        self
    }
}

impl From<String> for BuildInput {
    fn from(v: String) -> Self {
        BuildInput::Single(v)
    }
}

impl From<&str> for BuildInput {
    fn from(v: &str) -> Self {
        BuildInput::Single(v.to_owned())
    }
}

impl From<Vec<String>> for BuildInput {
    fn from(v: Vec<String>) -> Self {
        BuildInput::Multiple(v)
    }
}

impl From<Glob> for BuildInput {
    fn from(v: Glob) -> Self {
        BuildInput::Glob(v)
    }
}

impl From<&BuildInput> for BuildInput {
    fn from(v: &BuildInput) -> Self {
        BuildInput::Inputs(vec![v.clone()])
    }
}

impl From<&[BuildInput]> for BuildInput {
    fn from(v: &[BuildInput]) -> Self {
        BuildInput::Inputs(v.to_vec())
    }
}

impl From<Vec<BuildInput>> for BuildInput {
    fn from(v: Vec<BuildInput>) -> Self {
        BuildInput::Inputs(v)
    }
}

impl From<Utf8PathBuf> for BuildInput {
    fn from(v: Utf8PathBuf) -> Self {
        BuildInput::Single(v.into_string())
    }
}

impl BuildInput {
    pub fn add_to_vec(
        &self,
        vec: &mut Vec<String>,
        exisiting_outputs: &HashMap<String, Vec<String>>,
    ) {
        let mut resolve_and_add = |value: &str| {
            if let Some(stripped) = value.strip_prefix(':') {
                let files = exisiting_outputs.get(stripped).unwrap_or_else(|| {
                    println!("{:?}", &exisiting_outputs);
                    panic!("input referenced {value}, but rule missing/not processed");
                });
                for file in files {
                    vec.push(file.into())
                }
            } else {
                vec.push(value.into());
            }
        };

        match self {
            BuildInput::Single(s) => resolve_and_add(s),
            BuildInput::Multiple(v) => {
                for item in v {
                    resolve_and_add(item);
                }
            }
            BuildInput::Glob(glob) => {
                for path in glob.resolve() {
                    vec.push(path.into_string());
                }
            }
            BuildInput::Inputs(inputs) => {
                for input in inputs {
                    input.add_to_vec(vec, exisiting_outputs)
                }
            }
            BuildInput::Empty => {}
        }
    }
}

#[derive(Debug, Clone, Hash)]
pub struct Glob {
    pub include: String,
    pub exclude: Option<String>,
}

static CACHED_FILES: LazyLock<Vec<Utf8PathBuf>> = LazyLock::new(cache_files);

/// Walking the source tree once instead of for each glob yields ~4x speed
/// improvements.
fn cache_files() -> Vec<Utf8PathBuf> {
    walkdir::WalkDir::new(".")
        // ensure the output order is predictable
        .sort_by_file_name()
        .into_iter()
        .filter_entry(move |e| {
            // don't walk into symlinks, or the top-level out/, or .git
            !(e.path_is_symlink()
                || (e.depth() == 1 && (e.file_name() == "out" || e.file_name() == ".git")))
        })
        .filter_map(move |e| {
            let path = e.as_ref().unwrap().path().strip_prefix("./").unwrap();
            if !path.is_dir() {
                Some(Utf8PathBuf::from_path_buf(path.to_owned()).unwrap())
            } else {
                None
            }
        })
        .collect()
}

impl Glob {
    pub fn resolve(&self) -> impl Iterator<Item = Utf8PathBuf> {
        let include = globset::GlobBuilder::new(&self.include)
            .literal_separator(true)
            .build()
            .unwrap()
            .compile_matcher();
        let exclude = self.exclude.as_ref().map(|glob| {
            globset::GlobBuilder::new(glob)
                .literal_separator(true)
                .build()
                .unwrap()
                .compile_matcher()
        });
        CACHED_FILES.iter().filter_map(move |path| {
            if include.is_match(path) {
                let excluded = exclude
                    .as_ref()
                    .map(|exclude| exclude.is_match(path))
                    .unwrap_or_default();
                if !excluded {
                    return Some(path.to_owned());
                }
            }
            None
        })
    }
}

pub fn space_separated<I>(iter: I) -> String
where
    I: IntoIterator,
    I::Item: Display,
{
    itertools::join(iter, " ")
}
