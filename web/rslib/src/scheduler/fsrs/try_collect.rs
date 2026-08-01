// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::error::AnkiError;
use crate::invalid_input;

// Roll our own implementation until this becomes stable
// https://github.com/rust-lang/rust/issues/94047
#[allow(unused)]
pub(crate) trait TryCollect: ExactSizeIterator {
    fn try_collect<const N: usize>(self) -> Result<[Self::Item; N], AnkiError>
    where
        // Self: Sized,
        Self::Item: Copy + Default;
}

impl<I, T> TryCollect for I
where
    I: ExactSizeIterator<Item = T>,
    T: Copy + Default,
{
    fn try_collect<const N: usize>(self) -> Result<[T; N], AnkiError> {
        if self.len() != N {
            invalid_input!("expected {N}; got {}", self.len());
        }

        let mut result = [T::default(); N];
        for (index, value) in self.enumerate() {
            result[index] = value;
        }

        Ok(result)
    }
}
