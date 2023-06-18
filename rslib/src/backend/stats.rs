// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub(super) use anki_proto::stats::stats_service::Service as StatsService;

use super::Backend;
use crate::prelude::*;
use crate::revlog::RevlogReviewKind;

impl StatsService for Backend {
    type Error = AnkiError;

    fn card_stats(
        &self,
        input: anki_proto::cards::CardId,
    ) -> Result<anki_proto::stats::CardStatsResponse> {
        self.with_col(|col| col.card_stats(input.cid.into()))
    }

    fn graphs(
        &self,
        input: anki_proto::stats::GraphsRequest,
    ) -> Result<anki_proto::stats::GraphsResponse> {
        self.with_col(|col| col.graph_data_for_search(&input.search, input.days))
    }

    fn get_graph_preferences(&self) -> Result<anki_proto::stats::GraphPreferences> {
        self.with_col(|col| Ok(col.get_graph_preferences()))
    }

    fn set_graph_preferences(&self, input: anki_proto::stats::GraphPreferences) -> Result<()> {
        self.with_col(|col| col.set_graph_preferences(input))
            .map(Into::into)
    }
}

impl From<RevlogReviewKind> for i32 {
    fn from(kind: RevlogReviewKind) -> Self {
        (match kind {
            RevlogReviewKind::Learning => anki_proto::stats::revlog_entry::ReviewKind::Learning,
            RevlogReviewKind::Review => anki_proto::stats::revlog_entry::ReviewKind::Review,
            RevlogReviewKind::Relearning => anki_proto::stats::revlog_entry::ReviewKind::Relearning,
            RevlogReviewKind::Filtered => anki_proto::stats::revlog_entry::ReviewKind::Filtered,
            RevlogReviewKind::Manual => anki_proto::stats::revlog_entry::ReviewKind::Manual,
        }) as i32
    }
}
