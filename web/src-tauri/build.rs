fn main() {
    println!("cargo:rerun-if-env-changed=PLAN_DESKTOP_UPDATE_URL");
    println!("cargo:rerun-if-env-changed=PLAN_DESKTOP_UPDATE_PUBKEY");
    tauri_build::build()
}
