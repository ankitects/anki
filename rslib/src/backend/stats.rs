// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Backend;
pub(super) use crate::backend_proto::stats_service::Service as StatsService;
use crate::{backend_proto as pb, prelude::*, revlog::RevlogReviewKind};

impl StatsService for Backend {
    fn card_stats(&self, input: pb::CardId) -> Result<pb::CardStatsResponse> {
        self.with_col(|col| col.card_stats(input.into()))
    }

    fn graphs(&self, input: pb::GraphsRequest) -> Result<pb::GraphsResponse> {
        self.with_col(|col| col.graph_data_for_search(&input.search, input.days))
    }

    fn get_graph_preferences(&self, _input: pb::Empty) -> Result<pb::GraphPreferences> {
        self.with_col(|col| Ok(col.get_graph_preferences()))
    }

    fn set_graph_preferences(&self, input: pb::GraphPreferences) -> Result<pb::Empty> {
        self.with_col(|col| col.set_graph_preferences(input))
            .map(Into::into)
    }
}

impl From<RevlogReviewKind> for i32 {
    fn from(kind: RevlogReviewKind) -> Self {
        (match kind {
            RevlogReviewKind::Learning => pb::revlog_entry::ReviewKind::Learning,
            RevlogReviewKind::Review => pb::revlog_entry::ReviewKind::Review,
            RevlogReviewKind::Relearning => pb::revlog_entry::ReviewKind::Relearning,
            RevlogReviewKind::Filtered => pb::revlog_entry::ReviewKind::Filtered,
            RevlogReviewKind::Manual => pb::revlog_entry::ReviewKind::Manual,
        }) as i32
    }
}
