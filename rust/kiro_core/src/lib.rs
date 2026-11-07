//! kiro_core: the CPU-heavy helpers of the Kiro Android app.
//! Everything here is plain Rust (no dependencies); `jni_glue` is a thin wrapper for the app.

pub mod captions;
pub mod fuzzy;

#[cfg(feature = "jni-glue")]
mod jni_glue;
