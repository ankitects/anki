// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub use crate::backend_proto::BackendMethod;
use crate::{
    backend::dbproxy::db_command_bytes,
    backend_proto as pb,
    backend_proto::builtin_search_order::BuiltinSortKind,
    backend_proto::{
        AddOrUpdateDeckConfigLegacyIn, BackendResult, Empty, RenderedTemplateReplacement,
        SyncMediaIn,
    },
    card::{Card, CardID},
    card::{CardQueue, CardType},
    cloze::add_cloze_numbers_in_string,
    collection::{open_collection, Collection},
    config::SortKind,
    deckconf::{DeckConf, DeckConfID, DeckConfSchema11},
    decks::{Deck, DeckID, DeckSchema11},
    err::{AnkiError, NetworkErrorKind, Result, SyncErrorKind},
    i18n::{tr_args, I18n, TR},
    latex::{extract_latex, extract_latex_expanding_clozes, ExtractedLatex},
    log,
    log::{default_logger, Logger},
    media::check::MediaChecker,
    media::sync::MediaSyncProgress,
    media::MediaManager,
    notes::{Note, NoteID},
    notetype::{
        all_stock_notetypes, CardTemplateSchema11, NoteType, NoteTypeID, NoteTypeSchema11,
        RenderCardOutput,
    },
    sched::cutoff::local_minutes_west_for_stamp,
    sched::timespan::{answer_button_time, learning_congrats, studied_today, time_span},
    search::SortMode,
    template::RenderedNode,
    text::{extract_av_tags, strip_av_tags, AVTag},
    timestamp::TimestampSecs,
    types::Usn,
};
use fluent::FluentValue;
use futures::future::{AbortHandle, Abortable};
use log::error;
use pb::BackendService;
use prost::Message;
use serde_json::Value as JsonValue;
use std::collections::{HashMap, HashSet};
use std::convert::TryFrom;
use std::path::PathBuf;
use std::{
    result,
    sync::{Arc, Mutex},
};
use tokio::runtime::Runtime;

mod dbproxy;

pub type ProtoProgressCallback = Box<dyn Fn(Vec<u8>) -> bool + Send>;

pub struct Backend {
    col: Arc<Mutex<Option<Collection>>>,
    progress_callback: Option<ProtoProgressCallback>,
    i18n: I18n,
    server: bool,
    media_sync_abort: Option<AbortHandle>,
}

enum Progress<'a> {
    MediaSync(&'a MediaSyncProgress),
    MediaCheck(u32),
}

/// Convert an Anki error to a protobuf error.
fn anki_error_to_proto_error(err: AnkiError, i18n: &I18n) -> pb::BackendError {
    use pb::backend_error::Value as V;
    let localized = err.localized_description(i18n);
    let value = match err {
        AnkiError::InvalidInput { .. } => V::InvalidInput(pb::Empty {}),
        AnkiError::TemplateError { .. } => V::TemplateParse(pb::Empty {}),
        AnkiError::IOError { .. } => V::IoError(pb::Empty {}),
        AnkiError::DBError { .. } => V::DbError(pb::Empty {}),
        AnkiError::NetworkError { kind, .. } => {
            V::NetworkError(pb::NetworkError { kind: kind.into() })
        }
        AnkiError::SyncError { kind, .. } => V::SyncError(pb::SyncError { kind: kind.into() }),
        AnkiError::Interrupted => V::Interrupted(Empty {}),
        AnkiError::CollectionNotOpen => V::InvalidInput(pb::Empty {}),
        AnkiError::CollectionAlreadyOpen => V::InvalidInput(pb::Empty {}),
        AnkiError::JSONError { info } => V::JsonError(info),
        AnkiError::ProtoError { info } => V::ProtoError(info),
        AnkiError::NotFound => V::NotFoundError(Empty {}),
        AnkiError::Existing => V::Exists(Empty {}),
        AnkiError::DeckIsFiltered => V::DeckIsFiltered(Empty {}),
    };

    pb::BackendError {
        value: Some(value),
        localized,
    }
}

impl std::convert::From<NetworkErrorKind> for i32 {
    fn from(e: NetworkErrorKind) -> Self {
        use pb::network_error::NetworkErrorKind as V;
        (match e {
            NetworkErrorKind::Offline => V::Offline,
            NetworkErrorKind::Timeout => V::Timeout,
            NetworkErrorKind::ProxyAuth => V::ProxyAuth,
            NetworkErrorKind::Other => V::Other,
        }) as i32
    }
}

impl std::convert::From<SyncErrorKind> for i32 {
    fn from(e: SyncErrorKind) -> Self {
        use pb::sync_error::SyncErrorKind as V;
        (match e {
            SyncErrorKind::Conflict => V::Conflict,
            SyncErrorKind::ServerError => V::ServerError,
            SyncErrorKind::ClientTooOld => V::ClientTooOld,
            SyncErrorKind::AuthFailed => V::AuthFailed,
            SyncErrorKind::ServerMessage => V::ServerMessage,
            SyncErrorKind::ResyncRequired => V::ResyncRequired,
            SyncErrorKind::Other => V::Other,
        }) as i32
    }
}

pub fn init_backend(init_msg: &[u8]) -> std::result::Result<Backend, String> {
    let input: pb::BackendInit = match pb::BackendInit::decode(init_msg) {
        Ok(req) => req,
        Err(_) => return Err("couldn't decode init request".into()),
    };

    let i18n = I18n::new(
        &input.preferred_langs,
        input.locale_folder_path,
        log::terminal(),
    );

    Ok(Backend::new(i18n, input.server))
}

