//! .ias 备份编解码：WinZip AES-256 加密 zip，内含 data.json 与 images/。
use base64::Engine;
use serde_json::{json, Map, Value};
use std::io::{Cursor, Read, Write};
use zip::write::SimpleFileOptions;
use zip::{AesMode, ZipArchive, ZipWriter};

pub const PASSWORD: &str = "iAsset#Support@2025!";
pub const PASSWORD_BYTES: &[u8] = PASSWORD.as_bytes();

pub fn decode_ias(bytes: &[u8]) -> Result<String, String> {
    let mut archive = ZipArchive::new(Cursor::new(bytes)).map_err(|e| e.to_string())?;
    let mut data: Option<Value> = None;
    let mut images = Map::new();
    for i in 0..archive.len() {
        // 空的加密目录条目（如 images/）会让 zip crate 的 AES 读取下溢，提前跳过。
        let encrypted;
        let small;
        {
            let raw = match archive.by_index_raw(i) {
                Ok(r) => r,
                Err(_) => continue,
            };
            if raw.is_dir() {
                continue;
            }
            encrypted = raw.encrypted();
            small = raw.compressed_size() < 28;
        }
        if encrypted && small {
            continue;
        }
        let mut name = String::new();
        let mut buf = Vec::new();
        let mut ok = false;
        if let Ok(mut entry) = archive.by_index_decrypt(i, PASSWORD_BYTES) {
            if !entry.is_dir() {
                name = entry.name().to_string();
                entry.read_to_end(&mut buf).map_err(|e| e.to_string())?;
                ok = true;
            }
        }
        if !ok {
            if let Ok(mut entry) = archive.by_index(i) {
                if !entry.is_dir() {
                    name = entry.name().to_string();
                    entry.read_to_end(&mut buf).map_err(|e| e.to_string())?;
                    ok = true;
                }
            }
        }
        if !ok || buf.is_empty() {
            continue;
        }
        if name == "data.json" {
            data = Some(serde_json::from_slice(&buf).map_err(|e| e.to_string())?);
        } else if name.starts_with("images/") {
            images.insert(name, Value::String(base64::engine::general_purpose::STANDARD.encode(&buf)));
        }
    }
    let data = data.ok_or_else(|| "data.json not found".to_string())?;
    Ok(json!({ "data": data, "images": Value::Object(images) }).to_string())
}

pub fn encode_ias(payload: &str) -> Result<Vec<u8>, String> {
    let root: Value = serde_json::from_str(payload).map_err(|e| e.to_string())?;
    let data = root.get("data").ok_or_else(|| "missing data".to_string())?;
    let data_bytes = serde_json::to_vec(data).map_err(|e| e.to_string())?;

    let mut out = Cursor::new(Vec::new());
    {
        let mut writer = ZipWriter::new(&mut out);
        let options = SimpleFileOptions::default()
            .compression_method(zip::CompressionMethod::Deflated)
            .with_aes_encryption(AesMode::Aes256, PASSWORD);
        writer
            .start_file("data.json", options)
            .map_err(|e| e.to_string())?;
        writer.write_all(&data_bytes).map_err(|e| e.to_string())?;

        if let Some(images) = root.get("images").and_then(|v| v.as_array()) {
            for img in images {
                let name = img.get("name").and_then(|v| v.as_str()).unwrap_or("");
                let b64 = img.get("data").and_then(|v| v.as_str()).unwrap_or("");
                if name.is_empty() || !name.starts_with("images/") {
                    continue;
                }
                let bytes = base64::engine::general_purpose::STANDARD
                    .decode(b64)
                    .map_err(|e| e.to_string())?;
                writer
                    .start_file(name, options)
                    .map_err(|e| e.to_string())?;
                writer.write_all(&bytes).map_err(|e| e.to_string())?;
            }
        }
        writer.finish().map_err(|e| e.to_string())?;
    }
    Ok(out.into_inner())
}
