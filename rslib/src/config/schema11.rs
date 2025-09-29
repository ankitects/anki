// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use serde_json::json;

/// These items are expected to exist in schema 11. When adding
/// new config variables, you do not need to add them here -
/// just create an accessor function in one of the config/*.rs files,
/// with an appropriate default for missing/invalid values instead.
pub(crate) fn schema11_config_as_string(creation_offset: Option<i32>) -> String {
    let obj = json!({
        "activeDecks": [1],
        "curDeck": 1,
        "newSpread": 0,
        "collapseTime": 1200,
        "timeLim": 0,
        "estTimes": true,
        "dueCounts": true,
        "curModel": null,
        "nextPos": 1,
        "sortType": "noteFld",
        "sortBackwards": false,
        "addToCur": true,
        "dayLearnFirst": false,
        "schedVer": 2,
        "creationOffset": creation_offset,
        "sched2021": true,
        "hideColor": true,
    });
    serde_json::to_string(&obj).unwrap()
}
