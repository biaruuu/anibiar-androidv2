//! Starting bandwidth guess from past throughput samples (bits per second).

/// Robust estimate: ignore the highest 20 % (bursts) and lowest 10 % (stalls), average the rest, keep 20 % headroom.
/// Returns `None` with fewer than 3 samples.
pub fn estimate(samples: &[f64]) -> Option<f64> {
    let mut v: Vec<f64> = samples.iter().copied().filter(|x| x.is_finite() && *x > 0.0).collect();
    if v.len() < 3 {
        return None;
    }
    v.sort_by(|a, b| a.partial_cmp(b).unwrap());
    let lo = v.len() / 10;
    let hi = v.len() - v.len() / 5;
    let mid = &v[lo..hi.max(lo + 1)];
    Some(mid.iter().sum::<f64>() / mid.len() as f64 * 0.8)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn needs_enough_samples() {
        assert!(estimate(&[5e6, 6e6]).is_none());
    }

    #[test]
    fn ignores_bursts_and_stalls() {
        let mut s = vec![8e6; 8];
        s.push(100e6);
        s.push(1e5);
        let e = estimate(&s).unwrap();
        assert!((e - 6.4e6).abs() < 1.0, "{e}");
    }
}
