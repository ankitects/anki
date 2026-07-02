// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/// The standard Rust chain does not implement ExactSizeIterator, and we need
/// to keep track of size so we can intersperse.
pub(crate) struct SizedChain<I, I2> {
    one: I,
    two: I2,
    one_idx: usize,
    two_idx: usize,
    one_len: usize,
    two_len: usize,
}

impl<I, I2> SizedChain<I, I2>
where
    I: ExactSizeIterator,
    I2: ExactSizeIterator<Item = I::Item>,
{
    pub fn new(one: I, two: I2) -> Self {
        let one_len = one.len();
        let two_len = two.len();
        SizedChain {
            one,
            two,
            one_idx: 0,
            two_idx: 0,
            one_len,
            two_len,
        }
    }
}

impl<I, I2> Iterator for SizedChain<I, I2>
where
    I: ExactSizeIterator,
    I2: ExactSizeIterator<Item = I::Item>,
{
    type Item = I::Item;

    fn next(&mut self) -> Option<Self::Item> {
        if self.one_idx < self.one_len {
            self.one_idx += 1;
            self.one.next()
        } else if self.two_idx < self.two_len {
            self.two_idx += 1;
            self.two.next()
        } else {
            None
        }
    }

    fn size_hint(&self) -> (usize, Option<usize>) {
        let remaining = (self.one_len + self.two_len) - (self.one_idx + self.two_idx);
        (remaining, Some(remaining))
    }
}

impl<I, I2> ExactSizeIterator for SizedChain<I, I2>
where
    I: ExactSizeIterator,
    I2: ExactSizeIterator<Item = I::Item>,
{
}

#[cfg(test)]
mod test {
    use super::SizedChain;

    fn chain(a: &[u32], b: &[u32]) -> Vec<u32> {
        SizedChain::new(a.iter().cloned(), b.iter().cloned()).collect()
    }

    #[test]
    fn sized_chain() {
        let a = &[1, 2, 3];
        let b = &[11, 22, 33];
        assert_eq!(&chain(a, b), &[1, 2, 3, 11, 22, 33]);
    }
}
