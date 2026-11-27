//! 跨模块共享的小工具。

/// RFC 3986 百分号编码：仅保留 ALPHA / DIGIT / `-` `_` `.` `~`。
pub(crate) fn percent_encode(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for b in s.bytes() {
        match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                out.push(b as char)
            }
            _ => out.push_str(&format!("%{b:02X}")),
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn encodes_reserved_and_spaces() {
        assert_eq!(percent_encode("a b"), "a%20b");
        assert_eq!(percent_encode("http://x/y?z=1"), "http%3A%2F%2Fx%2Fy%3Fz%3D1");
        assert_eq!(percent_encode("a-b_c.d~e"), "a-b_c.d~e");
        assert_eq!(percent_encode("中文"), "%E4%B8%AD%E6%96%87");
    }
}
