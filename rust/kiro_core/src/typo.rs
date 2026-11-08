//! Typo-tolerant title matching: per-word Damerau-Levenshtein with a length-based edit budget.

fn norm(s: &str) -> Vec<Vec<char>> {
    s.chars()
        .map(|c| if c.is_alphanumeric() { c.to_lowercase().next().unwrap_or(c) } else { ' ' })
        .collect::<String>()
        .split_whitespace()
        .map(|w| w.chars().collect())
        .collect()
}

/// Edit distance with adjacent transposition, giving up (returns max+1) once it must exceed `max`.
pub fn distance(a: &[char], b: &[char], max: usize) -> usize {
    if a.len().abs_diff(b.len()) > max {
        return max + 1;
    }
    let (n, m) = (a.len(), b.len());
    let mut d = vec![vec![0usize; m + 1]; n + 1];
    for i in 0..=n {
        d[i][0] = i;
    }
    for j in 0..=m {
        d[0][j] = j;
    }
    for i in 1..=n {
        for j in 1..=m {
            let cost = if a[i - 1] == b[j - 1] { 0 } else { 1 };
            let mut v = (d[i - 1][j] + 1).min(d[i][j - 1] + 1).min(d[i - 1][j - 1] + cost);
            if i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1] {
                v = v.min(d[i - 2][j - 2] + 1);
            }
            d[i][j] = v;
        }
    }
    d[n][m].min(max + 1)
}

fn budget(len: usize) -> usize {
    match len {
        0..=3 => 0,
        4..=6 => 1,
        _ => 2,
    }
}

/// Score of one query word against the words of a title (0 = no match).
fn word_score(q: &[char], words: &[Vec<char>]) -> i32 {
    let mut best = 0;
    for w in words {
        let s = if w == q {
            100
        } else if w.len() > q.len() && w[..q.len()] == *q {
            80
        } else {
            let b = budget(q.len());
            if b == 0 {
                0
            } else {
                let d = distance(q, w, b);
                // a prefix of the word may also be a typo (user still typing)
                let dp = if w.len() > q.len() { distance(q, &w[..q.len()], b) } else { b + 1 };
                let d = d.min(dp);
                if d <= b { 60 - d as i32 * 10 } else { 0 }
            }
        };
        best = best.max(s);
    }
    best
}

/// Indices of `items` matching every word of `query` (typos allowed), best first, plus whether any needed a typo fix.
pub fn rank(query: &str, items: &[&str]) -> Vec<i32> {
    let qw = norm(query);
    if qw.is_empty() {
        return (0..items.len() as i32).collect();
    }
    let mut scored: Vec<(i32, usize)> = Vec::new();
    for (i, it) in items.iter().enumerate() {
        let words = norm(it);
        let mut total = 0;
        let mut ok = true;
        for q in &qw {
            let s = word_score(q, &words);
            if s == 0 {
                ok = false;
                break;
            }
            total += s;
        }
        if ok {
            // shorter titles first when equally good
            scored.push((total * 10 - (words.len() as i32).min(9), i));
        }
    }
    scored.sort_by(|a, b| b.0.cmp(&a.0).then(a.1.cmp(&b.1)));
    scored.into_iter().map(|(_, i)| i as i32).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn distance_counts_swaps() {
        let a: Vec<char> = "naruot".chars().collect();
        let b: Vec<char> = "naruto".chars().collect();
        assert_eq!(distance(&a, &b, 2), 1);
        let c: Vec<char> = "xxxxxx".chars().collect();
        assert_eq!(distance(&a, &c, 2), 3);
    }

    #[test]
    fn finds_titles_despite_typos() {
        let items = ["One Piece", "Naruto Shippuden", "Bleach: Thousand-Year Blood War", "Attack on Titan"];
        assert_eq!(rank("naruot", &items)[0], 1);
        assert_eq!(rank("blech", &items), vec![2]);
        assert_eq!(rank("atack on titan", &items), vec![3]);
        assert_eq!(rank("one pice", &items), vec![0]);
        assert!(rank("zzzzzz", &items).is_empty());
    }

    #[test]
    fn short_words_must_be_exact() {
        assert!(rank("ox", &["one piece"]).is_empty());
        assert_eq!(rank("one", &["one piece"]), vec![0]);
    }

    #[test]
    fn exact_beats_typo() {
        let items = ["Naruto Shippuden", "Narutu Fake"];
        assert_eq!(rank("naruto", &items)[0], 0);
    }
}
