// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#[macro_export]
macro_rules! define_newtype {
    ( $name:ident, $type:ident ) => {
        #[repr(transparent)]
        #[derive(
            Debug,
            Default,
            Clone,
            Copy,
            PartialEq,
            Eq,
            PartialOrd,
            Ord,
            Hash,
            serde::Serialize,
            serde::Deserialize,
        )]
        pub struct $name(pub $type);

        impl std::fmt::Display for $name {
            fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
                self.0.fmt(f)
            }
        }

        impl std::str::FromStr for $name {
            type Err = std::num::ParseIntError;
            fn from_str(s: &std::primitive::str) -> std::result::Result<Self, Self::Err> {
                $type::from_str(s).map($name)
            }
        }

        impl rusqlite::types::FromSql for $name {
            fn column_result(
                value: rusqlite::types::ValueRef<'_>,
            ) -> std::result::Result<Self, rusqlite::types::FromSqlError> {
                if let rusqlite::types::ValueRef::Integer(i) = value {
                    Ok(Self(i as $type))
                } else {
                    Err(rusqlite::types::FromSqlError::InvalidType)
                }
            }
        }

        impl rusqlite::ToSql for $name {
            fn to_sql(&self) -> ::rusqlite::Result<rusqlite::types::ToSqlOutput<'_>> {
                Ok(rusqlite::types::ToSqlOutput::Owned(
                    rusqlite::types::Value::Integer(self.0 as i64),
                ))
            }
        }

        impl From<$type> for $name {
            fn from(t: $type) -> $name {
                $name(t)
            }
        }

        impl From<$name> for $type {
            fn from(n: $name) -> $type {
                n.0
            }
        }
    };
}

define_newtype!(Usn, i32);

pub(crate) trait IntoNewtypeVec {
    fn into_newtype<F, T>(self, func: F) -> Vec<T>
    where
        F: FnMut(i64) -> T;
}

impl IntoNewtypeVec for Vec<i64> {
    fn into_newtype<F, T>(self, func: F) -> Vec<T>
    where
        F: FnMut(i64) -> T,
    {
        self.into_iter().map(func).collect()
    }
}
