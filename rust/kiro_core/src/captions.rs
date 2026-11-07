//! WebVTT / SRT / ASS-SSA subtitle parser. Mirrors the Kotlin `Captions` object it replaces.
//! Output is one string: records split by U+001E, fields by U+001F: start, end, italic(0/1), text.

pub struct Cue {
    pub start: f64,
    pub end: f64,
    pub text: String,
    pub italic: bool,
}

pub fn parse(raw: &str) -> Vec<Cue> {
    let raw = raw.strip_prefix('\u{FEFF}').unwrap_or(raw);
    let text = raw.replace("\r\n", "\n").replace('\r', "\n");
    let mut cues = if text.to_lowercase().contains("[events]") { ass(&text) } else { vtt_srt(&text) };
    cues.retain(|c| !c.text.trim().is_empty());
    cues.sort_by(|a, b| a.start.partial_cmp(&b.start).unwrap_or(std::cmp::Ordering::Equal));
    cues
}

pub fn encode(cues: &[Cue]) -> String {
    let mut out = String::with_capacity(cues.len() * 48);
    for (i, c) in cues.iter().enumerate() {
        if i > 0 {
            out.push('\u{1E}');
        }
        out.push_str(&format!("{}\u{1F}{}\u{1F}{}\u{1F}{}", c.start, c.end, if c.italic { 1 } else { 0 }, c.text));
    }
    out
}

/// Removes everything from `open` to the next `close` (like the regex `open[^close]*close`).
fn strip_between(s: &str, open: char, close: char) -> String {
    let mut out = String::with_capacity(s.len());
    let mut rest = s;
    while let Some(i) = rest.find(open) {
        let after = &rest[i + open.len_utf8()..];
        match after.find(close) {
            Some(j) => {
                out.push_str(&rest[..i]);
                rest = &after[j + close.len_utf8()..];
            }
            None => break,
        }
    }
    out.push_str(rest);
    out
}

fn clean(s: &str) -> String {
    let s = strip_between(s, '<', '>');
    let s = strip_between(&s, '{', '}');
    s.replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace("\\N", "\n")
        .replace("\\n", "\n")
        .replace("\\h", " ")
        .trim()
        .to_string()
}

/// "[h:]mm:ss.fff" (or ',' as the separator) to seconds.
fn stamp(t: &str) -> Option<f64> {
    let sep = t.rfind(|c| c == '.' || c == ',')?;
    let (hms, frac) = (&t[..sep], &t[sep + 1..]);
    if frac.is_empty() || !frac.bytes().all(|b| b.is_ascii_digit()) {
        return None;
    }
    let parts: Vec<&str> = hms.split(':').collect();
    let (h, m, s) = match parts.len() {
        2 => (0u64, parts[0], parts[1]),
        3 => (parts[0].parse().ok()?, parts[1], parts[2]),
        _ => return None,
    };
    let (m, s): (u64, u64) = (m.parse().ok()?, s.parse().ok()?);
    let f: f64 = format!("0.{}", frac).parse().ok()?;
    Some(h as f64 * 3600.0 + m as f64 * 60.0 + s as f64 + f)
}

fn arrow(line: &str) -> Option<(f64, f64)> {
    let i = line.find("-->")?;
    let left = line[..i].trim_end();
    let right = line[i + 3..].trim_start();
    let is_t = |c: char| c.is_ascii_digit() || c == ':' || c == '.' || c == ',';
    let ls = left.rfind(|c: char| !is_t(c)).map(|p| p + left[p..].chars().next().map_or(1, |c| c.len_utf8())).unwrap_or(0);
    let re = right.find(|c: char| !is_t(c)).unwrap_or(right.len());
    Some((stamp(&left[ls..])?, stamp(&right[..re])?))
}

fn vtt_srt(text: &str) -> Vec<Cue> {
    let mut out = Vec::new();
    let mut block: Vec<&str> = Vec::new();
    let mut flush = |block: &mut Vec<&str>, out: &mut Vec<Cue>| {
        if let Some(i) = block.iter().position(|l| l.contains("-->")) {
            if let Some((start, end)) = arrow(block[i]) {
                let raw = block[i + 1..].join("\n");
                let italic = raw.contains("<i ") || raw.contains("<i>");
                out.push(Cue { start, end, text: clean(&raw), italic });
            }
        }
        block.clear();
    };
    for line in text.split('\n') {
        if line.trim().is_empty() {
            flush(&mut block, &mut out);
        } else {
            block.push(line);
        }
    }
    flush(&mut block, &mut out);
    out
}

