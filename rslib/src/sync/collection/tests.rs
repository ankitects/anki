// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#![cfg(test)]

use std::collections::HashMap;
use std::future::Future;
use std::sync::LazyLock;

use axum::http::StatusCode;
use fsrs::DEFAULT_PARAMETERS;
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
use crate::card::FsrsMemoryState;
use crate::collection::CollectionBuilder;
use crate::config::BoolKey;
use crate::deckconfig::DeckConfig;
use crate::decks::DeckKind;
use crate::decks::NativeDeckName;
use crate::error::SyncError;
use crate::error::SyncErrorKind;
use crate::log::set_global_logger;
use crate::notetype::all_stock_notetypes;
use crate::ops::Op;
use crate::prelude::*;
use crate::revlog::RevlogEntry;
use crate::revlog::RevlogId;
use crate::revlog::RevlogReviewKind;
use crate::scheduler::fsrs::memory_state::get_decay_from_params;
use crate::scheduler::fsrs::memory_state::UpdateMemoryStateEntry;
use crate::scheduler::fsrs::memory_state::UpdateMemoryStateRequest;
use crate::scheduler::fsrs::params::ignore_revlogs_before_ms_from_config;
use crate::search::SearchNode;
use crate::search::SortMode;
use crate::sync::collection::graves::ApplyGravesRequest;
use crate::sync::collection::meta::MetaRequest;
use crate::sync::collection::normal::NormalSyncer;
use crate::sync::collection::normal::SyncActionRequired;
use crate::sync::collection::normal::SyncOutput;
use crate::sync::collection::protocol::EmptyInput;
use crate::sync::collection::protocol::SyncProtocol;
use crate::sync::collection::start::StartRequest;
use crate::sync::collection::upload::UploadResponse;
use crate::sync::collection::upload::CORRUPT_MESSAGE;
use crate::sync::http_client::HttpSyncClient;
use crate::sync::http_server::default_ip_header;
use crate::sync::http_server::SimpleServer;
use crate::sync::http_server::SyncServerConfig;
use crate::sync::login::HostKeyRequest;
use crate::sync::login::SyncAuth;
use crate::sync::request::IntoSyncRequest;

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
async fn fsrs_stale_card_state_is_reconciled_during_sync() -> Result<()> {
    with_active_server(|client| async move {
        let ctx = SyncTestContext::new(client);

        let mut col1 = ctx.col1();
        col1.set_config_bool(BoolKey::Fsrs, true, false)?;
        let nt = col1.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        note.set_field(0, "fsrs")?;
        col1.add_note(&mut note, DeckId(1))?;
        let card_id = col1.answer_easy().card_id;

        let out = ctx.normal_sync(&mut col1).await;
        assert!(matches!(
            out.required,
            SyncActionRequired::FullSyncRequired { .. }
        ));
        ctx.full_upload(col1).await;

        let mut col2 = ctx.col2();
        let out = ctx.normal_sync(&mut col2).await;
        assert_eq!(
            out.required,
            SyncActionRequired::FullSyncRequired {
                upload_ok: false,
                download_ok: true,
            }
        );
        ctx.full_download(col2).await;

        let mut col1 = ctx.col1();
        let mut col2 = ctx.col2();

        std::thread::sleep(std::time::Duration::from_millis(1100));
        col1.storage
            .db
            .execute("update cards set due = 0 where id = ?", [card_id])?;
        col1.clear_study_queues();
        col1.answer_good();

        std::thread::sleep(std::time::Duration::from_millis(1));

        let config = col2.get_deck_config(DeckConfigId(1), false)?.unwrap();
        let ignore_before = ignore_revlogs_before_ms_from_config(&config)?;
        let request = UpdateMemoryStateRequest {
            params: config.fsrs_params().clone(),
            preset_desired_retention: config.inner.desired_retention,
            historical_retention: config.inner.historical_retention,
            max_interval: config.inner.maximum_review_interval,
            reschedule: false,
            deck_desired_retention: HashMap::new(),
        };
        col2.transact(Op::UpdateDeckConfig, |col| {
            col.update_memory_state(vec![UpdateMemoryStateEntry {
                req: Some(request),
                search: SearchNode::CardIds(card_id.to_string()),
                ignore_before,
            }])?;
            Ok(())
        })?;

        let stale_card = col2.storage.get_card(card_id)?.unwrap();
        let reviewed_card = col1.storage.get_card(card_id)?.unwrap();
        assert!(
            stale_card.memory_state != reviewed_card.memory_state
                || stale_card.last_review_time != reviewed_card.last_review_time
                || stale_card.interval != reviewed_card.interval
                || stale_card.due != reviewed_card.due
        );

        let out = ctx.normal_sync(&mut col1).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);
        let out = ctx.normal_sync(&mut col2).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);

        let reconciled_card = col2.storage.get_card(card_id)?.unwrap();
        let reviewed_card = col1.storage.get_card(card_id)?.unwrap();
        assert_eq!(reconciled_card.memory_state, reviewed_card.memory_state);
        assert_eq!(
            reconciled_card.last_review_time,
            reviewed_card.last_review_time
        );
        assert_eq!(reconciled_card.interval, reviewed_card.interval);
        assert_eq!(reconciled_card.due, reviewed_card.due);

        let out = ctx.normal_sync(&mut col1).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);
        let synced_card = col1.storage.get_card(card_id)?.unwrap();
        assert_eq!(synced_card.memory_state, reconciled_card.memory_state);
        assert_eq!(
            synced_card.last_review_time,
            reconciled_card.last_review_time
        );
        assert_eq!(synced_card.interval, reconciled_card.interval);
        assert_eq!(synced_card.due, reconciled_card.due);

        Ok(())
    })
    .await
}

