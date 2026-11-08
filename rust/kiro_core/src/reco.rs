//! "Because you watched" scoring: cosine similarity between the viewer's genre taste and each candidate, nudged by score.
use std::collections::HashMap;

/// `taste`: (genre, weight). `cands`: (genres, average score 0..100). Returns candidate indices, best first.
pub fn rank(taste: &[(String, f64)], cands: &[(Vec<String>, f64)]) -> Vec<i32> {
    let t: HashMap<&str, f64> = taste.iter().map(|(g, w)| (g.as_str(), *w)).collect();
    let tn = t.values().map(|v| v * v).sum::<f64>().sqrt();
    if tn == 0.0 {
        return Vec::new();
    }
    let mut out: Vec<(f64, usize)> = Vec::new();
    for (i, (genres, score)) in cands.iter().enumerate() {
        if genres.is_empty() {
            continue;
        }
        let dot: f64 = genres.iter().map(|g| t.get(g.as_str()).copied().unwrap_or(0.0)).sum();
        if dot == 0.0 {
            continue;
        }
        let cn = (genres.len() as f64).sqrt();
        let cos = dot / (tn * cn);
        out.push((cos + score / 100.0 * 0.25, i));
    }
    out.sort_by(|a, b| b.0.partial_cmp(&a.0).unwrap_or(std::cmp::Ordering::Equal).then(a.1.cmp(&b.1)));
    out.into_iter().map(|(_, i)| i as i32).collect()
}

/// Wire format: taste lines `genre\tweight`; candidate lines `score\tg1,g2`.
pub fn parse_taste(s: &str) -> Vec<(String, f64)> {
    s.lines()
        .filter_map(|l| {
            let (g, w) = l.split_once('\t')?;
            Some((g.to_string(), w.trim().parse().ok()?))
        })
        .collect()
}

pub fn parse_cands(s: &str) -> Vec<(Vec<String>, f64)> {
    s.split('\n')
        .map(|l| {
            let (sc, g) = l.split_once('\t').unwrap_or(("0", ""));
            (g.split(',').filter(|x| !x.is_empty()).map(String::from).collect(), sc.trim().parse().unwrap_or(0.0))
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn prefers_matching_genres() {
        let taste = parse_taste("Action\t5\nFantasy\t3");
        let cands = parse_cands("80\tRomance,Drama\n70\tAction,Fantasy\n90\tAction\n60\t");
        let r = rank(&taste, &cands);
        assert_eq!(r[0], 1);
        assert_eq!(r, vec![1, 2]);
    }

    #[test]
    fn no_taste_means_no_picks() {
        assert!(rank(&[], &parse_cands("80\tAction")).is_empty());
    }
}
