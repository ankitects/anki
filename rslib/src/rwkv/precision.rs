// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Compact card/note state. Computation and the shared scopes remain f32.

use std::borrow::Cow;
use std::io;

use half::f16;

use super::Cursor;
use super::LayerState;
use super::ModuleState;
use super::TimeState;
use super::D_MODEL;
use super::HEADS;
use super::HEAD_SIZE;

pub(super) const MAGIC: &[u8] = b"ARWKVMODSTATE2";

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub enum StatePrecision {
    #[default]
    F32,
    Int8,
    Int4,
    /// Runtime policy; modules resolve this to Int4 cards and Int8 notes.
    Int4CardsInt8Notes,
}

impl StatePrecision {
    pub fn parse(value: &str) -> io::Result<Self> {
        match value {
            "f32" => Ok(Self::F32),
            "int8" => Ok(Self::Int8),
            "cards_int4_notes_int8" => Ok(Self::Int4CardsInt8Notes),
            _ => Err(invalid_state()),
        }
    }

    fn limit(self) -> f32 {
        match self {
            Self::Int8 => 127.0,
            Self::Int4 => 7.0,
            Self::F32 | Self::Int4CardsInt8Notes => unreachable!(),
        }
    }

    fn row_bytes(self) -> usize {
        4 + match self {
            Self::Int8 => HEAD_SIZE,
            Self::Int4 => HEAD_SIZE / 2,
            Self::F32 | Self::Int4CardsInt8Notes => unreachable!(),
        }
    }

    pub(super) fn round_matrix(self, values: &mut [f32], packed: &mut Option<PackedMatrix>) {
        if self == Self::F32 {
            return;
        }
        let packed = packed.get_or_insert_with(|| PackedMatrix {
            precision: self,
            bytes: Vec::new(),
        });
        packed.precision = self;
        packed.bytes.clear();
        encode_matrix(self, values, &mut packed.bytes);
        decode_matrix(self, &packed.bytes, values);
    }

    pub(super) fn round_shift(self, values: &mut [f32]) {
        if self != Self::F32 {
            for value in values {
                *value = f16::from_f32(*value).to_f32();
            }
        }
    }
}

impl ModuleState {
    pub(super) fn try_decoded(&self) -> io::Result<Cow<'_, Self>> {
        match &self.packed {
            Some(bytes) => decode(bytes).map(Cow::Owned),
            None => Ok(Cow::Borrowed(self)),
        }
    }

    pub(super) fn decoded(&self) -> Cow<'_, Self> {
        self.try_decoded()
            .expect("packed state was validated on load")
    }

    pub(super) fn into_decoded(self) -> Self {
        match &self.packed {
            Some(bytes) => decode(bytes).expect("packed state was validated on load"),
            None => self,
        }
    }

    pub(super) fn compact(self, precision: StatePrecision) -> Self {
        if precision == StatePrecision::F32 {
            return self;
        }
        let mut bytes = Vec::new();
        bytes.extend_from_slice(MAGIC);
        bytes.push(if precision == StatePrecision::Int8 {
            8
        } else {
            4
        });
        super::write_u32(&mut bytes, self.layers.len() as u32);
        for layer in self.layers {
            if let Some(time) = layer.time {
                write_shift(&mut bytes, &time.x_shift);
                super::write_u32(&mut bytes, time.matrix.len() as u32);
                if let Some(packed) = time
                    .packed_matrix
                    .filter(|packed| packed.precision == precision)
                {
                    bytes.extend(packed.bytes);
                } else {
                    encode_matrix(precision, &time.matrix, &mut bytes);
                }
            } else {
                super::write_u32(&mut bytes, 0);
                super::write_u32(&mut bytes, 0);
            }
            write_shift(&mut bytes, layer.channel_shift.as_deref().unwrap_or(&[]));
        }
        Self {
            layers: Vec::new(),
            packed: Some(bytes),
        }
    }
}

fn write_shift(out: &mut Vec<u8>, values: &[f32]) {
    super::write_u32(out, values.len() as u32);
    for value in values {
        out.extend_from_slice(&f16::from_f32(*value).to_bits().to_le_bytes());
    }
}