#[tokio::test]
async fn fsrs_metadata_conflict_is_reconciled_without_rescheduling() -> Result<()> {
    with_active_server(|client| async move {
        let ctx = SyncTestContext::new(client);

        let mut col1 = ctx.col1();
        col1.set_config_bool(BoolKey::Fsrs, true, false)?;
        let nt = col1.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        note.set_field(0, "fsrs-metadata")?;
        col1.add_note(&mut note, DeckId(1))?;
        let card_id = col1.answer_easy().card_id;

        sync_fsrs_collections(&ctx, col1).await?;

        let mut col1 = ctx.col1();
        let mut col2 = ctx.col2();

        // Recompute FSRS metadata on col1 with a different desired retention,
        // but without rescheduling. This gives us a conflict where only
        // FSRS-derived card fields should be reconciled.
        let mut deck = (*col1.get_deck(DeckId(1))?.unwrap()).clone();
        deck.normal_mut().unwrap().desired_retention = Some(0.83);
        col1.add_or_update_deck(&mut deck)?;

        let config = col1.get_deck_config(DeckConfigId(1), false)?.unwrap();
        let ignore_before = ignore_revlogs_before_ms_from_config(&config)?;
        let request = UpdateMemoryStateRequest {
            params: config.fsrs_params().clone(),
            preset_desired_retention: config.inner.desired_retention,
            historical_retention: config.inner.historical_retention,
            max_interval: config.inner.maximum_review_interval,
            reschedule: false,
            deck_desired_retention: HashMap::from([(DeckId(1), 0.83)]),
        };
        col1.transact(Op::UpdateDeckConfig, |col| {
            col.update_memory_state(vec![UpdateMemoryStateEntry {
                req: Some(request),
                search: SearchNode::CardIds(card_id.to_string()),
                ignore_before,
            }])?;
            Ok(())
        })?;

        // Simulate the other device holding stale FSRS data while the
        // scheduling fields still match the current review history.
        let stale_card = col2.get_and_update_card(card_id, |card| {
            card.memory_state = Some(FsrsMemoryState {
                stability: card.memory_state.unwrap().stability + 1.0,
                difficulty: card.memory_state.unwrap().difficulty,
            });
            card.desired_retention = Some(0.97);
            card.decay = Some(0.12);
            Ok(())
        })?;
        let updated_card = col1.storage.get_card(card_id)?.unwrap();
        assert_eq!(stale_card.interval, updated_card.interval);
        assert_eq!(stale_card.due, updated_card.due);
        assert_ne!(stale_card.memory_state, updated_card.memory_state);
        assert_ne!(stale_card.desired_retention, updated_card.desired_retention);

        let out = ctx.normal_sync(&mut col1).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);
        let out = ctx.normal_sync(&mut col2).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);

        let reconciled_card = col2.storage.get_card(card_id)?.unwrap();
        let synced_card = col1.storage.get_card(card_id)?.unwrap();
        assert_eq!(reconciled_card.memory_state, synced_card.memory_state);
        assert_eq!(
            reconciled_card.desired_retention,
            synced_card.desired_retention
        );
        assert_eq!(reconciled_card.decay, synced_card.decay);
        assert_eq!(
            reconciled_card.last_review_time,
            synced_card.last_review_time
        );
        // Because only FSRS metadata diverged, reconciliation should not
        // rewrite the schedule.
        assert_eq!(reconciled_card.interval, stale_card.interval);
        assert_eq!(reconciled_card.due, stale_card.due);

        Ok(())
    })
    .await
}

