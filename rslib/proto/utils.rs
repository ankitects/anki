// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use prost_types::FileDescriptorProto;

#[derive(Debug)]
pub struct Comments {
    path_map: HashMap<Vec<i32>, String>,
}

impl Comments {
    pub fn from_file(file: &FileDescriptorProto) -> Self {
        Self {
            path_map: file
                .source_code_info
                .as_ref()
                .unwrap()
                .location
                .iter()
                .map(|l| (l.path.clone(), l.leading_comments().trim().to_string()))
                .collect(),
        }
    }

    pub fn get_for_path(&self, path: &[i32]) -> Option<&str> {
        self.path_map.get(path).map(|s| s.as_str()).and_then(|s| {
            if s.is_empty() {
                None
            } else {
                Some(s)
            }
        })
    }
}
