package com.example.KochiRailMetroProject.KochiRailMetro.util;

 // use your package

import javax.net.ssl.*;
import java.security.cert.X509Certificate;

public class SSLUtil {

    // Call this method to disable SSL validation
    public static void disableSSLVerification() {
        try {
            // Trust all certificates
            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };

            // Initialize SSL context
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());

            // Apply the SSL context to all HTTPS connections
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Accept all hostnames
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

            System.out.println("⚠️ SSL verification disabled!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

