// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::generic;
use anki_proto::launcher::get_langs_response;
use anki_proto::launcher::get_mirrors_response;
use anki_proto::launcher::state::Kind as StateProtoKind;
use anki_proto::launcher::ChooseVersionRequest;
use anki_proto::launcher::ChooseVersionResponse;
use anki_proto::launcher::Event;
use anki_proto::launcher::GetLangsResponse;
use anki_proto::launcher::GetMirrorsResponse;
use anki_proto::launcher::I18nResourcesRequest;
use anki_proto::launcher::Mirror;
use anki_proto::launcher::NormalState as NormalStateProto;
use anki_proto::launcher::Options;
use anki_proto::launcher::State as StateProto;
use anki_proto::launcher::Uninstall as UninstallProto;
use anki_proto::launcher::UninstallRequest;
use anki_proto::launcher::UninstallResponse;
use anki_proto::launcher::ZoomWebviewRequest;
use anyhow::anyhow;
use anyhow::Context;
use anyhow::Result;
use strum::IntoEnumIterator;
use tauri::AppHandle;
use tauri::Emitter;
use tauri::Runtime;
use tauri::WebviewWindow;
use tauri_plugin_os::locale;

use crate::app::StateExt;
use crate::lang::I18nExt;
use crate::lang::LANGS;
use crate::lang::LANGS_DEFAULT_REGION;
use crate::lang::LANGS_WITH_REGIONS;
use crate::state::ExistingVersions;
use crate::state::State;
use crate::state::Uninstall;
use crate::state::Versions;
use crate::uv;

pub async fn i18n_resources<R: Runtime>(
    app: AppHandle<R>,
    _window: WebviewWindow<R>,
    input: I18nResourcesRequest,
) -> Result<generic::Json> {
    let tr = app.tr()?;
    serde_json::to_vec(&tr.resources_for_js(&input.modules))
        .with_context(|| "failed to serialise i18n resources")
        .map(Into::into)
}

pub async fn get_langs<R: Runtime>(
    _app: AppHandle<R>,
    _window: WebviewWindow<R>,
) -> Result<GetLangsResponse> {
    let langs = LANGS
        .into_iter()
        .map(|(locale, name)| get_langs_response::Pair {
            name: name.to_string(),
            locale: locale.to_string(),
        })
        .collect();

    let user_locale = locale()
        .and_then(|l| {
            if LANGS.contains_key(&l) {
                Some(l)
            } else {
                LANGS_DEFAULT_REGION
                    .get(l.split('-').next().unwrap())
                    .or_else(|| LANGS_DEFAULT_REGION.get("en"))
                    .map(ToString::to_string)
            }
        })
        .unwrap();

    Ok(GetLangsResponse { user_locale, langs })
}

pub async fn set_lang<R: Runtime>(
    app: AppHandle<R>,
    _window: WebviewWindow<R>,
    input: generic::String,
) -> Result<()> {
    // python's lang_to_disk_lang
    let input = input.val;
    let input = if LANGS_WITH_REGIONS.contains(input.as_str()) {
        input
    } else {
        input.split('-').next().unwrap().to_owned()
    };
    app.setup_tr(&[&*input]);
    Ok(())
}

pub async fn get_state<R: Runtime>(
    app: AppHandle<R>,
    _window: WebviewWindow<R>,
) -> Result<StateProto> {
    let kind = match app.flow() {
        State::LaunchAnki(_) => unreachable!(),
        State::OsUnsupported(e) => StateProtoKind::OsUnsupported(format!("{e:?}")),
        State::UnknownError(e) => StateProtoKind::UnknownError(format!("{e:?}")),
        State::Uninstall(_) => StateProtoKind::Uninstall(()),
        State::Normal(normal) => StateProtoKind::Normal(NormalStateProto {
            options: Some((&normal.initial_options).into()),
        }),
    };
    Ok(StateProto { kind: Some(kind) })
}

pub async fn get_mirrors<R: Runtime>(
    app: AppHandle<R>,
    _window: WebviewWindow<R>,
) -> Result<GetMirrorsResponse> {
    let tr = app.tr()?;
    Ok(GetMirrorsResponse {
        mirrors: Mirror::iter()
            .map(|mirror| get_mirrors_response::Pair {
                mirror: mirror.into(),
                name: match mirror {
                    Mirror::Disabled => tr.launcher_mirror_no_mirror(),
                    Mirror::China => tr.launcher_mirror_china(),
                }
                .into(),
            })
            .collect(),
    })
}

