#[unsafe(no_mangle)]
pub extern "C" fn add_i32(a: i32, b: i32) -> i32 {
    a + b
}

#[unsafe(no_mangle)]
pub extern "C" fn add_i64(a: i64, b: i64) -> i64 {
    a + b
}

#[unsafe(no_mangle)]
pub extern "C" fn add_f32(a: f32, b: f32) -> f32 {
    a + b
}

#[unsafe(no_mangle)]
pub extern "C" fn add_f64(a: f64, b: f64) -> f64 {
    a + b
}

fn main() {
    // Empty main for WASI
}
