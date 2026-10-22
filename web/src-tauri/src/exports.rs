//! Narrow export-only Save As. The renderer never supplies a destination path.
use std::path::Path;

const MAX_EXPORT_BYTES: usize = 32 * 1024 * 1024;

fn validate(file_name: &str, body: &str) -> Result<String, String> {
    if file_name.is_empty() || file_name.chars().count() > 160 || file_name.starts_with('.')
        || file_name.ends_with([' ', '.']) || file_name.chars().any(|c| c.is_control() || "\\/:*?\"<>|".contains(c)) {
        return Err("导出文件名无效。".into());
    }
    let stem = file_name.split('.').next().unwrap_or("").trim().to_ascii_uppercase();
    if ["CON", "PRN", "AUX", "NUL"].contains(&stem.as_str())
        || (stem.len() == 4 && (stem.starts_with("COM") || stem.starts_with("LPT")) && stem.as_bytes()[3].is_ascii_digit()) {
        return Err("导出文件名是系统保留名称。".into());
    }
    let extension = Path::new(file_name).extension().and_then(|e| e.to_str()).unwrap_or("").to_ascii_lowercase();
    if !["csv", "json", "md"].contains(&extension.as_str()) { return Err("仅支持 CSV、JSON 和 Markdown 导出。".into()); }
    if body.len() > MAX_EXPORT_BYTES { return Err("导出文件超过32 MiB，请缩小范围。".into()); }
    Ok(extension)
}

#[cfg(windows)]
pub fn save_as(owner: isize, file_name: String, body: String) -> Result<bool, String> {
    use windows::{core::{PCWSTR, w}, Win32::{Foundation::HWND, System::Com::*, UI::Shell::{*, Common::COMDLG_FILTERSPEC}}};
    let extension = validate(&file_name, &body)?;
    static ACTIVE: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);
    if ACTIVE.compare_exchange(false,true,std::sync::atomic::Ordering::AcqRel,std::sync::atomic::Ordering::Acquire).is_err() {
        return Err("已有保存窗口打开，请先完成或取消。".into());
    }
    struct Active;
    impl Drop for Active { fn drop(&mut self) { ACTIVE.store(false,std::sync::atomic::Ordering::Release); } }
    let _active=Active;
    struct Com;
    impl Drop for Com { fn drop(&mut self) { unsafe { CoUninitialize(); } } }
    unsafe {
        CoInitializeEx(None, COINIT_APARTMENTTHREADED | COINIT_DISABLE_OLE1DDE).ok().map_err(|_| "无法启动系统保存窗口。")?;
        let _com = Com;
        let dialog: IFileSaveDialog = CoCreateInstance(&FileSaveDialog, None, CLSCTX_INPROC_SERVER).map_err(|_| "无法创建系统保存窗口。")?;
        let name: Vec<u16> = file_name.encode_utf16().chain(Some(0)).collect();
        let ext: Vec<u16> = extension.encode_utf16().chain(Some(0)).collect();
        let pattern: Vec<u16> = format!("*.{extension}").encode_utf16().chain(Some(0)).collect();
        let setup = || -> windows::core::Result<()> {
            dialog.SetTitle(w!("保存计划舱导出文件"))?;
            dialog.SetFileName(PCWSTR(name.as_ptr()))?;
            dialog.SetDefaultExtension(PCWSTR(ext.as_ptr()))?;
            dialog.SetFileTypes(&[COMDLG_FILTERSPEC { pszName: w!("计划舱导出文件"), pszSpec: PCWSTR(pattern.as_ptr()) }])?;
            dialog.SetOptions(dialog.GetOptions()? | FOS_FORCEFILESYSTEM | FOS_PATHMUSTEXIST | FOS_OVERWRITEPROMPT | FOS_STRICTFILETYPES)?;
            Ok(())
        };
        setup().map_err(|_| "无法配置系统保存窗口。")?;
        match dialog.Show(Some(HWND(owner as *mut _))) {
            Err(error) if error.code().0 as u32 == 0x800704c7 => return Ok(false),
            Err(_) => return Err("系统保存窗口未能完成，请重试。".into()),
            Ok(()) => {}
        }
        let item = dialog.GetResult().map_err(|_| "无法读取所选保存位置。")?;
        let raw = item.GetDisplayName(SIGDN_FILESYSPATH).map_err(|_| "仅支持保存到文件系统。")?;
        let value = raw.to_string();
        CoTaskMemFree(Some(raw.0.cast()));
        let destination = std::path::PathBuf::from(value.map_err(|_| "保存路径编码无效。")?);
        if !destination.is_absolute() || destination.extension().and_then(|e| e.to_str()).is_none_or(|e| !e.eq_ignore_ascii_case(&extension)) {
            return Err("请保留所选导出文件的扩展名。".into());
        }
        write_selected(&destination, body.as_bytes())?;
        Ok(true)
    }
}