pub async fn get_options<R: Runtime>(
    app: AppHandle<R>,
    _window: WebviewWindow<R>,
) -> Result<Options> {
    let state = app.flow();
    let options = (&state.normal()?.initial_options).into();
    Ok(options)
}

pub async fn get_available_versions<R: Runtime>(
    app: AppHandle<R>,
    _window: WebviewWindow<R>,
) -> Result<Versions> {
    let state = app.flow().normal()?;
    let mut rx = state.available_versions.clone().unwrap();
    rx.changed().await.unwrap();
    let x = rx.borrow();
    match x.as_ref().unwrap() {
        Ok(versions) => Ok(versions.clone()),
        // TODO: errors are passed as strings to the web
        Err(e) => Err(anyhow!("{e:?}")),
    }
}

pub async fn get_existing_versions<R: Runtime>(
    app: AppHandle<R>,
    _window: WebviewWindow<R>,
) -> Result<ExistingVersions> {
    let state = app.flow().normal()?;
    let mut rx = state.current_versions.clone().unwrap();
    rx.changed().await.unwrap();
    let x = rx.borrow();
    match x.as_ref().unwrap() {
        Ok(versions) => Ok(versions.clone()),
        Err(e) => Err(anyhow!("{e:?}")),
    }
}

pub async fn choose_version<R: Runtime>(
    app: AppHandle<R>,
    _window: WebviewWindow<R>,
    input: ChooseVersionRequest,
) -> Result<ChooseVersionResponse> {
    let state = app.flow().normal()?;
    let paths = state.paths.clone();

    tauri::async_runtime::spawn_blocking(move || {
        if let Some(options) = input.options {
            uv::set_allow_betas(&paths, options.allow_betas)?;
            uv::set_cache_enabled(&paths, options.download_caching)?;
            uv::set_mirror(&paths, options.mirror != Mirror::Disabled as i32)?;
        }

        let version = input.version;
        let on_pty_data = move |data| {
            let _ = app.emit(Event::PtyData.as_str_name(), data);
        };

        if !input.keep_existing || paths.pyproject_modified_by_user {
            // install or resync
            uv::handle_version_install_or_update(
                &paths,
                &version,
                input.keep_existing,
                input.current.as_deref(),
                on_pty_data,
            )?;
        }

        let warming_up = uv::post_install(&paths)?;

        Ok(ChooseVersionResponse {
            version,
            warming_up,
        })
    })
    .await?
}

pub async fn launch_anki<R: Runtime>(app: AppHandle<R>, _window: WebviewWindow<R>) -> Result<()> {
    app.flow().paths().and_then(uv::launch_anki)
}

pub async fn exit<R: Runtime>(app: AppHandle<R>, window: WebviewWindow<R>) -> Result<()> {
    tauri::async_runtime::spawn_blocking(move || {
        let _ = window.destroy();
        // can't be called from the main thread
        app.exit(0);
    });

    Ok(())
}

pub async fn get_uninstall_info<R: Runtime>(
    app: AppHandle<R>,
    _window: WebviewWindow<R>,
) -> Result<UninstallProto> {
    app.flow().paths().map(Uninstall::from).map(Into::into)
}

pub async fn uninstall_anki<R: Runtime>(
    app: AppHandle<R>,
    _window: WebviewWindow<R>,
    input: UninstallRequest,
) -> Result<UninstallResponse> {
    let paths = app.flow().paths()?;
    let action_needed = uv::handle_uninstall(paths, input.delete_base_folder)?;
    Ok(UninstallResponse { action_needed })
}

/// NOTE:  [zoomHotkeysEnabled](https://v2.tauri.app/reference/config/#zoomhotkeysenabled) exists
/// but the polyfill it uses on linux doesn't allow regular scrolling
pub async fn zoom_webview<R: Runtime>(
    _app: AppHandle<R>,
    window: WebviewWindow<R>,
    input: ZoomWebviewRequest,
) -> Result<()> {
    let factor = input.scale_factor.into();
    // NOTE: not supported on windows
    let _ = window.set_zoom(factor);
    Ok(())
}

pub async fn window_ready<R: Runtime>(_app: AppHandle<R>, window: WebviewWindow<R>) -> Result<()> {
    window
        .show()
        .with_context(|| format!("could not show window: {}", window.label()))
}
