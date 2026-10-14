package com.wardk.meeteam_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableCaching
public class MeeteamBackendApplication {

	public static void main(String[] args) {

		System.out.println("Available processors: " + Runtime.getRuntime().availableProcessors());
		SpringApplication.run(MeeteamBackendApplication.class, args);
	}


}
