// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::config::preferences::scheduling::NewReviewMix as NewRevMixPB;
use anki_proto::config::preferences::Editing;
use anki_proto::config::preferences::Reviewing;
use anki_proto::config::preferences::Scheduling;
use anki_proto::config::Preferences;

use crate::collection::Collection;
use crate::config::BoolKey;
use crate::config::StringKey;
use crate::error::Result;
use crate::prelude::*;
use crate::scheduler::timing::local_minutes_west_for_stamp;

impl Collection {
    pub fn get_preferences(&self) -> Result<Preferences> {
        Ok(Preferences {
            scheduling: Some(self.get_scheduling_preferences()?),
            reviewing: Some(self.get_reviewing_preferences()?),
            editing: Some(self.get_editing_preferences()?),
            backups: Some(self.get_backup_limits()),
        })
    }

    pub fn set_preferences(&mut self, prefs: Preferences) -> Result<OpOutput<()>> {
        self.transact(Op::UpdatePreferences, |col| {
            col.set_preferences_inner(prefs)
        })
    }

    fn set_preferences_inner(&mut self, prefs: Preferences) -> Result<()> {
        if let Some(sched) = prefs.scheduling {
            self.set_scheduling_preferences(sched)?;
        }
        if let Some(reviewing) = prefs.reviewing {
            self.set_reviewing_preferences(reviewing)?;
        }
        if let Some(editing) = prefs.editing {
            self.set_editing_preferences(editing)?;
        }
        if let Some(backups) = prefs.backups {
            self.set_backup_limits(backups)?;
        }
        Ok(())
    }

    pub fn get_scheduling_preferences(&self) -> Result<Scheduling> {
        Ok(Scheduling {
            rollover: self.rollover_for_current_scheduler()? as u32,
            learn_ahead_secs: self.learn_ahead_secs(),
            new_review_mix: match self.get_new_review_mix() {
                crate::config::NewReviewMix::Mix => NewRevMixPB::Distribute,
                crate::config::NewReviewMix::ReviewsFirst => NewRevMixPB::ReviewsFirst,
                crate::config::NewReviewMix::NewFirst => NewRevMixPB::NewFirst,
            } as i32,
            new_timezone: self.get_creation_utc_offset().is_some(),
            day_learn_first: self.get_config_bool(BoolKey::ShowDayLearningCardsFirst),
        })
    }

    pub(crate) fn set_scheduling_preferences(&mut self, settings: Scheduling) -> Result<()> {
        let s = settings;

        self.set_config_bool_inner(BoolKey::ShowDayLearningCardsFirst, s.day_learn_first)?;
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
                self.set_creation_utc_offset(Some(local_minutes_west_for_stamp(created)?))?;
            }
        } else {
            self.set_creation_utc_offset(None)?;
        }

        Ok(())
    }

    pub fn get_reviewing_preferences(&self) -> Result<Reviewing> {
        Ok(Reviewing {
            hide_audio_play_buttons: self.get_config_bool(BoolKey::HideAudioPlayButtons),
            interrupt_audio_when_answering: self
                .get_config_bool(BoolKey::InterruptAudioWhenAnswering),
            show_remaining_due_counts: self.get_config_bool(BoolKey::ShowRemainingDueCountsInStudy),
            show_intervals_on_buttons: self
                .get_config_bool(BoolKey::ShowIntervalsAboveAnswerButtons),
            time_limit_secs: self.get_answer_time_limit_secs(),
            load_balancer_enabled: self.get_config_bool(BoolKey::LoadBalancerEnabled),
            fsrs_short_term_with_steps_enabled: self
                .get_config_bool(BoolKey::FsrsShortTermWithStepsEnabled),
        })
    }

    pub(crate) fn set_reviewing_preferences(&mut self, settings: Reviewing) -> Result<()> {
        let s = settings;
        self.set_config_bool_inner(BoolKey::HideAudioPlayButtons, s.hide_audio_play_buttons)?;
        self.set_config_bool_inner(
            BoolKey::InterruptAudioWhenAnswering,
            s.interrupt_audio_when_answering,
        )?;
        self.set_config_bool_inner(
            BoolKey::ShowRemainingDueCountsInStudy,
            s.show_remaining_due_counts,
        )?;
        self.set_config_bool_inner(
            BoolKey::ShowIntervalsAboveAnswerButtons,
            s.show_intervals_on_buttons,
        )?;
        self.set_answer_time_limit_secs(s.time_limit_secs)?;
        self.set_config_bool_inner(BoolKey::LoadBalancerEnabled, s.load_balancer_enabled)?;
        self.set_config_bool_inner(
            BoolKey::FsrsShortTermWithStepsEnabled,
            s.fsrs_short_term_with_steps_enabled,
        )?;
        Ok(())
    }

    pub fn get_editing_preferences(&self) -> Result<Editing> {
        Ok(Editing {
            adding_defaults_to_current_deck: self
                .get_config_bool(BoolKey::AddingDefaultsToCurrentDeck),
            paste_images_as_png: self.get_config_bool(BoolKey::PasteImagesAsPng),
            paste_strips_formatting: self.get_config_bool(BoolKey::PasteStripsFormatting),
            middle_click_paste: self.get_config_bool(BoolKey::MiddleClickPaste),
            default_search_text: self.get_config_string(StringKey::DefaultSearchText),
            ignore_accents_in_search: self.get_config_bool(BoolKey::IgnoreAccentsInSearch),
            render_latex: self.get_config_bool(BoolKey::RenderLatex),
        })
    }

    pub(crate) fn set_editing_preferences(&mut self, settings: Editing) -> Result<()> {
        let s = settings;
        self.set_config_bool_inner(
            BoolKey::AddingDefaultsToCurrentDeck,
            s.adding_defaults_to_current_deck,
        )?;
        self.set_config_bool_inner(BoolKey::PasteImagesAsPng, s.paste_images_as_png)?;
        self.set_config_bool_inner(BoolKey::PasteStripsFormatting, s.paste_strips_formatting)?;
        self.set_config_bool_inner(BoolKey::MiddleClickPaste, s.middle_click_paste)?;
        self.set_config_string_inner(StringKey::DefaultSearchText, &s.default_search_text)?;
        self.set_config_bool_inner(BoolKey::IgnoreAccentsInSearch, s.ignore_accents_in_search)?;
        self.set_config_bool_inner(BoolKey::RenderLatex, s.render_latex)?;
        Ok(())
    }
}
