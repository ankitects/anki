// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::sync::Arc;

pub use anki_proto::launcher::ExistingVersions;
use anki_proto::launcher::Mirror;
use anki_proto::launcher::Options as OptionsProto;
pub use anki_proto::launcher::Version;
pub use anki_proto::launcher::Versions;
use anyhow::anyhow;
use anyhow::Result;
use tokio::sync::watch;

use crate::uv;

pub struct Options {
    allow_betas: bool,
    download_caching: bool,
    mirror: Mirror,
}

impl From<&Options> for OptionsProto {
    fn from(o: &Options) -> Self {
        Self {
            allow_betas: o.allow_betas,
            download_caching: o.download_caching,
            mirror: o.mirror.into(),
        }
    }
}

impl From<&uv::Paths> for Options {
    fn from(state: &uv::Paths) -> Self {
        let allow_betas = state.prerelease_marker.exists();
        let download_caching = !state.no_cache_marker.exists();
        let mirror = if state.mirror_path.exists() {
            Mirror::China
        } else {
            Mirror::Disabled
        };
        Self {
            allow_betas,
            download_caching,
            mirror,
        }
    }
}

pub struct NormalState {
    pub paths: Arc<uv::Paths>,
    pub initial_options: Options,
    pub current_versions: Option<watch::Receiver<Option<Result<ExistingVersions>>>>,
    pub available_versions: Option<watch::Receiver<Option<Result<Versions>>>>,
}

impl From<uv::Paths> for NormalState {
    fn from(paths: uv::Paths) -> Self {
        Self {
            initial_options: Options::from(&paths),
            current_versions: None,
            available_versions: None,
            paths: paths.into(),
        }
    }
}

impl From<NormalState> for State {
    fn from(state: NormalState) -> Self {
        Self::Normal(state)
    }
}

pub enum State {
    LaunchAnki(Arc<uv::Paths>),
    OsUnsupported(anyhow::Error),
    UnknownError(anyhow::Error),
    Uninstall(Arc<uv::Paths>),
    Normal(NormalState),
}

impl State {
    pub fn normal(&self) -> Result<&NormalState> {
        match self {
            State::Normal(state) => Ok(state),
            _ => Err(anyhow!("unexpected state")),
        }
    }

    pub fn paths(&self) -> Result<&uv::Paths> {
        match self {
            State::LaunchAnki(paths) => Ok(paths),
            State::Uninstall(paths) => Ok(paths),
            State::Normal(state) => Ok(&state.paths),
            _ => Err(anyhow!("unexpected state")),
        }
    }
}

impl NormalState {
    pub fn check_versions(&mut self) {
        let (av_tx, av_rx) = tokio::sync::watch::channel(None);
        let paths = self.paths.clone();
        tauri::async_runtime::spawn_blocking(move || {
            let res = paths.get_releases();
            let _ = av_tx.send(Some(res));
        });

        let (cv_tx, cv_rx) = tokio::sync::watch::channel(None);
        let paths = self.paths.clone();
        tauri::async_runtime::spawn_blocking(move || {
            let res = paths.check_versions();
            let _ = cv_tx.send(Some(res));
        });

        self.current_versions = Some(cv_rx);
        self.available_versions = Some(av_rx);
    }
}
