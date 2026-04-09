package com.example.ragbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

//@SpringBootApplication(exclude = { org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class })
@SpringBootApplication
@MapperScan("com.example.ragbackend.mapper")
public class RagbackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(RagbackendApplication.class, args);
	}

}
