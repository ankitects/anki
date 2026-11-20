// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Offline block-compression experiment; desktop checkpoint formats are
//! unchanged.

use std::io::Read;
use std::io::Seek;
use std::io::SeekFrom;
use std::io::Write;
use std::time::Instant;

use serde::Deserialize;
use serde_json::json;

use super::state_scope_bench::Row;
use super::*;

const MAGIC: &[u8] = b"ARWKVZBENCH1";
const GENERATION: &str = "zstd-bench";
const MAX_BLOCK_BYTES: usize = 16 * 1024 * 1024;

fn invalid(message: &str) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, message)
}

struct BlockWriter {
    file: fs::File,
    block: Vec<u8>,
    block_bytes: usize,
    remaining: u64,
    compressor: Option<zstd::bulk::Compressor<'static>>,
}

impl BlockWriter {
    fn new(path: &Path, level: i32, block_bytes: usize, payload_bytes: u64) -> io::Result<Self> {
        if !(0..=3).contains(&level) || !(1..=MAX_BLOCK_BYTES).contains(&block_bytes) {
            return Err(invalid("invalid benchmark block configuration"));
        }
        let mut file = fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(path)?;
        file.write_all(MAGIC)?;
        file.write_all(&level.to_le_bytes())?;
        file.write_all(&(block_bytes as u32).to_le_bytes())?;
        file.write_all(&payload_bytes.to_le_bytes())?;
        Ok(Self {
            file,
            block: Vec::with_capacity(block_bytes),
            block_bytes,
            remaining: payload_bytes,
            compressor: (level != 0)
                .then(|| zstd::bulk::Compressor::new(level))
                .transpose()?,
        })
    }

    fn write_block(&mut self) -> io::Result<()> {
        if self.block.is_empty() {
            return Ok(());
        }
        let compressed = match &mut self.compressor {
            Some(compressor) => Some(compressor.compress(&self.block)?),
            None => None,
        };
        let stored = compressed.as_deref().unwrap_or(&self.block);
        self.file
            .write_all(&(self.block.len() as u32).to_le_bytes())?;
        self.file.write_all(&(stored.len() as u32).to_le_bytes())?;
        self.file.write_all(stored)?;
        self.block.clear();
        Ok(())
    }

    fn finish(mut self) -> io::Result<()> {
        if self.remaining != 0 {
            return Err(invalid("incomplete benchmark checkpoint write"));
        }
        self.write_block()?;
        self.file.write_all(&[0; 8])?;
        self.file.flush()?;
        self.file.sync_all()
    }
}

impl Write for BlockWriter {
    fn write(&mut self, mut bytes: &[u8]) -> io::Result<usize> {
        let count = bytes.len();
        if count as u64 > self.remaining {
            return Err(invalid("benchmark checkpoint exceeds declared length"));
        }
        while !bytes.is_empty() {
            let take = bytes.len().min(self.block_bytes - self.block.len());
            self.block.extend_from_slice(&bytes[..take]);
            bytes = &bytes[take..];
            if self.block.len() == self.block_bytes {
                self.write_block()?;
            }
        }
        self.remaining -= count as u64;
        Ok(count)
    }

    fn flush(&mut self) -> io::Result<()> {
        self.file.flush()
    }
}

struct BlockReader {
    file: fs::File,
    block_bytes: usize,
    payload_bytes: u64,
    remaining_frames: u64,
    position: u64,
    block: Vec<u8>,
    offset: usize,
    stored: Vec<u8>,
    decompressor: Option<zstd::bulk::Decompressor<'static>>,
    ended: bool,
}

