fn main() {
    if std::env::var_os("CARGO_FEATURE_UPDATER_PROBE").is_some() {
        assert!(std::env::var_os("CARGO_FEATURE_NATIVE_REGRESSION").is_none(), "Update probe cannot enable WebView debugging.");
        assert!(std::env::var("PLAN_DESKTOP_UPDATE_URL").unwrap_or_default() == "https://127.0.0.1:43133/windows/latest.json", "Update probe requires the fixed local HTTPS endpoint.");
        assert!(std::env::var("PLAN_DESKTOP_TEST_UPDATE_CERT").is_ok_and(|value| !value.is_empty()), "Update probe requires isolated TLS trust.");
    }
    if std::env::var_os("CARGO_FEATURE_NATIVE_REGRESSION").is_some() {
        assert!(std::env::var("PROFILE").unwrap_or_default() != "release", "Regression debugging cannot be built as a release client.");
    }
    println!("cargo:rerun-if-env-changed=PLAN_DESKTOP_UPDATE_URL");
    println!("cargo:rerun-if-env-changed=PLAN_DESKTOP_UPDATE_PUBKEY");
    println!("cargo:rerun-if-env-changed=PLAN_DESKTOP_TEST_UPDATE_CERT");
    if std::env::var("PLAN_DESKTOP_TEST_UPDATE_CERT").is_ok_and(|value| !value.is_empty()) {
        let certificate = std::env::var("PLAN_DESKTOP_TEST_UPDATE_CERT").unwrap();
        assert!(certificate.trim().starts_with("-----BEGIN CERTIFICATE-----") && certificate.trim().ends_with("-----END CERTIFICATE-----"), "Test TLS certificate was truncated before compilation.");
        let endpoint = std::env::var("PLAN_DESKTOP_UPDATE_URL").unwrap_or_default();
        assert!(endpoint.starts_with("https://127.0.0.1:43133/"), "Test TLS trust is restricted to the loopback update lab.");
    }
    tauri_build::build()
}