fn read_shift(cursor: &mut Cursor<'_>) -> io::Result<Vec<f32>> {
    let count = cursor.u32()? as usize;
    if count != 0 && count != D_MODEL {
        return Err(invalid_state());
    }
    cursor
        .bytes(count * 2)?
        .chunks_exact(2)
        .map(|bytes| {
            let value = f16::from_bits(u16::from_le_bytes(bytes.try_into().unwrap())).to_f32();
            if value.is_finite() {
                Ok(value)
            } else {
                Err(io::Error::new(
                    io::ErrorKind::InvalidData,
                    "invalid RWKV compact state: non-finite shift",
                ))
            }
        })
        .collect()
}

pub(super) fn decode(bytes: &[u8]) -> io::Result<ModuleState> {
    let mut cursor = Cursor::new(bytes);
    cursor.expect_magic(MAGIC)?;
    let precision = match cursor.u8()? {
        8 => StatePrecision::Int8,
        4 => StatePrecision::Int4,
        _ => return Err(invalid_state()),
    };
    let count = cursor.u32()? as usize;
    if !(1..=4).contains(&count) {
        return Err(invalid_state());
    }
    let mut layers = Vec::with_capacity(count);
    for _ in 0..count {
        let x_shift = read_shift(&mut cursor)?;
        let matrix_count = cursor.u32()? as usize;
        if matrix_count != 0 && matrix_count != HEADS * HEAD_SIZE * HEAD_SIZE {
            return Err(invalid_state());
        }
        let packed = cursor.bytes(matrix_count / HEAD_SIZE * precision.row_bytes())?;
        for row in packed.chunks_exact(precision.row_bytes()) {
            let scale = f32::from_le_bytes(row[..4].try_into().unwrap());
            if !scale.is_finite() || scale <= 0.0 {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidData,
                    "invalid RWKV compact state: non-finite or non-positive matrix scale",
                ));
            }
            let invalid_code = if precision == StatePrecision::Int8 {
                row[4..].contains(&128)
            } else {
                row[4..].iter().any(|byte| byte & 15 == 8 || byte >> 4 == 8)
            };
            if invalid_code {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidData,
                    "invalid RWKV compact state: out-of-range matrix code",
                ));
            }
        }
        let channel_shift = read_shift(&mut cursor)?;
        if x_shift.is_empty() != (matrix_count == 0) {
            return Err(invalid_state());
        }
        let time = if matrix_count == 0 {
            None
        } else {
            let mut matrix = vec![0.0; matrix_count];
            decode_matrix(precision, packed, &mut matrix);
            if matrix.iter().any(|value| !value.is_finite()) {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidData,
                    "invalid RWKV compact state: non-finite matrix value",
                ));
            }
            Some(TimeState {
                x_shift,
                matrix,
                packed_matrix: Some(PackedMatrix {
                    precision,
                    bytes: packed.to_vec(),
                }),
            })
        };
        layers.push(LayerState {
            time,
            channel_shift: (!channel_shift.is_empty()).then_some(channel_shift),
        });
    }
    cursor.expect_end()?;
    Ok(ModuleState {
        layers,
        packed: None,
    })
}

/// Kept only while bulk replay holds a decoded layer, so its final packed
/// representation preserves the exact scales from the last answer.
#[derive(Clone)]
pub(super) struct PackedMatrix {
    precision: StatePrecision,
    bytes: Vec<u8>,
}

fn encode_matrix(precision: StatePrecision, values: &[f32], out: &mut Vec<u8>) {
    out.reserve(values.len() / HEAD_SIZE * precision.row_bytes());
    for row in values.chunks_exact(HEAD_SIZE) {
        let maximum = row.iter().map(|v| v.abs()).fold(0.0_f32, f32::max);
        let scale = if maximum == 0.0 {
            1.0
        } else {
            (maximum / precision.limit()).max(f32::from_bits(1))
        };
        out.extend_from_slice(&scale.to_le_bytes());
        let codes: [i8; HEAD_SIZE] = std::array::from_fn(|i| {
            (row[i] / scale)
                .round_ties_even()
                .clamp(-precision.limit(), precision.limit()) as i8
        });
        if precision == StatePrecision::Int8 {
            out.extend_from_slice(&codes.map(|code| code as u8));
        } else {
            let packed: [u8; HEAD_SIZE / 2] = std::array::from_fn(|index| {
                (codes[2 * index] as u8 & 15) | ((codes[2 * index + 1] as u8 & 15) << 4)
            });
            out.extend_from_slice(&packed);
        }
    }
}