#[tokio::test]
async fn fsrs_itemless_card_state_is_cleared_during_sync() -> Result<()> {
    with_active_server(|client| async move {
        let ctx = SyncTestContext::new(client);

        let mut col1 = ctx.col1();
        col1.set_config_bool(BoolKey::Fsrs, true, false)?;
        let nt = col1.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        note.set_field(0, "fsrs-itemless")?;
        col1.add_note(&mut note, DeckId(1))?;
        let card_id = col1.search_cards(note.id, SortMode::NoOrder)?[0];

        sync_fsrs_collections(&ctx, col1).await?;

        let mut col1 = ctx.col1();
        let mut col2 = ctx.col2();

        // A manual-only revlog makes the card show up in the merged history
        // while still producing no FSRS item, which exercises the itemless
        // reconciliation path.
        col1.get_and_update_card(card_id, |card| {
            card.due += 7;
            Ok(())
        })?;
        col1.storage.add_revlog_entry(
            &RevlogEntry {
                id: RevlogId::new(),
                cid: card_id,
                usn: col1.usn()?,
                button_chosen: 0,
                interval: 0,
                last_interval: 0,
                ease_factor: 2500,
                taken_millis: 0,
                review_kind: RevlogReviewKind::Manual,
            },
            true,
        )?;
        col2.get_and_update_card(card_id, |card| {
            card.memory_state = Some(FsrsMemoryState {
                stability: 7.0,
                difficulty: 4.0,
            });
            card.desired_retention = Some(0.72);
            card.decay = Some(0.34);
            card.last_review_time = Some(TimestampSecs(123));
            Ok(())
        })?;

        // Confirm the local side really carries stale FSRS state before sync.
        let stale_card = col2.storage.get_card(card_id)?.unwrap();
        assert!(stale_card.memory_state.is_some());
        assert!(stale_card.last_review_time.is_some());

        let out = ctx.normal_sync(&mut col1).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);
        let out = ctx.normal_sync(&mut col2).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);

        let reconciled_card = col2.storage.get_card(card_id)?.unwrap();
        let config = col2.get_deck_config(DeckConfigId(1), false)?.unwrap();
        assert_eq!(reconciled_card.memory_state, None);
        assert_eq!(reconciled_card.last_review_time, None);
        assert_eq!(
            reconciled_card.desired_retention,
            Some(config.inner.desired_retention)
        );
        assert!(
            (reconciled_card.decay.unwrap() - get_decay_from_params(config.fsrs_params())).abs()
                < 0.001
        );
        // Itemless reconciliation clears FSRS-derived fields, but it does not
        // reschedule the card.
        assert_eq!(reconciled_card.due, stale_card.due);
        assert_eq!(reconciled_card.interval, stale_card.interval);

        Ok(())
    })
    .await
}

