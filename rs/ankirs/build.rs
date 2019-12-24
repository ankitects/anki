use prost_build;

fn main() {
    // avoid default OUT_DIR for now, for code completion
    std::env::set_var("OUT_DIR", "src");
    prost_build::compile_protos(&["../../proto/backend.proto"], &["../../proto/"]).unwrap();
}
