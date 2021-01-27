use super::Deck;
use crate::{
    collection::Collection,
    err::{AnkiError, Result},
    i18n::TR,
    notes::Note,
    notetype::{CardGenContext, NoteType},
};

impl Collection {
    pub fn create_sample_deck(&mut self) -> Result<()> {
        let deck_name = self.i18n.tr(TR::SampleDeckName).to_string();
        let nt_name = self.i18n.tr(TR::SampleNotetypeName).to_string();
        if self.get_deck_id(&deck_name)? != None || self.get_notetype_by_name(&nt_name)? != None {
            return Err(AnkiError::Existing);
        }
        let mut deck = Deck::new_normal();
        deck.name = deck_name;
        let mut nt = self.sample_notetype(&nt_name);
        self.state.deck_cache.clear();
        self.transact(None, |col| {
            let usn = col.usn()?;

            col.prepare_deck_for_update(&mut deck, usn)?;
            col.storage.add_deck(&mut deck)?;

            nt.set_modified(usn);
            nt.prepare_for_adding()?;
            col.storage.add_new_notetype(&mut nt)?;

            let did = col.get_deck_id(&deck.name)?.ok_or(AnkiError::NotFound)?;
            let col_nt = col
                .get_notetype_by_name(&nt_name)?
                .ok_or(AnkiError::NotFound)?;
            let ctx = CardGenContext::new(&col_nt, usn);

            for mut note in col.sample_notes(&col_nt)? {
                note.prepare_for_update(&col_nt, true)?;
                note.set_modified(usn);
                col.storage.add_note(&mut note)?;
                col.generate_cards_for_new_note(&ctx, &note, did)?;
            }

            Ok(())
        })
    }

    fn sample_notetype(&self, nt_name: &str) -> NoteType {
        let mut nt = NoteType::default();
        nt.name = nt_name.to_string();
        let question = self.i18n.tr(TR::SampleFieldQuestion);
        let answer = self.i18n.tr(TR::SampleFieldAnswer);
        let details = self.i18n.tr(TR::SampleFieldDetails);
        let manual = self.i18n.tr(TR::SampleFieldManual);
        let front = format!("<div class=question>{{{{{}}}}}</div>", question);
        let back = format!(
            "{{{{FrontSide}}}}
    <hr id=answer>

    <div class=answer>{{{{{}}}}}</div>
    <div class=details>{{{{{}}}}}</div>
    <a href=https://docs.ankiweb.net/#/{{{{{}}}}}></a>",
            answer, details, manual
        );
        nt.add_field(question);
        nt.add_field(answer);
        nt.add_field(details);
        nt.add_field(manual);
        nt.add_template(self.i18n.tr(TR::SampleTemplateName), front, back);
        nt
    }

    fn sample_notes(&self, nt: &NoteType) -> Result<Vec<Note>> {
        Ok(vec![
            sample_note(
                nt,
                &self.i18n.tr(TR::SampleQuestionCard),
                &self.i18n.tr(TR::SampleAnswerCard),
                &self.i18n.tr(TR::SampleDetailsCard),
                "getting-started?id=cards",
            )?,
            sample_note(
                nt,
                &self.i18n.tr(TR::SampleQuestionDeck),
                &self.i18n.tr(TR::SampleAnswerDeck),
                &self.i18n.tr(TR::SampleDetailsDeck),
                "getting-started?id=decks",
            )?,
            sample_note(
                nt,
                &self.i18n.tr(TR::SampleQuestionNote),
                &self.i18n.tr(TR::SampleAnswerNote),
                &self.i18n.tr(TR::SampleDetailsNote),
                "getting-started?id=notes-amp-fields",
            )?,
            sample_note(
                nt,
                &self.i18n.tr(TR::SampleQuestionField),
                &self.i18n.tr(TR::SampleAnswerField),
                &self.i18n.tr(TR::SampleDetailsField),
                "getting-started?id=notes-amp-fields",
            )?,
        ])
    }
}

fn sample_note(
    nt: &NoteType,
    question: &str,
    answer: &str,
    details: &str,
    manual: &str,
) -> Result<Note> {
    let mut note = Note::new(nt);
    note.set_field(0, question)?;
    note.set_field(1, answer)?;
    note.set_field(2, details)?;
    note.set_field(3, manual)?;
    Ok(note)
}
