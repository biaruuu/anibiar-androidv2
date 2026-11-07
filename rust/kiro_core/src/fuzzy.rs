//! Ranked fuzzy matching for short lists (usernames, titles).

fn score(query: &[char], hay: &str) -> Option<i32> {
    let h: Vec<char> = hay.chars().flat_map(|c| c.to_lowercase()).collect();
    if query.is_empty() {
        return Some(0);
    }
    if h.len() == query.len() && h == query {
        return Some(10_000);
    }
    // substring: earlier is better, word start gets a bonus
    if let Some(pos) = find(&h, query) {
        let word_start = pos == 0 || !h[pos - 1].is_alphanumeric();
        return Some(8_000 - pos as i32 * 10 + if word_start { 1_000 } else { 0 });
    }
    // subsequence: fewer gaps is better
    let mut qi = 0usize;
    let mut gaps = 0i32;
    let mut last: Option<usize> = None;
    for (i, c) in h.iter().enumerate() {
        if qi < query.len() && *c == query[qi] {
            if let Some(l) = last {
                gaps += (i - l - 1) as i32;
            }
            last = Some(i);
            qi += 1;
        }
    }
    if qi == query.len() {
        Some((4_000 - gaps * 20).max(1))
    } else {
        None
    }
}

fn find(h: &[char], q: &[char]) -> Option<usize> {
    if q.len() > h.len() {
        return None;
    }
    (0..=h.len() - q.len()).find(|&i| &h[i..i + q.len()] == q)
}

/// Indices of `items` that match `query`, best first (ties keep their original order).
pub fn rank(query: &str, items: &[&str]) -> Vec<i32> {
    let q: Vec<char> = query.trim().chars().flat_map(|c| c.to_lowercase()).collect();
    let mut scored: Vec<(i32, usize)> = items
        .iter()
        .enumerate()
        .filter_map(|(i, s)| score(&q, s).map(|sc| (sc, i)))
        .collect();
    scored.sort_by(|a, b| b.0.cmp(&a.0).then(a.1.cmp(&b.1)));
    scored.into_iter().map(|(_, i)| i as i32).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ranks_exact_then_prefix_then_subsequence() {
        let items = ["ryoevisu", "ry", "xxryoxx", "r_y_o", "zzz"];
        let r = rank("ryo", &items);
        assert_eq!(r, vec![0, 2, 3]);
        assert_eq!(rank("RY", &items)[0], 1);
        assert!(rank("q", &items).is_empty());
    }

    #[test]
    fn empty_query_keeps_everything_in_order() {
        assert_eq!(rank("  ", &["a", "b", "c"]), vec![0, 1, 2]);
    }
}
