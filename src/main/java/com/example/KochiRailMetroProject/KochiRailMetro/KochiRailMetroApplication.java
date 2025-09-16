package com.example.KochiRailMetroProject.KochiRailMetro;

import com.example.KochiRailMetroProject.KochiRailMetro.util.SSLUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;


@SpringBootApplication
public class KochiRailMetroApplication {

	public static void main(String[] args) {
		SpringApplication.run(KochiRailMetroApplication.class, args);

        SSLUtil.disableSSLVerification();
    }
        }


