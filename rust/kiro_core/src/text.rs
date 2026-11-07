//! Title similarity and HTML-to-text helpers used by the stream scrapers and anime descriptions.
//! These mirror the Kotlin versions they replace (including their replacement order).

use std::collections::HashMap;

/// Lowercase and keep only a-z / 0-9 (as bytes).
pub fn norm(s: &str) -> Vec<u8> {
    s.chars().flat_map(|c| c.to_lowercase()).filter(|c| c.is_ascii_lowercase() || c.is_ascii_digit()).map(|c| c as u8).collect()
}

/// Sørensen–Dice coefficient over character bigrams of the normalised strings (1.0 = same, 0.0 = nothing alike).
pub fn dice(a: &str, b: &str) -> f64 {
    let (na, nb) = (norm(a), norm(b));
    if na == nb {
        return 1.0;
    }
    if na.len() < 2 || nb.len() < 2 {
        return 0.0;
    }
    let mut grams: HashMap<[u8; 2], i32> = HashMap::with_capacity(na.len());
    for w in na.windows(2) {
        *grams.entry([w[0], w[1]]).or_insert(0) += 1;
    }
    let mut hits = 0;
    for w in nb.windows(2) {
        if let Some(c) = grams.get_mut(&[w[0], w[1]]) {
            if *c > 0 {
                *c -= 1;
                hits += 1;
            }
        }
    }
    2.0 * hits as f64 / ((na.len() - 1 + nb.len() - 1) as f64)
}

fn is_ws(c: char) -> bool {
    matches!(c, ' ' | '\t' | '\n' | '\u{0B}' | '\u{0C}' | '\r')
}

fn is_br(inner: &str) -> bool {
    let l = inner.to_ascii_lowercase();
    match l.strip_prefix("br") {
        Some(rest) => {
            let r = rest.trim_matches(is_ws);
            r.is_empty() || (rest.trim_start_matches(is_ws).starts_with('/') && rest.trim_start_matches(is_ws)[1..].is_empty())
        }
        None => false,
    }
}

/// Removes `<...>` tags. `allow_empty` also removes `<>`; `br_space` turns `<br>` into a space.
fn strip_tags(s: &str, repl: &str, allow_empty: bool, br_space: bool) -> String {
    let mut out = String::with_capacity(s.len());
    let mut rest = s;
    while let Some(i) = rest.find('<') {
        let after = &rest[i + 1..];
        let Some(j) = after.find('>') else { break };
        let inner = &after[..j];
        if inner.is_empty() && !allow_empty {
            out.push_str(&rest[..i + 1]);
            rest = after;
            continue;
        }
        out.push_str(&rest[..i]);
        out.push_str(if br_space && is_br(inner) { " " } else { repl });
        rest = &after[j + 1..];
    }
    out.push_str(rest);
    out
}

fn collapse_ws(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut prev = false;
    for c in s.chars() {
        if is_ws(c) {
            if !prev {
                out.push(' ');
            }
            prev = true;
        } else {
            out.push(c);
            prev = false;
        }
    }
    out
}

/// Replaces `&#123;` with the character (BMP only, like Kotlin's `toInt().toChar()`).
fn numeric_entities(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut rest = s;
    while let Some(i) = rest.find("&#") {
        let after = &rest[i + 2..];
        let digits = after.bytes().take_while(|b| b.is_ascii_digit()).count();
        if digits > 0 && after[digits..].starts_with(';') {
            if let Some(c) = after[..digits].parse::<u32>().ok().filter(|n| *n <= 0xFFFF).and_then(char::from_u32) {
                out.push_str(&rest[..i]);
                out.push(c);
                rest = &after[digits + 1..];
                continue;
            }
        }
        out.push_str(&rest[..i + 2]);
        rest = after;
    }
    out.push_str(rest);
    out
}

/// mode 0: anime description (`<br>` to space, other tags removed, entities, collapse, trim)
/// mode 1: scraped HTML snippet (tags to space, collapse, numeric + named entities, trim)
/// mode 2: entities only, then trim
pub fn html_text(s: &str, mode: i32) -> String {
    match mode {
        0 => collapse_ws(
            &strip_tags(s, "", false, true)
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&#039;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">"),
        )
        .trim()
        .to_string(),
        1 => entities(&collapse_ws(&strip_tags(s, " ", true, false))),
        _ => entities(s),
    }
}

fn entities(s: &str) -> String {
    numeric_entities(s)
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .trim()
        .to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn dice_matches_the_kotlin_behaviour() {
        assert_eq!(dice("One Piece", "one-piece!"), 1.0);
        assert_eq!(dice("a", "b"), 0.0);
        assert_eq!(dice("", ""), 1.0);
        // "night" vs "nacht": bigrams ni ig gh ht / na ac ch ht -> 1 hit of 8
        assert!((dice("night", "nacht") - 0.25).abs() < 1e-9);
        assert!(dice("Attack on Titan", "Attack on Titan Season 2") > 0.7);
    }

    #[test]
    fn description_cleaning() {
        let d = "A &quot;hero&quot;<br>rises<BR />again <i>now</i> &amp; forever\n\n  ok&#039;s";
        assert_eq!(html_text(d, 0), "A \"hero\" rises again now & forever ok's");
        assert_eq!(html_text("a <> b", 0), "a <> b");
        // same as the Kotlin regex: "< 2 and 3 >" looks like a tag
        assert_eq!(html_text("1 < 2 and 3 > 2", 0), "1 2");
    }

    #[test]
    fn scraped_snippets() {
        assert_eq!(html_text("<strong>Tom &amp; Jerry</strong>\n <i>x</i>&#33;", 1), "Tom & Jerry x !");
        assert_eq!(html_text("&#65;&#66; &lt;3 ", 2), "AB <3");
    }
}