impl From<Vec<u8>> for pb::Json {
    fn from(json: Vec<u8>) -> Self {
        pb::Json { json }
    }
}

impl From<String> for pb::String {
    fn from(val: String) -> Self {
        pb::String { val }
    }
}

impl From<i64> for pb::Int64 {
    fn from(val: i64) -> Self {
        pb::Int64 { val }
    }
}

impl From<u32> for pb::UInt32 {
    fn from(val: u32) -> Self {
        pb::UInt32 { val }
    }
}

impl From<()> for pb::Empty {
    fn from(_val: ()) -> Self {
        pb::Empty {}
    }
}

impl From<pb::CardId> for CardID {
    fn from(cid: pb::CardId) -> Self {
        CardID(cid.cid)
    }
}

impl From<pb::NoteId> for NoteID {
    fn from(nid: pb::NoteId) -> Self {
        NoteID(nid.nid)
    }
}

impl From<pb::NoteTypeId> for NoteTypeID {
    fn from(ntid: pb::NoteTypeId) -> Self {
        NoteTypeID(ntid.ntid)
    }
}

impl From<pb::DeckId> for DeckID {
    fn from(did: pb::DeckId) -> Self {
        DeckID(did.did)
    }
}

impl From<pb::DeckConfigId> for DeckConfID {
    fn from(dcid: pb::DeckConfigId) -> Self {
        DeckConfID(dcid.dcid)
    }
}

impl BackendService for Backend {
    // card rendering

    fn render_existing_card(
        &mut self,
        input: pb::RenderExistingCardIn,
    ) -> BackendResult<pb::RenderCardOut> {
        self.with_col(|col| {
            col.render_existing_card(CardID(input.card_id), input.browser)
                .map(Into::into)
        })
    }

    fn render_uncommitted_card(
        &mut self,
        input: pb::RenderUncommittedCardIn,
    ) -> BackendResult<pb::RenderCardOut> {
        let schema11: CardTemplateSchema11 = serde_json::from_slice(&input.template)?;
        let template = schema11.into();
        let mut note = input
            .note
            .ok_or_else(|| AnkiError::invalid_input("missing note"))?
            .into();
        let ord = input.card_ord as u16;
        let fill_empty = input.fill_empty;
        self.with_col(|col| {
            col.render_uncommitted_card(&mut note, &template, ord, fill_empty)
                .map(Into::into)
        })
    }

    fn get_empty_cards(&mut self, _input: pb::Empty) -> Result<pb::EmptyCardsReport> {
        self.with_col(|col| {
            let mut empty = col.empty_cards()?;
            let report = col.empty_cards_report(&mut empty)?;

            let mut outnotes = vec![];
            for (_ntid, notes) in empty {
                outnotes.extend(notes.into_iter().map(|e| pb::NoteWithEmptyCards {
                    note_id: e.nid.0,
                    will_delete_note: e.empty.len() == e.current_count,
                    card_ids: e.empty.into_iter().map(|(_ord, id)| id.0).collect(),
                }))
            }
            Ok(pb::EmptyCardsReport {
                report,
                notes: outnotes,
            })
        })
    }

    fn strip_av_tags(&mut self, input: pb::String) -> BackendResult<pb::String> {
        Ok(pb::String {
            val: strip_av_tags(&input.val).into(),
        })
    }

    fn extract_av_tags(
        &mut self,
        input: pb::ExtractAvTagsIn,
    ) -> BackendResult<pb::ExtractAvTagsOut> {
        let (text, tags) = extract_av_tags(&input.text, input.question_side);
        let pt_tags = tags
            .into_iter()
            .map(|avtag| match avtag {
                AVTag::SoundOrVideo(file) => pb::AvTag {
                    value: Some(pb::av_tag::Value::SoundOrVideo(file)),
                },
                AVTag::TextToSpeech {
                    field_text,
                    lang,
                    voices,
                    other_args,
                    speed,
                } => pb::AvTag {
                    value: Some(pb::av_tag::Value::Tts(pb::TtsTag {
                        field_text,
                        lang,
                        voices,
                        other_args,
                        speed,
                    })),
                },
            })
            .collect();

        Ok(pb::ExtractAvTagsOut {
            text: text.into(),
            av_tags: pt_tags,
        })
    }

    fn extract_latex(&mut self, input: pb::ExtractLatexIn) -> BackendResult<pb::ExtractLatexOut> {
        let func = if input.expand_clozes {
            extract_latex_expanding_clozes
        } else {
            extract_latex
        };
        let (text, extracted) = func(&input.text, input.svg);

        Ok(pb::ExtractLatexOut {
            text,
            latex: extracted
                .into_iter()
                .map(|e: ExtractedLatex| pb::ExtractedLatex {
                    filename: e.fname,
                    latex_body: e.latex,
                })
                .collect(),
        })
    }

    // searching
    //-----------------------------------------------

    fn search_cards(&mut self, input: pb::SearchCardsIn) -> Result<pb::SearchCardsOut> {
        self.with_col(|col| {
            let order = if let Some(order) = input.order {
                use pb::sort_order::Value as V;
                match order.value {
                    Some(V::None(_)) => SortMode::NoOrder,
                    Some(V::Custom(s)) => SortMode::Custom(s),
                    Some(V::FromConfig(_)) => SortMode::FromConfig,
                    Some(V::Builtin(b)) => SortMode::Builtin {
                        kind: sort_kind_from_pb(b.kind),
                        reverse: b.reverse,
                    },
                    None => SortMode::FromConfig,
                }
            } else {
                SortMode::FromConfig
            };
            let cids = col.search_cards(&input.search, order)?;
            Ok(pb::SearchCardsOut {
                card_ids: cids.into_iter().map(|v| v.0).collect(),
            })
        })
    }

