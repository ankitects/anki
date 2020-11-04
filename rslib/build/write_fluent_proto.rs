include!("mergeftl.rs");

fn main() {
    let args: Vec<_> = std::env::args().collect();
    write_fluent_proto(&args[1]);
}