#[tokio::test]
async fn fsrs_conflicts_are_reconciled_per_preset() -> Result<()> {
    with_active_server(|client| async move {
        let ctx = SyncTestContext::new(client);

        let mut col1 = ctx.col1();
        col1.set_config_bool(BoolKey::Fsrs, true, false)?;

        let mut config1 = col1.get_deck_config(DeckConfigId(1), false)?.unwrap();
        config1.inner.desired_retention = 0.81;
        config1.inner.fsrs_params_6 = DEFAULT_PARAMETERS.into();
        col1.add_or_update_deck_config(&mut config1)?;

        let mut config2 = DeckConfig {
            name: "fsrs second config".into(),
            ..Default::default()
        };
        config2.inner.desired_retention = 0.93;
        config2.inner.fsrs_params_6 = DEFAULT_PARAMETERS.into();
        config2.inner.fsrs_params_6[20] = 0.21;
        col1.add_or_update_deck_config(&mut config2)?;

        let mut deck2 = col1.get_or_create_normal_deck("fsrs second deck")?;
        if let DeckKind::Normal(deck) = &mut deck2.kind {
            deck.config_id = config2.id.0;
        }
        col1.add_or_update_deck(&mut deck2)?;

        let nt = col1.get_notetype_by_name("Basic")?.unwrap();
        let mut note1 = nt.new_note();
        note1.set_field(0, "fsrs-preset-1")?;
        col1.add_note(&mut note1, DeckId(1))?;
        col1.set_current_deck(DeckId(1))?;
        let card1 = col1.answer_easy().card_id;

        let mut note2 = nt.new_note();
        note2.set_field(0, "fsrs-preset-2")?;
        col1.add_note(&mut note2, deck2.id)?;
        col1.set_current_deck(deck2.id)?;
        let card2 = col1.answer_easy().card_id;

        sync_fsrs_collections(&ctx, col1).await?;

        let mut col1 = ctx.col1();
        let mut col2 = ctx.col2();

        // Recompute both cards on the source side using different presets so
        // reconciliation has to group by config instead of treating all cards
        // as if they shared one FSRS setup.
        let config1 = col1.get_deck_config(DeckConfigId(1), false)?.unwrap();
        let config2 = col1.get_deck_config(config2.id, false)?.unwrap();
        let ignore_before1 = ignore_revlogs_before_ms_from_config(&config1)?;
        let ignore_before2 = ignore_revlogs_before_ms_from_config(&config2)?;
        col1.transact(Op::UpdateDeckConfig, |col| {
            col.update_memory_state(vec![
                UpdateMemoryStateEntry {
                    req: Some(UpdateMemoryStateRequest {
                        params: config1.fsrs_params().clone(),
                        preset_desired_retention: config1.inner.desired_retention,
                        historical_retention: config1.inner.historical_retention,
                        max_interval: config1.inner.maximum_review_interval,
                        reschedule: false,
                        deck_desired_retention: HashMap::new(),
                    }),
                    search: SearchNode::CardIds(card1.to_string()),
                    ignore_before: ignore_before1,
                },
                UpdateMemoryStateEntry {
                    req: Some(UpdateMemoryStateRequest {
                        params: config2.fsrs_params().clone(),
                        preset_desired_retention: config2.inner.desired_retention,
                        historical_retention: config2.inner.historical_retention,
                        max_interval: config2.inner.maximum_review_interval,
                        reschedule: false,
                        deck_desired_retention: HashMap::new(),
                    }),
                    search: SearchNode::CardIds(card2.to_string()),
                    ignore_before: ignore_before2,
                },
            ])?;
            Ok(())
        })?;
        let out = ctx.normal_sync(&mut col1).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);

        // Make the destination side newer so the stale cards stay local and
        // must be repaired by the post-sync reconciliation pass.
        col2.get_and_update_card(card1, |card| {
            card.memory_state = Some(FsrsMemoryState {
                stability: card.memory_state.unwrap().stability + 1.0,
                difficulty: card.memory_state.unwrap().difficulty,
            });
            card.desired_retention = Some(0.99);
            card.decay = Some(0.11);
            Ok(())
        })?;
        col2.get_and_update_card(card2, |card| {
            card.memory_state = Some(FsrsMemoryState {
                stability: card.memory_state.unwrap().stability + 2.0,
                difficulty: card.memory_state.unwrap().difficulty,
            });
            card.desired_retention = Some(0.77);
            card.decay = Some(0.31);
            Ok(())
        })?;

        let out = ctx.normal_sync(&mut col2).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);

        let synced_card1 = col1.storage.get_card(card1)?.unwrap();
        let synced_card2 = col1.storage.get_card(card2)?.unwrap();
        let reconciled_card1 = col2.storage.get_card(card1)?.unwrap();
        let reconciled_card2 = col2.storage.get_card(card2)?.unwrap();
        assert_eq!(reconciled_card1.memory_state, synced_card1.memory_state);
        assert_eq!(reconciled_card2.memory_state, synced_card2.memory_state);
        assert_eq!(
            reconciled_card1.desired_retention,
            Some(config1.inner.desired_retention)
        );
        assert_eq!(
            reconciled_card2.desired_retention,
            Some(config2.inner.desired_retention)
        );
        assert!(
            (reconciled_card1.decay.unwrap() - get_decay_from_params(config1.fsrs_params())).abs()
                < 0.001
        );
        assert!(
            (reconciled_card2.decay.unwrap() - get_decay_from_params(config2.fsrs_params())).abs()
                < 0.001
        );
        assert_ne!(
            reconciled_card1.desired_retention,
            reconciled_card2.desired_retention
        );
        assert_ne!(reconciled_card1.decay, reconciled_card2.decay);

        Ok(())
    })
    .await
}

