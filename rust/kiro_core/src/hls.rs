//! Reads the picture height out of an HLS master playlist.

/// Largest `RESOLUTION=WxH` height in the playlist, 0 when there is none.
pub fn max_height(text: &str) -> i32 {
    let mut best = 0i32;
    let mut rest = text;
    while let Some(i) = rest.find("RESOLUTION=") {
        let after = &rest[i + "RESOLUTION=".len()..];
        let w = after.bytes().take_while(|b| b.is_ascii_digit()).count();
        if w > 0 && after[w..].starts_with('x') {
            let h_part = &after[w + 1..];
            let h = h_part.bytes().take_while(|b| b.is_ascii_digit()).count();
            if h > 0 {
                if let Ok(v) = h_part[..h].parse::<i32>() {
                    best = best.max(v);
                }
            }
        }
        rest = after;
    }
    best
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn finds_the_tallest_variant() {
        let m = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360\na.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1920x1080,CODECS=\"x\"\nb.m3u8\n#EXT-X-STREAM-INF:RESOLUTION=1280x720\nc.m3u8\n";
        assert_eq!(max_height(m), 1080);
        assert_eq!(max_height("#EXTM3U\nsegment.ts"), 0);
        assert_eq!(max_height("RESOLUTION=bad RESOLUTION=1x"), 0);
    }
}
