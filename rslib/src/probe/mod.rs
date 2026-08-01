// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Ascent fork: probes are AI-generated reworded variants of existing cards,
//! served instead of the original when the scheduler is already confident of
//! a pass (see the substitution branch in [crate::scheduler::queue]).
//!
//! Probe content is stored in the local `probes` table, which does not take
//! part in normal sync; only probe *outcomes* travel, via the revlog's data
//! column. How probe text reaches a second device is an open design decision
//! (pre-generated packs, apkg, or regeneration per device).

pub(crate) mod undo;

use crate::define_newtype;
use crate::prelude::*;

define_newtype!(ProbeId, i64);

impl ProbeId {
    pub fn new() -> Self {
        ProbeId(TimestampMillis::now().0)
    }
}

/// A reworded variant of a card. Same fact, deliberately unfamiliar surface.
#[derive(Debug, Default, PartialEq, Eq, Clone)]
pub struct Probe {
    pub id: ProbeId,
    /// The parent card this probe rewords.
    pub card_id: CardId,
    pub question: String,
    pub answer: String,
    /// Source citation, for tracing a probe back to its material.
    pub citation: String,
    /// Free-form JSON generation provenance (model, date, prompt hash).
    pub provenance: String,
}

impl Collection {
    /// Add a probe for an existing card, assigning a new id if `probe.id` is
    /// zero or taken. This is the insertion seam for the (out of scope)
    /// generation pipeline; for now probes arrive via this call or apkg
    /// import.
    pub fn add_probe(&mut self, probe: &mut Probe) -> Result<OpOutput<()>> {
        let card_id = probe.card_id;
        self.transact(Op::Custom("Add probe".into()), |col| {
            col.storage
                .get_card(card_id)?
                .or_not_found(card_id)
                .map(|_| ())?;
            col.add_probe_undoable(probe)
        })
    }

    pub fn get_probes_for_card(&self, card_id: CardId) -> Result<Vec<Probe>> {
        self.storage.get_probes_for_card(card_id)
    }
}

impl From<Probe> for anki_proto::scheduler::Probe {
    fn from(p: Probe) -> Self {
        Self {
            id: p.id.0,
            card_id: p.card_id.0,
            question: p.question,
            answer: p.answer,
            citation: p.citation,
            provenance: p.provenance,
        }
    }
}

impl From<anki_proto::scheduler::Probe> for Probe {
    fn from(p: anki_proto::scheduler::Probe) -> Self {
        Self {
            id: ProbeId(p.id),
            card_id: CardId(p.card_id),
            question: p.question,
            answer: p.answer,
            citation: p.citation,
            provenance: p.provenance,
        }
    }
}

#[cfg(test)]
pub(crate) mod test {
    use super::*;
    use crate::tests::CardAdder;

    /// Attach a probe to `card_id`, returning it with its assigned id.
    pub(crate) fn add_test_probe(col: &mut Collection, card_id: CardId) -> Probe {
        let mut probe = Probe {
            card_id,
            question: "An assay shows unchanged Vmax but doubled Km. Which inhibition?".to_string(),
            answer: "Competitive".to_string(),
            citation: "Lehninger 6.3".to_string(),
            provenance: r#"{"model":"test","date":"2026-07-31"}"#.to_string(),
            ..Default::default()
        };
        col.add_probe(&mut probe).unwrap();
        probe
    }

    #[test]
    fn stored_and_retrieved_by_parent_card() {
        let mut col = Collection::new();
        let card_id = CardAdder::new().add(&mut col)[0].id;

        let probe = add_test_probe(&mut col, card_id);
        assert_ne!(probe.id.0, 0, "a fresh id must be assigned");
        assert_eq!(
            col.get_probes_for_card(card_id).unwrap(),
            vec![probe.clone()]
        );

        // a second probe on the same card gets a distinct id and both are
        // returned
        let second = add_test_probe(&mut col, card_id);
        assert_ne!(second.id, probe.id);
        assert_eq!(col.get_probes_for_card(card_id).unwrap().len(), 2);

        // undo removes only the most recent one
        col.undo().unwrap();
        assert_eq!(col.get_probes_for_card(card_id).unwrap(), vec![probe]);
        col.redo().unwrap();
        assert_eq!(col.get_probes_for_card(card_id).unwrap().len(), 2);
    }

    #[test]
    fn rejects_probe_for_missing_card() {
        let mut col = Collection::new();
        let mut probe = Probe {
            card_id: CardId(12345),
            ..Default::default()
        };
        assert!(col.add_probe(&mut probe).is_err());
    }

    #[test]
    fn removed_with_parent_card() {
        let mut col = Collection::new();
        let card_id = CardAdder::new().add(&mut col)[0].id;
        add_test_probe(&mut col, card_id);

        col.transact(Op::EmptyCards, |col| {
            col.remove_cards_and_orphaned_notes(&[card_id])
        })
        .unwrap();
        assert!(col.get_probes_for_card(card_id).unwrap().is_empty());

        col.undo().unwrap();
        assert_eq!(col.get_probes_for_card(card_id).unwrap().len(), 1);
    }
}
