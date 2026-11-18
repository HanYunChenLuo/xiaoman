fn main() {
    let path = std::env::args().nth(1).expect("path");
    let bytes = std::fs::read(&path).expect("read");
    match xiaoman_core::backup::decode_ias(&bytes) {
        Ok(text) => {
            let v: serde_json::Value = serde_json::from_str(&text).unwrap();
            let keys: Vec<&String> = v["data"].as_object().map(|o| o.keys().collect()).unwrap_or_default();
            let images = v["images"].as_object().map(|o| o.len()).unwrap_or(0);
            let assets = v["data"]["assets"].as_array().map(|a| a.len()).unwrap_or(0);
            println!("ok data_keys={:?} assets={} images={}", keys, assets, images);
            let re = xiaoman_core::backup::encode_ias(&text).expect("encode");
            let dec = xiaoman_core::backup::decode_ias(&re).expect("re-decode");
            let a: serde_json::Value = serde_json::from_str(&text).unwrap();
            let b: serde_json::Value = serde_json::from_str(&dec).unwrap();
            println!("roundtrip equal={} size={}", a == b, re.len());
            std::fs::write("/tmp/roundtrip.ias", &re).unwrap();
        }
        Err(e) => println!("decode error: {e}"),
    }
}
