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

pub fn deserialize_int_from_number<'de, T, D>(deserializer: D) -> Result<T, D::Error>
where
    D: Deserializer<'de>,
    T: serde::Deserialize<'de> + FromF64,
{
    #[derive(DeTrait)]
    #[serde(untagged)]
    enum IntOrFloat<T> {
        Int(T),
        Float(f64),
    }

    match IntOrFloat::<T>::deserialize(deserializer)? {
        IntOrFloat::Float(s) => Ok(T::from_f64(s)),
        IntOrFloat::Int(i) => Ok(i),
    }
}

// It may be possible to use the num_traits crate instead in the future.
pub trait FromF64 {
    fn from_f64(val: f64) -> Self;
}

impl FromF64 for i32 {
    fn from_f64(val: f64) -> Self {
        val as Self
    }
}

impl FromF64 for u32 {
    fn from_f64(val: f64) -> Self {
        val as Self
    }
}

impl FromF64 for i64 {
    fn from_f64(val: f64) -> Self {
        val as Self
    }
}

impl FromF64 for TimestampSecs {
    fn from_f64(val: f64) -> Self {
        TimestampSecs(val as i64)
    }
}