fn ass_time(s: &str) -> Option<f64> {
    let p: Vec<&str> = s.trim().split(|c| c == ':' || c == '.').collect();
    if p.len() < 4 {
        return Some(0.0);
    }
    let f: f64 = format!("0.{}", p[3]).parse().ok()?;
    Some(p[0].parse::<i64>().ok()? as f64 * 3600.0 + p[1].parse::<i64>().ok()? as f64 * 60.0 + p[2].parse::<i64>().ok()? as f64 + f)
}

fn ass(text: &str) -> Vec<Cue> {
    let mut out = Vec::new();
    let mut format: Vec<String> = Vec::new();
    let mut in_events = false;
    for line in text.split('\n') {
        let t = line.trim();
        if t.starts_with('[') {
            in_events = t.eq_ignore_ascii_case("[Events]");
            continue;
        }
        if !in_events {
            continue;
        }
        let lower = t.to_ascii_lowercase();
        if lower.starts_with("format:") {
            format = t[t.find(':').map_or(0, |i| i + 1)..].split(',').map(|x| x.trim().to_lowercase()).collect();
        } else if lower.starts_with("dialogue:") && !format.is_empty() {
            let body = t[t.find(':').map_or(0, |i| i + 1)..].trim_start();
            let parts: Vec<&str> = body.splitn(format.len(), ',').collect();
            let idx = |n: &str| format.iter().position(|f| f == n);
            let (Some(s), Some(e), Some(tx)) = (idx("start"), idx("end"), idx("text")) else { continue };
            if parts.len() <= tx {
                continue;
            }
            let (Some(start), Some(end)) = (ass_time(parts[s]), ass_time(parts[e])) else { continue };
            out.push(Cue { start, end, text: clean(parts[tx]), italic: parts[tx].contains("\\i1") });
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_srt_and_vtt() {
        let srt = "1\r\n00:00:01,500 --> 00:00:03,000\r\nHello &amp; <i>bye</i>\r\n\r\n2\r\n00:01:00.250 --> 00:01:02.000\r\nSecond\r\nline\r\n";
        let c = parse(srt);
        assert_eq!(c.len(), 2);
        assert_eq!((c[0].start, c[0].end), (1.5, 3.0));
        assert_eq!(c[0].text, "Hello & bye");
        assert!(c[0].italic);
        assert_eq!(c[1].start, 60.25);
        assert_eq!(c[1].text, "Second\nline");
        let vtt = "\u{FEFF}WEBVTT\n\n01:02.000 --> 01:04.500 align:start\nHi\n";
        let v = parse(vtt);
        assert_eq!(v.len(), 1);
        assert_eq!((v[0].start, v[0].end), (62.0, 64.5));
    }

    #[test]
    fn parses_ass() {
        let ass = "[Script Info]\nTitle: x\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\nDialogue: 0,0:00:05.00,0:00:07.50,Default,,0,0,0,,{\\i1}Line, with comma\\Nnext\nDialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,First\n";
        let c = parse(ass);
        assert_eq!(c.len(), 2);
        assert_eq!(c[0].text, "First");
        assert_eq!(c[1].text, "Line, with comma\nnext");
        assert!(c[1].italic);
        assert_eq!((c[1].start, c[1].end), (5.0, 7.5));
    }

    #[test]
    fn encodes_and_skips_blank_text() {
        let c = parse("1\n00:00:01.000 --> 00:00:02.000\n{\\an8}\n\n2\n00:00:03.000 --> 00:00:04.000\nOk\n");
        assert_eq!(c.len(), 1);
        assert_eq!(encode(&c), "3\u{1F}4\u{1F}0\u{1F}Ok");
    }

    #[test]
    fn garbage_is_empty_not_a_panic() {
        assert!(parse("").is_empty());
        assert!(parse("--> -->\n--> 1").is_empty());
        assert!(parse("[Events]\nDialogue: x").is_empty());
    }
}