fn decode_matrix(precision: StatePrecision, bytes: &[u8], out: &mut [f32]) {
    for (row, encoded) in out
        .chunks_exact_mut(HEAD_SIZE)
        .zip(bytes.chunks_exact(precision.row_bytes()))
    {
        let scale = f32::from_le_bytes(encoded[..4].try_into().unwrap());
        for (column, value) in row.iter_mut().enumerate() {
            let code = if precision == StatePrecision::Int8 {
                encoded[4 + column] as i8
            } else {
                let nibble = (encoded[4 + column / 2] >> (4 * (column % 2))) & 15;
                ((nibble as i8) << 4) >> 4
            };
            *value = code as f32 * scale;
        }
    }
}

fn invalid_state() -> io::Error {
    io::Error::new(
        io::ErrorKind::InvalidData,
        "invalid RWKV state precision or compact state",
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rwkv_state_precision_codes_use_signed_rows_and_even_rounding() {
        for precision in [StatePrecision::Int8, StatePrecision::Int4] {
            let mut row = [0.0; HEAD_SIZE];
            row[..7].copy_from_slice(&[
                precision.limit(),
                -precision.limit(),
                0.5,
                1.5,
                -0.5,
                -1.5,
                0.0,
            ]);
            let mut packed = None;
            precision.round_matrix(&mut row, &mut packed);
            assert_eq!(
                &row[..7],
                &[
                    precision.limit(),
                    -precision.limit(),
                    0.0,
                    2.0,
                    0.0,
                    -2.0,
                    0.0
                ]
            );
            assert_eq!(packed.unwrap().bytes.len(), precision.row_bytes());
            let mut zero = [0.0; HEAD_SIZE];
            precision.round_matrix(&mut zero, &mut None);
            assert_eq!(zero, [0.0; HEAD_SIZE]);
        }
    }

    #[test]
    fn rwkv_state_precision_rejects_corrupt_lengths_scales_and_codes() {
        let layer = LayerState {
            time: Some(TimeState {
                x_shift: vec![1.0; D_MODEL],
                matrix: vec![1.0; HEADS * HEAD_SIZE * HEAD_SIZE],
                packed_matrix: None,
            }),
            channel_shift: Some(vec![1.0; D_MODEL]),
        };
        for precision in [StatePrecision::Int8, StatePrecision::Int4] {
            let packed = ModuleState {
                layers: vec![layer.clone(); 2],
                packed: None,
            }
            .compact(precision);
            let bytes = packed.packed.unwrap();
            assert!(decode(&bytes).is_ok());
            for length in [0, MAGIC.len(), bytes.len() - 1] {
                assert!(decode(&bytes[..length]).is_err());
            }
            let mut corrupt = bytes.clone();
            corrupt[MAGIC.len()] = 16;
            assert_eq!(
                decode(&corrupt).err().unwrap().kind(),
                io::ErrorKind::InvalidData
            );
            let scale_start = MAGIC.len() + 1 + 4 + 4 + 2 * D_MODEL + 4;
            corrupt = bytes.clone();
            corrupt[scale_start..scale_start + 4].copy_from_slice(&f32::NAN.to_le_bytes());
            assert_eq!(
                decode(&corrupt).err().unwrap().kind(),
                io::ErrorKind::InvalidData
            );
            corrupt = bytes.clone();
            corrupt[scale_start + 4] = if precision == StatePrecision::Int8 {
                128
            } else {
                8
            };
            assert_eq!(
                decode(&corrupt).err().unwrap().kind(),
                io::ErrorKind::InvalidData
            );
            corrupt = bytes.clone();
            corrupt[MAGIC.len() + 1..MAGIC.len() + 5].copy_from_slice(&u32::MAX.to_le_bytes());
            assert_eq!(
                decode(&corrupt).err().unwrap().kind(),
                io::ErrorKind::InvalidData
            );
            // Loading and querying must not re-quantize scales or codes.
            assert_eq!(
                decode(&bytes).unwrap().compact(precision).packed.unwrap(),
                bytes
            );
        }
    }
}
