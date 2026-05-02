package io.twba.tk.cdc.message_relay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class StatePropagationDebeziumApp {

    public static void main(String[] args) {
        SpringApplication.run(StatePropagationDebeziumApp.class, args);
    }

}
