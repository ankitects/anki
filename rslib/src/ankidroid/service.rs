// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use anki_proto::ankidroid::DbResponse;
use anki_proto::ankidroid::GetActiveSequenceNumbersResponse;
use anki_proto::ankidroid::GetNextResultPageRequest;
use anki_proto::generic;

use crate::ankidroid::db;
use crate::ankidroid::db::active_sequences;
use crate::ankidroid::db::execute_for_row_count;
use crate::ankidroid::db::insert_for_id;
use crate::backend::dbproxy::db_command_bytes;
use crate::backend::dbproxy::db_command_proto;
use crate::collection::Collection;
use crate::error;
use crate::error::OrInvalid;

impl crate::services::AnkidroidService for Collection {
    fn run_db_command(&mut self, input: generic::Json) -> error::Result<generic::Json> {
        db_command_bytes(self, &input.json).map(|json| generic::Json { json })
    }

    fn run_db_command_proto(&mut self, input: generic::Json) -> error::Result<DbResponse> {
        db_command_proto(self, &input.json)
    }

    fn run_db_command_for_row_count(
        &mut self,
        input: generic::Json,
    ) -> error::Result<generic::Int64> {
        execute_for_row_count(self, &input.json).map(|val| generic::Int64 { val })
    }

    fn flush_all_queries(&mut self) -> error::Result<()> {
        db::flush_collection(self);
        Ok(())
    }

    fn flush_query(&mut self, input: generic::Int32) -> error::Result<()> {
        db::flush_single_result(self, input.val);
        Ok(())
    }

    fn get_next_result_page(
        &mut self,
        input: GetNextResultPageRequest,
    ) -> error::Result<DbResponse> {
        db::get_next(self, input.sequence, input.index).or_invalid("missing result page")
    }

    fn insert_for_id(&mut self, input: generic::Json) -> error::Result<generic::Int64> {
        insert_for_id(self, &input.json).map(Into::into)
    }

    fn get_column_names_from_query(
        &mut self,
        input: generic::String,
    ) -> error::Result<generic::StringList> {
        let stmt = self.storage.db.prepare(&input.val)?;
        let names = stmt.column_names();
        let names: Vec<_> = names.iter().map(ToString::to_string).collect();
        Ok(names.into())
    }

    fn get_active_sequence_numbers(&mut self) -> error::Result<GetActiveSequenceNumbersResponse> {
        Ok(GetActiveSequenceNumbersResponse {
            numbers: active_sequences(self),
        })
    }
}
