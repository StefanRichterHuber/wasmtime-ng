wit_bindgen::generate!({
    world: "imports",
    path: "wit",
    generate_all,
});

use wasi::http::outgoing_handler;
use wasi::http::types::{Fields, Method, OutgoingBody, OutgoingRequest, Scheme};

fn main() {
    let port: u16 = std::env::var("WASIP2_HTTP_PORT")
        .expect("WASIP2_HTTP_PORT not set")
        .parse()
        .expect("WASIP2_HTTP_PORT not a number");

    let headers = Fields::new();
    headers
        .append(&"x-request-id".to_string(), &b"hello".to_vec())
        .expect("failed to append header");

    let request = OutgoingRequest::new(headers);
    request
        .set_method(&Method::Get)
        .expect("failed to set method");
    request
        .set_path_with_query(Some("/hello"))
        .expect("failed to set path");
    request
        .set_scheme(Some(&Scheme::Http))
        .expect("failed to set scheme");
    request
        .set_authority(Some(&format!("127.0.0.1:{port}")))
        .expect("failed to set authority");

    let outgoing_body = request.body().expect("failed to get outgoing body");
    // No request body needed for a GET; finish immediately.
    OutgoingBody::finish(outgoing_body, None).expect("failed to finish outgoing body");

    let future_response = outgoing_handler::handle(request, None).expect("handle failed");

    // Resolves synchronously in this host already, but still poll the
    // pollable once to mirror how a real guest would wait for it.
    future_response.subscribe().block();

    let response = future_response
        .get()
        .expect("future-incoming-response not ready")
        .expect("future-incoming-response already consumed")
        .expect("outgoing-handler.handle resolved to an error");

    println!("STATUS={}", response.status());

    let incoming_body = response.consume().expect("failed to consume response body");
    let mut body = Vec::new();
    {
        let stream = incoming_body
            .stream()
            .expect("failed to get incoming body stream");
        loop {
            match stream.blocking_read(4096) {
                Ok(chunk) if chunk.is_empty() => break,
                Ok(mut chunk) => body.append(&mut chunk),
                Err(_) => break,
            }
        }
    }
    let text = String::from_utf8(body).expect("response body was not valid utf-8");
    println!("BODY={}", text);

    println!("Done!");
}
