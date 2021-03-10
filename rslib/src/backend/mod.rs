// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod adding;
mod card;
mod config;
mod dbproxy;
mod err;
mod generic;
mod http_sync_server;
mod progress;
mod scheduler;
mod search;
mod sync;

pub use crate::backend_proto::BackendMethod;
use crate::{
    backend::dbproxy::db_command_bytes,
    backend_proto as pb,
    backend_proto::{
        AddOrUpdateDeckConfigLegacyIn, BackendResult, Empty, RenderedTemplateReplacement,
    },
    card::{Card, CardID},
    cloze::add_cloze_numbers_in_string,
    collection::{open_collection, Collection},
    deckconf::{DeckConf, DeckConfSchema11},
    decks::{Deck, DeckID, DeckSchema11},
    err::{AnkiError, Result},
    i18n::I18n,
    latex::{extract_latex, extract_latex_expanding_clozes, ExtractedLatex},
    log,
    log::default_logger,
    markdown::render_markdown,
    media::check::MediaChecker,
    media::MediaManager,
    notes::{Note, NoteID},
    notetype::{
        all_stock_notetypes, CardTemplateSchema11, NoteType, NoteTypeSchema11, RenderCardOutput,
    },
    scheduler::{
        new::NewCardSortOrder,
        parse_due_date_str,
        states::{CardState, NextCardStates},
        timespan::{answer_button_time, time_span},
    },
    search::{concatenate_searches, replace_search_node, write_nodes, Node},
    stats::studied_today,
    sync::{http::SyncRequest, LocalServer},
    template::RenderedNode,
    text::{extract_av_tags, sanitize_html_no_images, strip_av_tags, AVTag},
    timestamp::TimestampSecs,
    undo::UndoableOpKind,
};
use fluent::FluentValue;
use futures::future::AbortHandle;
use log::error;
use once_cell::sync::OnceCell;
use pb::BackendService;
use progress::{AbortHandleSlot, Progress};
use prost::Message;
use serde_json::Value as JsonValue;
use std::{collections::HashSet, convert::TryInto};
use std::{
    result,
    sync::{Arc, Mutex},
};
use tokio::runtime::{self, Runtime};

use self::{
    err::anki_error_to_proto_error,
    progress::{progress_to_proto, ProgressState},
    sync::RemoteSyncStatus,
};

pub struct Backend {
    col: Arc<Mutex<Option<Collection>>>,
    i18n: I18n,
    server: bool,
    sync_abort: AbortHandleSlot,
    progress_state: Arc<Mutex<ProgressState>>,
    runtime: OnceCell<Runtime>,
    state: Arc<Mutex<BackendState>>,
}

// fixme: move other items like runtime into here as well

#[derive(Default)]
struct BackendState {
    remote_sync_status: RemoteSyncStatus,
    media_sync_abort: Option<AbortHandle>,
    http_sync_server: Option<LocalServer>,
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

impl BackendService for Backend {
    fn latest_progress(&self, _input: Empty) -> BackendResult<pb::Progress> {
        let progress = self.progress_state.lock().unwrap().last_progress;
        Ok(progress_to_proto(progress, &self.i18n))
    }

    fn set_wants_abort(&self, _input: Empty) -> BackendResult<Empty> {
        self.progress_state.lock().unwrap().want_abort = true;
        Ok(().into())
    }

    // card rendering