    fn search_notes(&mut self, input: pb::SearchNotesIn) -> Result<pb::SearchNotesOut> {
        self.with_col(|col| {
            let nids = col.search_notes(&input.search)?;
            Ok(pb::SearchNotesOut {
                note_ids: nids.into_iter().map(|v| v.0).collect(),
            })
        })
    }

    fn find_and_replace(&mut self, input: pb::FindAndReplaceIn) -> BackendResult<pb::UInt32> {
        let mut search = if input.regex {
            input.search
        } else {
            regex::escape(&input.search)
        };
        if !input.match_case {
            search = format!("(?i){}", search);
        }
        let nids = input.nids.into_iter().map(NoteID).collect();
        let field_name = if input.field_name.is_empty() {
            None
        } else {
            Some(input.field_name)
        };
        let repl = input.replacement;
        self.with_col(|col| {
            col.find_and_replace(nids, &search, &repl, field_name)
                .map(|cnt| pb::UInt32 { val: cnt as u32 })
        })
    }

    // scheduling
    //-----------------------------------------------

    fn sched_timing_today(&mut self, _input: pb::Empty) -> Result<pb::SchedTimingTodayOut> {
        self.with_col(|col| col.timing_today().map(Into::into))
    }

    fn local_minutes_west(&mut self, input: pb::Int64) -> BackendResult<pb::Int32> {
        Ok(pb::Int32 {
            val: local_minutes_west_for_stamp(input.val),
        })
    }

    fn set_local_minutes_west(&mut self, input: pb::Int32) -> BackendResult<Empty> {
        self.with_col(|col| {
            col.transact(None, |col| {
                col.set_local_mins_west(input.val).map(Into::into)
            })
        })
    }

    fn studied_today(&mut self, input: pb::StudiedTodayIn) -> BackendResult<pb::String> {
        Ok(studied_today(input.cards as usize, input.seconds as f32, &self.i18n).into())
    }

    fn congrats_learn_message(
        &mut self,
        input: pb::CongratsLearnMessageIn,
    ) -> BackendResult<pb::String> {
        Ok(learning_congrats(input.remaining as usize, input.next_due, &self.i18n).into())
    }

    // decks
    //-----------------------------------------------

    fn deck_tree(&mut self, input: pb::DeckTreeIn) -> Result<pb::DeckTreeNode> {
        let lim = if input.top_deck_id > 0 {
            Some(DeckID(input.top_deck_id))
        } else {
            None
        };
        self.with_col(|col| col.deck_tree(input.include_counts, lim))
    }

    fn deck_tree_legacy(&mut self, _input: pb::Empty) -> BackendResult<pb::Json> {
        self.with_col(|col| {
            let tree = col.legacy_deck_tree()?;
            serde_json::to_vec(&tree)
                .map_err(Into::into)
                .map(Into::into)
        })
    }

    fn get_deck_legacy(&mut self, input: pb::DeckId) -> Result<pb::Json> {
        self.with_col(|col| {
            let deck: DeckSchema11 = col
                .storage
                .get_deck(input.into())?
                .ok_or(AnkiError::NotFound)?
                .into();
            serde_json::to_vec(&deck)
                .map_err(Into::into)
                .map(Into::into)
        })
    }

    fn get_deck_id_by_name(&mut self, input: pb::String) -> Result<pb::DeckId> {
        self.with_col(|col| {
            col.get_deck_id(&input.val).and_then(|d| {
                d.ok_or(AnkiError::NotFound)
                    .map(|d| pb::DeckId { did: d.0 })
            })
        })
    }

    fn get_all_decks_legacy(&mut self, _input: Empty) -> BackendResult<pb::Json> {
        self.with_col(|col| {
            let decks = col.storage.get_all_decks_as_schema11()?;
            serde_json::to_vec(&decks).map_err(Into::into)
        })
        .map(Into::into)
    }

    fn get_deck_names(&mut self, input: pb::GetDeckNamesIn) -> Result<pb::DeckNames> {
        self.with_col(|col| {
            let names = if input.include_filtered {
                col.get_all_deck_names(input.skip_empty_default)?
            } else {
                col.get_all_normal_deck_names()?
            };
            Ok(pb::DeckNames {
                entries: names
                    .into_iter()
                    .map(|(id, name)| pb::DeckNameId { id: id.0, name })
                    .collect(),
            })
        })
    }

    fn add_or_update_deck_legacy(
        &mut self,
        input: pb::AddOrUpdateDeckLegacyIn,
    ) -> Result<pb::DeckId> {
        self.with_col(|col| {
            let schema11: DeckSchema11 = serde_json::from_slice(&input.deck)?;
            let mut deck: Deck = schema11.into();
            if input.preserve_usn_and_mtime {
                col.transact(None, |col| {
                    let usn = col.usn()?;
                    col.add_or_update_single_deck(&mut deck, usn)
                })?;
            } else {
                col.add_or_update_deck(&mut deck)?;
            }
            Ok(pb::DeckId { did: deck.id.0 })
        })
    }

    fn new_deck_legacy(&mut self, input: pb::Bool) -> BackendResult<pb::Json> {
        let deck = if input.val {
            Deck::new_filtered()
        } else {
            Deck::new_normal()
        };
        let schema11: DeckSchema11 = deck.into();
        serde_json::to_vec(&schema11)
            .map_err(Into::into)
            .map(Into::into)
    }

