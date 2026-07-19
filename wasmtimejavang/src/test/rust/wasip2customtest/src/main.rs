wit_bindgen::generate!({
    world: "custom-world",
    path: "wit",
});

use my::custom::greet;

fn main() {
    let greeting = greet::hello("Wasmtime-Java");
    println!("GREETING={}", greeting);

    let sum = greet::add(19, 23);
    println!("SUM={}", sum);

    println!("Done!");
}
