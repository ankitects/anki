// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    prelude::*,
    scheduler::states::{CardState, ReschedulingFilterState},
};

use super::{CardStateUpdater, RevlogEntryPartial};

impl CardStateUpdater {
    pub(super) fn apply_rescheduling_filter_state(
        &mut self,
        current: CardState,
        next: ReschedulingFilterState,
    ) -> Result<Option<RevlogEntryPartial>> {
        self.ensure_filtered()?;
        self.apply_study_state(current, next.original_state.into())
    }
}
