//! XP, levels and badges, derived from the watch-stats summary.

/// XP needed to reach `level` (level 1 needs 0).
pub fn xp_for(level: i64) -> i64 {
    if level <= 1 {
        return 0;
    }
    (120.0 * ((level - 1) as f64).powf(1.5)).round() as i64
}

/// XP: one per minute watched, 20 per title, 50 per day of best streak.
pub fn xp(total_secs: i64, shows: i64, best_streak: i64) -> i64 {
    total_secs.max(0) / 60 + shows.max(0) * 20 + best_streak.max(0) * 50
}

/// (level, xp at this level, xp for the next level)
pub fn level(xp: i64) -> (i64, i64, i64) {
    let mut l = 1;
    while xp_for(l + 1) <= xp && l < 200 {
        l += 1;
    }
    (l, xp_for(l), xp_for(l + 1))
}

/// Badge ids earned so far, in display order.
pub fn badges(total_secs: i64, best_streak: i64, shows: i64, max_day_secs: i64, level: i64) -> Vec<&'static str> {
    let h = total_secs / 3600;
    let mut b = Vec::new();
    if total_secs > 0 { b.push("first"); }
    if best_streak >= 3 { b.push("streak3"); }
    if best_streak >= 7 { b.push("streak7"); }
    if best_streak >= 30 { b.push("streak30"); }
    if shows >= 10 { b.push("shows10"); }
    if shows >= 25 { b.push("shows25"); }
    if shows >= 100 { b.push("shows100"); }
    if h >= 24 { b.push("hours24"); }
    if h >= 100 { b.push("hours100"); }
    if h >= 500 { b.push("hours500"); }
    if max_day_secs >= 4 * 3600 { b.push("binge"); }
    if max_day_secs >= 8 * 3600 { b.push("marathon"); }
    if level >= 10 { b.push("lv10"); }
    if level >= 25 { b.push("lv25"); }
    b
}

/// `xp\tlevel\tfloor\tnext\tbadge,badge,...`
pub fn summary(total_secs: i64, best_streak: i64, shows: i64, max_day_secs: i64) -> String {
    let x = xp(total_secs, shows, best_streak);
    let (l, lo, hi) = level(x);
    format!("{}\t{}\t{}\t{}\t{}", x, l, lo, hi, badges(total_secs, best_streak, shows, max_day_secs, l).join(","))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn levels_grow() {
        assert_eq!(level(0).0, 1);
        assert!(xp_for(2) > 0 && xp_for(3) > xp_for(2));
        assert_eq!(level(xp_for(5)).0, 5);
        assert_eq!(level(xp_for(5) - 1).0, 4);
    }

    #[test]
    fn badges_and_summary() {
        let s = summary(25 * 3600, 7, 12, 5 * 3600);
        let f: Vec<&str> = s.split('\t').collect();
        assert_eq!(f.len(), 5);
        assert!(f[4].contains("streak7") && f[4].contains("hours24") && f[4].contains("binge") && !f[4].contains("marathon"));
    }
}
