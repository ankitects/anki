// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use serde::Deserialize as DeTrait;
use serde_json::Value;

pub(crate) fn default_on_invalid<'de, T, D>(deserializer: D) -> Result<T, D::Error>
where
    T: Default + DeTrait<'de>,
    D: serde::de::Deserializer<'de>,
{
    let v: Value = DeTrait::deserialize(deserializer)?;
    Ok(T::deserialize(v).unwrap_or_default())
}
