pub mod mergeftl;
pub mod protobuf;

fn main() {
    mergeftl::write_ftl_files_and_fluent_rs();
    protobuf::write_backend_proto_rs();

    // copy or mock buildinfo in out_dir
    let buildinfo = if let Ok(buildinfo) = std::env::var("BUILDINFO") {
        std::fs::read_to_string(&buildinfo).expect("buildinfo missing")
    } else {
        "".to_string()
    };
    let buildinfo_out =
        std::path::Path::new(&std::env::var("OUT_DIR").unwrap()).join("buildinfo.txt");
    std::fs::write(&buildinfo_out, buildinfo).unwrap();
}
