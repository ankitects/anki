// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::LearningQueueEntry;
use super::MainQueueEntry;
use super::MainQueueEntryKind;
use crate::card::CardQueue;
use crate::prelude::*;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum QueueEntry {
    IntradayLearning(LearningQueueEntry),
    Main(MainQueueEntry),
}

impl QueueEntry {
    pub fn card_id(&self) -> CardId {
        match self {
            QueueEntry::IntradayLearning(e) => e.id,
            QueueEntry::Main(e) => e.id,
        }
    }

    pub fn mtime(&self) -> TimestampSecs {
        match self {
            QueueEntry::IntradayLearning(e) => e.mtime,
            QueueEntry::Main(e) => e.mtime,
        }
    }

    pub fn kind(&self) -> QueueEntryKind {
        match self {
            QueueEntry::IntradayLearning(_e) => QueueEntryKind::Learning,
            QueueEntry::Main(e) => match e.kind {
                MainQueueEntryKind::New => QueueEntryKind::New,
                MainQueueEntryKind::Review => QueueEntryKind::Review,
                MainQueueEntryKind::InterdayLearning => QueueEntryKind::Learning,
            },
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum QueueEntryKind {
    New,
    Learning,
    Review,
}

impl From<&Card> for QueueEntry {
    fn from(card: &Card) -> Self {
        let kind = match card.queue {
            CardQueue::Learn | CardQueue::PreviewRepeat => {
                return QueueEntry::IntradayLearning(LearningQueueEntry {
                    due: TimestampSecs(card.due as i64),
                    id: card.id,
                    mtime: card.mtime,
                });
            }
            CardQueue::New => MainQueueEntryKind::New,
            CardQueue::Review | CardQueue::DayLearn => MainQueueEntryKind::Review,
            CardQueue::Suspended | CardQueue::SchedBuried | CardQueue::UserBuried => {
                unreachable!()
            }
        };
        QueueEntry::Main(MainQueueEntry {
            id: card.id,
            mtime: card.mtime,
            kind,
        })
    }
}

impl From<LearningQueueEntry> for QueueEntry {
    fn from(e: LearningQueueEntry) -> Self {
        Self::IntradayLearning(e)
    }
}

impl From<MainQueueEntry> for QueueEntry {
    fn from(e: MainQueueEntry) -> Self {
        Self::Main(e)
    }
}

impl From<&LearningQueueEntry> for QueueEntry {
    fn from(e: &LearningQueueEntry) -> Self {
        Self::IntradayLearning(*e)
    }
}

impl From<&MainQueueEntry> for QueueEntry {
    fn from(e: &MainQueueEntry) -> Self {
        Self::Main(*e)
    }
}