#[cfg(windows)]
fn write_selected(destination: &Path, bytes: &[u8]) -> Result<(), String> {
    use std::{fs::OpenOptions, io::Write, os::windows::ffi::OsStrExt};
    use windows::{core::PCWSTR, Win32::Storage::FileSystem::{MoveFileExW, MOVEFILE_REPLACE_EXISTING, MOVEFILE_WRITE_THROUGH}};
    let parent = destination.parent().ok_or("保存位置无效。")?;
    struct Temporary(std::path::PathBuf);
    impl Drop for Temporary { fn drop(&mut self) { let _ = std::fs::remove_file(&self.0); } }
    let stamp = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).map_err(|_| "本机时间无效。")?.as_nanos();
    for attempt in 0..16 {
        let path = parent.join(format!(".plan-cabin-export-{}-{stamp}-{attempt}.tmp", std::process::id()));
        let mut file = match OpenOptions::new().write(true).create_new(true).open(&path) {
            Ok(file) => file,
            Err(e) if e.kind() == std::io::ErrorKind::AlreadyExists => continue,
            Err(_) => return Err("无法写入所选位置，请检查目录权限与剩余空间。".into()),
        };
        let temporary = Temporary(path);
        let written=file.write_all(bytes).and_then(|_| file.sync_all());
        drop(file);
        written.map_err(|_| "文件写入未完成，原目标文件未被替换。")?;
        let from: Vec<u16> = temporary.0.as_os_str().encode_wide().chain(Some(0)).collect();
        let to: Vec<u16> = destination.as_os_str().encode_wide().chain(Some(0)).collect();
        unsafe { MoveFileExW(PCWSTR(from.as_ptr()), PCWSTR(to.as_ptr()), MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH) }
            .map_err(|_| "无法完成保存，原文件未被替换；请检查文件是否正在被其他程序占用。")?;
        return Ok(());
    }
    Err("无法创建临时导出文件，请重试。".into())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn export_validation_rejects_paths_reserved_names_and_executables() {
        for name in ["../private.json", "C:\\private.json", "file:stream.csv", "CON.json", "LPT1.md", "test.exe", "hidden.json.", ".secret.json", "bad\0.csv"] {
            assert!(validate(name, "合成内容").is_err(), "{name}");
        }
        assert_eq!(validate("计划复盘.JSON", "\u{feff}合成🎸\r\n").unwrap(), "json");
    }
    #[test] fn export_validation_bounds_utf8_bytes() {
        assert!(validate("test.csv", &"x".repeat(MAX_EXPORT_BYTES)).is_ok());
        assert!(validate("test.csv", &"x".repeat(MAX_EXPORT_BYTES + 1)).is_err());
    }
    #[cfg(windows)]
    #[test] fn selected_file_write_preserves_bytes_and_replaces_only_the_selected_file() {
        let stamp=std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos();
        let dir=std::env::temp_dir().join(format!("plan-cabin-export-test-{}-{stamp}",std::process::id()));
        std::fs::create_dir(&dir).unwrap();let target=dir.join("合成导出.csv");let neighbor=dir.join("untouched.json");
        std::fs::write(&target,b"old synthetic").unwrap();std::fs::write(&neighbor,b"neighbor").unwrap();
        let bytes="\u{feff}日期,计划\r\n2026-10-22,合成🎸".as_bytes();write_selected(&target,bytes).unwrap();
        assert_eq!(std::fs::read(&target).unwrap(),bytes);assert_eq!(std::fs::read(&neighbor).unwrap(),b"neighbor");
        assert_eq!(std::fs::read_dir(&dir).unwrap().count(),2);
        std::fs::remove_file(target).unwrap();std::fs::remove_file(neighbor).unwrap();std::fs::remove_dir(dir).unwrap();
    }
}
