fn version(value: &str) -> Result<[u64; 3], String> {
    let parts: Vec<_> = value.split('.').collect();
    if parts.len() != 3 || parts.iter().any(|part| part.is_empty() || !part.bytes().all(|c| c.is_ascii_digit()) || (part.len() > 1 && part.starts_with('0'))) {
        return Err("发布版本必须使用 x.y.z 数字格式。".into());
    }
    Ok([parts[0].parse().map_err(|_| "版本号无效。")?, parts[1].parse().map_err(|_| "版本号无效。")?, parts[2].parse().map_err(|_| "版本号无效。")?])
}
pub fn mandatory(current: &str, latest: &str, minimum: &str) -> Result<bool, String> {
    let current = version(current)?; let latest = version(latest)?; let minimum = version(minimum)?;
    if minimum > latest { return Err("最低支持版本不能高于发布版本。".into()); }
    Ok(current < minimum)
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn optional_and_required_are_explicit() {
        assert!(!mandatory("0.1.0", "0.2.0", "0.1.0").unwrap());
        assert!(mandatory("0.1.0", "0.2.0", "0.2.0").unwrap());
        assert!(!mandatory("0.2.0", "0.2.0", "0.2.0").unwrap());
    }
    #[test] fn numeric_comparison_not_lexical() { assert!(mandatory("1.9.0", "1.10.0", "1.10.0").unwrap()); }
    #[test] fn invalid_policy_is_rejected() {
        for value in ["1", "01.2.3", "1.2.beta", "1.2.3-rc"] { assert!(mandatory("1.0.0", "2.0.0", value).is_err()); }
        assert!(mandatory("1.0.0", "2.0.0", "3.0.0").is_err());
    }
}
