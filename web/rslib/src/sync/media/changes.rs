// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use serde::Deserialize;
use serde::Serialize;
use serde_tuple::Serialize_tuple;
use tracing::debug;

use crate::error;
use crate::prelude::Usn;
use crate::sync::media::database::client::MediaDatabase;

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MediaChangesRequest {
    pub last_usn: Usn,
}

pub type MediaChangesResponse = Vec<MediaChange>;

#[derive(Debug, Serialize_tuple, Deserialize)]
pub struct MediaChange {
    pub fname: String,
    pub usn: Usn,
    pub sha1: String,
}

#[derive(Debug, Clone, Copy)]
pub enum LocalState {
    NotInDb,
    InDbNotPending,
    InDbAndPending,
}

#[derive(PartialEq, Eq, Debug)]
pub enum RequiredChange {
    // none also covers the case where we'll later upload
    None,
    Download,
    Delete,
    RemovePending,
}

pub fn determine_required_change(
    local_sha1: &str,
    remote_sha1: &str,
    local_state: LocalState,
) -> RequiredChange {
    match (local_sha1, remote_sha1, local_state) {
        // both deleted, not in local DB
        ("", "", LocalState::NotInDb) => RequiredChange::None,
        // both deleted, in local DB
        ("", "", _) => RequiredChange::Delete,
        // added on server, add even if local deletion pending
        ("", _, _) => RequiredChange::Download,
        // deleted on server but added locally; upload later
        (_, "", LocalState::InDbAndPending) => RequiredChange::None,
        // deleted on server and not pending sync
        (_, "", _) => RequiredChange::Delete,
        // if pending but the same as server, don't need to upload
        (lsum, rsum, LocalState::InDbAndPending) if lsum == rsum => RequiredChange::RemovePending,
        (lsum, rsum, _) => {
            if lsum == rsum {
                // not pending and same as server, nothing to do
                RequiredChange::None
            } else {
                // differs from server, favour server
                RequiredChange::Download
            }
        }
    }
}

/// Get a list of server filenames and the actions required on them.
/// Returns filenames in (to_download, to_delete).
pub fn determine_required_changes(
    ctx: &MediaDatabase,
    records: Vec<MediaChange>,
) -> error::Result<(Vec<String>, Vec<String>, Vec<String>)> {
    let mut to_download = vec![];
    let mut to_delete = vec![];
    let mut to_remove_pending = vec![];

    for remote in records {
        let (local_sha1, local_state) = match ctx.get_entry(&remote.fname)? {
            Some(entry) => (
                match entry.sha1 {
                    Some(arr) => hex::encode(arr),
                    None => "".to_string(),
                },
                if entry.sync_required {
                    LocalState::InDbAndPending
                } else {
                    LocalState::InDbNotPending
                },
            ),
            None => ("".to_string(), LocalState::NotInDb),
        };

        let req_change = determine_required_change(&local_sha1, &remote.sha1, local_state);
        debug!(
            fname = &remote.fname,
            lsha = local_sha1.chars().take(8).collect::<String>(),
            rsha = remote.sha1.chars().take(8).collect::<String>(),
            state = ?local_state,
            action = ?req_change,
            "determine action"
        );
        match req_change {
            RequiredChange::Download => to_download.push(remote.fname),
            RequiredChange::Delete => to_delete.push(remote.fname),
            RequiredChange::RemovePending => to_remove_pending.push(remote.fname),
            RequiredChange::None => (),
        };
    }

    Ok((to_download, to_delete, to_remove_pending))
}

#[cfg(test)]
mod test {

    #[test]
    fn required_change() {
        use crate::sync::media::changes::determine_required_change as d;
        use crate::sync::media::changes::LocalState as L;
        use crate::sync::media::changes::RequiredChange as R;
        assert_eq!(d("", "", L::NotInDb), R::None);
        assert_eq!(d("", "", L::InDbNotPending), R::Delete);
        assert_eq!(d("", "1", L::InDbAndPending), R::Download);
        assert_eq!(d("1", "", L::InDbAndPending), R::None);
        assert_eq!(d("1", "", L::InDbNotPending), R::Delete);
        assert_eq!(d("1", "1", L::InDbNotPending), R::None);
        assert_eq!(d("1", "1", L::InDbAndPending), R::RemovePending);
        assert_eq!(d("a", "b", L::InDbAndPending), R::Download);
        assert_eq!(d("a", "b", L::InDbNotPending), R::Download);
    }
}
