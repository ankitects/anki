// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anyhow::Result;
use ninja_gen::action::BuildAction;
use ninja_gen::command::RunCommand;
use ninja_gen::copy::CopyFile;
use ninja_gen::copy::CopyFiles;
use ninja_gen::glob;
use ninja_gen::hashmap;
use ninja_gen::inputs;
use ninja_gen::node::CompileSass;
use ninja_gen::node::EsbuildScript;
use ninja_gen::node::TypescriptCheck;
use ninja_gen::python::python_format;
use ninja_gen::python::PythonTest;
use ninja_gen::rsync::RsyncFiles;
use ninja_gen::Build;
use ninja_gen::Utf8Path;
use ninja_gen::Utf8PathBuf;

use crate::anki_version;
use crate::python::BuildWheel;
use crate::web::copy_mathjax;

pub fn build_and_check_aqt(build: &mut Build) -> Result<()> {
    build_forms(build)?;
    build_generated_sources(build)?;
    build_data_folder(build)?;
    build_wheel(build)?;
    check_python(build)?;
    Ok(())
}

fn build_forms(build: &mut Build) -> Result<()> {
    let ui_files = glob!["qt/aqt/forms/*.ui"];
    let outdir = Utf8PathBuf::from("qt/_aqt/forms");
    let mut py_files = vec![];
    for path in ui_files.resolve() {
        let outpath = outdir.join(path.file_name().unwrap()).into_string();
        py_files.push(outpath.replace(".ui", "_qt6.py"));
    }
    build.add_action(
        "qt:aqt:forms",
        RunCommand {
            command: ":pyenv:bin",
            args: "$script $first_form",
            inputs: hashmap! {
                "script" => inputs!["qt/tools/build_ui.py"],
                "" => inputs![ui_files],
            },
            outputs: hashmap! {
                "first_form" => vec![py_files[0].as_str()],
                "" => py_files.iter().skip(1).map(|s| s.as_str()).collect(),
            },
        },
    )
}

/// For legacy reasons, we can not easily separate sources and generated files
/// up with a PEP420 namespace, as aqt/__init__.py exports a bunch of things.
/// To allow code to run/typecheck without having to merge source and generated
/// files into a separate folder, the generated files are exported as a separate
/// _aqt module.
fn build_generated_sources(build: &mut Build) -> Result<()> {
    build.add_action(
        "qt:aqt:hooks.py",
        RunCommand {
            command: ":pyenv:bin",
            args: "$script $out",
            inputs: hashmap! {
                "script" => inputs!["qt/tools/genhooks_gui.py"],
                "" => inputs!["pylib/anki/_vendor/stringcase.py", "pylib/tools/hookslib.py"]
            },
            outputs: hashmap! {
                "out" => vec!["qt/_aqt/hooks.py"]
            },
        },
    )?;
    build.add_action(
        "qt:aqt:sass_vars",
        RunCommand {
            command: ":pyenv:bin",
            args: "$script $root_scss $out",
            inputs: hashmap! {
                "script" => inputs!["qt/tools/extract_sass_vars.py"],
                "root_scss" => inputs![":css:_root-vars"],
            },
            outputs: hashmap! {
                "out" => vec![
                    "qt/_aqt/colors.py",
                    "qt/_aqt/props.py"
                ]
            },
        },
    )?;
    // we need to add a py.typed file to the generated sources, or mypy
    // will ignore them when used with the generated wheel
    build.add_action(
        "qt:aqt:py.typed",
        CopyFile {
            input: "qt/aqt/py.typed".into(),
            output: "qt/_aqt/py.typed",
        },
    )?;
    Ok(())
}

fn build_data_folder(build: &mut Build) -> Result<()> {
    build_css(build)?;
    build_imgs(build)?;
    build_js(build)?;
    build_pages(build)?;
    build_icons(build)?;
    copy_sveltekit(build)?;
    Ok(())
}

fn copy_sveltekit(build: &mut Build) -> Result<()> {
    build.add_action(
        "qt:aqt:data:web:sveltekit",
        RsyncFiles {
            inputs: inputs![":sveltekit:folder"],
            target_folder: "qt/_aqt/data/web/",
            strip_prefix: "$builddir/",
            extra_args: "-a --delete",
        },
    )
}

