use std::fs;
use std::io::{Read, Seek, SeekFrom, Write};
use std::time::{Duration, SystemTime};

fn main() -> std::io::Result<()> {
    // 1. Read input.txt (created by Java)
    println!("Reading 'input.txt'...");
    let mut input_file = fs::File::open("input.txt")?;
    let mut content = String::new();
    input_file.read_to_string(&mut content)?;
    println!("Content of 'input.txt': {}", content);

    // 2. Create directory 'wasm_out'
    println!("Creating directory 'wasm_out'...");
    fs::create_dir_all("wasm_out")?;

    // 3. Write output.txt
    println!("Writing to 'wasm_out/output.txt'...");
    let mut output_file = fs::File::create("wasm_out/output.txt")?;
    output_file.write_all(format!("WASM received: {}", content).as_bytes())?;

    // 4. Rename
    println!("Testing rename...");
    fs::create_dir_all("test_rename_dir")?;
    let rename_src = "test_rename_dir/file.txt";
    let rename_dst = "test_rename_dir/moved.txt";
    fs::File::create(rename_src)?.write_all(b"rename test")?;
    fs::rename(rename_src, rename_dst)?;

    let mut rename_content = String::new();
    fs::File::open(rename_dst)?.read_to_string(&mut rename_content)?;
    assert_eq!(rename_content, "rename test");

    // 5. Seek / tell / set_len
    println!("Testing seek and set_len...");
    let mut file = fs::OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(true)
        .open("fd_test.txt")?;
    file.write_all(b"0123456789ABCDEF")?;
    file.set_len(8)?;
    file.seek(SeekFrom::Start(4))?;
    let pos = file.stream_position()?;
    assert_eq!(pos, 4);
    file.seek(SeekFrom::End(0))?;
    file.write_all(b"XYZ")?;

    // 6. Sync
    println!("Testing sync...");
    file.sync_all()?;
    file.sync_data()?;

    // 7. Set times
    println!("Testing set_times...");
    let now = SystemTime::now();
    let earlier = now - Duration::from_secs(3600);
    let times = fs::FileTimes::new().set_accessed(earlier).set_modified(earlier);
    file.set_times(times)?;

    // 8. Readdir
    println!("Testing readdir...");
    let mut entries: Vec<String> = fs::read_dir(".")?
        .filter_map(|e| e.ok())
        .map(|e| e.file_name().to_string_lossy().into_owned())
        .collect();
    entries.sort();
    for name in &entries {
        println!("  Found entry: {}", name);
    }

    // 9. Metadata
    println!("Testing metadata...");
    let metadata = fs::metadata("input.txt")?;
    println!("  File size: {}", metadata.len());
    println!("  Is file: {}", metadata.is_file());
    let dir_metadata = fs::metadata("wasm_out")?;
    println!("  Is dir: {}", dir_metadata.is_dir());

    // 10. Hard link
    println!("Testing hard_link...");
    fs::hard_link("input.txt", "hardlink_to_input.txt")?;
    assert!(fs::metadata("hardlink_to_input.txt")?.is_file());

    // 11. Remove file
    println!("Testing remove_file...");
    fs::remove_file("hardlink_to_input.txt")?;

    // 12. Remove dir
    println!("Testing remove_dir...");
    fs::create_dir("dir_to_remove")?;
    fs::remove_dir("dir_to_remove")?;

    // 13. Sandbox check: escaping the preopened directory must fail
    println!("Testing sandbox escape prevention...");
    match fs::File::open("../../../../../../../../../../etc/passwd") {
        Ok(_) => panic!("Sandbox escape via '..' should not succeed!"),
        Err(e) => println!("  Correctly failed to escape sandbox: {}", e),
    }

    println!("Done!");
    Ok(())
}
