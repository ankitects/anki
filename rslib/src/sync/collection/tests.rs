// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#![cfg(test)]

use std::future::Future;
use std::sync::LazyLock;

use anki_proto::sync::sync_status_response;
use axum::http::StatusCode;
use reqwest::Client;
use reqwest::Url;
use serde_json::json;
use tempfile::tempdir;
use tempfile::TempDir;
use tokio::sync::Mutex;
use tokio::sync::MutexGuard;
use tracing::Instrument;
use tracing::Span;
use wiremock::matchers::method;
use wiremock::matchers::path;
use wiremock::Mock;
use wiremock::MockServer;
use wiremock::ResponseTemplate;

use crate::card::CardQueue;
use crate::card::CardType;
use crate::collection::Collection;
use crate::collection::CollectionBuilder;
use crate::deckconfig::DeckConfig;
use crate::decks::DeckKind;
use crate::error::SyncError;
use crate::error::SyncErrorKind;
use crate::log::set_global_logger;
use crate::notes::AddNoteRequest;
use crate::notetype::all_stock_notetypes;
use crate::prelude::*;
use crate::revlog::RevlogEntry;
use crate::search::SortMode;
use crate::sync::collection::graves::ApplyGravesRequest;
use crate::sync::collection::meta::MetaRequest;
use crate::sync::collection::normal::NormalSyncer;
use crate::sync::collection::normal::SyncActionRequired;
use crate::sync::collection::normal::SyncOutput;
use crate::sync::collection::protocol::EmptyInput;
use crate::sync::collection::protocol::SyncProtocol;
use crate::sync::collection::start::StartRequest;
use crate::sync::collection::status::online_sync_status_check;
use crate::sync::collection::upload::UploadResponse;
use crate::sync::collection::upload::CORRUPT_MESSAGE;
use crate::sync::http_client::HttpSyncClient;
use crate::sync::http_server::default_ip_header;
use crate::sync::http_server::SimpleServer;
use crate::sync::http_server::SyncServerConfig;
use crate::sync::login::HostKeyRequest;
use crate::sync::login::SyncAuth;
use crate::sync::request::IntoSyncRequest;

const FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE: &str = "search_stats_fsrs_review_retrievability";
const RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE: &str = "search_stats_rwkv_review_retrievability";

struct TestAuth {
    username: String,
    password: String,
    host_key: String,
}

static AUTH: LazyLock<TestAuth> = LazyLock::new(|| {
    if let Ok(auth) = std::env::var("TEST_AUTH") {
        let mut auth = auth.split(':');
        TestAuth {
            username: auth.next().unwrap().into(),
            password: auth.next().unwrap().into(),
            host_key: auth.next().unwrap().into(),
        }
    } else {
        TestAuth {
            username: "user".to_string(),
            password: "pass".to_string(),
            host_key: "b2619aa1529dfdc4248e6edbf3c1b2a2b014cf6d".to_string(),
        }
    }
});

pub(in crate::sync) async fn with_active_server<F, O>(op: F) -> Result<()>
where
    F: FnOnce(HttpSyncClient) -> O,
    O: Future<Output = Result<()>>,
{
    let _ = set_global_logger(None);
    // start server
    let base_folder = tempdir()?;
    std::env::set_var("SYNC_USER1", "user:pass");
    let (addr, server_fut) = SimpleServer::make_server(SyncServerConfig {
        host: "127.0.0.1".parse().unwrap(),
        port: 0,
        base_folder: base_folder.path().into(),
        ip_header: default_ip_header(),
    })
    .await
    .unwrap();
    tokio::spawn(server_fut.instrument(Span::current()));
    // when not using ephemeral servers, tests need to be serialized
    static LOCK: LazyLock<Mutex<()>> = LazyLock::new(|| Mutex::new(()));
    let _lock: MutexGuard<()>;
    // setup client to connect to it
    let endpoint = if let Ok(endpoint) = std::env::var("TEST_ENDPOINT") {
        _lock = LOCK.lock().await;
        endpoint
    } else {
        format!("http://{addr}/")
    };
    let endpoint = Url::try_from(endpoint.as_str()).unwrap();
    let auth = SyncAuth {
        hkey: AUTH.host_key.clone(),
        endpoint: Some(endpoint),
        io_timeout_secs: None,
    };
    let client = HttpSyncClient::new(auth, Client::new());
    op(client).await
}

fn unwrap_sync_err_kind(err: AnkiError) -> SyncErrorKind {
    let AnkiError::SyncError {
        source: SyncError { kind, .. },
    } = err
    else {
        panic!("not sync err: {err:?}");
    };
    kind
}

fn main_table_exists(col: &Collection, table: &str) -> Result<bool> {
    col.storage
        .db
        .prepare("SELECT null FROM main.sqlite_master WHERE type = 'table' AND name = ?")?
        .exists([table])
        .map_err(Into::into)
}

