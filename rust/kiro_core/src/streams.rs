//! Ranks the streams found for an episode so the best one plays first.
//! 1. Picture quality (2160 / 1080 / 720 / 480 / 360 / lower / unknown).
//! 2. For sub audio, soft subtitles (separate tracks the app can restyle) before burned-in ones: English tracks first,
//!    then other languages, then "soft" streams that carry no tracks, then hard subs.
//! 3. Exact height, then HLS (adaptive) over plain files, then the original provider order.

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Row {
    pub soft: bool,
    pub english: bool,
    pub has_tracks: bool,
    pub hls: bool,
    pub height: i32,
}

fn tier(sub_audio: bool, r: &Row) -> i32 {
    if !sub_audio {
        0
    } else if !r.soft {
        0
    } else if r.english {
        3
    } else if r.has_tracks {
        2
    } else {
        1
    }
}

fn bucket(h: i32) -> i32 {
    match h {
        i32::MIN..=0 => 0,
        1..=299 => 240,
        300..=449 => 360,
        450..=699 => 480,
        700..=999 => 720,
        1000..=1999 => 1080,
        _ => 2160,
    }
}

/// Indices into `rows`, best first.
pub fn rank(sub_audio: bool, rows: &[Row]) -> Vec<i32> {
    let mut idx: Vec<usize> = (0..rows.len()).collect();
    idx.sort_by(|&a, &b| {
        let (x, y) = (&rows[a], &rows[b]);
        bucket(y.height)
            .cmp(&bucket(x.height))
            .then(tier(sub_audio, y).cmp(&tier(sub_audio, x)))
            .then(y.height.cmp(&x.height))
            .then(y.hls.cmp(&x.hls))
            .then(a.cmp(&b))
    });
    idx.into_iter().map(|i| i as i32).collect()
}

/// Parses lines of `soft,english,tracks,hls,height` (flags are 0/1). Bad lines become the worst possible row.
pub fn parse(text: &str) -> Vec<Row> {
    text.split('\n')
        .map(|l| {
            let f: Vec<&str> = l.split(',').collect();
            let flag = |i: usize| f.get(i).map_or(false, |v| *v == "1");
            Row { soft: flag(0), english: flag(1), has_tracks: flag(2), hls: flag(3), height: f.get(4).and_then(|v| v.trim().parse().ok()).unwrap_or(0) }
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn r(soft: bool, english: bool, tracks: bool, hls: bool, height: i32) -> Row {
        Row { soft, english, has_tracks: tracks, hls, height }
    }

    #[test]
    fn quality_first_then_soft_english() {
        // 1080 hard beats 720 soft; at equal quality soft English beats soft other beats hard.
        let rows = [r(false, false, false, true, 1080), r(true, true, true, true, 720), r(true, false, true, true, 1080), r(true, true, true, true, 1080)];
        assert_eq!(rank(true, &rows), vec![3, 2, 0, 1]);
    }

    #[test]
    fn near_equal_heights_count_as_one_quality() {
        let rows = [r(false, false, false, true, 1080), r(true, true, true, true, 1072)];
        assert_eq!(rank(true, &rows), vec![1, 0]);
    }

    #[test]
    fn dub_ignores_subtitle_kind_and_uses_height() {
        let rows = [r(true, true, true, true, 480), r(false, false, false, true, 1080)];
        assert_eq!(rank(false, &rows), vec![1, 0]);
    }

    #[test]
    fn ties_keep_provider_order_and_hls_wins_over_files() {
        let rows = [r(true, true, true, false, 720), r(true, true, true, true, 720), r(true, true, true, true, 720)];
        assert_eq!(rank(true, &rows), vec![1, 2, 0]);
    }

    #[test]
    fn parses_lines() {
        let p = parse("1,1,1,1,1080\n0,0,0,0,x\n");
        assert_eq!(p.len(), 3);
        assert_eq!(p[0], r(true, true, true, true, 1080));
        assert_eq!(p[1], r(false, false, false, false, 0));
    }
}