#[tokio::test]
async fn fsrs_reconciliation_uses_original_deck_for_filtered_cards() -> Result<()> {
    with_active_server(|client| async move {
        let ctx = SyncTestContext::new(client);

        let mut col1 = ctx.col1();
        col1.set_config_bool(BoolKey::Fsrs, true, false)?;

        let mut home_deck = col1.get_or_create_normal_deck("fsrs home deck")?;
        home_deck.normal_mut().unwrap().desired_retention = Some(0.84);
        col1.add_or_update_deck(&mut home_deck)?;

        let nt = col1.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        note.set_field(0, "fsrs-filtered-home")?;
        col1.add_note(&mut note, home_deck.id)?;
        col1.set_current_deck(home_deck.id)?;
        let card_id = col1.answer_easy().card_id;

        sync_fsrs_collections(&ctx, col1).await?;

        let mut col1 = ctx.col1();
        let mut col2 = ctx.col2();

        // Refresh the source card so the server has a pending card change,
        // then create a newer local filtered-deck variant on the other side.
        let config = col1.get_deck_config(DeckConfigId(1), false)?.unwrap();
        let ignore_before = ignore_revlogs_before_ms_from_config(&config)?;
        col1.transact(Op::UpdateDeckConfig, |col| {
            col.update_memory_state(vec![UpdateMemoryStateEntry {
                req: Some(UpdateMemoryStateRequest {
                    params: config.fsrs_params().clone(),
                    preset_desired_retention: config.inner.desired_retention,
                    historical_retention: config.inner.historical_retention,
                    max_interval: config.inner.maximum_review_interval,
                    reschedule: false,
                    deck_desired_retention: HashMap::from([(home_deck.id, 0.84)]),
                }),
                search: SearchNode::CardIds(card_id.to_string()),
                ignore_before,
            }])?;
            Ok(())
        })?;
        let out = ctx.normal_sync(&mut col1).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);

        let mut filtered_deck = Deck::new_filtered();
        filtered_deck.name = NativeDeckName::from_native_str("fsrs filtered");
        {
            let filtered = filtered_deck.filtered_mut()?;
            filtered.reschedule = false;
            filtered.search_terms[0].search = format!("cid:{}", card_id.0);
        }
        col2.add_or_update_deck(&mut filtered_deck)?;
        assert_eq!(col2.rebuild_filtered_deck(filtered_deck.id)?.output, 1);
        let stale_card = col2.get_and_update_card(card_id, |card| {
            card.memory_state = Some(FsrsMemoryState {
                stability: card.memory_state.unwrap().stability + 1.5,
                difficulty: card.memory_state.unwrap().difficulty,
            });
            card.desired_retention = Some(0.99);
            card.decay = Some(0.12);
            Ok(())
        })?;
        assert_eq!(stale_card.deck_id, filtered_deck.id);
        assert_eq!(stale_card.original_deck_id, home_deck.id);

        let out = ctx.normal_sync(&mut col2).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);

        let reconciled_card = col2.storage.get_card(card_id)?.unwrap();
        let synced_card = col1.storage.get_card(card_id)?.unwrap();
        assert_eq!(reconciled_card.deck_id, filtered_deck.id);
        assert_eq!(reconciled_card.original_deck_id, home_deck.id);
        assert_eq!(reconciled_card.memory_state, synced_card.memory_state);
        assert_eq!(reconciled_card.desired_retention, Some(0.84));
        assert_eq!(
            reconciled_card.last_review_time,
            synced_card.last_review_time
        );

        Ok(())
    })
    .await
}

#[tokio::test]
async fn fsrs_reconciliation_respects_deck_overrides_within_one_preset() -> Result<()> {
    with_active_server(|client| async move {
        let ctx = SyncTestContext::new(client);

        let mut col1 = ctx.col1();
        col1.set_config_bool(BoolKey::Fsrs, true, false)?;

        let mut shared_config = col1.get_deck_config(DeckConfigId(1), false)?.unwrap();
        shared_config.inner.desired_retention = 0.89;
        shared_config.inner.fsrs_params_6 = DEFAULT_PARAMETERS.into();
        col1.add_or_update_deck_config(&mut shared_config)?;

        let mut deck1 = col1.get_or_create_normal_deck("fsrs override deck 1")?;
        deck1.normal_mut().unwrap().desired_retention = Some(0.82);
        col1.add_or_update_deck(&mut deck1)?;

        let mut deck2 = col1.get_or_create_normal_deck("fsrs override deck 2")?;
        deck2.normal_mut().unwrap().desired_retention = Some(0.95);
        col1.add_or_update_deck(&mut deck2)?;

        let nt = col1.get_notetype_by_name("Basic")?.unwrap();
        let mut note1 = nt.new_note();
        note1.set_field(0, "fsrs-override-1")?;
        col1.add_note(&mut note1, deck1.id)?;
        col1.set_current_deck(deck1.id)?;
        let card1 = col1.answer_easy().card_id;

        let mut note2 = nt.new_note();
        note2.set_field(0, "fsrs-override-2")?;
        col1.add_note(&mut note2, deck2.id)?;
        col1.set_current_deck(deck2.id)?;
        let card2 = col1.answer_easy().card_id;

        sync_fsrs_collections(&ctx, col1).await?;

        let mut col1 = ctx.col1();
        let mut col2 = ctx.col2();

        let config = col1.get_deck_config(DeckConfigId(1), false)?.unwrap();
        let ignore_before = ignore_revlogs_before_ms_from_config(&config)?;
        // Both cards share the same preset, so reconciliation will process
        // them together and must still apply the deck-level desired retention
        // override for each home deck.
        col1.transact(Op::UpdateDeckConfig, |col| {
            col.update_memory_state(vec![UpdateMemoryStateEntry {
                req: Some(UpdateMemoryStateRequest {
                    params: config.fsrs_params().clone(),
                    preset_desired_retention: config.inner.desired_retention,
                    historical_retention: config.inner.historical_retention,
                    max_interval: config.inner.maximum_review_interval,
                    reschedule: false,
                    deck_desired_retention: HashMap::from([(deck1.id, 0.82), (deck2.id, 0.95)]),
                }),
                search: SearchNode::CardIds(format!("{},{}", card1.0, card2.0)),
                ignore_before,
            }])?;
            Ok(())
        })?;
        let out = ctx.normal_sync(&mut col1).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);

        col2.get_and_update_card(card1, |card| {
            card.memory_state = Some(FsrsMemoryState {
                stability: card.memory_state.unwrap().stability + 1.0,
                difficulty: card.memory_state.unwrap().difficulty,
            });
            card.desired_retention = Some(0.99);
            card.decay = Some(0.11);
            Ok(())
        })?;
        col2.get_and_update_card(card2, |card| {
            card.memory_state = Some(FsrsMemoryState {
                stability: card.memory_state.unwrap().stability + 1.5,
                difficulty: card.memory_state.unwrap().difficulty,
            });
            card.desired_retention = Some(0.77);
            card.decay = Some(0.31);
            Ok(())
        })?;

        let out = ctx.normal_sync(&mut col2).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);

        let reconciled_card1 = col2.storage.get_card(card1)?.unwrap();
        let reconciled_card2 = col2.storage.get_card(card2)?.unwrap();
        let synced_card1 = col1.storage.get_card(card1)?.unwrap();
        let synced_card2 = col1.storage.get_card(card2)?.unwrap();
        assert_eq!(reconciled_card1.memory_state, synced_card1.memory_state);
        assert_eq!(reconciled_card2.memory_state, synced_card2.memory_state);
        assert_eq!(reconciled_card1.desired_retention, Some(0.82));
        assert_eq!(reconciled_card2.desired_retention, Some(0.95));
        assert_ne!(
            reconciled_card1.desired_retention,
            reconciled_card2.desired_retention
        );
        assert!(
            (reconciled_card1.decay.unwrap() - get_decay_from_params(config.fsrs_params())).abs()
                < 0.001
        );
        assert!(
            (reconciled_card2.decay.unwrap() - get_decay_from_params(config.fsrs_params())).abs()
                < 0.001
        );

        Ok(())
    })
    .await
}

