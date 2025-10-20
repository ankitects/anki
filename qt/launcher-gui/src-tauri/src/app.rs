// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

// use std::sync::Mutex;

use tauri::http;
use tauri::App;
use tauri::AppHandle;
use tauri::Manager;
use tauri::Runtime;
use tauri::UriSchemeContext;
use tauri::UriSchemeResponder;
use tauri_plugin_os::locale;

use crate::generated;
use crate::lang::I18n;
use crate::state::State;
use crate::uv;

pub const PROTOCOL: &str = "anki";

pub fn init() -> Option<State> {
    let mut state = State::init().unwrap_or_else(State::UnknownError);

    match state {
        State::Normal(ref mut state) => state.check_versions(),
        State::LaunchAnki(ref paths) => {
            let args: Vec<String> = std::env::args().skip(1).collect();
            let cmd = uv::build_python_command(paths, &args).unwrap();
            uv::launch_anki_normally(cmd).unwrap();
            return None;
        }
        _ => {}
    }

    Some(state)
}

pub fn setup(app: &mut App, state: State) -> anyhow::Result<()> {
    let tr = I18n::new(&[&locale().unwrap_or_default()]);
    app.manage(crate::lang::Tr::new(Some(tr)));

    app.manage(state);

    #[cfg(debug_assertions)]
    let _ = app
        .get_webview_window("main")
        .unwrap()
        .set_always_on_top(true);

    Ok(())
}

pub fn serve<R: Runtime>(
    ctx: UriSchemeContext<'_, R>,
    req: http::Request<Vec<u8>>,
    responder: UriSchemeResponder,
) {
    let app = ctx.app_handle().to_owned();
    let window = app
        .get_webview_window(ctx.webview_label())
        .expect("could not get webview");

    let builder = http::Response::builder()
        .header(http::header::ACCESS_CONTROL_ALLOW_ORIGIN, "*")
        .header(http::header::CONTENT_TYPE, "application/binary");

    tauri::async_runtime::spawn(async move {
        match *req.method() {
            http::Method::POST => {
                let response = match generated::handle_rpc(app, window, req).await {
                    Ok(res) if !res.is_empty() => builder.body(res),
                    Ok(res) => builder.status(http::StatusCode::NO_CONTENT).body(res),
                    Err(e) => {
                        eprintln!("ERROR: {e:?}");
                        builder
                            .status(http::StatusCode::INTERNAL_SERVER_ERROR)
                            .header(http::header::CONTENT_TYPE, "text/plain")
                            .body(format!("{e:?}").as_bytes().to_vec())
                    }
                };
                responder.respond(response.expect("could not build response"));
            }
            // handle preflight requests (on windows at least)
            http::Method::OPTIONS => {
                responder.respond(
                    builder
                        .header(http::header::ACCESS_CONTROL_ALLOW_METHODS, "POST")
                        .header(http::header::ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type")
                        .body(vec![])
                        .unwrap(),
                );
            }
            _ => unimplemented!("rpc calls must use POST"),
        }
    });
}

pub fn on_second_instance(app: &AppHandle, _args: Vec<String>, _cwd: String) {
    let _ = app
        .get_webview_window("main")
        .expect("no main window")
        .set_focus();
}