fn add_legacy_retrievability_cache_tables(col: &Collection) -> Result<()> {
    col.storage.db.execute_batch(&format!(
        "
        CREATE TABLE main.{FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE} (
            revlog_id INTEGER NOT NULL,
            prediction REAL NOT NULL,
            source TEXT NOT NULL,
            updated_at INTEGER NOT NULL,
            sample_role TEXT NOT NULL DEFAULT 'final_fit',
            fold_index INTEGER NOT NULL DEFAULT -1,
            PRIMARY KEY (revlog_id, sample_role, fold_index, source)
        );
        INSERT INTO main.{FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE}
            (revlog_id, prediction, source, updated_at, sample_role, fold_index)
        VALUES (1, 0.25, 'legacy_fsrs', 123, 'validation_fold', 2);
        CREATE TABLE main.{RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE} (
            revlog_id INTEGER NOT NULL,
            prediction REAL NOT NULL,
            source TEXT NOT NULL,
            updated_at INTEGER NOT NULL,
            sample_role TEXT NOT NULL DEFAULT 'final_fit',
            fold_index INTEGER NOT NULL DEFAULT -1,
            PRIMARY KEY (revlog_id, sample_role, fold_index, source)
        );
        INSERT INTO main.{RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE}
            (revlog_id, prediction, source, updated_at, sample_role, fold_index)
        VALUES (2, 0.75, 'legacy_rwkv', 456, 'test_fold', 0);
        "
    ))?;
    Ok(())
}

#[tokio::test]
async fn host_key() -> Result<()> {
    with_active_server(|mut client| async move {
        let err = client
            .host_key(
                HostKeyRequest {
                    username: "bad".to_string(),
                    password: "bad".to_string(),
                }
                .try_into_sync_request()?,
            )
            .await
            .unwrap_err();
        assert_eq!(err.code, StatusCode::FORBIDDEN);
        assert_eq!(
            unwrap_sync_err_kind(AnkiError::from(err)),
            SyncErrorKind::AuthFailed
        );
        // hkey should be automatically set after successful login
        client.sync_key = String::new();
        let resp = client
            .host_key(
                HostKeyRequest {
                    username: AUTH.username.clone(),
                    password: AUTH.password.clone(),
                }
                .try_into_sync_request()?,
            )
            .await?
            .json()?;
        assert_eq!(resp.key, *AUTH.host_key);
        Ok(())
    })
    .await
}

#[tokio::test]
async fn meta() -> Result<()> {
    with_active_server(|client| async move {
        // unsupported sync version
        assert_eq!(
            SyncProtocol::meta(
                &client,
                MetaRequest {
                    sync_version: 0,
                    client_version: "".to_string(),
                }
                .try_into_sync_request()?,
            )
            .await
            .unwrap_err()
            .code,
            StatusCode::NOT_IMPLEMENTED
        );

        Ok(())
    })
    .await
}

#[tokio::test]
async fn aborting_is_idempotent() -> Result<()> {
    with_active_server(|mut client| async move {
        // abort is a no-op if no sync in progress
        client.abort(EmptyInput::request()).await?;

        // start a sync
        let _graves = client
            .start(
                StartRequest {
                    client_usn: Default::default(),
                    local_is_newer: false,
                    deprecated_client_graves: None,
                }
                .try_into_sync_request()?,
            )
            .await?;

        // an abort request with the wrong key is ignored
        let orig_key = client.skey().to_string();
        client.set_skey("aabbccdd".into());
        client.abort(EmptyInput::request()).await?;

        // it should succeed with the correct key
        client.set_skey(orig_key);
        client.abort(EmptyInput::request()).await?;
        Ok(())
    })
    .await
}

#[tokio::test]
async fn new_syncs_cancel_old_ones() -> Result<()> {
    with_active_server(|mut client| async move {
        let ctx = SyncTestContext::new(client.clone());

        // start a sync
        let req = StartRequest {
            client_usn: Default::default(),
            local_is_newer: false,
            deprecated_client_graves: None,
        }
        .try_into_sync_request()?;
        let _ = client.start(req.clone()).await?;

        // a new sync aborts the previous one
        let orig_key = client.skey().to_string();
        client.set_skey("1".into());
        let _ = client.start(req.clone()).await?;

        // old sync can no longer proceed
        client.set_skey(orig_key);
        let graves_req = ApplyGravesRequest::default().try_into_sync_request()?;
        assert_eq!(
            client
                .apply_graves(graves_req.clone())
                .await
                .unwrap_err()
                .code,
            StatusCode::CONFLICT
        );

        // with the correct key, it can continue
        client.set_skey("1".into());
        client.apply_graves(graves_req.clone()).await?;
        // but a full upload will break the lock
        ctx.full_upload(ctx.col1()).await;
        assert_eq!(
            client
                .apply_graves(graves_req.clone())
                .await
                .unwrap_err()
                .code,
            StatusCode::CONFLICT
        );

        // likewise with download
        let _ = client.start(req.clone()).await?;
        ctx.full_download(ctx.col1()).await;
        assert_eq!(
            client
                .apply_graves(graves_req.clone())
                .await
                .unwrap_err()
                .code,
            StatusCode::CONFLICT
        );

        Ok(())
    })
    .await
}

#[tokio::test]
async fn sync_roundtrip() -> Result<()> {
    with_active_server(|client| async move {
        let ctx = SyncTestContext::new(client);
        upload_download(&ctx).await?;
        regular_sync(&ctx).await?;
        Ok(())
    })
    .await
}

