//! Watch-time statistics from a compact event log.
use std::collections::{BTreeSet, HashMap};

/// Event line: `day\tmediaId\tseconds\tg1,g2` (day = days since 1970-01-01).
/// Output: first line `total_secs\tweek_secs\tstreak\tbest_streak\ttitles`, then `genre\tsecs` lines (top 6).
pub fn summarize(today: i64, rows: &str) -> String {
    let mut total = 0i64;
    let mut week = 0i64;
    let mut days: BTreeSet<i64> = BTreeSet::new();
    let mut shows: BTreeSet<i64> = BTreeSet::new();
    let mut genres: HashMap<String, i64> = HashMap::new();
    for l in rows.lines() {
        let mut f = l.split('\t');
        let (Some(d), Some(m), Some(s)) = (f.next(), f.next(), f.next()) else { continue };
        let (Ok(d), Ok(m), Ok(s)) = (d.parse::<i64>(), m.parse::<i64>(), s.parse::<i64>()) else { continue };
        if s <= 0 {
            continue;
        }
        total += s;
        if d > today - 7 && d <= today {
            week += s;
        }
        days.insert(d);
        shows.insert(m);
        for g in f.next().unwrap_or("").split(',').filter(|g| !g.is_empty()) {
            *genres.entry(g.to_string()).or_insert(0) += s;
        }
    }
    // streak: consecutive days ending today (or yesterday, so it doesn't reset before you watch today)
    let mut streak = 0;
    let mut d = if days.contains(&today) { today } else { today - 1 };
    while days.contains(&d) {
        streak += 1;
        d -= 1;
    }
    let mut best = 0;
    let mut run = 0;
    let mut prev: Option<i64> = None;
    for &d in &days {
        run = if prev == Some(d - 1) { run + 1 } else { 1 };
        best = best.max(run);
        prev = Some(d);
    }
    let mut out = format!("{}\t{}\t{}\t{}\t{}", total, week, streak, best, shows.len());
    let mut g: Vec<_> = genres.into_iter().collect();
    g.sort_by(|a, b| b.1.cmp(&a.1).then(a.0.cmp(&b.0)));
    for (name, secs) in g.into_iter().take(6) {
        out.push_str(&format!("\n{}\t{}", name, secs));
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn totals_streak_and_genres() {
        let rows = "100\t1\t600\tAction,Drama\n99\t1\t300\tAction\n98\t2\t60\tComedy\n90\t3\t1000\tAction\nbad line";
        let out = summarize(100, rows);
        let first = out.lines().next().unwrap();
        assert_eq!(first, "1960\t960\t3\t3\t3");
        assert_eq!(out.lines().nth(1).unwrap(), "Action\t1900");
    }

    #[test]
    fn streak_survives_until_tomorrow() {
        assert_eq!(summarize(101, "100\t1\t10\t").lines().next().unwrap(), "10\t10\t1\t1\t1");
        assert_eq!(summarize(102, "100\t1\t10\t").lines().next().unwrap(), "10\t10\t0\t1\t1");
    }
}
