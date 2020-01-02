use prost_build;

fn main() {
    // avoid default OUT_DIR for now, for code completion
    std::env::set_var("OUT_DIR", "src");
    println!("cargo:rerun-if-changed=../proto/backend.proto");
    prost_build::compile_protos(&["../proto/backend.proto"], &["../proto"]).unwrap();
}