#[tokio::test]
async fn normal_sync_transfers_large_batches_between_clients() -> Result<()> {
    with_active_server(|client| async move {
        let ctx = SyncTestContext::new(client);
        upload_download(&ctx).await?;
        let mut desktop = ctx.col1();
        let mut other = ctx.col2();
        let notetype = desktop.basic_notetype();
        let mut requests = (0..600)
            .map(|index| {
                let mut note = notetype.new_note();
                note.set_field(0, format!("sync batch note {index}"))?;
                Ok(AddNoteRequest {
                    note,
                    deck_id: DeckId(1),
                })
            })
            .collect::<Result<Vec<_>>>()?;
        desktop.add_notes(&mut requests)?;

        ctx.normal_sync(&mut desktop).await;
        assert!(ctx.normal_sync(&mut other).await.remote_collection_changed);
        assert_eq!(other.storage.get_all_note_ids()?.len(), 601);
        assert_eq!(other.storage.get_all_card_ids()?.len(), 601);
        for request in &requests {
            let received = other.storage.get_note(request.note.id)?.unwrap();
            assert_eq!(received.fields(), request.note.fields());
        }
        Ok(())
    })
    .await
}

#[tokio::test]
async fn deck_options_metadata_syncs_after_opaque_client_edits() -> Result<()> {
    with_active_server(|client| async move {
        let ctx = SyncTestContext::new(client);
        upload_download(&ctx).await?;
        let mut desktop = ctx.col1();
        let mut other_client = ctx.col2();
        let mut config = DeckConfig {
            name: "Custom display order".into(),
            ..Default::default()
        };
        config.inner.review_order =
            crate::deckconfig::ReviewCardOrder::RetrievabilityDescending as i32;
        let metadata = json!({
            "intraday_learning_mix": crate::deckconfig::ReviewMix::AfterReviews as i32,
            "future_option": "preserve",
        });
        config.inner.other = serde_json::to_vec(&json!({
            "jschoreels.deckoptions": metadata,
        }))?;
        desktop.transact_no_undo(|col| col.add_or_update_deck_config(&mut config))?;
        let config_id = config.id;
        ctx.normal_sync(&mut desktop).await;
        assert!(
            ctx.normal_sync(&mut other_client)
                .await
                .remote_collection_changed
        );

        // An unsupported client edits known preset settings without interpreting
        // the namespaced options carried by the generic sync representation.
        let original = other_client.get_deck_config(config_id, false)?.unwrap();
        let original_collection_modified = other_client.sync_meta()?.modified;
        let mut wire =
            serde_json::to_value(crate::deckconfig::DeckConfSchema11::from(original.clone()))?;
        assert_eq!(wire["jschoreels.deckoptions"], metadata);
        wire["name"] = json!("Edited on another client");
        wire["rev"]["perDay"] = json!(123);
        // Preset conflict resolution uses seconds. Give this sequential edit an
        // explicit later timestamp instead of depending on wall-clock rollover.
        wire["mod"] = json!(original.mtime_secs.adding_secs(1).0);
        let mut config =
            DeckConfig::from(serde_json::from_value::<crate::deckconfig::DeckConfSchema11>(wire)?);
        config.usn = other_client.usn()?;
        other_client
            .transact_no_undo(|col| col.update_deck_config_inner(&mut config, original, None))?;
        // Normal sync also compares millisecond collection timestamps. Ensure
        // this immediate test edit cannot equal the preceding sync timestamp.
        other_client
            .storage
            .set_modified_time(TimestampMillis(original_collection_modified.0 + 1))?;
        let edited = other_client.get_deck_config(config_id, false)?.unwrap();
        assert_eq!(edited.name, "Edited on another client");
        ctx.normal_sync(&mut other_client).await;
        assert!(
            ctx.normal_sync(&mut desktop)
                .await
                .remote_collection_changed
        );

        let config = desktop.get_deck_config(config_id, false)?.unwrap();
        assert_eq!(
            config.intraday_learning_mix(),
            crate::deckconfig::ReviewMix::AfterReviews
        );
        assert_eq!(config.name, "Edited on another client");
        assert_eq!(config.inner.reviews_per_day, 123);
        assert_eq!(
            config.inner.review_order,
            crate::deckconfig::ReviewCardOrder::RetrievabilityDescending as i32
        );
        let wire = serde_json::to_value(crate::deckconfig::DeckConfSchema11::from(config))?;
        assert_eq!(wire["jschoreels.deckoptions"], metadata);
        Ok(())
    })
    .await
}

fn update_rwkv_sync_test_card(
    col: &mut Collection,
    card_id: CardId,
    boundary: Option<i64>,
) -> Result<()> {
    col.transact(Op::UpdateCard, |col| {
        let original = col.storage.get_card(card_id)?.unwrap();
        let mut card = original.clone();
        card.ctype = CardType::Review;
        card.queue = CardQueue::Review;
        card.rwkv_history_reset_after = boundary;
        col.mirror_rwkv_history_reset(&card)?;
        col.update_card_inner(&mut card, original, col.usn()?)?;
        Ok(())
    })?;
    Ok(())
}

