// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/// Adapter to evenly mix two iterators of varying lengths into one.
pub(crate) struct Intersperser<I, I2>
where
    I: Iterator + ExactSizeIterator,
{
    one: I,
    two: I2,
    one_idx: usize,
    two_idx: usize,
    one_len: usize,
    two_len: usize,
    ratio: f32,
}

impl<I, I2> Intersperser<I, I2>
where
    I: ExactSizeIterator,
    I2: ExactSizeIterator<Item = I::Item>,
{
    pub fn new(one: I, two: I2) -> Self {
        let one_len = one.len();
        let two_len = two.len();
        let ratio = (one_len + 1) as f32 / (two_len + 1) as f32;
        Intersperser {
            one,
            two,
            one_idx: 0,
            two_idx: 0,
            one_len,
            two_len,
            ratio,
        }
    }

    fn one_idx(&self) -> Option<usize> {
        if self.one_idx == self.one_len {
            None
        } else {
            Some(self.one_idx)
        }
    }

    fn two_idx(&self) -> Option<usize> {
        if self.two_idx == self.two_len {
            None
        } else {
            Some(self.two_idx)
        }
    }

    fn next_one(&mut self) -> Option<I::Item> {
        self.one_idx += 1;
        self.one.next()
    }

    fn next_two(&mut self) -> Option<I::Item> {
        self.two_idx += 1;
        self.two.next()
    }
}

impl<I, I2> Iterator for Intersperser<I, I2>
where
    I: ExactSizeIterator,
    I2: ExactSizeIterator<Item = I::Item>,
{
    type Item = I::Item;

    fn next(&mut self) -> Option<Self::Item> {
        match (self.one_idx(), self.two_idx()) {
            (Some(idx1), Some(idx2)) => {
                let relative_idx2 = (idx2 + 1) as f32 * self.ratio;
                if relative_idx2 < (idx1 + 1) as f32 {
                    self.next_two()
                } else {
                    self.next_one()
                }
            }
            (Some(_), None) => self.next_one(),
            (None, Some(_)) => self.next_two(),
            (None, None) => None,
        }
    }

    fn size_hint(&self) -> (usize, Option<usize>) {
        let remaining = (self.one_len + self.two_len) - (self.one_idx + self.two_idx);
        (remaining, Some(remaining))
    }
}

impl<I, I2> ExactSizeIterator for Intersperser<I, I2>
where
    I: ExactSizeIterator,
    I2: ExactSizeIterator<Item = I::Item>,
{
}

#[cfg(test)]
mod test {
    use super::Intersperser;

    fn intersperse(a: &[u32], b: &[u32]) -> Vec<u32> {
        Intersperser::new(a.iter().cloned(), b.iter().cloned()).collect()
    }

    #[test]
    fn interspersing() {
        let a = &[1, 2, 3];
        let b = &[11, 22, 33];
        assert_eq!(&intersperse(a, b), &[1, 11, 2, 22, 3, 33]);

        let b = &[11, 22];
        assert_eq!(&intersperse(a, b), &[1, 11, 2, 22, 3]);

        // always add from longer iter first
        let b = &[11, 22, 33, 44, 55, 66];
        assert_eq!(&intersperse(a, b), &[11, 1, 22, 33, 2, 44, 55, 3, 66]);

        // space is distributed as evenly as possible between elements of
        // the same iter and start and end
        let b = &[11, 22, 33, 44, 55, 66, 77, 88];
        assert_eq!(
            &intersperse(a, b),
            &[11, 22, 1, 33, 44, 2, 55, 66, 3, 77, 88]
        );

        let b = &[];
        assert_eq!(&intersperse(a, b), &[1, 2, 3]);
    }
}