impl BlockReader {
    fn open(path: &Path) -> io::Result<Self> {
        let mut file = fs::File::open(path)?;
        let mut magic = vec![0; MAGIC.len()];
        file.read_exact(&mut magic)?;
        if magic != MAGIC {
            return Err(invalid("invalid benchmark checkpoint header"));
        }
        let level = read_state_cache_u32(&mut file)?;
        let block_bytes = read_state_cache_u32(&mut file)? as usize;
        let mut length = [0; 8];
        file.read_exact(&mut length)?;
        let payload_bytes = u64::from_le_bytes(length);
        if level > 3 || !(1..=MAX_BLOCK_BYTES).contains(&block_bytes) {
            return Err(invalid("invalid benchmark block configuration"));
        }
        Ok(Self {
            file,
            block_bytes,
            payload_bytes,
            remaining_frames: payload_bytes,
            position: 0,
            block: Vec::new(),
            offset: 0,
            stored: Vec::new(),
            decompressor: (level != 0)
                .then(zstd::bulk::Decompressor::new)
                .transpose()?,
            ended: false,
        })
    }

    fn frame_header(&mut self) -> io::Result<Option<(usize, usize)>> {
        if self.ended {
            return Ok(None);
        }
        let raw = read_state_cache_u32(&mut self.file)? as usize;
        let stored = read_state_cache_u32(&mut self.file)? as usize;
        if raw == 0 && stored == 0 {
            if self.remaining_frames != 0 || self.file.read(&mut [0])? != 0 {
                return Err(invalid("incomplete or trailing benchmark checkpoint data"));
            }
            self.ended = true;
            return Ok(None);
        }
        if raw == 0
            || raw > self.block_bytes
            || raw as u64 > self.remaining_frames
            || stored == 0
            || stored > self.block_bytes * 2 + 1024
            || (self.decompressor.is_none() && raw != stored)
            || self.file.stream_position()? + stored as u64 > self.file.metadata()?.len()
        {
            return Err(invalid("invalid or truncated benchmark checkpoint frame"));
        }
        self.remaining_frames -= raw as u64;
        Ok(Some((raw, stored)))
    }

    fn load_frame(&mut self, raw: usize, stored: usize) -> io::Result<()> {
        self.block.resize(raw, 0);
        if let Some(decompressor) = &mut self.decompressor {
            self.stored.resize(stored, 0);
            self.file.read_exact(&mut self.stored)?;
            let decoded = decompressor.decompress_to_buffer(&self.stored, &mut self.block[..])?;
            if decoded != raw {
                return Err(invalid("incorrect decoded benchmark block length"));
            }
        } else {
            self.file.read_exact(&mut self.block)?;
        }
        self.offset = 0;
        Ok(())
    }
}

impl Read for BlockReader {
    fn read(&mut self, bytes: &mut [u8]) -> io::Result<usize> {
        if bytes.is_empty() {
            return Ok(0);
        }
        if self.offset == self.block.len() {
            let Some((raw, stored)) = self.frame_header()? else {
                return Ok(0);
            };
            self.load_frame(raw, stored)?;
        }
        let take = bytes.len().min(self.block.len() - self.offset);
        bytes[..take].copy_from_slice(&self.block[self.offset..self.offset + take]);
        self.offset += take;
        self.position += take as u64;
        Ok(take)
    }
}

impl Seek for BlockReader {
    fn seek(&mut self, position: SeekFrom) -> io::Result<u64> {
        let SeekFrom::Current(offset @ 0..) = position else {
            return Err(invalid(
                "benchmark reader supports only forward relative seeks",
            ));
        };
        let mut remaining = offset as u64;
        if remaining > self.payload_bytes - self.position {
            return Err(invalid("benchmark seek exceeds checkpoint length"));
        }
        let take = remaining.min((self.block.len() - self.offset) as u64);
        self.offset += take as usize;
        self.position += take;
        remaining -= take;
        while remaining != 0 {
            let (raw, stored) = self
                .frame_header()?
                .ok_or_else(|| invalid("truncated benchmark seek"))?;
            if remaining >= raw as u64 {
                self.file.seek(SeekFrom::Current(stored as i64))?;
                self.position += raw as u64;
                remaining -= raw as u64;
            } else {
                self.load_frame(raw, stored)?;
                self.offset = remaining as usize;
                self.position += remaining;
                remaining = 0;
            }
        }
        Ok(self.position)
    }
}

#[derive(Deserialize)]
struct Request {
    action: String,
    source: Option<PathBuf>,
    checkpoint: PathBuf,
    reference: Option<PathBuf>,
    codec: String,
    input: Option<PathBuf>,
    weights: PathBuf,
    precision: String,
    block_bytes: usize,
}

