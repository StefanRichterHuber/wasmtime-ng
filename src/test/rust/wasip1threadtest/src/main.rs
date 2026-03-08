use std::thread;
use std::sync::{Arc, Mutex};

fn main() {
    println!("WASM: Hello from main thread!");

    let message = Arc::new(Mutex::new("Greeting from main to thread".to_string()));

    let mut handles = vec![];

    for _ in 0..3 {
        let msg = Arc::clone(&message);
        let handle = thread::spawn(move || {
            let data = msg.lock().unwrap();
            println!("WASM: Hello from thread {:?}, arg: {}", thread::current().id(), *data);
        });
        handles.push(handle);
    }

    for handle in handles {
        handle.join().unwrap();
    }
    
    println!("WASM: All threads finished!");
}
