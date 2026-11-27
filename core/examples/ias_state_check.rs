use serde_json::Value;
use std::time::{SystemTime, UNIX_EPOCH};
use xiaoman_core::{backup, state};

fn load(bytes: &[u8]) -> Value {
    serde_json::from_str(&backup::decode_ias(bytes).expect("decode")).expect("json")
}

fn main() {
    let path = std::env::args().nth(1).expect("path");
    let bytes = std::fs::read(&path).expect("read");
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_millis() as i64;
    let tz = 8 * 3600 * 1000;

    let decoded = load(&bytes);
    let imported = state::from_backup(&decoded, now, tz).expect("from_backup");
    let st = &imported["state"];
    let assets = st["assets"].as_array().map(Vec::len).unwrap_or(0);
    let usages: usize = st["assets"]
        .as_array()
        .map(|a| {
            a.iter()
                .map(|x| x["usages"].as_array().map(Vec::len).unwrap_or(0))
                .sum()
        })
        .unwrap_or(0);
    let incomes: usize = st["assets"]
        .as_array()
        .map(|a| {
            a.iter()
                .map(|x| x["incomeRecords"].as_array().map(Vec::len).unwrap_or(0))
                .sum()
        })
        .unwrap_or(0);
    let images = decoded["images"].as_object().map(|m| m.len()).unwrap_or(0);
    println!(
        "import: assets={assets} usages={usages} incomes={incomes} save_money={} images={images} backup_time_ms={}",
        st["saveMoney"].as_array().map(Vec::len).unwrap_or(0),
        imported["meta"]["backupTimeMs"]
    );
    if let Some(a) = st["assets"].as_array().and_then(|a| a.first()) {
        println!(
            "first: id={} name={} buyDate={} status={} cover={:?} reminders={:?}",
            a["id"], a["name"], a["buyDate"], a["status"], a["coverImage"], a["expireAt"]
        );
    }

    let payload = state::to_payload(st, &decoded["images"], "AOS", now, tz).expect("to_payload");
    let re = backup::encode_ias(&payload.to_string()).expect("encode");
    let again = state::from_backup(&load(&re), now, tz).expect("re-import");
    let st2 = &again["state"];
    let assets2 = st2["assets"].as_array().map(Vec::len).unwrap_or(0);
    let usages2: usize = st2["assets"]
        .as_array()
        .map(|a| {
            a.iter()
                .map(|x| x["usages"].as_array().map(Vec::len).unwrap_or(0))
                .sum()
        })
        .unwrap_or(0);
    let first_a = st["assets"].as_array().and_then(|a| a.first());
    let first_b = st2["assets"].as_array().and_then(|a| a.first());
    println!(
        "roundtrip: assets={assets2} usages={usages2} save_money={} equal_first={} size={}",
        st2["saveMoney"].as_array().map(Vec::len).unwrap_or(0),
        first_a == first_b,
        re.len()
    );
    if first_a != first_b {
        println!(
            "before={}",
            serde_json::to_string_pretty(first_a.unwrap()).unwrap()
        );
        println!(
            "after ={}",
            serde_json::to_string_pretty(first_b.unwrap()).unwrap()
        );
    }
}
