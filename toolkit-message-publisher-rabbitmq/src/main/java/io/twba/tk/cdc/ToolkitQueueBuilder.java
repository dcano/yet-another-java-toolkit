package io.twba.tk.cdc;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;

/**
 * Builds the {@link Declarables} a consuming service needs to declare a durable main queue
 * backed by a dead-letter exchange (DLX) and dead-letter queue (DLQ), in one step.
 *
 * <p>This is a plain utility — no Spring annotations and no auto-configuration. A consuming
 * service exposes the result as a {@code @Bean Declarables} so that {@code RabbitAdmin}
 * declares the topology on connection:
 *
 * <pre>{@code
 * @Bean
 * Declarables courseQueueTopology() {
 *     return ToolkitQueueBuilder.forQueue("course-projector.course-events").build();
 * }
 * }</pre>
 *
 * <p>Naming defaults for a main queue {@code q}:
 * <ul>
 *   <li>DLX: {@code "dlx." + q}</li>
 *   <li>DLQ: {@code "dlq." + q}</li>
 *   <li>DLQ binding routing key: {@code q}</li>
 * </ul>
 */
public final class ToolkitQueueBuilder {

    private static final String DLX_PREFIX = "dlx.";
    private static final String DLQ_PREFIX = "dlq.";

    private final String queueName;
    private String deadLetterExchange;

    private ToolkitQueueBuilder(String queueName) {
        if (queueName == null || queueName.isBlank()) {
            throw new IllegalArgumentException("queueName must not be null or blank");
        }
        this.queueName = queueName;
        this.deadLetterExchange = DLX_PREFIX + queueName;
    }

    public static ToolkitQueueBuilder forQueue(String queueName) {
        return new ToolkitQueueBuilder(queueName);
    }

    /**
     * Overrides the dead-letter exchange name. Defaults to {@code "dlx." + queueName}.
     */
    public ToolkitQueueBuilder withDeadLetterExchange(String dlxName) {
        if (dlxName == null || dlxName.isBlank()) {
            throw new IllegalArgumentException("dlxName must not be null or blank");
        }
        this.deadLetterExchange = dlxName;
        return this;
    }

    public Declarables build() {
        String dlqName = DLQ_PREFIX + queueName;

        DirectExchange dlx = new DirectExchange(deadLetterExchange, true, false);

        Queue deadLetterQueue = QueueBuilder.durable(dlqName).build();

        Binding dlqBinding = BindingBuilder.bind(deadLetterQueue).to(dlx).with(queueName);

        Queue mainQueue = QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", deadLetterExchange)
                .withArgument("x-dead-letter-routing-key", queueName)
                .build();

        return new Declarables(dlx, deadLetterQueue, dlqBinding, mainQueue);
    }
}
