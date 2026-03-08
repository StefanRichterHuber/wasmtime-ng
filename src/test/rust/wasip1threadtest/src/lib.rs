use std::sync::atomic::{AtomicI32, Ordering};

#[link(wasm_import_module = "wasi_threads")]
unsafe extern "C" {
    fn thread_spawn(arg: i32) -> i32;
}

static SHARED_VALUE: AtomicI32 = AtomicI32::new(0);

#[unsafe(no_mangle)]
pub extern "C" fn wasi_thread_start(tid: i32, arg: i32) {
    println!("WASM: Hello from thread {}, arg: {}", tid, arg);
    // Change the shared value to 42 to prove memory sharing
    SHARED_VALUE.store(42, Ordering::SeqCst);
}

#[unsafe(no_mangle)]
pub extern "C" fn test_entry() {
    println!("WASM: Hello from main thread!");

    // Initialize the shared value to 10
    SHARED_VALUE.store(10, Ordering::SeqCst);

    unsafe {
        let res = thread_spawn(0);
        if res < 0 {
            println!("WASM: Failed to spawn thread: {}", res);
        } else {
            println!("WASM: Spawned thread, tid: {}", res);
            
            // Wait for the thread to change the value
            let mut timeout = 0;
            while SHARED_VALUE.load(Ordering::SeqCst) == 10 && timeout < 1000000 {
                timeout += 1;
                // Just busy wait for this simple test
            }

            if SHARED_VALUE.load(Ordering::SeqCst) == 42 {
                println!("WASM: Success! Shared memory proved.");
            } else {
                println!("WASM: Error! Shared memory NOT proved. Value: {}", SHARED_VALUE.load(Ordering::SeqCst));
            }
        }
    }
}
