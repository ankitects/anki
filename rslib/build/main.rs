pub mod mergeftl;
pub mod protobuf;

fn main() {
    mergeftl::write_ftl_files_and_fluent_rs();
    protobuf::write_backend_proto_rs();

    // when building with cargo (eg for rust analyzer), generate a dummy BUILDINFO
    if std::env::var("BAZEL").is_err() {
        let buildinfo_out =
            std::path::Path::new(&std::env::var("OUT_DIR").unwrap()).join("buildinfo.txt");
        std::fs::write(&buildinfo_out, "").unwrap();
        println!(
            "cargo:rustc-env=BUILDINFO={}",
            buildinfo_out.to_str().expect("buildinfo")
        );
    }
}