async fn stale_legacy_card_edit_preserves_rwkv_boundary(
    initial_boundary: Option<i64>,
    final_boundary: Option<i64>,
) -> Result<()> {
    with_active_server(|client| async move {
        let ctx = SyncTestContext::new(client);
        upload_download(&ctx).await?;
        let mut desktop = ctx.col1();
        let mut legacy = ctx.col2();
        let card_id = *desktop.storage.get_all_card_ids()?.iter().next().unwrap();
        desktop.transact(Op::UpdateCard, |col| {
            for review_id in [1_700_000_000_000, 1_700_000_001_000] {
                col.add_revlog_entry_undoable(RevlogEntry {
                    id: RevlogId(review_id),
                    cid: card_id,
                    usn: col.usn()?,
                    button_chosen: 3,
                    interval: 1,
                    ease_factor: 2_500,
                    ..Default::default()
                })?;
            }
            Ok(())
        })?;

        if initial_boundary.is_some() {
            update_rwkv_sync_test_card(&mut desktop, card_id, initial_boundary)?;
            ctx.normal_sync(&mut desktop).await;
            ctx.normal_sync(&mut legacy).await;
        }
        update_rwkv_sync_test_card(&mut desktop, card_id, final_boundary)?;
        ctx.normal_sync(&mut desktop).await;
        let server_mtime = desktop.sync_meta()?.modified.0;
        let card_mtime = desktop.storage.get_card(card_id)?.unwrap().mtime.0;

        // The older client was offline during the new boundary decision. Its
        // card edit discards the unknown field and syncs its stale config.
        while TimestampMillis::now().0 <= server_mtime || TimestampSecs::now().0 <= card_mtime {
            tokio::task::yield_now().await;
        }
        legacy.transact(Op::UpdateCard, |col| {
            let original = col.storage.get_card(card_id)?.unwrap();
            let mut card = original.clone();
            card.rwkv_history_reset_after = None;
            card.flags = 1;
            card.ctype = CardType::Review;
            card.queue = CardQueue::Review;
            col.update_card_inner(&mut card, original, col.usn()?)?;
            Ok(())
        })?;
        ctx.normal_sync(&mut legacy).await;
        let stale: Option<crate::config::RwkvHistoryResetMirror> = legacy
            .storage
            .get_config_value(&crate::config::rwkv_history_reset_config_key(card_id))?;
        assert_eq!(stale.and_then(|mirror| mirror.after), initial_boundary);
        let synced = ctx.normal_sync(&mut desktop).await;
        assert_eq!(synced.required, SyncActionRequired::NoChanges);
        let card = desktop.storage.get_card(card_id)?.unwrap();
        assert_eq!(card.flags, 1);
        assert_eq!(card.rwkv_history_reset_after, final_boundary);
        let expected_reviews = if final_boundary.is_some() { 1 } else { 2 };
        assert_eq!(
            desktop
                .rwkv_historical_review_inputs(Default::default())?
                .reviews
                .len(),
            expected_reviews
        );
        assert!(!desktop
            .storage
            .get_collection_timestamps()?
            .collection_changed_since_sync());

        // A fresh device must get the reconciled boundary from the server in
        // the same sync, with no local copy to rescue it.
        let third_path = ctx.folder.path().join("third.anki2");
        let third = CollectionBuilder::new(&third_path).build()?;
        ctx.full_download(third).await;
        let mut third = CollectionBuilder::new(&third_path).build()?;
        assert_eq!(
            third
                .storage
                .get_card(card_id)?
                .unwrap()
                .rwkv_history_reset_after,
            final_boundary
        );
        assert_eq!(
            third
                .rwkv_historical_review_inputs(Default::default())?
                .reviews
                .len(),
            expected_reviews
        );
        Ok(())
    })
    .await
}

#[tokio::test]
async fn rwkv_boundary_survives_older_client_stale_sync() -> Result<()> {
    stale_legacy_card_edit_preserves_rwkv_boundary(None, Some(1_700_000_000_123)).await
}

#[tokio::test]
async fn rwkv_scheduled_restore_survives_older_client_stale_sync() -> Result<()> {
    stale_legacy_card_edit_preserves_rwkv_boundary(Some(1_700_000_000_123), None).await
}

/// See issue #5109
#[tokio::test]
async fn new_empty_collection_should_not_require_full_sync() -> Result<()> {
    with_active_server(|client: HttpSyncClient| async move {
        let ctx = SyncTestContext::new(client);
        let mut col1 = ctx.col1();

        // a new collection reports NoChanges, deferring to the online check
        assert_eq!(
            col1.sync_status_offline()?,
            sync_status_response::Required::NoChanges
        );

        // the online check agrees when the remote account is also new
        let state = online_sync_status_check(col1.sync_meta()?, &mut ctx.cloned_client()).await?;
        assert_eq!(state.required, SyncActionRequired::NoChanges);

        // syncing is a no-op that doesn't stamp last_sync, and the status
        // must remain NoChanges afterwards
        let out = ctx.normal_sync(&mut col1).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);
        assert_eq!(
            col1.sync_status_offline()?,
            sync_status_response::Required::NoChanges
        );

        // once local changes exist, the online check picks them up, even
        // though the offline check still defers
        col1_setup(&mut col1);
        assert_eq!(
            col1.sync_status_offline()?,
            sync_status_response::Required::NoChanges
        );
        let state = online_sync_status_check(col1.sync_meta()?, &mut ctx.cloned_client()).await?;
        assert!(matches!(
            state.required,
            SyncActionRequired::FullSyncRequired { .. }
        ));

        Ok(())
    })
    .await
}

