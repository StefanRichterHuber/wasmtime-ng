use std::mem;

#[link(wasm_import_module = "wasi_threads")]
unsafe extern "C" {
    fn thread_spawn(arg: i32) -> i32;
}

#[unsafe(no_mangle)]
pub extern "C" fn wasi_thread_start(tid: i32, arg: i32) {
    let a = unsafe { Box::from_raw(arg as *mut String) };
    println!("WASM: Hello from thread {}, arg: {}", tid, a);
}

/// Give the host a way to free memory to prevent leaks
#[unsafe(no_mangle)]
pub extern "C" fn dealloc(ptr: *mut u8, size: usize) {
    unsafe {
        let _ = Vec::from_raw_parts(ptr, 0, size);
    }
}

/// Give the host a way to allocate memory inside the Wasm module
#[unsafe(no_mangle)]
pub extern "C" fn alloc(size: usize) -> *mut u8 {
    let mut buf = Vec::with_capacity(size);
    let ptr = buf.as_mut_ptr();
    mem::forget(buf); // Prevent Rust from freeing the memory
    ptr
}

fn main() {
    println!("WASM: Hello from main thread!");

    // Write something to shared memory
    let value = Box::new("Greeting from main to thread".to_string());
    let ptr = Box::into_raw(value) as i32;

    unsafe {
        let res = thread_spawn(ptr);
        if res < 0 {
            println!("WASM: Failed to spawn thread: {}", res);
        } else {
            println!("WASM: Spawned thread, tid: {}", res);
        }
    }
}
