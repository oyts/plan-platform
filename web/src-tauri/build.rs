fn main() {
    println!("cargo:rerun-if-env-changed=PLAN_DESKTOP_UPDATE_URL");
    println!("cargo:rerun-if-env-changed=PLAN_DESKTOP_UPDATE_PUBKEY");
    println!("cargo:rerun-if-env-changed=PLAN_DESKTOP_TEST_UPDATE_CERT");
    if std::env::var("PLAN_DESKTOP_TEST_UPDATE_CERT").is_ok_and(|value| !value.is_empty()) {
        let endpoint = std::env::var("PLAN_DESKTOP_UPDATE_URL").unwrap_or_default();
        assert!(endpoint.starts_with("https://127.0.0.1:43133/"), "Test TLS trust is restricted to the loopback update lab.");
    }
    tauri_build::build()
}