#[tokio::test]
async fn check_database_legacy_retrievability_cache_cleanup_reaches_server() -> Result<()> {
    with_active_server(|client| async move {
        let ctx = SyncTestContext::new(client);
        upload_download(&ctx).await?;

        let mut col1 = ctx.col1();
        add_legacy_retrievability_cache_tables(&col1)?;
        col1.check_database()?;

        let out = ctx.normal_sync(&mut col1).await;
        assert_eq!(
            out.required,
            SyncActionRequired::FullSyncRequired {
                upload_ok: true,
                download_ok: true,
            }
        );
        ctx.full_upload(col1).await;

        let col2 = ctx.col2();
        ctx.full_download(col2).await;

        let col2 = ctx.col2();
        assert!(!main_table_exists(
            &col2,
            FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE
        )?);
        assert!(!main_table_exists(
            &col2,
            RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE
        )?);

        Ok(())
    })
    .await
}

#[tokio::test]
async fn sanity_check_should_roll_back_and_force_full_sync() -> Result<()> {
    with_active_server(|client| async move {
        let ctx = SyncTestContext::new(client);
        upload_download(&ctx).await?;

        let mut col1 = ctx.col1();

        // add a deck but don't mark it as requiring a sync, which will trigger the
        // sanity check to fail
        let mut deck = col1.get_or_create_normal_deck("unsynced deck")?;
        col1.add_or_update_deck(&mut deck)?;
        col1.storage
            .db
            .execute("update decks set usn=0 where id=?", [deck.id])?;

        // the sync should fail
        let err = NormalSyncer::new(&mut col1, ctx.cloned_client())
            .sync()
            .await
            .unwrap_err();
        assert!(matches!(
            err,
            AnkiError::SyncError {
                source: SyncError {
                    kind: SyncErrorKind::SanityCheckFailed { .. },
                    ..
                }
            }
        ));

        // the server should have rolled back
        let mut col2 = ctx.col2();
        let out = ctx.normal_sync(&mut col2).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);

        // and the client should have forced a one-way sync
        let out = ctx.normal_sync(&mut col1).await;
        assert_eq!(
            out.required,
            SyncActionRequired::FullSyncRequired {
                upload_ok: true,
                download_ok: true,
            }
        );

        Ok(())
    })
    .await
}

#[tokio::test]
async fn sync_errors_should_prompt_db_check() -> Result<()> {
    with_active_server(|client| async move {
        let ctx = SyncTestContext::new(client);
        upload_download(&ctx).await?;

        let mut col1 = ctx.col1();

        // Add a a new notetype, and a note that uses it, but don't mark the notetype as
        // requiring a sync, which will cause the sync to fail as the note is added.
        let mut nt = all_stock_notetypes(&col1.tr).remove(0);
        nt.name = "new".into();
        col1.add_notetype(&mut nt, false)?;
        let mut note = nt.new_note();
        note.set_field(0, "test")?;
        col1.add_note(&mut note, DeckId(1))?;
        col1.storage.db.execute("update notetypes set usn=0", [])?;

        // the sync should fail
        let err = NormalSyncer::new(&mut col1, ctx.cloned_client())
            .sync()
            .await
            .unwrap_err();
        let AnkiError::SyncError {
            source: SyncError { info: _, kind },
        } = err
        else {
            panic!()
        };
        assert_eq!(kind, SyncErrorKind::DatabaseCheckRequired);

        // the server should have rolled back
        let mut col2 = ctx.col2();
        let out = ctx.normal_sync(&mut col2).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);

        // and the client should be able to sync again without a forced one-way sync
        let err = NormalSyncer::new(&mut col1, ctx.cloned_client())
            .sync()
            .await
            .unwrap_err();
        let AnkiError::SyncError {
            source: SyncError { info: _, kind },
        } = err
        else {
            panic!()
        };
        assert_eq!(kind, SyncErrorKind::DatabaseCheckRequired);

        Ok(())
    })
    .await
}

/// Old AnkiMobile versions sent grave ids as strings
#[tokio::test]
async fn string_grave_ids_are_handled() -> Result<()> {
    with_active_server(|client| async move {
        let req = json!({
            "minUsn": 0,
            "lnewer": false,
            "graves": {
                "cards": vec!["1"],
                "decks": vec!["2", "3"],
                "notes": vec!["4"],
            }
        });
        let req = serde_json::to_vec(&req)
            .unwrap()
            .try_into_sync_request()
            .unwrap();
        // should not return err 400
        client.start(req.into_output_type()).await.unwrap();
        client.abort(EmptyInput::request()).await?;
        Ok(())
    })
    .await?;
    // a missing value should be handled
    with_active_server(|client| async move {
        let req = json!({
            "minUsn": 0,
            "lnewer": false,
        });
        let req = serde_json::to_vec(&req)
            .unwrap()
            .try_into_sync_request()
            .unwrap();
        client.start(req.into_output_type()).await.unwrap();
        client.abort(EmptyInput::request()).await?;
        Ok(())
    })
    .await
}

#[tokio::test]
async fn invalid_uploads_should_be_handled() -> Result<()> {
    with_active_server(|client| async move {
        let ctx = SyncTestContext::new(client);
        let res = ctx
            .client
            .upload(b"fake data".to_vec().try_into_sync_request()?)
            .await?;
        assert_eq!(
            res.upload_response(),
            UploadResponse::Err(CORRUPT_MESSAGE.into())
        );
        Ok(())
    })
    .await
}