fn event(value: serde_json::Value) {
    println!("RWKV_CHECKPOINT_EVENT={value}");
    io::stdout().flush().unwrap();
    if std::env::var("ANKI_RWKV_CHECKPOINT_HANDSHAKE").as_deref() == Ok("1") {
        let mut acknowledgment = String::new();
        assert!(io::stdin().read_line(&mut acknowledgment).unwrap() > 0);
    }
}

fn process_peak_rss() -> Option<u64> {
    #[cfg(unix)]
    {
        let mut usage = std::mem::MaybeUninit::<libc::rusage>::uninit();
        // SAFETY: getrusage initializes usage when it succeeds.
        if unsafe { libc::getrusage(libc::RUSAGE_SELF, usage.as_mut_ptr()) } == 0 {
            let peak = unsafe { usage.assume_init() }.ru_maxrss as u64;
            return Some(if cfg!(target_os = "macos") {
                peak
            } else {
                peak * 1024
            });
        }
    }
    None
}

fn timed<T>(phase: &str, call: impl FnOnce() -> T) -> T {
    release_free_memory_to_os();
    event(json!({"kind": "start", "phase": phase}));
    let started = Instant::now();
    let result = call();
    let wall_seconds = started.elapsed().as_secs_f64();
    event(
        json!({"kind": "end", "phase": phase, "wall_seconds": wall_seconds,
        "process_peak_rss_bytes": process_peak_rss()}),
    );
    result
}

fn write_sqlite(inference: &mut RwkvInference, path: &Path, review_count: i64, last_review: i64) {
    assert!(
        !path.exists(),
        "benchmark refuses to overwrite a checkpoint"
    );
    let segment = inference
        .write_warm_up_state_checkpoint(
            path.to_path_buf(),
            GENERATION,
            None,
            last_review,
            review_count,
            "history",
            "replay",
            &[],
            &[],
            &[],
            true,
            true,
        )
        .unwrap();
    inference.finish_warm_up_state_checkpoints().unwrap();
    assert_eq!(segment, 1);
}

fn payload_bytes(inference: &RwkvInference) -> usize {
    4 + inference.runtime_cache_state_len()
        + inference.warm_up_states.checkpoint_delta_len(true).unwrap()
}

fn write_payload(inference: &RwkvInference, out: &mut impl Write) -> io::Result<()> {
    write_state_cache_u32(out, inference.runtime_cache_state_len())?;
    inference.write_runtime_cache_state(out)?;
    inference.warm_up_states.write_checkpoint_delta(out, true)
}

fn restore_blocks(inference: &mut RwkvInference, path: &Path) -> io::Result<()> {
    let mut input = BlockReader::open(path)?;
    let runtime_len = read_state_cache_u32(&mut input)? as usize;
    if runtime_len as u64 > input.payload_bytes - input.position {
        return Err(invalid("invalid benchmark runtime-state length"));
    }
    let mut runtime = vec![0; runtime_len];
    input.read_exact(&mut runtime)?;
    let mut magic = vec![0; STATE_CACHE_DELTA_MAGIC.len()];
    input.read_exact(&mut magic)?;
    if magic != STATE_CACHE_DELTA_MAGIC {
        return Err(invalid("invalid benchmark state-delta header"));
    }
    let mut states = ReviewStateMaps::default();
    let mut state_bytes = Vec::new();
    for map in [
        &mut states.card as &mut dyn StateMap,
        &mut states.note,
        &mut states.deck,
        &mut states.preset,
    ] {
        read_state_cache_map_delta(&mut input, map, &mut HashSet::new(), &mut state_bytes)?;
    }
    states.global = match read_state_cache_u8(&mut input)? {
        1 => None,
        2 => {
            let size = read_state_cache_u32(&mut input)?;
            read_state_cache_module_state(&mut input, size, &mut state_bytes)?
        }
        _ => return Err(invalid("invalid benchmark full-root global state")),
    };
    if input.read(&mut [0])? != 0 {
        return Err(invalid("trailing benchmark state-delta data"));
    }
    let (features, curves) = read_runtime_cache_state(&runtime)?;
    inference.warm_up_states = states;
    inference.features = features;
    inference.curves = curves;
    Ok(())
}

