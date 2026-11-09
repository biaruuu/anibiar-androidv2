//! Photo upload planning: how far to shrink a picked photo so it sends fast without looking soft.

/// Returns `(sample, out_w, out_h, quality, passthrough)`.
/// `passthrough` means the original bytes are already small enough to send untouched (no re-encode, EXIF kept).
pub fn plan(w: u32, h: u32, bytes: u64, metered: bool) -> (u32, u32, u32, u32, bool) {
    let (w, h) = (w.max(1), h.max(1));
    let max_side: u32 = if metered { 1024 } else { 1280 };
    let quality = if metered { 74 } else { 82 };
    let big = w.max(h);
    if big <= max_side && bytes <= 300_000 {
        return (1, w, h, quality, true);
    }
    let scale = (max_side as f64 / big as f64).min(1.0);
    let ow = ((w as f64 * scale).round() as u32).max(1);
    let oh = ((h as f64 * scale).round() as u32).max(1);
    // Decode at the largest power-of-two reduction that still has at least `max_side` pixels on the long edge.
    let mut sample = 1;
    while big / (sample * 2) >= max_side {
        sample *= 2;
    }
    (sample, ow, oh, quality, false)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn small_photo_is_sent_as_is() {
        assert_eq!(plan(1000, 800, 120_000, false), (1, 1000, 800, 82, true));
    }

    #[test]
    fn big_photo_is_scaled_and_sampled() {
        let (sample, w, h, q, pass) = plan(4000, 3000, 4_000_000, false);
        assert_eq!((sample, w, h, q, pass), (2, 1280, 960, 82, false));
    }

    #[test]
    fn metered_connections_get_smaller_photos() {
        let (_, w, _, q, _) = plan(4000, 3000, 4_000_000, true);
        assert_eq!((w, q), (1024, 74));
    }

    #[test]
    fn small_dimensions_but_heavy_file_is_recompressed() {
        let (s, w, h, _, pass) = plan(1200, 900, 2_000_000, false);
        assert_eq!((s, w, h, pass), (1, 1200, 900, false));
    }

    #[test]
    fn zero_sizes_do_not_panic() {
        let _ = plan(0, 0, 0, false);
    }
}
