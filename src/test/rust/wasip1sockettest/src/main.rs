use wasi;

fn main() {
    let server_fd = 3; // Expect pre-opened server socket at fd 3
    
    println!("WASM: Calling sock_accept on fd {}...", server_fd);
    unsafe {
        let client_fd = wasi::sock_accept(server_fd, 0).expect("sock_accept failed");
        println!("WASM: Accepted new connection on fd {}", client_fd);
        
        let mut buf = [0u8; 1024];
        let iov = [wasi::Iovec {
            buf: buf.as_mut_ptr(),
            buf_len: buf.len(),
        }];
        
        println!("WASM: Calling sock_recv on fd {}...", client_fd);
        let (nread, _flags) = wasi::sock_recv(client_fd, &iov, 0).expect("sock_recv failed");
        let received = String::from_utf8_lossy(&buf[..nread]);
        println!("WASM: Received from client: {}", received);
        
        let msg = format!("WASM echo: {}", received);
        let ciov = [wasi::Ciovec {
            buf: msg.as_ptr(),
            buf_len: msg.len(),
        }];
        println!("WASM: Calling sock_send on fd {}...", client_fd);
        let nwritten = wasi::sock_send(client_fd, &ciov, 0).expect("sock_send failed");
        println!("WASM: Sent {} bytes to client", nwritten);
        
        println!("WASM: Calling sock_shutdown on fd {}...", client_fd);
        wasi::sock_shutdown(client_fd, wasi::SDFLAGS_RD | wasi::SDFLAGS_WR).expect("sock_shutdown failed");
        
        wasi::fd_close(client_fd).expect("fd_close failed");
        println!("WASM: Closed client connection");
    }
}