    fn remove_deck(&mut self, input: pb::DeckId) -> BackendResult<Empty> {
        self.with_col(|col| col.remove_deck_and_child_decks(input.into()))
            .map(Into::into)
    }

    // deck config
    //----------------------------------------------------

    fn add_or_update_deck_config_legacy(
        &mut self,
        input: AddOrUpdateDeckConfigLegacyIn,
    ) -> BackendResult<pb::DeckConfigId> {
        let conf: DeckConfSchema11 = serde_json::from_slice(&input.config)?;
        let mut conf: DeckConf = conf.into();
        self.with_col(|col| {
            col.transact(None, |col| {
                col.add_or_update_deck_config(&mut conf, input.preserve_usn_and_mtime)?;
                Ok(pb::DeckConfigId { dcid: conf.id.0 })
            })
        })
        .map(Into::into)
    }

    fn all_deck_config_legacy(&mut self, _input: Empty) -> BackendResult<pb::Json> {
        self.with_col(|col| {
            let conf: Vec<DeckConfSchema11> = col
                .storage
                .all_deck_config()?
                .into_iter()
                .map(Into::into)
                .collect();
            serde_json::to_vec(&conf).map_err(Into::into)
        })
        .map(Into::into)
    }

    fn new_deck_config_legacy(&mut self, _input: Empty) -> BackendResult<pb::Json> {
        serde_json::to_vec(&DeckConfSchema11::default())
            .map_err(Into::into)
            .map(Into::into)
    }

    fn remove_deck_config(&mut self, input: pb::DeckConfigId) -> BackendResult<Empty> {
        self.with_col(|col| col.transact(None, |col| col.remove_deck_config(input.into())))
            .map(Into::into)
    }

    fn get_deck_config_legacy(&mut self, input: pb::DeckConfigId) -> BackendResult<pb::Json> {
        self.with_col(|col| {
            let conf = col.get_deck_config(input.into(), true)?.unwrap();
            let conf: DeckConfSchema11 = conf.into();
            Ok(serde_json::to_vec(&conf)?)
        })
        .map(Into::into)
    }

    // cards
    //-------------------------------------------------------------------

    fn get_card(&mut self, input: pb::CardId) -> BackendResult<pb::Card> {
        self.with_col(|col| {
            col.storage
                .get_card(input.into())
                .and_then(|opt| opt.ok_or(AnkiError::NotFound))
                .map(card_to_pb)
        })
    }

    fn update_card(&mut self, input: pb::Card) -> BackendResult<Empty> {
        let mut card = pbcard_to_native(input)?;
        self.with_col(|col| {
            col.transact(None, |ctx| {
                let orig = ctx
                    .storage
                    .get_card(card.id)?
                    .ok_or_else(|| AnkiError::invalid_input("missing card"))?;
                ctx.update_card(&mut card, &orig)
            })
        })
        .map(Into::into)
    }

    fn add_card(&mut self, input: pb::Card) -> BackendResult<pb::CardId> {
        let mut card = pbcard_to_native(input)?;
        self.with_col(|col| col.transact(None, |ctx| ctx.add_card(&mut card)))?;
        Ok(pb::CardId { cid: card.id.0 })
    }

    // notes
    //-------------------------------------------------------------------

    fn new_note(&mut self, input: pb::NoteTypeId) -> BackendResult<pb::Note> {
        self.with_col(|col| {
            let nt = col.get_notetype(input.into())?.ok_or(AnkiError::NotFound)?;
            Ok(nt.new_note().into())
        })
    }

    fn add_note(&mut self, input: pb::AddNoteIn) -> BackendResult<pb::NoteId> {
        self.with_col(|col| {
            let mut note: Note = input.note.ok_or(AnkiError::NotFound)?.into();
            col.add_note(&mut note, DeckID(input.deck_id))
                .map(|_| pb::NoteId { nid: note.id.0 })
        })
    }

    fn update_note(&mut self, input: pb::Note) -> BackendResult<Empty> {
        self.with_col(|col| {
            let mut note: Note = input.into();
            col.update_note(&mut note)
        })
        .map(Into::into)
    }

    fn get_note(&mut self, input: pb::NoteId) -> BackendResult<pb::Note> {
        self.with_col(|col| {
            col.storage
                .get_note(input.into())?
                .ok_or(AnkiError::NotFound)
                .map(Into::into)
        })
    }

    fn add_note_tags(&mut self, input: pb::AddNoteTagsIn) -> BackendResult<pb::UInt32> {
        self.with_col(|col| {
            col.add_tags_for_notes(&to_nids(input.nids), &input.tags)
                .map(|n| n as u32)
        })
        .map(Into::into)
    }

    fn update_note_tags(&mut self, input: pb::UpdateNoteTagsIn) -> BackendResult<pb::UInt32> {
        self.with_col(|col| {
            col.replace_tags_for_notes(
                &to_nids(input.nids),
                &input.tags,
                &input.replacement,
                input.regex,
            )
            .map(|n| (n as u32).into())
        })
    }

    fn cloze_numbers_in_note(
        &mut self,
        note: pb::Note,
    ) -> BackendResult<pb::ClozeNumbersInNoteOut> {
        let mut set = HashSet::with_capacity(4);
        for field in &note.fields {
            add_cloze_numbers_in_string(field, &mut set);
        }
        Ok(pb::ClozeNumbersInNoteOut {
            numbers: set.into_iter().map(|n| n as u32).collect(),
        })
    }

