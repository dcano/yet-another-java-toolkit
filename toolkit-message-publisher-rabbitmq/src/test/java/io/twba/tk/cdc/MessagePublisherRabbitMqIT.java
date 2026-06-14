package io.twba.tk.cdc;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.v1.CloudEventBuilder;
import io.twba.tk.cdc.testevents.SampleEvent;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

import static io.twba.tk.event.TwbaCloudEvent.CLOUD_EVENT_GENERATING_APP_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
class MessagePublisherRabbitMqIT {

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));

    private static CachingConnectionFactory connectionFactory;

    @BeforeAll
    static void setUp() {
        connectionFactory = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        connectionFactory.setUsername(RABBIT.getAdminUsername());
        connectionFactory.setPassword(RABBIT.getAdminPassword());
        // Required for the ReturnsCallback to fire on unroutable messages.
        connectionFactory.setPublisherReturns(true);
    }

    @AfterAll
    static void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void publishesToRoutableExchangeAndQueueDeliversMessage() {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        String appName = "test-app-" + UUID.randomUUID();
        String exchangeName = "__MR__" + appName;
        String eventType = "io.twba.example.SomethingHappened";
        String queueName = "q-" + UUID.randomUUID();

        TopicExchange exchange = new TopicExchange(exchangeName, true, false);
        Queue queue = new Queue(queueName, true, false, false);
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(eventType);
        admin.declareExchange(exchange);
        admin.declareQueue(queue);
        admin.declareBinding(binding);

        MessagePublisher publisher = new MessagePublisherRabbitMq(new RabbitTemplate(connectionFactory));
        publisher.publish(cloudEvent(appName, eventType, "{\"hello\":\"world\"}"));

        RabbitTemplate receiver = new RabbitTemplate(connectionFactory);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Message received = receiver.receive(queueName);
            assertThat(received).isNotNull();
            assertThat(new String(received.getBody(), StandardCharsets.UTF_8)).contains("hello");
            assertThat(received.getMessageProperties().getHeaders().get("cloudEvents_type")).isEqualTo(eventType);
        });
    }

    @Test
    void consumerDeserializesPublishedEventViaCloudEventMessageConverter() {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        String appName = "projector-app-" + UUID.randomUUID();
        String exchangeName = "__MR__" + appName;
        // Producer emits the type as the lowercased fully-qualified class name.
        String eventType = SampleEvent.class.getName().toLowerCase(Locale.ROOT);
        String queueName = "q-" + UUID.randomUUID();

        TopicExchange exchange = new TopicExchange(exchangeName, true, false);
        Queue queue = new Queue(queueName, true, false, false);
        admin.declareExchange(exchange);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(eventType));

        MessagePublisher publisher = new MessagePublisherRabbitMq(new RabbitTemplate(connectionFactory));
        publisher.publish(cloudEvent(appName, eventType, "{\"aggregateId\":\"agg-7\",\"amount\":99}"));

        EventRegistry registry = new EventRegistryReflection("io.twba.tk.cdc.testevents");
        RabbitTemplate receiver = new RabbitTemplate(connectionFactory);
        receiver.setMessageConverter(new CloudEventMessageConverter(JsonMapper.builder().build(), registry));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Object received = receiver.receiveAndConvert(queueName);
            assertThat(received).isInstanceOf(SampleEvent.class);
            SampleEvent event = (SampleEvent) received;
            assertThat(event.aggregateId()).isEqualTo("agg-7");
            assertThat(event.amount()).isEqualTo(99);
        });
    }

    @Test
    void unroutableMessageTriggersReturnsCallbackAndLogsError() {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        String appName = "no-binding-" + UUID.randomUUID();
        String exchangeName = "__MR__" + appName;
        // Declare the exchange but bind no queue -> message is unroutable.
        admin.declareExchange(new TopicExchange(exchangeName, true, false));

        Logger publisherLogger = (Logger) LoggerFactory.getLogger(MessagePublisherRabbitMq.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        publisherLogger.addAppender(appender);
        try {
            MessagePublisher publisher = new MessagePublisherRabbitMq(new RabbitTemplate(connectionFactory), true);
            publisher.publish(cloudEvent(appName, "io.twba.example.Unroutable", "{}"));

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(appender.list)
                            .anyMatch(event -> event.getLevel() == Level.ERROR
                                    && event.getFormattedMessage().contains("Unroutable message returned by broker")
                                    && event.getFormattedMessage().contains(exchangeName)));
        } finally {
            publisherLogger.detachAppender(appender);
        }
    }

    @Test
    void toolkitQueueBuilderDeclaresTopologyOnBroker() throws Exception {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        String queueName = "orders-" + UUID.randomUUID();
        declare(admin, ToolkitQueueBuilder.forQueue(queueName).build());

        assertThat(admin.getQueueProperties(queueName)).isNotNull();
        assertThat(admin.getQueueProperties("dlq." + queueName)).isNotNull();

        // Passive declaration throws if the dead-letter exchange does not exist.
        org.springframework.amqp.rabbit.connection.Connection connection = connectionFactory.createConnection();
        Channel channel = connection.createChannel(false);
        try {
            channel.exchangeDeclarePassive("dlx." + queueName);
        } finally {
            channel.close();
        }
    }

    @Test
    void nackedMessageIsRoutedToDeadLetterQueue() throws Exception {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        String queueName = "payments-" + UUID.randomUUID();
        declare(admin, ToolkitQueueBuilder.forQueue(queueName).build());

        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.convertAndSend(queueName, "poison-message");

        // Reject the message without requeue so it is dead-lettered.
        org.springframework.amqp.rabbit.connection.Connection connection = connectionFactory.createConnection();
        Channel channel = connection.createChannel(false);
        try {
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                GetResponse response = channel.basicGet(queueName, false);
                assertThat(response).isNotNull();
                channel.basicNack(response.getEnvelope().getDeliveryTag(), false, false);
            });
        } finally {
            channel.close();
        }

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Message dead = template.receive("dlq." + queueName);
            assertThat(dead).isNotNull();
            assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).contains("poison-message");
        });
    }

    private static void declare(RabbitAdmin admin, Declarables declarables) {
        for (Declarable declarable : declarables.getDeclarables()) {
            if (declarable instanceof Exchange exchange) {
                admin.declareExchange(exchange);
            } else if (declarable instanceof Queue queue) {
                admin.declareQueue(queue);
            } else if (declarable instanceof Binding binding) {
                admin.declareBinding(binding);
            }
        }
    }

    private static CloudEvent cloudEvent(String appName, String type, String jsonPayload) {
        return new CloudEventBuilder()
                .withId(UUID.randomUUID().toString())
                .withType(type)
                .withSubject("aggregate-1")
                .withSource(URI.create("https://thewhiteboardarchitect.com/" + appName))
                .withExtension(CLOUD_EVENT_GENERATING_APP_NAME, appName)
                .withData("application/json", jsonPayload.getBytes(StandardCharsets.UTF_8))
                .build();
    }
}
