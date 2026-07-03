// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! The current sync protocol sends changed notetypes, decks, tags and config
//! all in a single request.

use std::collections::HashMap;

use serde::Deserialize;
use serde::Serialize;
use serde_json::Value;
use serde_tuple::Serialize_tuple;
use tracing::debug;
use tracing::trace;

use crate::deckconfig::DeckConfSchema11;
use crate::decks::DeckSchema11;
use crate::error::SyncErrorKind;
use crate::notetype::NotetypeSchema11;
use crate::prelude::*;
use crate::sync::collection::normal::ClientSyncState;
use crate::sync::collection::normal::NormalSyncer;
use crate::sync::collection::protocol::SyncProtocol;
use crate::sync::collection::start::ServerSyncState;
use crate::sync::request::IntoSyncRequest;
use crate::tags::Tag;

#[derive(Serialize, Deserialize, Debug)]
pub struct ApplyChangesRequest {
    pub changes: UnchunkedChanges,
}

#[derive(Serialize, Deserialize, Debug, Default)]
pub struct UnchunkedChanges {
    #[serde(rename = "models")]
    notetypes: Vec<NotetypeSchema11>,
    #[serde(rename = "decks")]
    decks_and_config: DecksAndConfig,
    tags: Vec<String>,

    // the following are only sent if local is newer
    #[serde(skip_serializing_if = "Option::is_none", rename = "conf")]
    config: Option<HashMap<String, Value>>,
    #[serde(skip_serializing_if = "Option::is_none", rename = "crt")]
    creation_stamp: Option<TimestampSecs>,
}

#[derive(Serialize_tuple, Deserialize, Debug, Default)]
pub struct DecksAndConfig {
    decks: Vec<DeckSchema11>,
    config: Vec<DeckConfSchema11>,
}

impl NormalSyncer<'_> {
    // This was assumed to a cheap operation when originally written - it didn't
    // anticipate the large deck trees and note types some users would create.
    // They should be chunked in the future, like other objects. Syncing tags
    // explicitly is also probably of limited usefulness.
    pub(in crate::sync) async fn process_unchunked_changes(
        &mut self,
        state: &ClientSyncState,
    ) -> Result<()> {
        debug!("gathering local changes");
        let local = self.col.local_unchunked_changes(
            state.pending_usn,
            Some(state.server_usn),
            state.local_is_newer,
        )?;

        debug!(
            notetypes = local.notetypes.len(),
            decks = local.decks_and_config.decks.len(),
            deck_config = local.decks_and_config.config.len(),
            tags = local.tags.len(),
            "sending"
        );

        self.progress.update(false, |p| {
            p.local_update += local.notetypes.len()
                + local.decks_and_config.decks.len()
                + local.decks_and_config.config.len()
                + local.tags.len();
        })?;
        let remote = self
            .server
            .apply_changes(ApplyChangesRequest { changes: local }.try_into_sync_request()?)
            .await?
            .json()?;
        self.progress.check_cancelled()?;

        debug!(
            notetypes = remote.notetypes.len(),
            decks = remote.decks_and_config.decks.len(),
            deck_config = remote.decks_and_config.config.len(),
            tags = remote.tags.len(),
            "received"
        );

        self.progress.update(false, |p| {
            p.remote_update += remote.notetypes.len()
                + remote.decks_and_config.decks.len()
                + remote.decks_and_config.config.len()
                + remote.tags.len();
        })?;

        self.col.apply_changes(remote, state.server_usn)?;
        self.progress.check_cancelled()?;
        Ok(())
    }
}

impl Collection {
    // Local->remote unchunked changes
    //----------------------------------------------------------------

    pub(in crate::sync) fn local_unchunked_changes(
        &mut self,
        pending_usn: Usn,
        server_usn_if_client: Option<Usn>,
        local_is_newer: bool,
    ) -> Result<UnchunkedChanges> {
        let mut changes = UnchunkedChanges {
            notetypes: self.changed_notetypes(pending_usn, server_usn_if_client)?,
            decks_and_config: DecksAndConfig {
                decks: self.changed_decks(pending_usn, server_usn_if_client)?,
                config: self.changed_deck_config(pending_usn, server_usn_if_client)?,
            },
            tags: self.changed_tags(pending_usn, server_usn_if_client)?,
            ..Default::default()
        };
        if local_is_newer {
            changes.config = Some(self.changed_config()?);
            changes.creation_stamp = Some(self.storage.creation_stamp()?);
        }

        Ok(changes)
    }

    fn changed_notetypes(
        &mut self,
        pending_usn: Usn,
        server_usn_if_client: Option<Usn>,
    ) -> Result<Vec<NotetypeSchema11>> {
        let ids = self
            .storage
            .objects_pending_sync("notetypes", pending_usn)?;
        self.storage
            .maybe_update_object_usns("notetypes", &ids, server_usn_if_client)?;
        self.state.notetype_cache.clear();
        ids.into_iter()
            .map(|id| {
                self.storage.get_notetype(id).map(|opt| {
                    let mut nt: NotetypeSchema11 = opt.unwrap().into();
                    nt.usn = server_usn_if_client.unwrap_or(nt.usn);
                    nt
                })
            })
            .collect()
    }