    fn field_names_for_notes(
        &mut self,
        input: pb::FieldNamesForNotesIn,
    ) -> BackendResult<pb::FieldNamesForNotesOut> {
        self.with_col(|col| {
            let nids: Vec<_> = input.nids.into_iter().map(NoteID).collect();
            col.storage
                .field_names_for_notes(&nids)
                .map(|fields| pb::FieldNamesForNotesOut { fields })
        })
    }

    fn after_note_updates(&mut self, input: pb::AfterNoteUpdatesIn) -> BackendResult<Empty> {
        self.with_col(|col| {
            col.transact(None, |col| {
                col.after_note_updates(
                    &to_nids(input.nids),
                    input.generate_cards,
                    input.mark_notes_modified,
                )?;
                Ok(pb::Empty {})
            })
        })
    }

    fn note_is_duplicate_or_empty(
        &mut self,
        input: pb::Note,
    ) -> BackendResult<pb::NoteIsDuplicateOrEmptyOut> {
        let note: Note = input.into();
        self.with_col(|col| {
            col.note_is_duplicate_or_empty(&note)
                .map(|r| pb::NoteIsDuplicateOrEmptyOut { state: r as i32 })
        })
    }

    // notetypes
    //-------------------------------------------------------------------

    fn get_stock_notetype_legacy(
        &mut self,
        input: pb::GetStockNotetypeIn,
    ) -> BackendResult<pb::Json> {
        // fixme: use individual functions instead of full vec
        let mut all = all_stock_notetypes(&self.i18n);
        let idx = (input.kind as usize).min(all.len() - 1);
        let nt = all.swap_remove(idx);
        let schema11: NoteTypeSchema11 = nt.into();
        serde_json::to_vec(&schema11)
            .map_err(Into::into)
            .map(Into::into)
    }

    fn get_notetype_names(&mut self, _input: Empty) -> BackendResult<pb::NoteTypeNames> {
        self.with_col(|col| {
            let entries: Vec<_> = col
                .storage
                .get_all_notetype_names()?
                .into_iter()
                .map(|(id, name)| pb::NoteTypeNameId { id: id.0, name })
                .collect();
            Ok(pb::NoteTypeNames { entries })
        })
    }

    fn get_notetype_names_and_counts(
        &mut self,
        _input: Empty,
    ) -> BackendResult<pb::NoteTypeUseCounts> {
        self.with_col(|col| {
            let entries: Vec<_> = col
                .storage
                .get_notetype_use_counts()?
                .into_iter()
                .map(|(id, name, use_count)| pb::NoteTypeNameIdUseCount {
                    id: id.0,
                    name,
                    use_count,
                })
                .collect();
            Ok(pb::NoteTypeUseCounts { entries })
        })
    }

    fn get_notetype_legacy(&mut self, input: pb::NoteTypeId) -> BackendResult<pb::Json> {
        self.with_col(|col| {
            let schema11: NoteTypeSchema11 = col
                .storage
                .get_notetype(input.into())?
                .ok_or(AnkiError::NotFound)?
                .into();
            Ok(serde_json::to_vec(&schema11)?).map(Into::into)
        })
    }

    fn get_notetype_id_by_name(&mut self, input: pb::String) -> BackendResult<pb::NoteTypeId> {
        self.with_col(|col| {
            col.storage
                .get_notetype_id(&input.val)
                .and_then(|nt| nt.ok_or(AnkiError::NotFound))
                .map(|ntid| pb::NoteTypeId { ntid: ntid.0 })
        })
    }

    fn add_or_update_notetype(
        &mut self,
        input: pb::AddOrUpdateNotetypeIn,
    ) -> BackendResult<pb::NoteTypeId> {
        self.with_col(|col| {
            let legacy: NoteTypeSchema11 = serde_json::from_slice(&input.json)?;
            let mut nt: NoteType = legacy.into();
            if nt.id.0 == 0 {
                col.add_notetype(&mut nt)?;
            } else {
                col.update_notetype(&mut nt, input.preserve_usn_and_mtime)?;
            }
            Ok(pb::NoteTypeId { ntid: nt.id.0 })
        })
    }

    fn remove_notetype(&mut self, input: pb::NoteTypeId) -> BackendResult<Empty> {
        self.with_col(|col| col.remove_notetype(input.into()))
            .map(Into::into)
    }

    // media
    //-------------------------------------------------------------------

    fn add_media_file(&mut self, input: pb::AddMediaFileIn) -> BackendResult<pb::String> {
        self.with_col(|col| {
            let mgr = MediaManager::new(&col.media_folder, &col.media_db)?;
            let mut ctx = mgr.dbctx();
            Ok(mgr
                .add_file(&mut ctx, &input.desired_name, &input.data)?
                .to_string()
                .into())
        })
    }

    fn empty_trash(&mut self, _input: Empty) -> BackendResult<Empty> {
        let callback =
            |progress: usize| self.fire_progress_callback(Progress::MediaCheck(progress as u32));

        self.with_col(|col| {
            let mgr = MediaManager::new(&col.media_folder, &col.media_db)?;
            col.transact(None, |ctx| {
                let mut checker = MediaChecker::new(ctx, &mgr, callback);

                checker.empty_trash()
            })
        })
        .map(Into::into)
    }

    fn restore_trash(&mut self, _input: Empty) -> BackendResult<Empty> {
        let callback =
            |progress: usize| self.fire_progress_callback(Progress::MediaCheck(progress as u32));

        self.with_col(|col| {
            let mgr = MediaManager::new(&col.media_folder, &col.media_db)?;

            col.transact(None, |ctx| {
                let mut checker = MediaChecker::new(ctx, &mgr, callback);

                checker.restore_trash()
            })
        })
        .map(Into::into)
    }

