use std::collections::HashMap;

fn main() {
    // 1. Test wasi:random/random via getrandom
    let mut buf = [0u8; 16];
    getrandom::getrandom(&mut buf).expect("getrandom failed");
    println!("RANDOM BYTES={:?}", buf);

    // 2. Test wasi:random/insecure-seed via HashMap
    let mut map = HashMap::new();
    map.insert("key1", "val1");
    map.insert("key2", "val2");
    println!("HASH MAP SIZE={}", map.len());
}
