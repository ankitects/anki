// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use axum::extract::Json;
use axum::routing::post;
use axum::Router;

use crate::backend::Backend;
use crate::services::CardRenderingService;

pub fn add_routes<'a, S>(backend: &'a Backend, router: Router<S>) -> Router<S>
where
    'a: 'static,
    S: Clone + Send + Sync + 'static,
{
    router.route(
        "/strip_html",
        post(
            async |Json(payload): Json<anki_proto::card_rendering::StripHtmlRequest>| {
                Json(backend.with_col(|col| col.strip_html(payload)).unwrap())
            },
        ),
    )
}
