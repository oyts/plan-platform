use updater_http::{Certificate, ClientBuilder, redirect::Policy};

pub fn validate_lab_url(value: &url::Url) -> Result<(), String> {
    if value.scheme() != "https" || value.host_str() != Some("127.0.0.1")
        || value.port() != Some(43133) || !value.username().is_empty()
        || value.password().is_some() || value.fragment().is_some() {
        return Err("本地验收更新仅允许 https://127.0.0.1:43133。".into());
    }
    Ok(())
}

/// Used exclusively by the updater; business transport and OS trust are unchanged.
pub fn client(builder: ClientBuilder, certificate: Option<Certificate>) -> ClientBuilder {
    let builder = builder.https_only(true).redirect(Policy::none());
    match certificate {
        Some(value) => builder.no_proxy().tls_certs_only([value]),
        None => builder,
    }
}

pub fn test_certificate() -> Result<Option<Certificate>, String> {
    option_env!("PLAN_DESKTOP_TEST_UPDATE_CERT").filter(|pem| !pem.is_empty())
        .map(|pem| Certificate::from_pem(pem.as_bytes()).map_err(|_| "本地更新测试证书无效。".into()))
        .transpose()
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn lab_trust_is_loopback_https_only() {
        assert!(validate_lab_url(&url::Url::parse("https://127.0.0.1:43133/windows/latest.json").unwrap()).is_ok());
        for value in ["http://127.0.0.1:43133/a", "https://localhost:43133/a", "https://127.0.0.1:443/a", "https://example.com/a", "https://user@127.0.0.1:43133/a", "https://127.0.0.1:43133/a#b"] {
            assert!(validate_lab_url(&url::Url::parse(value).unwrap()).is_err());
        }
    }
    #[tokio::test]
    #[ignore = "requires the managed local HTTPS update lab"]
    async fn local_https_requires_the_embedded_ca_and_correct_hostname() {
        // Tauri's updater initializes ring before building requests; mirror that in this standalone test.
        let _ = rustls::crypto::ring::default_provider().install_default();
        let path = std::env::var("PLAN_UPDATE_LAB_CA_PATH").expect("Set the public lab CA path.");
        let certificate = Certificate::from_pem(&std::fs::read(path).unwrap()).unwrap();
        let trusted = client(ClientBuilder::new(), Some(certificate.clone())).timeout(std::time::Duration::from_secs(5)).build().unwrap();
        assert_eq!(trusted.get("https://127.0.0.1:43133/health").send().await.unwrap().status(), 200);
        let untrusted = client(ClientBuilder::new().no_proxy(), None).timeout(std::time::Duration::from_secs(5)).build().unwrap();
        assert!(untrusted.get("https://127.0.0.1:43133/health").send().await.is_err());
        let wrong_hostname = client(ClientBuilder::new().resolve("localhost", "127.0.0.1:43133".parse().unwrap()), Some(certificate)).timeout(std::time::Duration::from_secs(5)).build().unwrap();
        assert!(wrong_hostname.get("https://localhost:43133/health").send().await.is_err());
        assert!(trusted.get("http://127.0.0.1:43133/health").send().await.is_err());
    }
}
