// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Backend;
use crate::{backend_proto as pb, prelude::*};
pub(super) use pb::stats_service::Service as StatsService;

impl StatsService for Backend {
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
}