    fn trash_media_files(&mut self, input: pb::TrashMediaFilesIn) -> BackendResult<Empty> {
        self.with_col(|col| {
            let mgr = MediaManager::new(&col.media_folder, &col.media_db)?;
            let mut ctx = mgr.dbctx();
            mgr.remove_files(&mut ctx, &input.fnames)
        })
        .map(Into::into)
    }

    fn check_media(&mut self, _input: pb::Empty) -> Result<pb::CheckMediaOut> {
        let callback =
            |progress: usize| self.fire_progress_callback(Progress::MediaCheck(progress as u32));

        self.with_col(|col| {
            let mgr = MediaManager::new(&col.media_folder, &col.media_db)?;
            col.transact(None, |ctx| {
                let mut checker = MediaChecker::new(ctx, &mgr, callback);
                let mut output = checker.check()?;

                let report = checker.summarize_output(&mut output);

                Ok(pb::CheckMediaOut {
                    unused: output.unused,
                    missing: output.missing,
                    report,
                    have_trash: output.trash_count > 0,
                })
            })
        })
    }

    // collection
    //-------------------------------------------------------------------

    fn check_database(&mut self, _input: pb::Empty) -> BackendResult<pb::CheckDatabaseOut> {
        self.with_col(|col| {
            col.check_database().map(|problems| pb::CheckDatabaseOut {
                problems: problems.to_i18n_strings(&col.i18n),
            })
        })
    }

    fn open_collection(&mut self, input: pb::OpenCollectionIn) -> BackendResult<Empty> {
        let mut col = self.col.lock().unwrap();
        if col.is_some() {
            return Err(AnkiError::CollectionAlreadyOpen);
        }

        let mut path = input.collection_path.clone();
        path.push_str(".log");

        let log_path = match input.log_path.as_str() {
            "" => None,
            path => Some(path),
        };
        let logger = default_logger(log_path)?;

        let new_col = open_collection(
            input.collection_path,
            input.media_folder_path,
            input.media_db_path,
            self.server,
            self.i18n.clone(),
            logger,
        )?;

        *col = Some(new_col);

        Ok(().into())
    }

    fn close_collection(&mut self, input: pb::CloseCollectionIn) -> BackendResult<Empty> {
        let mut col = self.col.lock().unwrap();
        if col.is_none() {
            return Err(AnkiError::CollectionNotOpen);
        }

        if !col.as_ref().unwrap().can_close() {
            return Err(AnkiError::invalid_input("can't close yet"));
        }

        let col_inner = col.take().unwrap();
        if input.downgrade_to_schema11 {
            let log = log::terminal();
            if let Err(e) = col_inner.close(input.downgrade_to_schema11) {
                error!(log, " failed: {:?}", e);
            }
        }

        Ok(().into())
    }

    // sync
    //-------------------------------------------------------------------

    fn sync_media(&mut self, input: SyncMediaIn) -> BackendResult<Empty> {
        let mut guard = self.col.lock().unwrap();

        let col = guard.as_mut().unwrap();
        col.set_media_sync_running()?;

        let folder = col.media_folder.clone();
        let db = col.media_db.clone();
        let log = col.log.clone();

        drop(guard);

        let res = self.sync_media_inner(input, folder, db, log);

        self.with_col(|col| col.set_media_sync_finished())?;

        res.map(Into::into)
    }

    fn abort_media_sync(&mut self, _input: Empty) -> BackendResult<Empty> {
        if let Some(handle) = self.media_sync_abort.take() {
            handle.abort();
        }
        Ok(().into())
    }

    fn before_upload(&mut self, _input: Empty) -> BackendResult<Empty> {
        self.with_col(|col| col.before_upload().map(Into::into))
    }

    // i18n/messages
    //-------------------------------------------------------------------

    fn translate_string(&mut self, input: pb::TranslateStringIn) -> BackendResult<pb::String> {
        let key = match pb::FluentString::from_i32(input.key) {
            Some(key) => key,
            None => return Ok("invalid key".to_string().into()),
        };

        let map = input
            .args
            .iter()
            .map(|(k, v)| (k.as_str(), translate_arg_to_fluent_val(&v)))
            .collect();

        Ok(self.i18n.trn(key, map).into())
    }

    fn format_timespan(&mut self, input: pb::FormatTimespanIn) -> BackendResult<pb::String> {
        let context = match pb::format_timespan_in::Context::from_i32(input.context) {
            Some(context) => context,
            None => return Ok("".to_string().into()),
        };
        Ok(match context {
            pb::format_timespan_in::Context::Precise => time_span(input.seconds, &self.i18n, true),
            pb::format_timespan_in::Context::Intervals => {
                time_span(input.seconds, &self.i18n, false)
            }
            pb::format_timespan_in::Context::AnswerButtons => {
                answer_button_time(input.seconds, &self.i18n)
            }
        }
        .into())
    }

    // tags
    //-------------------------------------------------------------------

    fn all_tags(&mut self, _input: Empty) -> BackendResult<pb::AllTagsOut> {
        let tags = self.with_col(|col| col.storage.all_tags())?;
        let tags: Vec<_> = tags
            .into_iter()
            .map(|(tag, usn)| pb::TagUsnTuple { tag, usn: usn.0 })
            .collect();
        Ok(pb::AllTagsOut { tags })
    }

