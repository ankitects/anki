// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use serde::Deserialize as DeTrait;
use serde::Deserializer;
pub(crate) use serde_aux::field_attributes::deserialize_bool_from_anything;
pub(crate) use serde_aux::field_attributes::deserialize_number_from_string;
use serde_json::Value;

use crate::text::snake_to_camel_case;
use crate::timestamp::TimestampSecs;

pub(crate) trait FieldNames {
    fn field_names() -> &'static [&'static str];
}

/// Removes any entries matching a field name of the associated struct in camel
/// case, and returns them.
///
/// This function solves a very specific problem when (de)serializing structs on
/// up-/downgrade:
/// Older clients may have received keys from a newer client when
/// syncing, which get bundled into `other`. If they then upgrade, then
/// downgrade their collection to schema11, serde will serialize the
/// new default keys, but then add them again from `other`, leading
/// to the keys being duplicated in the resulting json - which older
/// clients then can't read. So we need to strip out any new keys we
/// add.
pub(crate) fn extract_field_names<T: FieldNames, V>(
    map: &mut HashMap<String, V>,
) -> HashMap<String, V> {
    T::field_names()
        .iter()
        .map(snake_to_camel_case)
        .filter_map(|name| map.remove(&name).map(|val| (name, val)))
        .collect()
}

/// Note: if you wish to cover the case where a field is missing, make sure you
/// also use the `serde(default)` flag.
pub(crate) fn default_on_invalid<'de, T, D>(deserializer: D) -> Result<T, D::Error>
where
    T: Default + DeTrait<'de>,
    D: Deserializer<'de>,
{
    let v: Value = DeTrait::deserialize(deserializer)?;
    Ok(T::deserialize(v).unwrap_or_default())
}

pub(crate) fn is_default<T: Default + PartialEq>(t: &T) -> bool {
    *t == Default::default()
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
        TimestampSecs(val)
    }
}

#[cfg(test)]
mod test {
    use serde::Deserialize;

    use super::*;

    #[derive(Deserialize, Debug, PartialEq, Eq)]
    struct MaybeInvalid {
        #[serde(deserialize_with = "default_on_invalid", default)]
        field: Option<usize>,
    }

    #[test]
    fn invalid_or_missing() {
        assert_eq!(
            serde_json::from_str::<MaybeInvalid>(r#"{"field": 5}"#).unwrap(),
            MaybeInvalid { field: Some(5) }
        );
        assert_eq!(
            serde_json::from_str::<MaybeInvalid>(r#"{"field": "5"}"#).unwrap(),
            MaybeInvalid { field: None }
        );
        assert_eq!(
            serde_json::from_str::<MaybeInvalid>(r#"{"another": 5}"#).unwrap(),
            MaybeInvalid { field: None }
        );
    }
}
