// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Backend;
use crate::pb;
pub(super) use crate::pb::stats::stats_service::Service as StatsService;
use crate::prelude::*;
use crate::revlog::RevlogReviewKind;

impl StatsService for Backend {
    fn card_stats(&self, input: pb::cards::CardId) -> Result<pb::stats::CardStatsResponse> {
        self.with_col(|col| col.card_stats(input.into()))
    }

    fn graphs(&self, input: pb::stats::GraphsRequest) -> Result<pb::stats::GraphsResponse> {
        self.with_col(|col| col.graph_data_for_search(&input.search, input.days))
    }

    fn get_graph_preferences(
        &self,
        _input: pb::generic::Empty,
    ) -> Result<pb::stats::GraphPreferences> {
        self.with_col(|col| Ok(col.get_graph_preferences()))
    }

    fn set_graph_preferences(
        &self,
        input: pb::stats::GraphPreferences,
    ) -> Result<pb::generic::Empty> {
        self.with_col(|col| col.set_graph_preferences(input))
            .map(Into::into)
    }
}

impl From<RevlogReviewKind> for i32 {
    fn from(kind: RevlogReviewKind) -> Self {
        (match kind {
            RevlogReviewKind::Learning => pb::stats::revlog_entry::ReviewKind::Learning,
            RevlogReviewKind::Review => pb::stats::revlog_entry::ReviewKind::Review,
            RevlogReviewKind::Relearning => pb::stats::revlog_entry::ReviewKind::Relearning,
            RevlogReviewKind::Filtered => pb::stats::revlog_entry::ReviewKind::Filtered,
            RevlogReviewKind::Manual => pb::stats::revlog_entry::ReviewKind::Manual,
        }) as i32
    }
}