    fn register_tags(&mut self, input: pb::RegisterTagsIn) -> BackendResult<pb::Bool> {
        self.with_col(|col| {
            col.transact(None, |col| {
                let usn = if input.preserve_usn {
                    Usn(input.usn)
                } else {
                    col.usn()?
                };
                col.register_tags(&input.tags, usn, input.clear_first)
                    .map(|val| pb::Bool { val })
            })
        })
    }

    fn get_changed_tags(&mut self, input: pb::Int32) -> BackendResult<pb::GetChangedTagsOut> {
        self.with_col(|col| {
            col.transact(None, |col| {
                Ok(pb::GetChangedTagsOut {
                    tags: col.storage.get_changed_tags(Usn(input.val))?,
                })
            })
        })
    }

    // config/preferences
    //-------------------------------------------------------------------

    fn get_config_json(&mut self, input: pb::String) -> BackendResult<pb::Json> {
        self.with_col(|col| {
            let val: Option<JsonValue> = col.get_config_optional(input.val.as_str());
            val.ok_or(AnkiError::NotFound)
                .and_then(|v| serde_json::to_vec(&v).map_err(Into::into))
                .map(Into::into)
        })
    }

    fn set_config_json(&mut self, input: pb::SetConfigJsonIn) -> BackendResult<Empty> {
        self.with_col(|col| {
            col.transact(None, |col| {
                // ensure it's a well-formed object
                let val: JsonValue = serde_json::from_slice(&input.value_json)?;
                col.set_config(input.key.as_str(), &val)
            })
        })
        .map(Into::into)
    }

    fn remove_config(&mut self, input: pb::String) -> BackendResult<Empty> {
        self.with_col(|col| col.transact(None, |col| col.remove_config(input.val.as_str())))
            .map(Into::into)
    }

    fn set_all_config(&mut self, input: pb::Json) -> BackendResult<Empty> {
        let val: HashMap<String, JsonValue> = serde_json::from_slice(&input.json)?;
        self.with_col(|col| {
            col.transact(None, |col| {
                col.storage
                    .set_all_config(val, col.usn()?, TimestampSecs::now())
            })
        })
        .map(Into::into)
    }

    fn get_all_config(&mut self, _input: Empty) -> BackendResult<pb::Json> {
        self.with_col(|col| {
            let conf = col.storage.get_all_config()?;
            serde_json::to_vec(&conf).map_err(Into::into)
        })
        .map(Into::into)
    }

    fn get_preferences(&mut self, _input: Empty) -> BackendResult<pb::Preferences> {
        self.with_col(|col| col.get_preferences())
    }

    fn set_preferences(&mut self, input: pb::Preferences) -> BackendResult<Empty> {
        self.with_col(|col| col.transact(None, |col| col.set_preferences(input)))
            .map(Into::into)
    }
}

impl Backend {
    pub fn new(i18n: I18n, server: bool) -> Backend {
        Backend {
            col: Arc::new(Mutex::new(None)),
            progress_callback: None,
            i18n,
            server,
            media_sync_abort: None,
        }
    }

    pub fn i18n(&self) -> &I18n {
        &self.i18n
    }

    pub fn run_command_bytes(
        &mut self,
        method: u32,
        input: &[u8],
    ) -> result::Result<Vec<u8>, Vec<u8>> {
        self.run_command_bytes2_inner(method, input).map_err(|err| {
            let backend_err = anki_error_to_proto_error(err, &self.i18n);
            let mut bytes = Vec::new();
            backend_err.encode(&mut bytes).unwrap();
            bytes
        })
    }

    /// If collection is open, run the provided closure while holding
    /// the mutex.
    /// If collection is not open, return an error.
    fn with_col<F, T>(&self, func: F) -> Result<T>
    where
        F: FnOnce(&mut Collection) -> Result<T>,
    {
        func(
            self.col
                .lock()
                .unwrap()
                .as_mut()
                .ok_or(AnkiError::CollectionNotOpen)?,
        )
    }

    fn fire_progress_callback(&self, progress: Progress) -> bool {
        if let Some(cb) = &self.progress_callback {
            let bytes = progress_to_proto_bytes(progress, &self.i18n);
            cb(bytes)
        } else {
            true
        }
    }

    pub fn set_progress_callback(&mut self, progress_cb: Option<ProtoProgressCallback>) {
        self.progress_callback = progress_cb;
    }

    fn sync_media_inner(
        &mut self,
        input: pb::SyncMediaIn,
        folder: PathBuf,
        db: PathBuf,
        log: Logger,
    ) -> Result<()> {
        let (abort_handle, abort_reg) = AbortHandle::new_pair();
        self.media_sync_abort = Some(abort_handle);

        let callback = |progress: &MediaSyncProgress| {
            self.fire_progress_callback(Progress::MediaSync(progress))
        };

        let mgr = MediaManager::new(&folder, &db)?;
        let mut rt = Runtime::new().unwrap();
        let sync_fut = mgr.sync_media(callback, &input.endpoint, &input.hkey, log);
        let abortable_sync = Abortable::new(sync_fut, abort_reg);
        let ret = match rt.block_on(abortable_sync) {
            Ok(sync_result) => sync_result,
            Err(_) => {
                // aborted sync
                Err(AnkiError::Interrupted)
            }
        };
        self.media_sync_abort = None;
        ret
    }

    pub fn db_command(&self, input: &[u8]) -> Result<String> {
        self.with_col(|col| db_command_bytes(&col.storage, input))
    }
}

fn to_nids(ids: Vec<i64>) -> Vec<NoteID> {
    ids.into_iter().map(NoteID).collect()
}

