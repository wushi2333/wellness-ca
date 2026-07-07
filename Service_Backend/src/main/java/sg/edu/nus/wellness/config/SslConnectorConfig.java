// Author: Guo Jiali
package sg.edu.nus.wellness.config;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * Adds an extra HTTPS connector on 8443 alongside the default HTTP connector on 8000.
 * The default HTTP port (server.port=8000) stays untouched for curl/server-side checks;
 * browsers use https://152.42.181.66:8443 for features that require a secure context
 * (microphone/ASR, Google OAuth). Self-signed cert => browsers warn once, then proceed.
 */
@Configuration
public class SslConnectorConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Value("${app.ssl.port:8443}")
    private int sslPort;

    @Value("${app.ssl.key-store:/home/wellness-dev/wellness-backend/ssl/wellness.p12}")
    private String keyStore;

    @Value("${app.ssl.key-store-password:wellness2026}")
    private String keyStorePassword;

    @Value("${app.ssl.key-store-type:PKCS12}")
    private String keyStoreType;

    @Value("${app.ssl.key-alias:wellness}")
    private String keyAlias;

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addAdditionalTomcatConnectors(httpsConnector());
    }

    private Connector httpsConnector() {
        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
        connector.setPort(sslPort);
        connector.setSecure(true);
        connector.setScheme("https");

        SSLHostConfig sslHostConfig = new SSLHostConfig();
        SSLHostConfigCertificate cert = new SSLHostConfigCertificate(
                sslHostConfig, SSLHostConfigCertificate.Type.RSA);
        cert.setCertificateKeystoreFile(keyStore);
        cert.setCertificateKeystorePassword(keyStorePassword);
        cert.setCertificateKeystoreType(keyStoreType);
        cert.setCertificateKeyAlias(keyAlias);
        sslHostConfig.addCertificate(cert);

        Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
        protocol.addSslHostConfig(sslHostConfig);
        protocol.setSSLEnabled(true);
        return connector;
    }
}