struct DigestWriter(blake3::Hasher);

impl Write for DigestWriter {
    fn write(&mut self, bytes: &[u8]) -> io::Result<usize> {
        self.0.update(bytes);
        Ok(bytes.len())
    }

    fn flush(&mut self) -> io::Result<()> {
        Ok(())
    }
}

fn summary(request: &Request, inference: &RwkvInference, reviews: Option<usize>) {
    let mut state = DigestWriter(blake3::Hasher::new());
    inference
        .warm_up_states
        .write_checkpoint_delta(&mut state, true)
        .unwrap();
    let mut runtime = DigestWriter(blake3::Hasher::new());
    inference.write_runtime_cache_state(&mut runtime).unwrap();
    event(
        json!({"kind": "summary", "action": request.action, "codec": request.codec,
        "state_blake3": state.0.finalize().to_hex().to_string(),
        "runtime_blake3": runtime.0.finalize().to_hex().to_string(),
        "entities": {"cards": inference.warm_up_states.card.len(),
            "notes": inference.warm_up_states.note.len(), "decks": inference.warm_up_states.deck.len(),
            "presets": inference.warm_up_states.preset.len(), "global": usize::from(inference.warm_up_states.global.is_some())},
        "payload_bytes": payload_bytes(inference), "block_bytes": request.block_bytes,
        "file_bytes": fs::metadata(&request.checkpoint).unwrap().len(), "reviews": reviews}),
    );
}

#[test]
#[ignore]
fn rwkv_checkpoint_compression_case() {
    let request: Request = serde_json::from_slice(
        &fs::read(std::env::var("ANKI_RWKV_CHECKPOINT_REQUEST").unwrap()).unwrap(),
    )
    .unwrap();
    if request.action == "verify" {
        let mut reference = BlockReader::open(request.reference.as_ref().unwrap()).unwrap();
        let mut candidate = BlockReader::open(&request.checkpoint).unwrap();
        assert_eq!(reference.payload_bytes, candidate.payload_bytes);
        let mut expected = vec![0; request.block_bytes];
        let mut actual = vec![0; request.block_bytes];
        let mut remaining = reference.payload_bytes;
        while remaining > 0 {
            let take = remaining.min(request.block_bytes as u64) as usize;
            reference.read_exact(&mut expected[..take]).unwrap();
            candidate.read_exact(&mut actual[..take]).unwrap();
            assert_eq!(
                &expected[..take],
                &actual[..take],
                "decoded checkpoint bytes differ"
            );
            remaining -= take as u64;
        }
        assert_eq!(reference.read(&mut [0]).unwrap(), 0);
        assert_eq!(candidate.read(&mut [0]).unwrap(), 0);
        event(
            json!({"kind": "summary", "action": "verify", "codec": request.codec,
            "byte_identical": true, "payload_bytes": reference.payload_bytes}),
        );
        return;
    }
    let precision = StatePrecision::parse(&request.precision).unwrap();
    let mut inference =
        RwkvInference::load_with_precision(request.weights.clone(), 0.9, 36_500, precision)
            .unwrap();
    if request.action == "prepare" {
        let source: serde_json::Value =
            serde_json::from_slice(&fs::read(request.input.as_ref().unwrap()).unwrap()).unwrap();
        let rows: Vec<Row> = serde_json::from_value(source["rows"].clone()).unwrap();
        drop(source);
        let reviews = rows.len();
        let last_review = rows.last().unwrap().0;
        for chunk in rows.chunks(16_384) {
            let inputs = chunk
                .iter()
                .map(|row| ReviewInput {
                    card_id: row.1,
                    note_id: row.2,
                    deck_id: row.3,
                    preset_id: row.4,
                    is_query: false,
                    ease: Some(row.5),
                    duration_millis: Some(row.6),
                    card_type: Some(row.7),
                    day_offset: Some(row.8),
                    current_elapsed_days: Some(row.9),
                    current_elapsed_seconds: Some(row.10),
                    target_retentions: [None; 4],
                    enforce_grade_order: true,
                })
                .collect();
            inference.warm_up_reviews(inputs, false).unwrap();
        }
        drop(rows);
        timed("save", || {
            write_sqlite(
                &mut inference,
                &request.checkpoint,
                reviews as i64,
                last_review,
            )
        });
        summary(&request, &inference, Some(reviews));
        return;
    }
    if request.action == "save" {
        let source = request.source.as_ref().unwrap();
        assert_ne!(source, &request.checkpoint);
        inference
            .restore_warm_up_state_checkpoint(source.clone(), GENERATION, 1)
            .unwrap();
        timed("save", || {
            if request.codec == "sqlite" {
                write_sqlite(&mut inference, &request.checkpoint, 0, 0);
            } else {
                let level = match request.codec.as_str() {
                    "raw" => 0,
                    "zstd1" => 1,
                    "zstd2" => 2,
                    "zstd3" => 3,
                    _ => panic!("unsupported benchmark codec"),
                };
                let mut out = BlockWriter::new(
                    &request.checkpoint,
                    level,
                    request.block_bytes,
                    payload_bytes(&inference) as u64,
                )
                .unwrap();
                write_payload(&inference, &mut out).unwrap();
                out.finish().unwrap();
            }
        });
    } else if request.action == "restore" {
        timed("restore", || {
            if request.codec == "sqlite" {
                inference
                    .restore_warm_up_state_checkpoint(request.checkpoint.clone(), GENERATION, 1)
                    .unwrap();
            } else {
                restore_blocks(&mut inference, &request.checkpoint).unwrap();
            }
        });
    } else {
        panic!("unsupported benchmark action");
    }
    summary(&request, &inference, None);
}