fn translate_arg_to_fluent_val(arg: &pb::TranslateArgValue) -> FluentValue {
    use pb::translate_arg_value::Value as V;
    match &arg.value {
        Some(val) => match val {
            V::Str(s) => FluentValue::String(s.into()),
            V::Number(f) => FluentValue::Number(f.into()),
        },
        None => FluentValue::String("".into()),
    }
}

fn rendered_nodes_to_proto(nodes: Vec<RenderedNode>) -> Vec<pb::RenderedTemplateNode> {
    nodes
        .into_iter()
        .map(|n| pb::RenderedTemplateNode {
            value: Some(rendered_node_to_proto(n)),
        })
        .collect()
}

fn rendered_node_to_proto(node: RenderedNode) -> pb::rendered_template_node::Value {
    match node {
        RenderedNode::Text { text } => pb::rendered_template_node::Value::Text(text),
        RenderedNode::Replacement {
            field_name,
            current_text,
            filters,
        } => pb::rendered_template_node::Value::Replacement(RenderedTemplateReplacement {
            field_name,
            current_text,
            filters,
        }),
    }
}

impl From<RenderCardOutput> for pb::RenderCardOut {
    fn from(o: RenderCardOutput) -> Self {
        pb::RenderCardOut {
            question_nodes: rendered_nodes_to_proto(o.qnodes),
            answer_nodes: rendered_nodes_to_proto(o.anodes),
        }
    }
}

fn progress_to_proto_bytes(progress: Progress, i18n: &I18n) -> Vec<u8> {
    let proto = pb::Progress {
        value: Some(match progress {
            Progress::MediaSync(p) => pb::progress::Value::MediaSync(media_sync_progress(p, i18n)),
            Progress::MediaCheck(n) => {
                let s = i18n.trn(TR::MediaCheckChecked, tr_args!["count"=>n]);
                pb::progress::Value::MediaCheck(s)
            }
        }),
    };

    let mut buf = vec![];
    proto.encode(&mut buf).expect("encode failed");
    buf
}

fn media_sync_progress(p: &MediaSyncProgress, i18n: &I18n) -> pb::MediaSyncProgress {
    pb::MediaSyncProgress {
        checked: i18n.trn(TR::SyncMediaCheckedCount, tr_args!["count"=>p.checked]),
        added: i18n.trn(
            TR::SyncMediaAddedCount,
            tr_args!["up"=>p.uploaded_files,"down"=>p.downloaded_files],
        ),
        removed: i18n.trn(
            TR::SyncMediaRemovedCount,
            tr_args!["up"=>p.uploaded_deletions,"down"=>p.downloaded_deletions],
        ),
    }
}

fn sort_kind_from_pb(kind: i32) -> SortKind {
    use SortKind as SK;
    match BuiltinSortKind::from_i32(kind) {
        Some(pbkind) => match pbkind {
            BuiltinSortKind::NoteCreation => SK::NoteCreation,
            BuiltinSortKind::NoteMod => SK::NoteMod,
            BuiltinSortKind::NoteField => SK::NoteField,
            BuiltinSortKind::NoteTags => SK::NoteTags,
            BuiltinSortKind::NoteType => SK::NoteType,
            BuiltinSortKind::CardMod => SK::CardMod,
            BuiltinSortKind::CardReps => SK::CardReps,
            BuiltinSortKind::CardDue => SK::CardDue,
            BuiltinSortKind::CardEase => SK::CardEase,
            BuiltinSortKind::CardLapses => SK::CardLapses,
            BuiltinSortKind::CardInterval => SK::CardInterval,
            BuiltinSortKind::CardDeck => SK::CardDeck,
            BuiltinSortKind::CardTemplate => SK::CardTemplate,
        },
        _ => SortKind::NoteCreation,
    }
}

fn card_to_pb(c: Card) -> pb::Card {
    pb::Card {
        id: c.id.0,
        nid: c.nid.0,
        did: c.did.0,
        ord: c.ord as u32,
        mtime: c.mtime.0,
        usn: c.usn.0,
        ctype: c.ctype as u32,
        queue: c.queue as i32,
        due: c.due,
        ivl: c.ivl,
        factor: c.factor as u32,
        reps: c.reps,
        lapses: c.lapses,
        left: c.left,
        odue: c.odue,
        odid: c.odid.0,
        flags: c.flags as u32,
        data: c.data,
    }
}

fn pbcard_to_native(c: pb::Card) -> Result<Card> {
    let ctype = CardType::try_from(c.ctype as u8)
        .map_err(|_| AnkiError::invalid_input("invalid card type"))?;
    let queue = CardQueue::try_from(c.queue as i8)
        .map_err(|_| AnkiError::invalid_input("invalid card queue"))?;
    Ok(Card {
        id: CardID(c.id),
        nid: NoteID(c.nid),
        did: DeckID(c.did),
        ord: c.ord as u16,
        mtime: TimestampSecs(c.mtime),
        usn: Usn(c.usn),
        ctype,
        queue,
        due: c.due,
        ivl: c.ivl,
        factor: c.factor as u16,
        reps: c.reps,
        lapses: c.lapses,
        left: c.left,
        odue: c.odue,
        odid: DeckID(c.odid),
        flags: c.flags as u8,
        data: c.data,
    })
}

impl From<crate::sched::cutoff::SchedTimingToday> for pb::SchedTimingTodayOut {
    fn from(t: crate::sched::cutoff::SchedTimingToday) -> pb::SchedTimingTodayOut {
        pb::SchedTimingTodayOut {
            days_elapsed: t.days_elapsed,
            next_day_at: t.next_day_at,
        }
    }
}