    fn changed_decks(
        &mut self,
        pending_usn: Usn,
        server_usn_if_client: Option<Usn>,
    ) -> Result<Vec<DeckSchema11>> {
        let ids = self.storage.objects_pending_sync("decks", pending_usn)?;
        self.storage
            .maybe_update_object_usns("decks", &ids, server_usn_if_client)?;
        self.state.deck_cache.clear();
        ids.into_iter()
            .map(|id| {
                self.storage.get_deck(id).map(|opt| {
                    let mut deck = opt.unwrap();
                    deck.usn = server_usn_if_client.unwrap_or(deck.usn);
                    deck.into()
                })
            })
            .collect()
    }

    fn changed_deck_config(
        &self,
        pending_usn: Usn,
        server_usn_if_client: Option<Usn>,
    ) -> Result<Vec<DeckConfSchema11>> {
        let ids = self
            .storage
            .objects_pending_sync("deck_config", pending_usn)?;
        self.storage
            .maybe_update_object_usns("deck_config", &ids, server_usn_if_client)?;
        ids.into_iter()
            .map(|id| {
                self.storage.get_deck_config(id).map(|opt| {
                    let mut conf: DeckConfSchema11 = opt.unwrap().into();
                    conf.usn = server_usn_if_client.unwrap_or(conf.usn);
                    conf
                })
            })
            .collect()
    }

    fn changed_tags(
        &self,
        pending_usn: Usn,
        server_usn_if_client: Option<Usn>,
    ) -> Result<Vec<String>> {
        let changed = self.storage.tags_pending_sync(pending_usn)?;
        if let Some(usn) = server_usn_if_client {
            self.storage.update_tag_usns(&changed, usn)?;
        }
        Ok(changed)
    }

    /// Currently this is all config, as legacy clients overwrite the local
    /// items with the provided value.
    fn changed_config(&self) -> Result<HashMap<String, Value>> {
        let conf = self.storage.get_all_config()?;
        self.storage.clear_config_usns()?;
        Ok(conf)
    }

    // Remote->local unchunked changes
    //----------------------------------------------------------------

    pub(in crate::sync) fn apply_changes(
        &mut self,
        remote: UnchunkedChanges,
        latest_usn: Usn,
    ) -> Result<()> {
        self.merge_notetypes(remote.notetypes, latest_usn)?;
        self.merge_decks(remote.decks_and_config.decks, latest_usn)?;
        self.merge_deck_config(remote.decks_and_config.config)?;
        self.merge_tags(remote.tags, latest_usn)?;
        if let Some(crt) = remote.creation_stamp {
            self.set_creation_stamp(crt)?;
        }
        if let Some(config) = remote.config {
            self.storage
                .set_all_config(config, latest_usn, TimestampSecs::now())?;
        }

        Ok(())
    }

    fn merge_notetypes(&mut self, notetypes: Vec<NotetypeSchema11>, latest_usn: Usn) -> Result<()> {
        for nt in notetypes {
            let mut nt: Notetype = nt.into();
            let proceed = if let Some(existing_nt) = self.storage.get_notetype(nt.id)? {
                if existing_nt.mtime_secs <= nt.mtime_secs {
                    if (existing_nt.fields.len() != nt.fields.len())
                        || (existing_nt.templates.len() != nt.templates.len())
                    {
                        return Err(AnkiError::sync_error(
                            "notetype schema changed",
                            SyncErrorKind::ResyncRequired,
                        ));
                    }
                    true
                } else {
                    false
                }
            } else {
                true
            };
            if proceed {
                self.ensure_notetype_name_unique(&mut nt, latest_usn)?;
                self.storage.add_or_update_notetype_with_existing_id(&nt)?;
                self.state.notetype_cache.remove(&nt.id);
            }
        }
        Ok(())
    }

    fn merge_decks(&mut self, decks: Vec<DeckSchema11>, latest_usn: Usn) -> Result<()> {
        for deck in decks {
            let proceed = if let Some(existing_deck) = self.storage.get_deck(deck.id())? {
                existing_deck.mtime_secs <= deck.common().mtime
            } else {
                true
            };
            if proceed {
                let mut deck = deck.into();
                self.ensure_deck_name_unique(&mut deck, latest_usn)?;
                self.storage.add_or_update_deck_with_existing_id(&deck)?;
                self.state.deck_cache.remove(&deck.id);
            }
        }
        Ok(())
    }

    fn merge_deck_config(&self, dconf: Vec<DeckConfSchema11>) -> Result<()> {
        for conf in dconf {
            let proceed = if let Some(existing_conf) = self.storage.get_deck_config(conf.id)? {
                existing_conf.mtime_secs <= conf.mtime
            } else {
                true
            };
            if proceed {
                let conf = conf.into();
                self.storage
                    .add_or_update_deck_config_with_existing_id(&conf)?;
            }
        }
        Ok(())
    }

    fn merge_tags(&mut self, tags: Vec<String>, latest_usn: Usn) -> Result<()> {
        for tag in tags {
            self.register_tag(&mut Tag::new(tag, latest_usn))?;
        }
        Ok(())
    }
}

pub fn server_apply_changes(
    req: ApplyChangesRequest,
    col: &mut Collection,
    state: &mut ServerSyncState,
) -> Result<UnchunkedChanges> {
    let server_changes =
        col.local_unchunked_changes(state.client_usn, None, !state.client_is_newer)?;
    trace!(?req.changes, ?server_changes);
    col.apply_changes(req.changes, state.server_usn)?;
    Ok(server_changes)
}