#[tokio::test]
async fn fsrs_mixed_schedule_and_metadata_conflicts_reconcile_selectively() -> Result<()> {
    with_active_server(|client| async move {
        let ctx = SyncTestContext::new(client);

        let mut col1 = ctx.col1();
        col1.set_config_bool(BoolKey::Fsrs, true, false)?;

        let mut metadata_deck = col1.get_or_create_normal_deck("fsrs metadata deck")?;
        metadata_deck.normal_mut().unwrap().desired_retention = Some(0.83);
        col1.add_or_update_deck(&mut metadata_deck)?;

        let nt = col1.get_notetype_by_name("Basic")?.unwrap();
        let mut note1 = nt.new_note();
        note1.set_field(0, "fsrs-mixed-schedule")?;
        col1.add_note(&mut note1, DeckId(1))?;
        col1.set_current_deck(DeckId(1))?;
        let card1 = col1.answer_easy().card_id;

        let mut note2 = nt.new_note();
        note2.set_field(0, "fsrs-mixed-metadata")?;
        col1.add_note(&mut note2, metadata_deck.id)?;
        col1.set_current_deck(metadata_deck.id)?;
        let card2 = col1.answer_easy().card_id;

        sync_fsrs_collections(&ctx, col1).await?;

        let mut col1 = ctx.col1();
        let mut col2 = ctx.col2();

        std::thread::sleep(std::time::Duration::from_millis(1100));

        // Card 1 gets a new review on the source side, so its schedule needs
        // to be recomputed from merged history. Card 2 only gets a metadata
        // refresh using the same preset and should keep its existing schedule.
        col1.storage
            .db
            .execute("update cards set due = 0 where id = ?", [card1])?;
        col1.set_current_deck(DeckId(1))?;
        col1.clear_study_queues();
        col1.answer_good();

        let config = col1.get_deck_config(DeckConfigId(1), false)?.unwrap();
        let ignore_before = ignore_revlogs_before_ms_from_config(&config)?;
        col1.transact(Op::UpdateDeckConfig, |col| {
            col.update_memory_state(vec![UpdateMemoryStateEntry {
                req: Some(UpdateMemoryStateRequest {
                    params: config.fsrs_params().clone(),
                    preset_desired_retention: config.inner.desired_retention,
                    historical_retention: config.inner.historical_retention,
                    max_interval: config.inner.maximum_review_interval,
                    reschedule: false,
                    deck_desired_retention: HashMap::from([(metadata_deck.id, 0.83)]),
                }),
                search: SearchNode::CardIds(card2.to_string()),
                ignore_before,
            }])?;
            Ok(())
        })?;

        let stale_card1 = col2.get_and_update_card(card1, |card| {
            card.memory_state = Some(FsrsMemoryState {
                stability: card.memory_state.unwrap().stability + 1.0,
                difficulty: card.memory_state.unwrap().difficulty,
            });
            card.interval += 1;
            card.due += 1;
            card.desired_retention = Some(0.99);
            card.decay = Some(0.11);
            Ok(())
        })?;
        let stale_card2 = col2.get_and_update_card(card2, |card| {
            card.memory_state = Some(FsrsMemoryState {
                stability: card.memory_state.unwrap().stability + 1.5,
                difficulty: card.memory_state.unwrap().difficulty,
            });
            card.desired_retention = Some(0.97);
            card.decay = Some(0.12);
            Ok(())
        })?;

        let reviewed_card = col1.storage.get_card(card1)?.unwrap();
        let refreshed_card = col1.storage.get_card(card2)?.unwrap();
        assert!(
            stale_card1.interval != reviewed_card.interval || stale_card1.due != reviewed_card.due
        );
        assert_eq!(stale_card2.interval, refreshed_card.interval);
        assert_eq!(stale_card2.due, refreshed_card.due);

        let out = ctx.normal_sync(&mut col1).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);
        let out = ctx.normal_sync(&mut col2).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);

        let reconciled_card1 = col2.storage.get_card(card1)?.unwrap();
        let reconciled_card2 = col2.storage.get_card(card2)?.unwrap();
        let synced_card1 = col1.storage.get_card(card1)?.unwrap();
        let synced_card2 = col1.storage.get_card(card2)?.unwrap();
        assert_eq!(reconciled_card1.memory_state, synced_card1.memory_state);
        assert_eq!(
            reconciled_card1.last_review_time,
            synced_card1.last_review_time
        );
        assert_eq!(reconciled_card1.interval, synced_card1.interval);
        assert_eq!(reconciled_card1.due, synced_card1.due);

        assert_eq!(reconciled_card2.memory_state, synced_card2.memory_state);
        assert_eq!(
            reconciled_card2.desired_retention,
            synced_card2.desired_retention
        );
        assert_eq!(
            reconciled_card2.last_review_time,
            synced_card2.last_review_time
        );
        // Card 2 was only marked for metadata reconciliation, so its schedule
        // should remain untouched even though it was processed in the same
        // preset batch as card 1.
        assert_eq!(reconciled_card2.interval, stale_card2.interval);
        assert_eq!(reconciled_card2.due, stale_card2.due);

        Ok(())
    })
    .await
}

