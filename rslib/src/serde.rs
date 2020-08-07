// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::timestamp::TimestampSecs;
use serde::{Deserialize as DeTrait, Deserializer};
pub(crate) use serde_aux::field_attributes::{
    deserialize_bool_from_anything, deserialize_number_from_string,
};
use serde_json::Value;

pub(crate) fn default_on_invalid<'de, T, D>(deserializer: D) -> Result<T, D::Error>
where
    T: Default + DeTrait<'de>,
    D: serde::de::Deserializer<'de>,
{
    let v: Value = DeTrait::deserialize(deserializer)?;
    Ok(T::deserialize(v).unwrap_or_default())
}

pub(crate) fn deserialize_int_from_number<'de, T, D>(deserializer: D) -> Result<T, D::Error>
where
    D: Deserializer<'de>,
    T: serde::Deserialize<'de> + FromI64,
{
    #[derive(DeTrait)]
    #[serde(untagged)]
    enum IntOrFloat {
        Int(i64),
        Float(f64),
    }

    match IntOrFloat::deserialize(deserializer)? {
        IntOrFloat::Float(f) => Ok(T::from_i64(f as i64)),
        IntOrFloat::Int(i) => Ok(T::from_i64(i)),
    }
}

// It may be possible to use the num_traits crate instead in the future.
pub(crate) trait FromI64 {
    fn from_i64(val: i64) -> Self;
}

impl FromI64 for i32 {
    fn from_i64(val: i64) -> Self {
        val as Self
    }
}

impl FromI64 for u32 {
    fn from_i64(val: i64) -> Self {
        val.max(0) as Self
    }
}

impl FromI64 for i64 {
    fn from_i64(val: i64) -> Self {
        val
    }
}

impl FromI64 for TimestampSecs {
    fn from_i64(val: i64) -> Self {
        TimestampSecs(val as i64)
    }
}