    fn extract_av_tags(&self, input: pb::ExtractAvTagsIn) -> BackendResult<pb::ExtractAvTagsOut> {
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

    fn extract_latex(&self, input: pb::ExtractLatexIn) -> BackendResult<pb::ExtractLatexOut> {
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

    fn get_empty_cards(&self, _input: pb::Empty) -> Result<pb::EmptyCardsReport> {
        self.with_col(|col| {
            let mut empty = col.empty_cards()?;
            let report = col.empty_cards_report(&mut empty)?;

            let mut outnotes = vec![];
            for (_ntid, notes) in empty {
                outnotes.extend(notes.into_iter().map(|e| {
                    pb::empty_cards_report::NoteWithEmptyCards {
                        note_id: e.nid.0,
                        will_delete_note: e.empty.len() == e.current_count,
                        card_ids: e.empty.into_iter().map(|(_ord, id)| id.0).collect(),
                    }
                }))
            }
            Ok(pb::EmptyCardsReport {
                report,
                notes: outnotes,
            })
        })
    }

    fn render_existing_card(
        &self,
        input: pb::RenderExistingCardIn,
    ) -> BackendResult<pb::RenderCardOut> {
        self.with_col(|col| {
            col.render_existing_card(CardID(input.card_id), input.browser)
                .map(Into::into)
        })
    }

    fn render_uncommitted_card(
        &self,
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

    fn strip_av_tags(&self, input: pb::String) -> BackendResult<pb::String> {
        Ok(pb::String {
            val: strip_av_tags(&input.val).into(),
        })
    }

    // searching
    //-----------------------------------------------

    fn build_search_string(&self, input: pb::SearchNode) -> Result<pb::String> {
        let node: Node = input.try_into()?;
        Ok(write_nodes(&node.into_node_list()).into())
    }

    fn search_cards(&self, input: pb::SearchCardsIn) -> Result<pb::SearchCardsOut> {
        self.with_col(|col| {
            let order = input.order.unwrap_or_default().value.into();
            let cids = col.search_cards(&input.search, order)?;
            Ok(pb::SearchCardsOut {
                card_ids: cids.into_iter().map(|v| v.0).collect(),
            })
        })
    }

    fn search_notes(&self, input: pb::SearchNotesIn) -> Result<pb::SearchNotesOut> {
        self.with_col(|col| {
            let nids = col.search_notes(&input.search)?;
            Ok(pb::SearchNotesOut {
                note_ids: nids.into_iter().map(|v| v.0).collect(),
            })
        })
    }

    fn join_search_nodes(&self, input: pb::JoinSearchNodesIn) -> Result<pb::String> {
        let sep = input.joiner().into();
        let existing_nodes = {
            let node: Node = input.existing_node.unwrap_or_default().try_into()?;
            node.into_node_list()
        };
        let additional_node = input.additional_node.unwrap_or_default().try_into()?;
        Ok(concatenate_searches(sep, existing_nodes, additional_node).into())
    }

    fn replace_search_node(&self, input: pb::ReplaceSearchNodeIn) -> Result<pb::String> {
        let existing = {
            let node = input.existing_node.unwrap_or_default().try_into()?;
            if let Node::Group(nodes) = node {
                nodes
            } else {
                vec![node]
            }
        };
        let replacement = input.replacement_node.unwrap_or_default().try_into()?;
        Ok(replace_search_node(existing, replacement).into())
    }

    fn find_and_replace(&self, input: pb::FindAndReplaceIn) -> BackendResult<pb::UInt32> {
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

    /// This behaves like _updateCutoff() in older code - it also unburies at the start of
    /// a new day.
    fn sched_timing_today(&self, _input: pb::Empty) -> Result<pb::SchedTimingTodayOut> {
        self.with_col(|col| {
            let timing = col.timing_today()?;
            col.unbury_if_day_rolled_over(timing)?;
            Ok(timing.into())
        })
    }

    /// Fetch data from DB and return rendered string.
    fn studied_today(&self, _input: pb::Empty) -> BackendResult<pb::String> {
        self.with_col(|col| col.studied_today().map(Into::into))
    }

    /// Message rendering only, for old graphs.
    fn studied_today_message(&self, input: pb::StudiedTodayMessageIn) -> BackendResult<pb::String> {
        Ok(studied_today(input.cards, input.seconds as f32, &self.i18n).into())
    }

    fn update_stats(&self, input: pb::UpdateStatsIn) -> BackendResult<Empty> {
        self.with_col(|col| {
            col.transact(None, |col| {
                let today = col.current_due_day(0)?;
                let usn = col.usn()?;
                col.update_deck_stats(today, usn, input).map(Into::into)
            })
        })
    }

    fn extend_limits(&self, input: pb::ExtendLimitsIn) -> BackendResult<Empty> {
        self.with_col(|col| {
            col.transact(None, |col| {
                let today = col.current_due_day(0)?;
                let usn = col.usn()?;
                col.extend_limits(
                    today,
                    usn,
                    input.deck_id.into(),
                    input.new_delta,
                    input.review_delta,
                )
                .map(Into::into)
            })
        })
    }

    fn counts_for_deck_today(&self, input: pb::DeckId) -> BackendResult<pb::CountsForDeckTodayOut> {
        self.with_col(|col| col.counts_for_deck_today(input.did.into()))
    }

    fn congrats_info(&self, _input: Empty) -> BackendResult<pb::CongratsInfoOut> {
        self.with_col(|col| col.congrats_info())
    }

    fn restore_buried_and_suspended_cards(&self, input: pb::CardIDs) -> BackendResult<Empty> {
        let cids: Vec<_> = input.into();
        self.with_col(|col| col.unbury_or_unsuspend_cards(&cids).map(Into::into))
    }

    fn unbury_cards_in_current_deck(
        &self,
        input: pb::UnburyCardsInCurrentDeckIn,
    ) -> BackendResult<Empty> {
        self.with_col(|col| {
            col.unbury_cards_in_current_deck(input.mode())
                .map(Into::into)
        })
    }

    fn bury_or_suspend_cards(&self, input: pb::BuryOrSuspendCardsIn) -> BackendResult<Empty> {
        self.with_col(|col| {
            let mode = input.mode();
            let cids: Vec<_> = input.card_ids.into_iter().map(CardID).collect();
            col.bury_or_suspend_cards(&cids, mode).map(Into::into)
        })
    }

    fn empty_filtered_deck(&self, input: pb::DeckId) -> BackendResult<Empty> {
        self.with_col(|col| col.empty_filtered_deck(input.did.into()).map(Into::into))
    }

    fn rebuild_filtered_deck(&self, input: pb::DeckId) -> BackendResult<pb::UInt32> {
        self.with_col(|col| col.rebuild_filtered_deck(input.did.into()).map(Into::into))
    }

    fn schedule_cards_as_new(&self, input: pb::ScheduleCardsAsNewIn) -> BackendResult<Empty> {
        self.with_col(|col| {
            let cids: Vec<_> = input.card_ids.into_iter().map(CardID).collect();
            let log = input.log;
            col.reschedule_cards_as_new(&cids, log).map(Into::into)
        })
    }

    fn set_due_date(&self, input: pb::SetDueDateIn) -> BackendResult<pb::Empty> {
        let cids: Vec<_> = input.card_ids.into_iter().map(CardID).collect();
        let spec = parse_due_date_str(&input.days)?;
        self.with_col(|col| col.set_due_date(&cids, spec).map(Into::into))
    }

    fn sort_cards(&self, input: pb::SortCardsIn) -> BackendResult<Empty> {
        let cids: Vec<_> = input.card_ids.into_iter().map(CardID).collect();
        let (start, step, random, shift) = (
            input.starting_from,
            input.step_size,
            input.randomize,
            input.shift_existing,
        );
        let order = if random {
            NewCardSortOrder::Random
        } else {
            NewCardSortOrder::Preserve
        };
        self.with_col(|col| {
            col.sort_cards(&cids, start, step, order, shift)
                .map(Into::into)
        })
    }

    fn sort_deck(&self, input: pb::SortDeckIn) -> BackendResult<Empty> {
        self.with_col(|col| {
            col.sort_deck(input.deck_id.into(), input.randomize)
                .map(Into::into)
        })
    }

    fn get_next_card_states(&self, input: pb::CardId) -> BackendResult<pb::NextCardStates> {
        let cid: CardID = input.into();
        self.with_col(|col| col.get_next_card_states(cid))
            .map(Into::into)
    }

    fn describe_next_states(&self, input: pb::NextCardStates) -> BackendResult<pb::StringList> {
        let states: NextCardStates = input.into();
        self.with_col(|col| col.describe_next_states(states))
            .map(Into::into)
    }

    fn state_is_leech(&self, input: pb::SchedulingState) -> BackendResult<pb::Bool> {
        let state: CardState = input.into();
        Ok(state.leeched().into())
    }

    fn answer_card(&self, input: pb::AnswerCardIn) -> BackendResult<pb::Empty> {
        self.with_col(|col| col.answer_card(&input.into()))
            .map(Into::into)
    }

    fn upgrade_scheduler(&self, _input: Empty) -> BackendResult<Empty> {
        self.with_col(|col| col.transact(None, |col| col.upgrade_to_v2_scheduler()))
            .map(Into::into)
    }

    fn get_queued_cards(
        &self,
        input: pb::GetQueuedCardsIn,
    ) -> BackendResult<pb::GetQueuedCardsOut> {
        self.with_col(|col| col.get_queued_cards(input.fetch_limit, input.intraday_learning_only))
    }

    // statistics
    //-----------------------------------------------

    fn card_stats(&self, input: pb::CardId) -> BackendResult<pb::String> {
        self.with_col(|col| col.card_stats(input.into()))
            .map(Into::into)
    }

    fn graphs(&self, input: pb::GraphsIn) -> BackendResult<pb::GraphsOut> {
        self.with_col(|col| col.graph_data_for_search(&input.search, input.days))
    }

    fn get_graph_preferences(&self, _input: pb::Empty) -> BackendResult<pb::GraphPreferences> {
        self.with_col(|col| col.get_graph_preferences())
    }

    fn set_graph_preferences(&self, input: pb::GraphPreferences) -> BackendResult<Empty> {
        self.with_col(|col| col.set_graph_preferences(input))
            .map(Into::into)
    }

    // media
    //-----------------------------------------------

    fn check_media(&self, _input: pb::Empty) -> Result<pb::CheckMediaOut> {
        let mut handler = self.new_progress_handler();
        let progress_fn =
            move |progress| handler.update(Progress::MediaCheck(progress as u32), true);
        self.with_col(|col| {
            let mgr = MediaManager::new(&col.media_folder, &col.media_db)?;
            col.transact(None, |ctx| {
                let mut checker = MediaChecker::new(ctx, &mgr, progress_fn);
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

    fn trash_media_files(&self, input: pb::TrashMediaFilesIn) -> BackendResult<Empty> {
        self.with_col(|col| {
            let mgr = MediaManager::new(&col.media_folder, &col.media_db)?;
            let mut ctx = mgr.dbctx();
            mgr.remove_files(&mut ctx, &input.fnames)
        })
        .map(Into::into)
    }

    fn add_media_file(&self, input: pb::AddMediaFileIn) -> BackendResult<pb::String> {
        self.with_col(|col| {
            let mgr = MediaManager::new(&col.media_folder, &col.media_db)?;
            let mut ctx = mgr.dbctx();
            Ok(mgr
                .add_file(&mut ctx, &input.desired_name, &input.data)?
                .to_string()
                .into())
        })
    }

    fn empty_trash(&self, _input: Empty) -> BackendResult<Empty> {
        let mut handler = self.new_progress_handler();
        let progress_fn =
            move |progress| handler.update(Progress::MediaCheck(progress as u32), true);

        self.with_col(|col| {
            let mgr = MediaManager::new(&col.media_folder, &col.media_db)?;
            col.transact(None, |ctx| {
                let mut checker = MediaChecker::new(ctx, &mgr, progress_fn);

                checker.empty_trash()
            })
        })
        .map(Into::into)
    }

    fn restore_trash(&self, _input: Empty) -> BackendResult<Empty> {
        let mut handler = self.new_progress_handler();
        let progress_fn =
            move |progress| handler.update(Progress::MediaCheck(progress as u32), true);
        self.with_col(|col| {
            let mgr = MediaManager::new(&col.media_folder, &col.media_db)?;

            col.transact(None, |ctx| {
                let mut checker = MediaChecker::new(ctx, &mgr, progress_fn);

                checker.restore_trash()
            })
        })
        .map(Into::into)
    }

    // decks
    //----------------------------------------------------

    fn add_or_update_deck_legacy(&self, input: pb::AddOrUpdateDeckLegacyIn) -> Result<pb::DeckId> {
        self.with_col(|col| {
            let schema11: DeckSchema11 = serde_json::from_slice(&input.deck)?;
            let mut deck: Deck = schema11.into();
            if input.preserve_usn_and_mtime {
                col.transact(None, |col| {
                    let usn = col.usn()?;
                    col.add_or_update_single_deck_with_existing_id(&mut deck, usn)
                })?;
            } else {
                col.add_or_update_deck(&mut deck)?;
            }
            Ok(pb::DeckId { did: deck.id.0 })
        })
    }

    fn deck_tree(&self, input: pb::DeckTreeIn) -> Result<pb::DeckTreeNode> {
        let lim = if input.top_deck_id > 0 {
            Some(DeckID(input.top_deck_id))
        } else {
            None
        };
        self.with_col(|col| {
            let now = if input.now == 0 {
                None
            } else {
                Some(TimestampSecs(input.now))
            };
            col.deck_tree(now, lim)
        })
    }

    fn deck_tree_legacy(&self, _input: pb::Empty) -> BackendResult<pb::Json> {
        self.with_col(|col| {
            let tree = col.legacy_deck_tree()?;
            serde_json::to_vec(&tree)
                .map_err(Into::into)
                .map(Into::into)
        })
    }

    fn get_all_decks_legacy(&self, _input: Empty) -> BackendResult<pb::Json> {
        self.with_col(|col| {
            let decks = col.storage.get_all_decks_as_schema11()?;
            serde_json::to_vec(&decks).map_err(Into::into)
        })
        .map(Into::into)
    }

    fn get_deck_id_by_name(&self, input: pb::String) -> Result<pb::DeckId> {
        self.with_col(|col| {
            col.get_deck_id(&input.val).and_then(|d| {
                d.ok_or(AnkiError::NotFound)
                    .map(|d| pb::DeckId { did: d.0 })
            })
        })
    }

    fn get_deck_legacy(&self, input: pb::DeckId) -> Result<pb::Json> {
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

    fn get_deck_names(&self, input: pb::GetDeckNamesIn) -> Result<pb::DeckNames> {
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

    fn new_deck_legacy(&self, input: pb::Bool) -> BackendResult<pb::Json> {
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

    fn remove_deck(&self, input: pb::DeckId) -> BackendResult<Empty> {
        self.with_col(|col| col.remove_deck_and_child_decks(input.into()))
            .map(Into::into)
    }

    fn drag_drop_decks(&self, input: pb::DragDropDecksIn) -> BackendResult<Empty> {
        let source_dids: Vec<_> = input.source_deck_ids.into_iter().map(Into::into).collect();
        let target_did = if input.target_deck_id == 0 {
            None
        } else {
            Some(input.target_deck_id.into())
        };
        self.with_col(|col| col.drag_drop_decks(&source_dids, target_did))
            .map(Into::into)
    }

    // deck config
    //----------------------------------------------------

    fn add_or_update_deck_config_legacy(
        &self,
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

    fn all_deck_config_legacy(&self, _input: Empty) -> BackendResult<pb::Json> {
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

    fn get_deck_config_legacy(&self, input: pb::DeckConfigId) -> BackendResult<pb::Json> {
        self.with_col(|col| {
            let conf = col.get_deck_config(input.into(), true)?.unwrap();
            let conf: DeckConfSchema11 = conf.into();
            Ok(serde_json::to_vec(&conf)?)
        })
        .map(Into::into)
    }

    fn new_deck_config_legacy(&self, _input: Empty) -> BackendResult<pb::Json> {
        serde_json::to_vec(&DeckConfSchema11::default())
            .map_err(Into::into)
            .map(Into::into)
    }

    fn remove_deck_config(&self, input: pb::DeckConfigId) -> BackendResult<Empty> {
        self.with_col(|col| col.transact(None, |col| col.remove_deck_config(input.into())))
            .map(Into::into)
    }

    // cards
    //-------------------------------------------------------------------

    fn get_card(&self, input: pb::CardId) -> BackendResult<pb::Card> {
        self.with_col(|col| {
            col.storage
                .get_card(input.into())
                .and_then(|opt| opt.ok_or(AnkiError::NotFound))
                .map(Into::into)
        })
    }

    fn update_card(&self, input: pb::UpdateCardIn) -> BackendResult<Empty> {
        self.with_col(|col| {
            let op = if input.skip_undo_entry {
                None
            } else {
                Some(UndoableOpKind::UpdateCard)
            };
            let mut card: Card = input.card.ok_or(AnkiError::NotFound)?.try_into()?;
            col.update_card_with_op(&mut card, op)
        })
        .map(Into::into)
    }

    fn remove_cards(&self, input: pb::RemoveCardsIn) -> BackendResult<Empty> {
        self.with_col(|col| {
            col.transact(None, |col| {
                col.remove_cards_and_orphaned_notes(
                    &input
                        .card_ids
                        .into_iter()
                        .map(Into::into)
                        .collect::<Vec<_>>(),
                )?;
                Ok(().into())
            })
        })
    }

    fn set_deck(&self, input: pb::SetDeckIn) -> BackendResult<Empty> {
        let cids: Vec<_> = input.card_ids.into_iter().map(CardID).collect();
        let deck_id = input.deck_id.into();
        self.with_col(|col| col.set_deck(&cids, deck_id).map(Into::into))
    }

    // notes
    //-------------------------------------------------------------------

    fn new_note(&self, input: pb::NoteTypeId) -> BackendResult<pb::Note> {
        self.with_col(|col| {
            let nt = col.get_notetype(input.into())?.ok_or(AnkiError::NotFound)?;
            Ok(nt.new_note().into())
        })
    }

    fn add_note(&self, input: pb::AddNoteIn) -> BackendResult<pb::NoteId> {
        self.with_col(|col| {
            let mut note: Note = input.note.ok_or(AnkiError::NotFound)?.into();
            col.add_note(&mut note, DeckID(input.deck_id))
                .map(|_| pb::NoteId { nid: note.id.0 })
        })
    }

    fn defaults_for_adding(
        &self,
        input: pb::DefaultsForAddingIn,
    ) -> BackendResult<pb::DeckAndNotetype> {
        self.with_col(|col| {
            let home_deck: DeckID = input.home_deck_of_current_review_card.into();
            col.defaults_for_adding(home_deck).map(Into::into)
        })
    }

    fn default_deck_for_notetype(&self, input: pb::NoteTypeId) -> BackendResult<pb::DeckId> {
        self.with_col(|col| {
            Ok(col
                .default_deck_for_notetype(input.into())?
                .unwrap_or(DeckID(0))
                .into())
        })
    }

    fn update_note(&self, input: pb::UpdateNoteIn) -> BackendResult<Empty> {
        self.with_col(|col| {
            let op = if input.skip_undo_entry {
                None
            } else {
                Some(UndoableOpKind::UpdateNote)
            };
            let mut note: Note = input.note.ok_or(AnkiError::NotFound)?.into();
            col.update_note_with_op(&mut note, op)
        })
        .map(Into::into)
    }

    fn get_note(&self, input: pb::NoteId) -> BackendResult<pb::Note> {
        self.with_col(|col| {
            col.storage
                .get_note(input.into())?
                .ok_or(AnkiError::NotFound)
                .map(Into::into)
        })
    }

    fn remove_notes(&self, input: pb::RemoveNotesIn) -> BackendResult<Empty> {
        self.with_col(|col| {
            if !input.note_ids.is_empty() {
                col.remove_notes(
                    &input
                        .note_ids
                        .into_iter()
                        .map(Into::into)
                        .collect::<Vec<_>>(),
                )?;
            }
            if !input.card_ids.is_empty() {
                let nids = col.storage.note_ids_of_cards(
                    &input
                        .card_ids
                        .into_iter()
                        .map(Into::into)
                        .collect::<Vec<_>>(),
                )?;
                col.remove_notes(&nids.into_iter().collect::<Vec<_>>())?
            }
            Ok(().into())
        })
    }

    fn add_note_tags(&self, input: pb::AddNoteTagsIn) -> BackendResult<pb::UInt32> {
        self.with_col(|col| {
            col.add_tags_to_notes(&to_nids(input.nids), &input.tags)
                .map(|n| n as u32)
        })
        .map(Into::into)
    }

    fn update_note_tags(&self, input: pb::UpdateNoteTagsIn) -> BackendResult<pb::UInt32> {
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

    fn cloze_numbers_in_note(&self, note: pb::Note) -> BackendResult<pb::ClozeNumbersInNoteOut> {
        let mut set = HashSet::with_capacity(4);
        for field in &note.fields {
            add_cloze_numbers_in_string(field, &mut set);
        }
        Ok(pb::ClozeNumbersInNoteOut {
            numbers: set.into_iter().map(|n| n as u32).collect(),
        })
    }

    fn after_note_updates(&self, input: pb::AfterNoteUpdatesIn) -> BackendResult<Empty> {
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

    fn field_names_for_notes(
        &self,
        input: pb::FieldNamesForNotesIn,
    ) -> BackendResult<pb::FieldNamesForNotesOut> {
        self.with_col(|col| {
            let nids: Vec<_> = input.nids.into_iter().map(NoteID).collect();
            col.storage
                .field_names_for_notes(&nids)
                .map(|fields| pb::FieldNamesForNotesOut { fields })
        })
    }

    fn note_is_duplicate_or_empty(
        &self,
        input: pb::Note,
    ) -> BackendResult<pb::NoteIsDuplicateOrEmptyOut> {
        let note: Note = input.into();
        self.with_col(|col| {
            col.note_is_duplicate_or_empty(&note)
                .map(|r| pb::NoteIsDuplicateOrEmptyOut { state: r as i32 })
        })
    }

    fn cards_of_note(&self, input: pb::NoteId) -> BackendResult<pb::CardIDs> {
        self.with_col(|col| {
            col.storage
                .all_card_ids_of_note(NoteID(input.nid))
                .map(|v| pb::CardIDs {
                    cids: v.into_iter().map(Into::into).collect(),
                })
        })
    }

    // notetypes
    //-------------------------------------------------------------------

    fn add_or_update_notetype(
        &self,
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

    fn get_stock_notetype_legacy(&self, input: pb::StockNoteType) -> BackendResult<pb::Json> {
        // fixme: use individual functions instead of full vec
        let mut all = all_stock_notetypes(&self.i18n);
        let idx = (input.kind as usize).min(all.len() - 1);
        let nt = all.swap_remove(idx);
        let schema11: NoteTypeSchema11 = nt.into();
        serde_json::to_vec(&schema11)
            .map_err(Into::into)
            .map(Into::into)
    }

    fn get_notetype_legacy(&self, input: pb::NoteTypeId) -> BackendResult<pb::Json> {
        self.with_col(|col| {
            let schema11: NoteTypeSchema11 = col
                .storage
                .get_notetype(input.into())?
                .ok_or(AnkiError::NotFound)?
                .into();
            Ok(serde_json::to_vec(&schema11)?).map(Into::into)
        })
    }

    fn get_notetype_names(&self, _input: Empty) -> BackendResult<pb::NoteTypeNames> {
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

    fn get_notetype_names_and_counts(&self, _input: Empty) -> BackendResult<pb::NoteTypeUseCounts> {
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

    fn get_notetype_id_by_name(&self, input: pb::String) -> BackendResult<pb::NoteTypeId> {
        self.with_col(|col| {
            col.storage
                .get_notetype_id(&input.val)
                .and_then(|nt| nt.ok_or(AnkiError::NotFound))
                .map(|ntid| pb::NoteTypeId { ntid: ntid.0 })
        })
    }

    fn remove_notetype(&self, input: pb::NoteTypeId) -> BackendResult<Empty> {
        self.with_col(|col| col.remove_notetype(input.into()))
            .map(Into::into)
    }

    // collection
    //-------------------------------------------------------------------

    fn open_collection(&self, input: pb::OpenCollectionIn) -> BackendResult<Empty> {
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

    fn close_collection(&self, input: pb::CloseCollectionIn) -> BackendResult<Empty> {
        self.abort_media_sync_and_wait();

        let mut col = self.col.lock().unwrap();
        if col.is_none() {
            return Err(AnkiError::CollectionNotOpen);
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

    fn check_database(&self, _input: pb::Empty) -> BackendResult<pb::CheckDatabaseOut> {
        let mut handler = self.new_progress_handler();
        let progress_fn = move |progress, throttle| {
            handler.update(Progress::DatabaseCheck(progress), throttle);
        };
        self.with_col(|col| {
            col.check_database(progress_fn)
                .map(|problems| pb::CheckDatabaseOut {
                    problems: problems.to_i18n_strings(&col.i18n),
                })
        })
    }

    fn get_undo_status(&self, _input: pb::Empty) -> Result<pb::UndoStatus> {
        self.with_col(|col| Ok(col.undo_status()))
    }

    fn undo(&self, _input: pb::Empty) -> Result<pb::UndoStatus> {
        self.with_col(|col| {
            col.undo()?;
            Ok(col.undo_status())
        })
    }

    fn redo(&self, _input: pb::Empty) -> Result<pb::UndoStatus> {
        self.with_col(|col| {
            col.redo()?;
            Ok(col.undo_status())
        })
    }

    // sync
    //-------------------------------------------------------------------

    fn sync_media(&self, input: pb::SyncAuth) -> BackendResult<Empty> {
        self.sync_media_inner(input).map(Into::into)
    }

    fn abort_sync(&self, _input: Empty) -> BackendResult<Empty> {
        if let Some(handle) = self.sync_abort.lock().unwrap().take() {
            handle.abort();
        }
        Ok(().into())
    }

    /// Abort the media sync. Does not wait for completion.
    fn abort_media_sync(&self, _input: Empty) -> BackendResult<Empty> {
        let guard = self.state.lock().unwrap();
        if let Some(handle) = &guard.media_sync_abort {
            handle.abort();
        }
        Ok(().into())
    }

    fn before_upload(&self, _input: Empty) -> BackendResult<Empty> {
        self.with_col(|col| col.before_upload().map(Into::into))
    }

    fn sync_login(&self, input: pb::SyncLoginIn) -> BackendResult<pb::SyncAuth> {
        self.sync_login_inner(input)
    }

    fn sync_status(&self, input: pb::SyncAuth) -> BackendResult<pb::SyncStatusOut> {
        self.sync_status_inner(input)
    }

    fn sync_collection(&self, input: pb::SyncAuth) -> BackendResult<pb::SyncCollectionOut> {
        self.sync_collection_inner(input)
    }

    fn full_upload(&self, input: pb::SyncAuth) -> BackendResult<Empty> {
        self.full_sync_inner(input, true)?;
        Ok(().into())
    }

    fn full_download(&self, input: pb::SyncAuth) -> BackendResult<Empty> {
        self.full_sync_inner(input, false)?;
        Ok(().into())
    }

    fn sync_server_method(&self, input: pb::SyncServerMethodIn) -> BackendResult<pb::Json> {
        let req = SyncRequest::from_method_and_data(input.method(), input.data)?;
        self.sync_server_method_inner(req).map(Into::into)
    }

    // i18n/messages
    //-------------------------------------------------------------------

    fn translate_string(&self, input: pb::TranslateStringIn) -> BackendResult<pb::String> {
        let key = match crate::fluent_proto::FluentString::from_i32(input.key) {
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

    fn format_timespan(&self, input: pb::FormatTimespanIn) -> BackendResult<pb::String> {
        use pb::format_timespan_in::Context;
        Ok(match input.context() {
            Context::Precise => time_span(input.seconds, &self.i18n, true),
            Context::Intervals => time_span(input.seconds, &self.i18n, false),
            Context::AnswerButtons => answer_button_time(input.seconds, &self.i18n),
        }
        .into())
    }

    fn i18n_resources(&self, _input: Empty) -> BackendResult<pb::Json> {
        serde_json::to_vec(&self.i18n.resources_for_js())
            .map(Into::into)
            .map_err(Into::into)
    }

    fn render_markdown(&self, input: pb::RenderMarkdownIn) -> BackendResult<pb::String> {
        let mut text = render_markdown(&input.markdown);
        if input.sanitize {
            // currently no images
            text = sanitize_html_no_images(&text);
        }
        Ok(text.into())
    }

    // tags
    //-------------------------------------------------------------------

    fn clear_unused_tags(&self, _input: pb::Empty) -> BackendResult<pb::Empty> {
        self.with_col(|col| col.transact(None, |col| col.clear_unused_tags().map(Into::into)))
    }

    fn all_tags(&self, _input: Empty) -> BackendResult<pb::StringList> {
        Ok(pb::StringList {
            vals: self.with_col(|col| {
                Ok(col
                    .storage
                    .all_tags()?
                    .into_iter()
                    .map(|t| t.name)
                    .collect())
            })?,
        })
    }

    fn set_tag_expanded(&self, input: pb::SetTagExpandedIn) -> BackendResult<pb::Empty> {
        self.with_col(|col| {
            col.transact(None, |col| {
                col.set_tag_expanded(&input.name, input.expanded)?;
                Ok(().into())
            })
        })
    }

    fn clear_tag(&self, tag: pb::String) -> BackendResult<pb::Empty> {
        self.with_col(|col| {
            col.transact(None, |col| {
                col.storage.clear_tag_and_children(tag.val.as_str())?;
                Ok(().into())
            })
        })
    }

    fn tag_tree(&self, _input: Empty) -> Result<pb::TagTreeNode> {
        self.with_col(|col| col.tag_tree())
    }

    fn drag_drop_tags(&self, input: pb::DragDropTagsIn) -> BackendResult<Empty> {
        let source_tags = input.source_tags;
        let target_tag = if input.target_tag.is_empty() {
            None
        } else {
            Some(input.target_tag)
        };
        self.with_col(|col| col.drag_drop_tags(&source_tags, target_tag))
            .map(Into::into)
    }

    // config/preferences
    //-------------------------------------------------------------------

    fn get_config_json(&self, input: pb::String) -> BackendResult<pb::Json> {
        self.with_col(|col| {
            let val: Option<JsonValue> = col.get_config_optional(input.val.as_str());
            val.ok_or(AnkiError::NotFound)
                .and_then(|v| serde_json::to_vec(&v).map_err(Into::into))
                .map(Into::into)
        })
    }

    fn set_config_json(&self, input: pb::SetConfigJsonIn) -> BackendResult<Empty> {
        self.with_col(|col| {
            col.transact(None, |col| {
                // ensure it's a well-formed object
                let val: JsonValue = serde_json::from_slice(&input.value_json)?;
                col.set_config(input.key.as_str(), &val)
            })
        })
        .map(Into::into)
    }

    fn remove_config(&self, input: pb::String) -> BackendResult<Empty> {
        self.with_col(|col| col.transact(None, |col| col.remove_config(input.val.as_str())))
            .map(Into::into)
    }

    fn get_all_config(&self, _input: Empty) -> BackendResult<pb::Json> {
        self.with_col(|col| {
            let conf = col.storage.get_all_config()?;
            serde_json::to_vec(&conf).map_err(Into::into)
        })
        .map(Into::into)
    }

    fn get_config_bool(&self, input: pb::config::Bool) -> BackendResult<pb::Bool> {
        self.with_col(|col| {
            Ok(pb::Bool {
                val: col.get_bool(input.key().into()),
            })
        })
    }

    fn set_config_bool(&self, input: pb::SetConfigBoolIn) -> BackendResult<pb::Empty> {
        self.with_col(|col| col.transact(None, |col| col.set_bool(input.key().into(), input.value)))
            .map(Into::into)
    }

    fn get_config_string(&self, input: pb::config::String) -> BackendResult<pb::String> {
        self.with_col(|col| {
            Ok(pb::String {
                val: col.get_string(input.key().into()),
            })
        })
    }

    fn set_config_string(&self, input: pb::SetConfigStringIn) -> BackendResult<pb::Empty> {
        self.with_col(|col| {
            col.transact(None, |col| col.set_string(input.key().into(), &input.value))
        })
        .map(Into::into)
    }

    fn get_preferences(&self, _input: Empty) -> BackendResult<pb::Preferences> {
        self.with_col(|col| col.get_preferences())
    }

    fn set_preferences(&self, input: pb::Preferences) -> BackendResult<Empty> {
        self.with_col(|col| col.set_preferences(input))
            .map(Into::into)
    }
}

impl Backend {
    pub fn new(i18n: I18n, server: bool) -> Backend {
        Backend {
            col: Arc::new(Mutex::new(None)),
            i18n,
            server,
            sync_abort: Arc::new(Mutex::new(None)),
            progress_state: Arc::new(Mutex::new(ProgressState {
                want_abort: false,
                last_progress: None,
            })),
            runtime: OnceCell::new(),
            state: Arc::new(Mutex::new(BackendState::default())),
        }
    }

    pub fn i18n(&self) -> &I18n {
        &self.i18n
    }

    pub fn run_command_bytes(&self, method: u32, input: &[u8]) -> result::Result<Vec<u8>, Vec<u8>> {
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

    fn runtime_handle(&self) -> runtime::Handle {
        self.runtime
            .get_or_init(|| {
                runtime::Builder::new()
                    .threaded_scheduler()
                    .core_threads(1)
                    .enable_all()
                    .build()
                    .unwrap()
            })
            .handle()
            .clone()
    }

    pub fn db_command(&self, input: &[u8]) -> Result<Vec<u8>> {
        self.with_col(|col| db_command_bytes(col, input))
    }

    pub fn run_db_command_bytes(&self, input: &[u8]) -> std::result::Result<Vec<u8>, Vec<u8>> {
        self.db_command(input).map_err(|err| {
            let backend_err = anki_error_to_proto_error(err, &self.i18n);
            let mut bytes = Vec::new();
            backend_err.encode(&mut bytes).unwrap();
            bytes
        })
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

impl From<Card> for pb::Card {
    fn from(c: Card) -> Self {
        pb::Card {
            id: c.id.0,
            note_id: c.note_id.0,
            deck_id: c.deck_id.0,
            template_idx: c.template_idx as u32,
            mtime_secs: c.mtime.0,
            usn: c.usn.0,
            ctype: c.ctype as u32,
            queue: c.queue as i32,
            due: c.due,
            interval: c.interval,
            ease_factor: c.ease_factor as u32,
            reps: c.reps,
            lapses: c.lapses,
            remaining_steps: c.remaining_steps,
            original_due: c.original_due,
            original_deck_id: c.original_deck_id.0,
            flags: c.flags as u32,
            data: c.data,
        }
    }
}

impl From<crate::scheduler::timing::SchedTimingToday> for pb::SchedTimingTodayOut {
    fn from(t: crate::scheduler::timing::SchedTimingToday) -> pb::SchedTimingTodayOut {
        pb::SchedTimingTodayOut {
            days_elapsed: t.days_elapsed,
            next_day_at: t.next_day_at,
        }
    }
}
