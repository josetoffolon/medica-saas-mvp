package com.bisioneers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class MedicaSaasApplication {

	public static void main(String[] args) {
		SpringApplication.run(MedicaSaasApplication.class, args);
	}

}
