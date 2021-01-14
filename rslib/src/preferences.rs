// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    backend_proto::{
        collection_scheduling_settings::NewReviewMix as NewRevMixPB, CollectionSchedulingSettings,
        Preferences,
    },
    collection::Collection,
    err::Result,
    sched::cutoff::local_minutes_west_for_stamp,
};

impl Collection {
    pub fn get_preferences(&self) -> Result<Preferences> {
        Ok(Preferences {
            sched: Some(self.get_collection_scheduling_settings()?),
        })
    }

    pub fn set_preferences(&self, prefs: Preferences) -> Result<()> {
        if let Some(sched) = prefs.sched {
            self.set_collection_scheduling_settings(sched)?;
        }

        Ok(())
    }

    pub fn get_collection_scheduling_settings(&self) -> Result<CollectionSchedulingSettings> {
        Ok(CollectionSchedulingSettings {
            scheduler_version: match self.sched_ver() {
                crate::config::SchedulerVersion::V1 => 1,
                crate::config::SchedulerVersion::V2 => 2,
            },
            rollover: self.rollover_for_current_scheduler()? as u32,
            learn_ahead_secs: self.learn_ahead_secs(),
            new_review_mix: match self.get_new_review_mix() {
                crate::config::NewReviewMix::Mix => NewRevMixPB::Distribute,
                crate::config::NewReviewMix::ReviewsFirst => NewRevMixPB::ReviewsFirst,
                crate::config::NewReviewMix::NewFirst => NewRevMixPB::NewFirst,
            } as i32,
            show_remaining_due_counts: self.get_show_due_counts(),
            show_intervals_on_buttons: self.get_show_intervals_above_buttons(),
            time_limit_secs: self.get_answer_time_limit_secs(),
            new_timezone: self.get_creation_utc_offset().is_some(),
            day_learn_first: self.get_day_learn_first(),
        })
    }

    pub(crate) fn set_collection_scheduling_settings(
        &self,
        settings: CollectionSchedulingSettings,
    ) -> Result<()> {
        let s = settings;

        self.set_day_learn_first(s.day_learn_first)?;
        self.set_answer_time_limit_secs(s.time_limit_secs)?;
        self.set_show_due_counts(s.show_remaining_due_counts)?;
        self.set_show_intervals_above_buttons(s.show_intervals_on_buttons)?;
        self.set_learn_ahead_secs(s.learn_ahead_secs)?;

        self.set_new_review_mix(match s.new_review_mix() {
            NewRevMixPB::Distribute => crate::config::NewReviewMix::Mix,
            NewRevMixPB::NewFirst => crate::config::NewReviewMix::NewFirst,
            NewRevMixPB::ReviewsFirst => crate::config::NewReviewMix::ReviewsFirst,
        })?;

        let created = self.storage.creation_stamp()?;

        if self.rollover_for_current_scheduler()? != s.rollover as u8 {
            self.set_rollover_for_current_scheduler(s.rollover as u8)?;
        }

        if s.new_timezone {
            if self.get_creation_utc_offset().is_none() {
                self.set_creation_utc_offset(Some(local_minutes_west_for_stamp(created.0)))?;
            }
        } else {
            self.set_creation_utc_offset(None)?;
        }

        // fixme: currently scheduler change unhandled
        Ok(())
    }
}
