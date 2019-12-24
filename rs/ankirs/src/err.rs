pub use failure::{Error, Fail};

pub type Result<T> = std::result::Result<T, AnkiError>;

#[derive(Debug, Fail)]
pub enum AnkiError {
    #[fail(display = "invalid input: {}", info)]
    InvalidInput { info: String },
}

// error helpers
impl AnkiError {
    pub(crate) fn invalid_input<S: Into<String>>(s: S) -> AnkiError {
        AnkiError::InvalidInput { info: s.into() }
    }
}
