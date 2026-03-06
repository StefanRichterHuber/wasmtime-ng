use std::env;
use std::time::{SystemTime, UNIX_EPOCH};

fn main() {
    println!("Hello, WASI!");

    // Triggers args_sizes_get and args_get
    println!("Arguments:");
    for (i, arg) in env::args().enumerate() {
        println!("  Arg {}: {}", i, arg);
    }

    // Triggers environ_sizes_get and environ_get
    println!("Environment variables:");
    for (key, value) in env::vars() {
        println!("  {}: {}", key, value);
    }

    // Triggers clock_time_get
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .expect("Time went backwards");
    println!("Current time (seconds since EPOCH): {:?}", now.as_secs());

    // Triggers random_get
    let mut random_buf = [0u8; 16];
    unsafe {
        wasi::random_get(random_buf.as_mut_ptr(), random_buf.len()).expect("random_get failed");
    }
    println!("Random values: {:?}", random_buf);
}