#[tokio::test]
async fn meta_redirect_is_handled() -> Result<()> {
    with_active_server(|client| async move {
        let mock_server = MockServer::start().await;
        Mock::given(method("POST"))
            .and(path("/sync/meta"))
            .respond_with(
                ResponseTemplate::new(308).insert_header("location", client.endpoint.as_str()),
            )
            .mount(&mock_server)
            .await;
        // starting from in-sync state
        let mut ctx = SyncTestContext::new(client);
        upload_download(&ctx).await?;
        // add another note to trigger a normal sync
        let mut col1 = ctx.col1();
        col1_setup(&mut col1);
        // switch to bad endpoint
        let orig_url = ctx.client.endpoint.to_string();
        ctx.client.endpoint = Url::try_from(mock_server.uri().as_str()).unwrap();
        // sync should succeed
        let out = ctx.normal_sync(&mut col1).await;
        // client should have received new endpoint
        assert_eq!(out.new_endpoint, Some(orig_url));
        // client should not have tried the old endpoint more than once
        assert_eq!(mock_server.received_requests().await.unwrap().len(), 1);
        Ok(())
    })
    .await
}

pub(in crate::sync) struct SyncTestContext {
    pub folder: TempDir,
    pub client: HttpSyncClient,
}

impl SyncTestContext {
    pub fn new(client: HttpSyncClient) -> Self {
        Self {
            folder: tempdir().expect("create temp dir"),
            client,
        }
    }

    pub fn col1(&self) -> Collection {
        let base = self.folder.path();
        CollectionBuilder::new(base.join("col1.anki2"))
            .with_desktop_media_paths()
            .build()
            .unwrap()
    }

    pub fn col2(&self) -> Collection {
        let base = self.folder.path();
        CollectionBuilder::new(base.join("col2.anki2"))
            .with_desktop_media_paths()
            .build()
            .unwrap()
    }

    async fn normal_sync(&self, col: &mut Collection) -> SyncOutput {
        NormalSyncer::new(col, self.cloned_client())
            .sync()
            .await
            .unwrap()
    }

    async fn full_upload(&self, col: Collection) {
        col.full_upload_with_server(self.cloned_client())
            .await
            .unwrap()
    }

    async fn full_download(&self, col: Collection) {
        col.full_download_with_server(self.cloned_client())
            .await
            .unwrap()
    }

    fn cloned_client(&self) -> HttpSyncClient {
        self.client.clone()
    }
}

// Setup + full syncs
/////////////////////

fn col1_setup(col: &mut Collection) {
    let nt = col.get_notetype_by_name("Basic").unwrap().unwrap();
    let mut note = nt.new_note();
    note.set_field(0, "1").unwrap();
    col.add_note(&mut note, DeckId(1)).unwrap();
}

async fn upload_download(ctx: &SyncTestContext) -> Result<()> {
    let mut col1 = ctx.col1();
    col1_setup(&mut col1);

    let out = ctx.normal_sync(&mut col1).await;
    assert!(matches!(
        out.required,
        SyncActionRequired::FullSyncRequired { .. }
    ));

    ctx.full_upload(col1).await;

    // another collection
    let mut col2 = ctx.col2();

    // won't allow ankiweb clobber
    let out = ctx.normal_sync(&mut col2).await;
    assert_eq!(
        out.required,
        SyncActionRequired::FullSyncRequired {
            upload_ok: false,
            download_ok: true,
        }
    );

    // fetch so we're in sync
    ctx.full_download(col2).await;

    Ok(())
}

#[tokio::test]
async fn rwkv_reset_history_survives_normal_sync_between_clients() -> Result<()> {
    use anki_proto::scheduler::card_answer::Rating;
    use anki_proto::scheduler::GradeNowRequest;
    use anki_proto::scheduler::ScheduleCardsAsNewRequest;

    use crate::services::SchedulerService;

    with_active_server(|client| async move {
        let ctx = SyncTestContext::new(client);
        let mut source = ctx.col1();
        col1_setup(&mut source);
        let card_id = source.get_first_card().id;
        let grade = || GradeNowRequest {
            card_ids: vec![card_id.0],
            rating: Rating::Easy as i32,
            card_options: vec![],
        };
        source.grade_now(grade())?;
        let old_history = source.rwkv_historical_review_inputs(Default::default())?;
        assert_eq!(old_history.reviews.len(), 1);
        let old_review_id = old_history.reviews[0].review_id;

        let initial_sync = ctx.normal_sync(&mut source).await;
        assert!(matches!(
            initial_sync.required,
            SyncActionRequired::FullSyncRequired { .. }
        ));
        ctx.full_upload(source).await;
        ctx.full_download(ctx.col2()).await;

        let mut source = ctx.col1();
        let mut other = ctx.col2();
        let _ = SchedulerService::schedule_cards_as_new(
            &mut source,
            ScheduleCardsAsNewRequest {
                card_ids: vec![card_id.0],
                log: false,
                ..Default::default()
            },
        )?;
        assert!(source
            .rwkv_historical_review_inputs(Default::default())?
            .reviews
            .is_empty());
        source.grade_now(grade())?;
        let fresh = source.rwkv_historical_review_inputs(Default::default())?;
        assert_eq!(fresh.reviews.len(), 1);
        assert!(fresh.reviews[0].review_id > old_review_id);

        ctx.normal_sync(&mut source).await;
        ctx.normal_sync(&mut other).await;
        assert_eq!(
            other
                .rwkv_historical_review_inputs(Default::default())?
                .reviews,
            fresh.reviews
        );
        Ok(())
    })
    .await
}

