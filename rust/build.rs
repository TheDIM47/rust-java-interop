fn main() {
    // Create C headers with cbindgen
    let crate_dir = std::env::var("CARGO_MANIFEST_DIR").unwrap();
    cbindgen::Builder::new()
        .with_crate(crate_dir.clone())
        .with_namespace("c")
        .with_language(cbindgen::Language::C)
        .generate()
        .unwrap()
        .write_to_file("bindings/java_interop.h");

    // use full path to `jextract` on error
    let jextract_home = std::env::var("JEXTRACT_HOME").unwrap_or_default();
    let mut binding = std::process::Command::new(format!("{}jextract", jextract_home));
    let cmd= binding.current_dir(crate_dir)
        .arg("--include-dir")
        .arg("bindings/")
        .arg("--output")
        .arg("../java/src/main/java")
        .arg("--target-package")
        .arg("golf.tweede.gen")
        .arg("--library")
        .arg(":../rust/target/release/libjava_interop.so")
        .arg("bindings/java_interop.h");

    let _ = cmd.spawn().unwrap().wait();
}
