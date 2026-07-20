use std::fs;
use std::io::{Read, Seek, SeekFrom, Write};
use std::os::fd::AsRawFd;

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

    // 4. Test some other FS operations (rename)
    println!("Testing rename...");
    fs::create_dir_all("test_rename_dir")?;
    let rename_src = "test_rename_dir/file.txt";
    let rename_dst = "test_rename_dir/moved.txt";
    fs::File::create(rename_src)?.write_all(b"rename test")?;
    fs::rename(rename_src, rename_dst)?;

    // Verify rename worked
    let mut rename_content = String::new();
    fs::File::open(rename_dst)?.read_to_string(&mut rename_content)?;
    assert_eq!(rename_content, "rename test");

    // 5. Test FD operations
    println!("Testing FD operations...");
    let mut file = fs::OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .open("fd_test.txt")?;
    let fd = file.as_raw_fd();

    // fd_allocate
    println!("  fd_allocate...");
    unsafe {
        wasi::fd_allocate(fd as wasi::Fd, 0, 1024).expect("fd_allocate failed");
    }

    // fd_filestat_set_size
    println!("  fd_filestat_set_size...");
    unsafe {
        wasi::fd_filestat_set_size(fd as wasi::Fd, 512).expect("fd_filestat_set_size failed");
    }

    // fd_seek
    println!("  fd_seek...");
    file.seek(SeekFrom::Start(10))?;

    // fd_tell (part of fd_seek in std)
    let pos = file.stream_position()?;
    assert_eq!(pos, 10);

    // fd_write
    file.write_all(b"Hello FD")?;

    // fd_sync / fd_datasync
    println!("  fd_sync / fd_datasync...");
    file.sync_all()?;
    file.sync_data()?;

    // fd_fdstat_get
    println!("  fd_fdstat_get...");
    unsafe {
        let stat = wasi::fd_fdstat_get(fd as wasi::Fd).expect("fd_fdstat_get failed");
        println!("  FD flags: {:?}", stat.fs_flags);
    }

    // fd_fdstat_set_flags
    println!("  fd_fdstat_set_flags...");
    unsafe {
        wasi::fd_fdstat_set_flags(fd as wasi::Fd, wasi::FDFLAGS_APPEND)
            .expect("fd_fdstat_set_flags failed");
    }

    // fd_pread
    println!("  fd_pread...");
    let mut pread_buf = [0u8; 8];
    unsafe {
        let iov = [wasi::Iovec {
            buf: pread_buf.as_mut_ptr(),
            buf_len: pread_buf.len(),
        }];
        wasi::fd_pread(fd as wasi::Fd, &iov, 10).expect("fd_pread failed");
    }
    assert_eq!(&pread_buf, b"Hello FD");

    // fd_readdir
    println!("  fd_readdir...");
    let dir = fs::read_dir(".")?;
    for entry in dir {
        println!("  Found entry: {:?}", entry?.file_name());
    }

    // fd_renumber
    println!("  fd_renumber...");
    let file2 = fs::File::open("fd_test.txt")?;
    let fd2 = file2.as_raw_fd();
    unsafe {
        let target_fd = 100;
        wasi::fd_renumber(fd2 as wasi::Fd, target_fd).expect("fd_renumber failed");
        wasi::fd_close(target_fd).expect("fd_close failed");
    }

    // 6. Path operations
    println!("Testing Path operations...");

    // path_link
    println!("  path_link...");
    let hardlink_path = "hardlink_to_input.txt";
    fs::hard_link("input.txt", hardlink_path)?;
    assert!(fs::metadata(hardlink_path)?.is_file());

    // path_filestat_get
    println!("  path_filestat_get...");
    let metadata = fs::metadata("input.txt")?;
    println!("  File size: {}", metadata.len());

    // path_filestat_set_times
    println!("  path_filestat_set_times...");
    unsafe {
        wasi::path_filestat_set_times(
            3, // FD of preopened "."
            0,
            "input.txt",
            1000,
            2000,
            wasi::FSTFLAGS_ATIM | wasi::FSTFLAGS_MTIM,
        )
        .expect("path_filestat_set_times failed");
    }

    // path_unlink_file
    println!("  path_unlink_file...");
    fs::remove_file(hardlink_path)?;

    // path_remove_directory
    println!("  path_remove_directory...");
    fs::create_dir("dir_to_remove")?;
    fs::remove_dir("dir_to_remove")?;

    println!("Done!");
    Ok(())
}