fn build_css(build: &mut Build) -> Result<()> {
    let scss_files = build.expand_inputs(inputs![glob!["qt/aqt/data/web/css/*.scss"]]);
    let out_dir = Utf8Path::new("qt/_aqt/data/web/css");
    for scss in scss_files {
        let stem = Utf8Path::new(&scss).file_stem().unwrap();
        let mut out_path = out_dir.join(stem);
        out_path.set_extension("css");

        build.add_action(
            "qt:aqt:data:web:css",
            CompileSass {
                input: scss.into(),
                output: out_path.as_str(),
                deps: inputs![":sass"],
                load_paths: vec![".", "node_modules"],
            },
        )?;
    }
    let other_ts_css = build.inputs_with_suffix(
        inputs![":ts:editor", ":ts:editable", ":ts:reviewer:reviewer.css"],
        ".css",
    );
    build.add_action(
        "qt:aqt:data:web:css",
        CopyFiles {
            inputs: other_ts_css.into(),
            output_folder: "qt/_aqt/data/web/css",
        },
    )
}

fn build_imgs(build: &mut Build) -> Result<()> {
    build.add_action(
        "qt:aqt:data:web:imgs",
        CopyFiles {
            inputs: inputs![glob!["qt/aqt/data/web/imgs/*"]],
            output_folder: "qt/_aqt/data/web/imgs",
        },
    )
}

fn build_js(build: &mut Build) -> Result<()> {
    for ts_file in &["deckbrowser", "webview", "toolbar", "reviewer-bottom"] {
        build.add_action(
            "qt:aqt:data:web:js",
            EsbuildScript {
                script: "ts/transform_ts.mjs".into(),
                entrypoint: format!("qt/aqt/data/web/js/{ts_file}.ts").into(),
                deps: inputs![],
                output_stem: &format!("qt/_aqt/data/web/js/{ts_file}"),
                extra_exts: &[],
            },
        )?;
    }
    let files = inputs![glob!["qt/aqt/data/web/js/*"]];
    build.add_action(
        "check:typescript:aqt",
        TypescriptCheck {
            tsconfig: "qt/aqt/data/web/js/tsconfig.json".into(),
            inputs: files,
        },
    )?;
    let files_from_ts = build.inputs_with_suffix(
        inputs![":ts:editor", ":ts:reviewer:reviewer.js", ":ts:mathjax"],
        ".js",
    );
    build.add_action(
        "qt:aqt:data:web:js",
        CopyFiles {
            inputs: files_from_ts.into(),
            output_folder: "qt/_aqt/data/web/js",
        },
    )?;
    build_vendor_js(build)
}

fn build_vendor_js(build: &mut Build) -> Result<()> {
    build.add_action("qt:aqt:data:web:js:vendor:mathjax", copy_mathjax())?;
    build.add_action(
        "qt:aqt:data:web:js:vendor",
        CopyFiles {
            inputs: inputs![
                ":node_modules:jquery",
                ":node_modules:jquery-ui",
                ":node_modules:bootstrap-dist",
                "qt/aqt/data/web/js/vendor/plot.js"
            ],
            output_folder: "qt/_aqt/data/web/js/vendor",
        },
    )
}

fn build_pages(build: &mut Build) -> Result<()> {
    build.add_action(
        "qt:aqt:data:web:pages",
        CopyFiles {
            inputs: inputs![":ts:pages"],
            output_folder: "qt/_aqt/data/web/pages",
        },
    )?;
    Ok(())
}

