use std::env;
use std::io::{Read, Write};
use std::net::{TcpStream, UdpSocket};

fn main() -> std::io::Result<()> {
    let args: Vec<String> = env::args().collect();
    let tcp_port: u16 = args[1].parse().expect("invalid tcp port");
    let udp_port: u16 = args[2].parse().expect("invalid udp port");

    // TCP: connect out to a Java-hosted echo server.
    println!("Connecting TCP to 127.0.0.1:{}", tcp_port);
    let mut tcp = TcpStream::connect(("127.0.0.1", tcp_port))?;
    tcp.write_all(b"Hello TCP from wasm\n")?;
    let local = tcp.local_addr()?;
    println!("TCP local address: {}", local);
    let mut buf = [0u8; 128];
    let n = tcp.read(&mut buf)?;
    let received = String::from_utf8_lossy(&buf[..n]);
    println!("TCP received: {}", received.trim_end());
    tcp.shutdown(std::net::Shutdown::Both)?;

    // UDP: bind an ephemeral local socket, connect to a Java-hosted echo
    // server, send a datagram, and read the echoed reply.
    println!("Connecting UDP to 127.0.0.1:{}", udp_port);
    let udp = UdpSocket::bind("127.0.0.1:0")?;
    udp.connect(("127.0.0.1", udp_port))?;
    udp.send(b"Hello UDP from wasm")?;
    let mut udp_buf = [0u8; 128];
    let n = udp.recv(&mut udp_buf)?;
    let udp_received = String::from_utf8_lossy(&udp_buf[..n]);
    println!("UDP received: {}", udp_received);

    println!("Done!");
    Ok(())
}