#[tokio::test]
async fn fsrs_filtered_card_schedule_conflict_uses_original_deck() -> Result<()> {
    with_active_server(|client| async move {
        let ctx = SyncTestContext::new(client);

        let mut col1 = ctx.col1();
        col1.set_config_bool(BoolKey::Fsrs, true, false)?;

        let mut home_deck = col1.get_or_create_normal_deck("fsrs filtered schedule home")?;
        home_deck.normal_mut().unwrap().desired_retention = Some(0.84);
        col1.add_or_update_deck(&mut home_deck)?;

        let nt = col1.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        note.set_field(0, "fsrs-filtered-schedule")?;
        col1.add_note(&mut note, home_deck.id)?;
        col1.set_current_deck(home_deck.id)?;
        let card_id = col1.answer_easy().card_id;

        sync_fsrs_collections(&ctx, col1).await?;

        let mut col1 = ctx.col1();
        let mut col2 = ctx.col2();

        std::thread::sleep(std::time::Duration::from_millis(1100));

        col1.storage
            .db
            .execute("update cards set due = 0 where id = ?", [card_id])?;
        col1.set_current_deck(home_deck.id)?;
        col1.clear_study_queues();
        col1.answer_good();

        let mut filtered_deck = Deck::new_filtered();
        filtered_deck.name = NativeDeckName::from_native_str("fsrs filtered schedule");
        {
            let filtered = filtered_deck.filtered_mut()?;
            filtered.reschedule = false;
            filtered.search_terms[0].search = format!("cid:{}", card_id.0);
        }
        col2.add_or_update_deck(&mut filtered_deck)?;
        assert_eq!(col2.rebuild_filtered_deck(filtered_deck.id)?.output, 1);
        let stale_card = col2.get_and_update_card(card_id, |card| {
            card.memory_state = Some(FsrsMemoryState {
                stability: card.memory_state.unwrap().stability + 1.5,
                difficulty: card.memory_state.unwrap().difficulty,
            });
            card.interval += 1;
            card.original_due += 1;
            card.desired_retention = Some(0.99);
            card.decay = Some(0.12);
            Ok(())
        })?;
        assert_eq!(stale_card.deck_id, filtered_deck.id);
        assert_eq!(stale_card.original_deck_id, home_deck.id);

        let reviewed_card = col1.storage.get_card(card_id)?.unwrap();
        assert!(
            stale_card.interval != reviewed_card.interval
                || stale_card.original_due != reviewed_card.due
        );

        let out = ctx.normal_sync(&mut col1).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);
        let out = ctx.normal_sync(&mut col2).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);

        let reconciled_card = col2.storage.get_card(card_id)?.unwrap();
        let synced_card = col1.storage.get_card(card_id)?.unwrap();
        assert_eq!(reconciled_card.deck_id, filtered_deck.id);
        assert_eq!(reconciled_card.original_deck_id, home_deck.id);
        assert_eq!(reconciled_card.memory_state, synced_card.memory_state);
        assert_eq!(reconciled_card.desired_retention, Some(0.84));
        assert_eq!(reconciled_card.interval, synced_card.interval);
        assert_eq!(reconciled_card.original_due, synced_card.due);
        // The filtered deck position should remain local to the filtered deck;
        // only the home-deck schedule is updated through original_due.
        assert_eq!(reconciled_card.due, stale_card.due);

        Ok(())
    })
    .await
}

