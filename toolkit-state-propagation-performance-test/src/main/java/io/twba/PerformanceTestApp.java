package io.twba;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootApplication
@EnableRabbit
public class PerformanceTestApp {

    private final static Logger log = LoggerFactory.getLogger(PerformanceTestApp.class);
    private static final int PRODUCERS = 2;

    public static void main(String[] args) {
        SpringApplication.run(PerformanceTestApp.class);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "testConsumerQueue", durable = "true"),
            exchange = @Exchange(value = "__MR__course-management", ignoreDeclarationExceptions = "true", type = "topic"),
            key = "com.twba.course_management.coursedefinitioncreatedevent")
    )
    public void receiveMessage(String message) {
        log.info("Message received:::::{}", message);
    }

    @PostConstruct
    public void startProducer() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        for(int i = 1; i < PRODUCERS; i++) {
            executor.execute(new DataProducer(UUID.randomUUID().toString()));
        }
    }

    public static class DataProducer implements Runnable {

        private final String shift;

        public DataProducer(String shift) {
            this.shift = shift;
        }

        @Override
        public void run() {
            RestTemplate restTemplate = new RestTemplateBuilder().build();
            String apiUrl = "http://localhost:9095/twba/course";
            final String requestJson = "{\n" +
                    "    \"id\": \"{{myCourseId}}\",\n" +
                    "    \"title\": \"{{courseTitle}}\",\n" +
                    "    \"summary\": \"Course Summary 2\",\n" +
                    "    \"description\": \"Course description2\",\n" +
                    "    \"teacherId\": \"teacherId2\",\n" +
                    "    \"openingDate\": \"2022-04-06T00:00:00.00Z\",\n" +
                    "    \"publicationDate\": \"2022-04-03T00:00:00.00Z\",\n" +
                    "    \"preRequirement\": \"Course pre requirement2\",\n" +
                    "    \"objective\": \"Course objective2\",\n" +
                    "    \"expectedDurationHours\": \"50\",\n" +
                    "    \"numberOfClasses\": \"10\",\n" +
                    "    \"status\": \"XX\"\n" +
                    "}";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            for (int i = 0; i < 2000; i++) {
                String requestJsonToSend = requestJson.replace("{{myCourseId}}", "courseId__" + i + shift);
                requestJsonToSend = requestJsonToSend.replace("{{courseTitle}}", "courseTitle__" + i + shift);

                HttpEntity<String> requestEntity = new HttpEntity<>(requestJsonToSend, headers);

                restTemplate.postForEntity(apiUrl, requestEntity, String.class);
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }


}
