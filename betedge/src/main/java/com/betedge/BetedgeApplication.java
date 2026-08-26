package com.betedge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BetedgeApplication {

	public static void main(String[] args) {
		SpringApplication.run(BetedgeApplication.class, args);
	}

}