// Regular syncs
/////////////////////

async fn regular_sync(ctx: &SyncTestContext) -> Result<()> {
    // add a deck
    let mut col1 = ctx.col1();
    let mut col2 = ctx.col2();

    let mut deck = col1.get_or_create_normal_deck("new deck")?;

    // give it a new option group
    let mut dconf = DeckConfig {
        name: "new dconf".into(),
        ..Default::default()
    };
    dconf.inner.review_fuzz_base = None;
    dconf.inner.review_fuzz_factor_short = None;
    dconf.inner.review_fuzz_factor_mid = None;
    dconf.inner.review_fuzz_factor_long = None;
    dconf.inner.review_fuzz_enabled = None;
    col1.add_or_update_deck_config(&mut dconf)?;
    if let DeckKind::Normal(deck) = &mut deck.kind {
        deck.config_id = dconf.id.0;
    }
    col1.add_or_update_deck(&mut deck)?;

    // and a new notetype
    let mut nt = all_stock_notetypes(&col1.tr).remove(0);
    nt.name = "new".into();
    col1.add_notetype(&mut nt, false)?;

    // add another note+card+tag
    let mut note = nt.new_note();
    note.set_field(0, "2")?;
    note.tags.push("tag".into());
    col1.add_note(&mut note, deck.id)?;

    // mock revlog entry
    col1.storage.add_revlog_entry(
        &RevlogEntry {
            id: RevlogId(123),
            cid: CardId(456),
            usn: Usn(-1),
            interval: 10,
            ..Default::default()
        },
        true,
    )?;

    // config + creation
    col1.set_config("test", &"test1")?;
    // bumping this will affect 'last studied at' on decks at the moment
    // col1.storage.set_creation_stamp(TimestampSecs(12345))?;

    // and sync our changes
    let remote_meta = ctx
        .client
        .meta(MetaRequest::request())
        .await
        .unwrap()
        .json()
        .unwrap();
    let out = col1.sync_meta()?.compared_to_remote(remote_meta, None);
    assert_eq!(out.required, SyncActionRequired::NormalSyncRequired);

    let out = ctx.normal_sync(&mut col1).await;
    assert_eq!(out.required, SyncActionRequired::NoChanges);
    assert!(!out.remote_collection_changed);
    assert!(out.remote_review_ids.is_empty());
    assert!(!out.remote_non_review_collection_changed);

    // sync the other collection
    let out = ctx.normal_sync(&mut col2).await;
    assert_eq!(out.required, SyncActionRequired::NoChanges);
    assert!(out.remote_collection_changed);
    assert_eq!(out.remote_review_ids, vec![RevlogId(123)]);
    assert!(out.remote_non_review_collection_changed);

    let ntid = nt.id;
    let deckid = deck.id;
    let dconfid = dconf.id;
    let noteid = note.id;
    let cardid = col1.search_cards(note.id, SortMode::NoOrder)?[0];
    let revlogid = RevlogId(123);

    let compare_sides = |col1: &mut Collection, col2: &mut Collection| -> Result<()> {
        assert_eq!(
            col1.get_notetype(ntid)?.unwrap(),
            col2.get_notetype(ntid)?.unwrap()
        );
        assert_eq!(
            col1.get_deck(deckid)?.unwrap(),
            col2.get_deck(deckid)?.unwrap()
        );
        assert_eq!(
            col1.get_deck_config(dconfid, false)?.unwrap(),
            col2.get_deck_config(dconfid, false)?.unwrap()
        );
        assert_eq!(
            col1.storage.get_note(noteid)?.unwrap(),
            col2.storage.get_note(noteid)?.unwrap()
        );
        assert_eq!(
            col1.storage.get_card(cardid)?.unwrap(),
            col2.storage.get_card(cardid)?.unwrap()
        );
        assert_eq!(
            col1.storage.get_revlog_entry(revlogid)?,
            col2.storage.get_revlog_entry(revlogid)?,
        );
        assert_eq!(
            col1.storage.get_all_config()?,
            col2.storage.get_all_config()?
        );
        assert_eq!(
            col1.storage.creation_stamp()?,
            col2.storage.creation_stamp()?
        );

        // server doesn't send tag usns, so we can only compare tags, not usns,
        // as the usns may not match
        assert_eq!(
            col1.storage
                .all_tags()?
                .into_iter()
                .map(|t| t.name)
                .collect::<Vec<_>>(),
            col2.storage
                .all_tags()?
                .into_iter()
                .map(|t| t.name)
                .collect::<Vec<_>>()
        );
        std::thread::sleep(std::time::Duration::from_millis(1));
        Ok(())
    };

    // make sure everything has been transferred across
    compare_sides(&mut col1, &mut col2)?;

    // A review-only download is reported separately from other collection
    // changes, although answering also updates the reviewed card.
    col1.storage.add_revlog_entry(
        &RevlogEntry {
            id: RevlogId(124),
            cid: cardid,
            usn: Usn(-1),
            interval: 11,
            ..Default::default()
        },
        true,
    )?;
    col1.get_and_update_card(cardid, |card| {
        card.interval = 11;
        card.due += 11;
        Ok(())
    })?;
    ctx.normal_sync(&mut col1).await;
    let out = ctx.normal_sync(&mut col2).await;
    assert!(out.remote_collection_changed);
    assert_eq!(out.remote_review_ids, vec![RevlogId(124)]);
    assert!(!out.remote_non_review_collection_changed);
    compare_sides(&mut col1, &mut col2)?;

    // Moving a card to another deck changes the identity of its reviews.
    col1.get_and_update_card(cardid, |card| {
        card.deck_id = DeckId(1);
        Ok(())
    })?;
    ctx.normal_sync(&mut col1).await;
    let out = ctx.normal_sync(&mut col2).await;
    assert!(out.remote_collection_changed);
    assert!(out.remote_review_ids.is_empty());
    assert!(out.remote_non_review_collection_changed);

    // make some modifications
    let mut note = col2.storage.get_note(note.id)?.unwrap();
    note.set_field(1, "new")?;
    note.tags.push("tag2".into());
    col2.update_note(&mut note)?;

    col2.get_and_update_card(cardid, |card| {
        card.queue = CardQueue::Review;
        Ok(())
    })?;

    let mut deck = col2.storage.get_deck(deck.id)?.unwrap();
    deck.name = NativeDeckName::from_native_str("newer");
    col2.add_or_update_deck(&mut deck)?;

    let mut nt = col2.storage.get_notetype(nt.id)?.unwrap();
    nt.name = "newer".into();
    col2.update_notetype(&mut nt, false)?;

    // sync the changes back
    let out = ctx.normal_sync(&mut col2).await;
    assert_eq!(out.required, SyncActionRequired::NoChanges);
    let out = ctx.normal_sync(&mut col1).await;
    assert_eq!(out.required, SyncActionRequired::NoChanges);

    // should still match
    compare_sides(&mut col1, &mut col2)?;

    // deletions should sync too
    for table in &["cards", "notes", "decks"] {
        assert_eq!(
            col1.storage
                .db_scalar::<u8>(&format!("select count() from {table}"))?,
            2
        );
    }

    // fixme: inconsistent usn arg
    std::thread::sleep(std::time::Duration::from_millis(1));
    col1.remove_cards_and_orphaned_notes(&[cardid])?;
    let usn = col1.usn()?;
    col1.remove_note_only_undoable(noteid, usn)?;
    col1.remove_decks_and_child_decks(&[deckid])?;

    let out = ctx.normal_sync(&mut col1).await;
    assert_eq!(out.required, SyncActionRequired::NoChanges);
    let out = ctx.normal_sync(&mut col2).await;
    assert_eq!(out.required, SyncActionRequired::NoChanges);

    for table in &["cards", "notes", "decks"] {
        assert_eq!(
            col2.storage
                .db_scalar::<u8>(&format!("select count() from {table}"))?,
            1
        );
    }

    // removing things like a notetype forces a full sync
    std::thread::sleep(std::time::Duration::from_millis(1));
    col2.remove_notetype(ntid)?;
    let out = ctx.normal_sync(&mut col2).await;
    assert!(matches!(
        out.required,
        SyncActionRequired::FullSyncRequired { .. }
    ));
    Ok(())
}