fn build_icons(build: &mut Build) -> Result<()> {
    build_themed_icons(build)?;
    build.add_action(
        "qt:aqt:data:qt:icons:mdi_unthemed",
        CopyFiles {
            inputs: inputs![":node_modules:mdi_unthemed"],
            output_folder: "qt/_aqt/data/qt/icons",
        },
    )?;
    build.add_action(
        "qt:aqt:data:qt:icons:from_src",
        CopyFiles {
            inputs: inputs![glob!["qt/aqt/data/qt/icons/*.{png,svg}"]],
            output_folder: "qt/_aqt/data/qt/icons",
        },
    )?;
    build.add_action(
        "qt:aqt:data:qt:icons",
        RunCommand {
            command: ":pyenv:bin",
            args: "$script $out $in",
            inputs: hashmap! {
                "script" => inputs!["qt/tools/build_qrc.py"],
                "in" => inputs![
                    ":qt:aqt:data:qt:icons:mdi_unthemed",
                    ":qt:aqt:data:qt:icons:mdi_themed",
                    ":qt:aqt:data:qt:icons:from_src",
                ]
            },
            outputs: hashmap! {
                "out" => vec!["qt/_aqt/data/qt/icons.qrc"]
            },
        },
    )?;
    Ok(())
}

fn build_themed_icons(build: &mut Build) -> Result<()> {
    let themed_icons_with_extra = hashmap! {
        "chevron-up" => &["FG_DISABLED"],
        "chevron-down" => &["FG_DISABLED"],
        "drag-vertical" => &["FG_SUBTLE"],
        "drag-horizontal" => &["FG_SUBTLE"],
        "check" => &["FG_DISABLED"],
        "circle-medium" => &["FG_DISABLED"],
        "minus-thick" => &["FG_DISABLED"],
    };
    for icon_path in build.expand_inputs(inputs![":node_modules:mdi_themed"]) {
        let path = Utf8Path::new(&icon_path);
        let stem = path.file_stem().unwrap();
        let mut colors = vec!["FG"];
        if let Some(&extra) = themed_icons_with_extra.get(stem) {
            colors.extend(extra);
        }
        build.add_action(
            "qt:aqt:data:qt:icons:mdi_themed",
            BuildThemedIcon {
                src_icon: path,
                colors,
            },
        )?;
    }
    Ok(())
}

struct BuildThemedIcon<'a> {
    src_icon: &'a Utf8Path,
    colors: Vec<&'a str>,
}

impl BuildAction for BuildThemedIcon<'_> {
    fn command(&self) -> &str {
        "$pyenv_bin $script $in $colors $out"
    }

    fn files(&mut self, build: &mut impl ninja_gen::build::FilesHandle) {
        let stem = self.src_icon.file_stem().unwrap();
        // eg foo-light.svg, foo-dark.svg, foo-FG_SUBTLE-light.svg,
        // foo-FG_SUBTLE-dark.svg
        let outputs: Vec<_> = self
            .colors
            .iter()
            .flat_map(|&color| {
                let variant = if color == "FG" {
                    "".into()
                } else {
                    format!("-{color}")
                };
                [
                    format!("qt/_aqt/data/qt/icons/{stem}{variant}-light.svg"),
                    format!("qt/_aqt/data/qt/icons/{stem}{variant}-dark.svg"),
                ]
            })
            .collect();

        build.add_inputs("pyenv_bin", inputs![":pyenv:bin"]);
        build.add_inputs("script", inputs!["qt/tools/color_svg.py"]);
        build.add_inputs("in", inputs![self.src_icon.as_str()]);
        build.add_inputs("", inputs![":qt:aqt:sass_vars"]);
        build.add_variable("colors", self.colors.join(":"));
        build.add_outputs("out", outputs);
    }
}

fn build_wheel(build: &mut Build) -> Result<()> {
    build.add_action(
        "wheels:aqt",
        BuildWheel {
            name: "aqt",
            version: anki_version(),
            platform: None,
            deps: inputs![":qt:aqt", glob!("qt/aqt/**"), "qt/pyproject.toml"],
        },
    )
}

fn check_python(build: &mut Build) -> Result<()> {
    python_format(build, "qt", inputs![glob!("qt/**/*.py")])?;

    build.add_action(
        "check:pytest:aqt",
        PythonTest {
            folder: "qt/tests",
            python_path: &["pylib", "$builddir/pylib", "$builddir/qt"],
            deps: inputs![":pylib:anki", ":qt:aqt", glob!["qt/tests/**"]],
        },
    )
}
