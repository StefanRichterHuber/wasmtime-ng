use std::env;
use std::io::{IsTerminal, Read};
use std::time::{SystemTime, UNIX_EPOCH};

fn main() {
    println!("Hello from wasip2clitest!");
    eprintln!("stderr line from wasip2clitest");

    // Triggers wasi:cli/environment#get-arguments
    println!("ARGS {:?}", env::args().collect::<Vec<_>>());

    // Triggers wasi:cli/environment#get-environment
    let mut vars: Vec<(String, String)> = env::vars().collect();
    vars.sort();
    for (k, v) in vars {
        println!("ENV {}={}", k, v);
    }

    // Triggers wasi:cli/stdin#get-stdin + wasi:io/streams#[method]input-stream.*
    let mut stdin_contents = String::new();
    std::io::stdin()
        .read_to_string(&mut stdin_contents)
        .expect("failed to read stdin");
    println!("STDIN={}", stdin_contents);

    // Triggers wasi:cli/terminal-stdin/-stdout/-stderr#get-terminal-*
    println!("STDIN_TERMINAL={}", std::io::stdin().is_terminal());
    println!("STDOUT_TERMINAL={}", std::io::stdout().is_terminal());
    println!("STDERR_TERMINAL={}", std::io::stderr().is_terminal());

    // Triggers wasi:clocks/wall-clock#now
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .expect("time went backwards");
    println!("WALL_CLOCK_SECONDS={}", now.as_secs());

    // Triggers wasi:cli/exit#exit with a non-ok result. Unlike WASI Preview 1's
    // proc_exit(rval: u32), WASI Preview 2's exit(status: result<_, _>) can only
    // convey success/failure, not an arbitrary code -- so any non-zero exit
    // code collapses to the same "not ok" result.
    std::process::exit(1);
}