#[tokio::test]
async fn synced_dynamic_retention_rules_drive_the_other_client() -> Result<()> {
    use anki_proto::scheduler::card_answer::Rating;
    use anki_proto::scheduler::GradeNowRequest;
    use anki_proto::scheduler::RwkvReviewInputRowsForCardsRequest;

    use crate::config::BoolKey;

    with_active_server(|client| async move {
        let ctx = SyncTestContext::new(client);
        let mut source = ctx.col1();
        col1_setup(&mut source);
        source.set_config_bool(BoolKey::Fsrs, true, false)?;
        let card_id = source.get_first_card().id;
        let grade = || GradeNowRequest {
            card_ids: vec![card_id.0],
            rating: Rating::Easy as i32,
            card_options: vec![],
        };
        source.grade_now(grade())?;
        assert!(matches!(
            ctx.normal_sync(&mut source).await.required,
            SyncActionRequired::FullSyncRequired { .. }
        ));
        ctx.full_upload(source).await;
        ctx.full_download(ctx.col2()).await;

        let mut source = ctx.col1();
        let mut other = ctx.col2();
        let rules = serde_json::json!({
            "version": 1,
            "rules": [{"query": "deck:Default", "desired_retention": 0.75}],
            "grade_rules": [{"grade": "easy", "mode": "absolute", "value": 0.85}]
        });
        source.set_config_json(
            crate::scheduler::addon_desired_retention::CONFIG_KEY,
            &rules,
            false,
        )?;
        ctx.normal_sync(&mut source).await;
        ctx.normal_sync(&mut other).await;
        assert_eq!(
            other.get_config_optional::<serde_json::Value, _>(
                crate::scheduler::addon_desired_retention::CONFIG_KEY
            ),
            Some(rules)
        );
        let rows = other.rwkv_review_input_rows_for_cards(RwkvReviewInputRowsForCardsRequest {
            card_ids: vec![card_id.0],
            include_disabled_decks: true,
            ..Default::default()
        })?;
        assert_eq!(rows.rows[0].target_retention, 0.75);
        other.grade_now(grade())?;
        assert_eq!(other.get_first_card().desired_retention, Some(0.85));
        Ok(())
    })
    .await
}
