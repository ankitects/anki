// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub(crate) mod batch;
pub(crate) mod dynamic_desired_retention;
mod error;
pub mod memory_state;
pub mod params;
pub(crate) mod preset;
pub mod rescheduler;
pub mod retention;
pub(crate) mod review_time_model;
pub mod simulator;
pub mod try_collect;

/// Preserve the empty-parameter convention of Anki's legacy parameter-only
/// APIs. Version-aware callers must resolve FSRS-7 defaults before using those
/// APIs.
pub(crate) fn legacy_fsrs_params(params: &[f32]) -> &[f32] {
    if params.is_empty() {
        &fsrs::FSRS6_DEFAULT_PARAMETERS
    } else {
        params
    }
}

pub(crate) fn uses_fractional_intervals(params: &[f32]) -> bool {
    params.len() == fsrs::DEFAULT_PARAMETERS.len()
}

pub(crate) fn params_fingerprint(params: &[f32]) -> u64 {
    params.iter().fold(0xcbf29ce484222325, |hash, param| {
        let hash = hash ^ u64::from(param.to_bits());
        hash.wrapping_mul(0x100000001b3)
    })
}

pub(crate) fn round_to_two_decimals(value: f32) -> f32 {
    (value * 100.0).round() / 100.0
}

#[cfg(test)]
mod tests {
    use fsrs::ModelVersion;
    use fsrs::FSRS;

    use super::legacy_fsrs_params;

    #[test]
    fn legacy_empty_params_keep_fsrs6_without_changing_explicit_fsrs7_params() {
        assert_eq!(
            FSRS::new(legacy_fsrs_params(&[])).unwrap().version(),
            ModelVersion::Fsrs6
        );
        assert_eq!(
            FSRS::new(legacy_fsrs_params(&fsrs::DEFAULT_PARAMETERS))
                .unwrap()
                .version(),
            ModelVersion::Fsrs7
        );
    }
}
