//! Subtitle line breaking in the Netflix way: one line when it fits, otherwise the fewest lines that fit,
//! with the words spread so the lines are close to the same length.

fn width(s: &str) -> usize {
    s.chars().count()
}

/// `cap` is how many characters fit on one line at the current caption size and player width.
pub fn balance(text: &str, cap: usize) -> String {
    let words: Vec<&str> = text.split_whitespace().collect();
    let joined = words.join(" ");
    if text.contains('\n') || cap < 8 || words.len() < 2 || width(&joined) <= cap {
        return if text.contains('\n') { text.to_string() } else { joined };
    }
    let total = width(&joined);
    let n = ((total + cap - 1) / cap).min(words.len()).max(2);
    let target = total as f64 / n as f64;
    let lens: Vec<usize> = words.iter().map(|w| width(w)).collect();
    let w = words.len();
    // Length of words[a..b] joined with spaces.
    let span = |a: usize, b: usize| -> usize { lens[a..b].iter().sum::<usize>() + (b - a - 1) };
    let inf = f64::INFINITY;
    // best[k][i]: lowest cost to put the first i words on k lines; from[k][i]: where the last of those lines starts.
    let mut best = vec![vec![inf; w + 1]; n + 1];
    let mut from = vec![vec![0usize; w + 1]; n + 1];
    best[0][0] = 0.0;
    for k in 1..=n {
        for i in k..=w {
            for a in (k - 1)..i {
                if best[k - 1][a].is_infinite() {
                    continue;
                }
                let l = span(a, i);
                if l > cap {
                    continue;
                }
                let d = l as f64 - target;
                let c = best[k - 1][a] + d * d;
                // `<=` keeps the later split on ties, so the bottom line is the longer one.
                if c <= best[k][i] {
                    best[k][i] = c;
                    from[k][i] = a;
                }
            }
        }
    }
    if best[n][w].is_infinite() {
        return joined;
    }
    let mut cuts = Vec::new();
    let (mut i, mut k) = (w, n);
    while k > 0 {
        let a = from[k][i];
        cuts.push((a, i));
        i = a;
        k -= 1;
    }
    cuts.reverse();
    cuts.iter().map(|&(a, b)| words[a..b].join(" ")).collect::<Vec<_>>().join("\n")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn short_line_stays_whole() {
        assert_eq!(balance("I'll be right back.", 42), "I'll be right back.");
    }

    #[test]
    fn long_line_splits_evenly() {
        let out = balance("We have to leave right now because they are coming for us", 42);
        let parts: Vec<&str> = out.split('\n').collect();
        assert_eq!(parts.len(), 2);
        assert!(parts.iter().all(|p| p.chars().count() <= 42));
        assert!((parts[0].chars().count() as i32 - parts[1].chars().count() as i32).abs() <= 8);
    }

    #[test]
    fn existing_breaks_are_kept() {
        assert_eq!(balance("one\ntwo", 42), "one\ntwo");
    }

    #[test]
    fn unbreakable_word_is_left_alone() {
        assert_eq!(balance("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa b", 10), "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa b");
    }

    #[test]
    fn three_lines_when_needed() {
        let out = balance("aaa bbb ccc ddd eee fff ggg hhh iii jjj kkk lll", 16);
        assert!(out.split('\n').count() >= 3);
        assert!(out.split('\n').all(|l| l.chars().count() <= 16));
    }
}
