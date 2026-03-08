use std::fs;
use std::io;

fn main() -> io::Result<()> {
    // 1. Try to open a file with an absolute path (should fail or at least not see host)
    println!("Trying to open '/etc/passwd'...");
    match fs::File::open("/etc/passwd") {
        Ok(_) => println!("Error: Successfully opened '/etc/passwd'!"),
        Err(e) => println!("Correctly failed to open '/etc/passwd': {}", e),
    }

    // 2. Try to open a file with many '..' (should fail or at least not escape Jimfs root)
    println!("Trying to open '../../../../../../../../../../etc/passwd'...");
    match fs::File::open("../../../../../../../../../../etc/passwd") {
        Ok(_) => println!("Error: Successfully opened '../etc/passwd'!"),
        Err(e) => println!("Correctly failed to open '../etc/passwd': {}", e),
    }

    // 3. Try to open a host-specific file passed as argument (absolute host path)
    let args: Vec<String> = std::env::args().collect();
    if args.len() > 1 {
        let host_file = &args[1];
        println!("Trying to open host-specific file: {}...", host_file);
        match fs::File::open(host_file) {
            Ok(_) => println!("Error: Successfully opened host-specific file!"),
            Err(e) => println!("Correctly failed to open host-specific file: {}", e),
        }
    }

    // 4. Try to open a file inside Jimfs (should succeed)
    println!("Trying to open '/tmp/input.txt'...");
    match fs::File::open("/tmp/input.txt") {
        Ok(_) => println!("Successfully opened 'input.txt'!"),
        Err(e) => println!("Failed to open 'input.txt': {}", e),
    }

    Ok(())
}
