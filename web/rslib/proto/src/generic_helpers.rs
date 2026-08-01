// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

impl From<Vec<u8>> for crate::generic::Json {
    fn from(json: Vec<u8>) -> Self {
        crate::generic::Json { json }
    }
}

impl From<String> for crate::generic::String {
    fn from(val: String) -> Self {
        crate::generic::String { val }
    }
}

impl From<Vec<String>> for crate::generic::StringList {
    fn from(vals: Vec<String>) -> Self {
        crate::generic::StringList { vals }
    }
}

impl From<bool> for crate::generic::Bool {
    fn from(val: bool) -> Self {
        crate::generic::Bool { val }
    }
}

impl From<i32> for crate::generic::Int32 {
    fn from(val: i32) -> Self {
        crate::generic::Int32 { val }
    }
}

impl From<i64> for crate::generic::Int64 {
    fn from(val: i64) -> Self {
        crate::generic::Int64 { val }
    }
}

impl From<u32> for crate::generic::UInt32 {
    fn from(val: u32) -> Self {
        crate::generic::UInt32 { val }
    }
}

impl From<usize> for crate::generic::UInt32 {
    fn from(val: usize) -> Self {
        crate::generic::UInt32 { val: val as u32 }
    }
}