#[test]
fn rwkv_checkpoint_compression_blocks_roundtrip_and_seek() {
    let directory = tempfile::tempdir().unwrap();
    for level in 0..=3 {
        for length in [0, 17, 35_017] {
            let path = directory.path().join(format!("{level}-{length}"));
            let data = (0..length).map(|i| (i % 251) as u8).collect::<Vec<_>>();
            let mut out = BlockWriter::new(&path, level, 1024, data.len() as u64).unwrap();
            out.write_all(&data).unwrap();
            out.finish().unwrap();
            let mut input = BlockReader::open(&path).unwrap();
            let mut decoded = Vec::new();
            input.read_to_end(&mut decoded).unwrap();
            assert_eq!(decoded, data, "level {level}, length {length}");
            let mut input = BlockReader::open(&path).unwrap();
            for target in [
                0,
                length.min(3),
                length.min(123),
                length.min(30_517),
                length,
            ] {
                let position = input.position;
                input
                    .seek_relative(target as i64 - position as i64)
                    .unwrap();
                if target < length {
                    let mut byte = [0];
                    input.read_exact(&mut byte).unwrap();
                    assert_eq!(byte[0], data[target]);
                }
            }
            assert_eq!(input.read(&mut [0]).unwrap(), 0);
        }
    }
}

#[test]
fn rwkv_checkpoint_compression_rejects_truncation_and_trailing_data() {
    let directory = tempfile::tempdir().unwrap();
    for level in 0..=3 {
        let path = directory.path().join(format!("{level}"));
        let mut out = BlockWriter::new(&path, level, 1024, 3072).unwrap();
        out.write_all(&vec![5; 3072]).unwrap();
        out.finish().unwrap();
        let valid = fs::read(&path).unwrap();
        for length in [MAGIC.len(), 29, valid.len() - 1, valid.len() - 9] {
            fs::write(&path, &valid[..length]).unwrap();
            let result =
                BlockReader::open(&path).and_then(|mut input| input.read_to_end(&mut Vec::new()));
            assert!(
                result.is_err(),
                "accepted truncation at {length}, level {level}"
            );
        }
        let mut trailing = valid;
        trailing.push(0);
        fs::write(&path, trailing).unwrap();
        assert!(BlockReader::open(&path)
            .unwrap()
            .read_to_end(&mut Vec::new())
            .is_err());
    }
}
