// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashSet;

use anki_proto::notetypes::get_non_empty_card_indexes_response;

use crate::cloze::cloze_number_in_fields;
use crate::collection::Collection;
use crate::error;
use crate::error::OrNotFound;
use crate::notetype::NotetypeId;
use crate::notetype::NotetypeKind;
use crate::template::field_is_empty;

impl Collection {
    pub(crate) fn get_non_empty_card_indexes(
        &mut self,
        ntid: NotetypeId,
        field_contents: Vec<std::string::String>,
    ) -> error::Result<anki_proto::notetypes::GetNonEmptyCardIndexesResponse> {
        let nt = self.get_notetype(ntid)?.or_not_found(ntid)?;
        Ok(match nt.config.kind() {
            NotetypeKind::Normal => {
                let mut non_empty_fields: HashSet<&str> =
                    HashSet::with_capacity(field_contents.len());
                let field_names = self.storage.get_field_names(ntid)?;
                for (field_idx, field_content) in field_contents.iter().enumerate() {
                    if !field_is_empty(&field_content) {
                        let field_name: &str = &field_names[field_idx];
                        non_empty_fields.insert(field_name);
                    }
                }
                anki_proto::notetypes::GetNonEmptyCardIndexesResponse {
                    response: Some (
                        anki_proto::notetypes::get_non_empty_card_indexes_response::Response::Normal(
                            anki_proto::notetypes::get_non_empty_card_indexes_response::GetNonEmptyCardIndexesNormalResponse{
                                is_empty: nt.templates.iter().map(|tmpl|
                                    if let Some(parsed_question) = tmpl.parsed_question() {
                                    if parsed_question.renders_with_fields(&non_empty_fields) {
                                        get_non_empty_card_indexes_response::get_non_empty_card_indexes_normal_response::IsEmpty::NonEmpty
                                    } else {
                                        get_non_empty_card_indexes_response::get_non_empty_card_indexes_normal_response::IsEmpty::Empty
                                    }
                                    } else {
                                    get_non_empty_card_indexes_response::get_non_empty_card_indexes_normal_response::IsEmpty::Error
                                    } as i32
                                ).collect()
                                }
                            )
                        )
                }
            }
            NotetypeKind::Cloze => {
                let mut cloze_numbers: Vec<u32> = Vec::from_iter(
                    cloze_number_in_fields(field_contents)
                        .into_iter()
                        // Decrementing so that we get the card order instead of the cloze number
                        // from the field.
                        .map(|i| (i - 1) as u32),
                );
                cloze_numbers.sort();
                anki_proto::notetypes::GetNonEmptyCardIndexesResponse {
                    response: Some(anki_proto::notetypes::get_non_empty_card_indexes_response::Response::Cloze(
                        anki_proto::notetypes::get_non_empty_card_indexes_response::GetNonEmptyCardIndexesClozeResponse{
                            indexes: cloze_numbers
                        }
                    ))
                }
            }
        })
    }
}
