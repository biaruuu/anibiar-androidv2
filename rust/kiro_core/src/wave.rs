//! Voice-message waveform: squash raw amplitude samples (0..32767) into `n` digits 0-9.

pub fn bars(samples: &[i32], n: usize) -> String {
    if n == 0 {
        return String::new();
    }
    if samples.is_empty() {
        return "0".repeat(n);
    }
    let mut peaks = vec![0f64; n];
    for (i, chunk) in (0..n).map(|i| (i, &samples[i * samples.len() / n..((i + 1) * samples.len() / n).max(i * samples.len() / n + 1).min(samples.len())])) {
        peaks[i] = chunk.iter().copied().max().unwrap_or(0).max(0) as f64;
    }
    // sqrt curve so quiet speech is still visible
    let top = peaks.iter().cloned().fold(1.0, f64::max);
    peaks.iter().map(|p| char::from(b'0' + ((p / top).sqrt() * 9.0).round() as u8)).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn scales_to_digits() {
        let s = bars(&[0, 0, 100, 400, 1600, 100], 3);
        assert_eq!(s.len(), 3);
        assert!(s.ends_with('9'));
        assert!(s.chars().all(|c| c.is_ascii_digit()));
    }

    #[test]
    fn handles_edges() {
        assert_eq!(bars(&[], 4), "0000");
        assert_eq!(bars(&[5], 4).len(), 4);
        assert_eq!(bars(&[1, 2], 0), "");
    }
}