#[tokio::test]
async fn fsrs_state_is_recomputed_from_reviews_on_both_devices() -> Result<()> {
    with_active_server(|client| async move {
        let ctx = SyncTestContext::new(client);

        let mut col1 = ctx.col1();
        col1.set_config_bool(BoolKey::Fsrs, true, false)?;
        let nt = col1.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        note.set_field(0, "fsrs-dual-review")?;
        col1.add_note(&mut note, DeckId(1))?;
        let card_id = col1.answer_easy().card_id;

        sync_fsrs_collections(&ctx, col1).await?;

        let mut col1 = ctx.col1();
        let mut col2 = ctx.col2();

        std::thread::sleep(std::time::Duration::from_millis(1100));
        col1.storage
            .db
            .execute("update cards set due = 0 where id = ?", [card_id])?;
        col1.clear_study_queues();
        col1.answer_good();

        std::thread::sleep(std::time::Duration::from_millis(1100));
        col2.storage
            .db
            .execute("update cards set due = 0 where id = ?", [card_id])?;
        col2.clear_study_queues();
        col2.answer_easy();

        let local_card1 = col1.storage.get_card(card_id)?.unwrap();
        let local_card2 = col2.storage.get_card(card_id)?.unwrap();
        assert!(
            local_card1.memory_state != local_card2.memory_state
                || local_card1.last_review_time != local_card2.last_review_time
                || local_card1.interval != local_card2.interval
                || local_card1.due != local_card2.due
        );
        assert_eq!(col1.storage.get_revlog_entries_for_card(card_id)?.len(), 2);
        assert_eq!(col2.storage.get_revlog_entries_for_card(card_id)?.len(), 2);

        let out = ctx.normal_sync(&mut col1).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);
        let out = ctx.normal_sync(&mut col2).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);

        let merged_card = col2.storage.get_card(card_id)?.unwrap();
        let pre_converged_card = col1.storage.get_card(card_id)?.unwrap();
        assert_eq!(col2.storage.get_revlog_entries_for_card(card_id)?.len(), 3);
        assert_eq!(col1.storage.get_revlog_entries_for_card(card_id)?.len(), 2);
        // After device 2 syncs, it has seen both review streams and should no
        // longer match device 1's still-local-only card state.
        assert!(
            merged_card.memory_state != pre_converged_card.memory_state
                || merged_card.last_review_time != pre_converged_card.last_review_time
                || merged_card.interval != pre_converged_card.interval
                || merged_card.due != pre_converged_card.due
        );

        let out = ctx.normal_sync(&mut col1).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);

        let synced_card1 = col1.storage.get_card(card_id)?.unwrap();
        let synced_card2 = col2.storage.get_card(card_id)?.unwrap();
        assert_eq!(col1.storage.get_revlog_entries_for_card(card_id)?.len(), 3);
        assert_eq!(col2.storage.get_revlog_entries_for_card(card_id)?.len(), 3);
        assert_eq!(synced_card1.memory_state, synced_card2.memory_state);
        assert_eq!(synced_card1.last_review_time, synced_card2.last_review_time);
        assert_eq!(synced_card1.interval, synced_card2.interval);
        assert_eq!(synced_card1.due, synced_card2.due);

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

async fn sync_fsrs_collections(ctx: &SyncTestContext, mut col1: Collection) -> Result<()> {
    let out = ctx.normal_sync(&mut col1).await;
    assert!(matches!(
        out.required,
        SyncActionRequired::FullSyncRequired { .. }
    ));
    ctx.full_upload(col1).await;

    let mut col2 = ctx.col2();
    let out = ctx.normal_sync(&mut col2).await;
    assert_eq!(
        out.required,
        SyncActionRequired::FullSyncRequired {
            upload_ok: false,
            download_ok: true,
        }
    );
    ctx.full_download(col2).await;

    Ok(())
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

    // sync the other collection
    let out = ctx.normal_sync(&mut col2).await;
    assert_eq!(out.required, SyncActionRequired::NoChanges);

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
