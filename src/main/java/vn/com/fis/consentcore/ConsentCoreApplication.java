package vn.com.fis.consentcore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ConsentCoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConsentCoreApplication.class, args);
    }
}
