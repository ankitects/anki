// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod adding;
mod card;
mod cardrendering;
mod config;
mod dbproxy;
mod deckconfig;
mod decks;
mod err;
mod generic;
mod notes;
mod notetypes;
mod progress;
mod scheduler;
mod search;
mod sync;
mod tags;

use self::{
    cardrendering::CardRenderingService,
    config::ConfigService,
    deckconfig::DeckConfigService,
    decks::DecksService,
    notes::NotesService,
    notetypes::NoteTypesService,
    scheduler::SchedulingService,
    sync::{SyncService, SyncState},
    tags::TagsService,
};
use crate::backend_proto::backend_service::Service as BackendService;

use crate::{
    backend::dbproxy::db_command_bytes,
    backend_proto as pb,
    backend_proto::RenderedTemplateReplacement,
    card::{Card, CardID},
    collection::{open_collection, Collection},
    err::{AnkiError, Result},
    i18n::I18n,
    log,
    log::default_logger,
    markdown::render_markdown,
    media::check::MediaChecker,
    media::MediaManager,
    notes::NoteID,
    notetype::RenderCardOutput,
    scheduler::timespan::{answer_button_time, time_span},
    search::{concatenate_searches, replace_search_node, write_nodes, Node},
    template::RenderedNode,
    text::sanitize_html_no_images,
    undo::UndoableOpKind,
};
use fluent::FluentValue;
use log::error;
use once_cell::sync::OnceCell;
use progress::{AbortHandleSlot, Progress};
use prost::Message;
use std::convert::TryInto;
use std::{
    result,
    sync::{Arc, Mutex},
};
use tokio::runtime::{self, Runtime};

use self::{
    err::anki_error_to_proto_error,
    progress::{progress_to_proto, ProgressState},
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
    sync: SyncState,
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
    fn latest_progress(&self, _input: pb::Empty) -> Result<pb::Progress> {
        let progress = self.progress_state.lock().unwrap().last_progress;
        Ok(progress_to_proto(progress, &self.i18n))
    }

    fn set_wants_abort(&self, _input: pb::Empty) -> Result<pb::Empty> {
        self.progress_state.lock().unwrap().want_abort = true;
        Ok(().into())
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

    fn find_and_replace(&self, input: pb::FindAndReplaceIn) -> Result<pb::UInt32> {
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
    // statistics
    //-----------------------------------------------

    fn card_stats(&self, input: pb::CardId) -> Result<pb::String> {
        self.with_col(|col| col.card_stats(input.into()))
            .map(Into::into)
    }

    fn graphs(&self, input: pb::GraphsIn) -> Result<pb::GraphsOut> {
        self.with_col(|col| col.graph_data_for_search(&input.search, input.days))
    }

    fn get_graph_preferences(&self, _input: pb::Empty) -> Result<pb::GraphPreferences> {
        self.with_col(|col| col.get_graph_preferences())
    }

    fn set_graph_preferences(&self, input: pb::GraphPreferences) -> Result<pb::Empty> {
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

    fn trash_media_files(&self, input: pb::TrashMediaFilesIn) -> Result<pb::Empty> {
        self.with_col(|col| {
            let mgr = MediaManager::new(&col.media_folder, &col.media_db)?;
            let mut ctx = mgr.dbctx();
            mgr.remove_files(&mut ctx, &input.fnames)
        })
        .map(Into::into)
    }

    fn add_media_file(&self, input: pb::AddMediaFileIn) -> Result<pb::String> {
        self.with_col(|col| {
            let mgr = MediaManager::new(&col.media_folder, &col.media_db)?;
            let mut ctx = mgr.dbctx();
            Ok(mgr
                .add_file(&mut ctx, &input.desired_name, &input.data)?
                .to_string()
                .into())
        })
    }

    fn empty_trash(&self, _input: pb::Empty) -> Result<pb::Empty> {
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

    fn restore_trash(&self, _input: pb::Empty) -> Result<pb::Empty> {
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

    // cards
    //-------------------------------------------------------------------

    fn get_card(&self, input: pb::CardId) -> Result<pb::Card> {
        self.with_col(|col| {
            col.storage
                .get_card(input.into())
                .and_then(|opt| opt.ok_or(AnkiError::NotFound))
                .map(Into::into)
        })
    }

    fn update_card(&self, input: pb::UpdateCardIn) -> Result<pb::Empty> {
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

    fn remove_cards(&self, input: pb::RemoveCardsIn) -> Result<pb::Empty> {
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

    fn set_deck(&self, input: pb::SetDeckIn) -> Result<pb::Empty> {
        let cids: Vec<_> = input.card_ids.into_iter().map(CardID).collect();
        let deck_id = input.deck_id.into();
        self.with_col(|col| col.set_deck(&cids, deck_id).map(Into::into))
    }

    // collection
    //-------------------------------------------------------------------

    fn open_collection(&self, input: pb::OpenCollectionIn) -> Result<pb::Empty> {
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

    fn close_collection(&self, input: pb::CloseCollectionIn) -> Result<pb::Empty> {
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

    fn check_database(&self, _input: pb::Empty) -> Result<pb::CheckDatabaseOut> {
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

    // i18n/messages
    //-------------------------------------------------------------------

    fn translate_string(&self, input: pb::TranslateStringIn) -> Result<pb::String> {
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

    fn format_timespan(&self, input: pb::FormatTimespanIn) -> Result<pb::String> {
        use pb::format_timespan_in::Context;
        Ok(match input.context() {
            Context::Precise => time_span(input.seconds, &self.i18n, true),
            Context::Intervals => time_span(input.seconds, &self.i18n, false),
            Context::AnswerButtons => answer_button_time(input.seconds, &self.i18n),
        }
        .into())
    }

    fn i18n_resources(&self, _input: pb::Empty) -> Result<pb::Json> {
        serde_json::to_vec(&self.i18n.resources_for_js())
            .map(Into::into)
            .map_err(Into::into)
    }

    fn render_markdown(&self, input: pb::RenderMarkdownIn) -> Result<pb::String> {
        let mut text = render_markdown(&input.markdown);
        if input.sanitize {
            // currently no images
            text = sanitize_html_no_images(&text);
        }
        Ok(text.into())
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

    pub fn run_method(
        &self,
        service: u32,
        method: u32,
        input: &[u8],
    ) -> result::Result<Vec<u8>, Vec<u8>> {
        pb::ServiceIndex::from_i32(service as i32)
            .ok_or_else(|| AnkiError::invalid_input("invalid service"))
            .and_then(|service| match service {
                pb::ServiceIndex::Scheduling => SchedulingService::run_method(self, method, input),
                pb::ServiceIndex::Backend => BackendService::run_method(self, method, input),
                pb::ServiceIndex::Decks => DecksService::run_method(self, method, input),
                pb::ServiceIndex::Notes => NotesService::run_method(self, method, input),
                pb::ServiceIndex::NoteTypes => NoteTypesService::run_method(self, method, input),
                pb::ServiceIndex::Config => ConfigService::run_method(self, method, input),
                pb::ServiceIndex::Sync => SyncService::run_method(self, method, input),
                pb::ServiceIndex::Tags => TagsService::run_method(self, method, input),
                pb::ServiceIndex::DeckConfig => DeckConfigService::run_method(self, method, input),
                pb::ServiceIndex::CardRendering => {
                    CardRenderingService::run_method(self, method, input)
                }
            })
            .map_err(|err| {
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
