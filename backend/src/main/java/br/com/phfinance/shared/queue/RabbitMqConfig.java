package br.com.phfinance.shared.queue;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class RabbitMqConfig {

    public static final String MAIN_EXCHANGE      = "finance.direct";
    public static final String DLX_EXCHANGE       = "finance.dlx";

    public static final String FINANCE_UPLOAD_QUEUE    = "finance.upload";
    public static final String INFLATION_UPLOAD_QUEUE  = "inflation.upload";
    public static final String FINANCE_REPROCESS_QUEUE = "finance.reprocess";
    public static final String FINANCE_UPLOAD_DLQ      = "finance.upload.dlq";
    public static final String INFLATION_UPLOAD_DLQ    = "inflation.upload.dlq";
    public static final String FINANCE_REPROCESS_DLQ   = "finance.reprocess.dlq";

    private static final String X_DEAD_LETTER_EXCHANGE    = "x-dead-letter-exchange";
    private static final String X_DEAD_LETTER_ROUTING_KEY = "x-dead-letter-routing-key";

    private final int concurrency;
    private final int maxAttempts;
    private final long initialInterval;
    private final double multiplier;

    public RabbitMqConfig(
            @Value("${app.queue.consumer.concurrency:1}") int concurrency,
            @Value("${app.queue.retry.max-attempts:3}") int maxAttempts,
            @Value("${app.queue.retry.initial-interval:5000}") long initialInterval,
            @Value("${app.queue.retry.multiplier:5.0}") double multiplier) {
        this.concurrency = concurrency;
        this.maxAttempts = maxAttempts;
        this.initialInterval = initialInterval;
        this.multiplier = multiplier;
    }

    @Bean public DirectExchange mainExchange() {
        return new DirectExchange(MAIN_EXCHANGE, true, false);
    }

    @Bean public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean public Queue financeUploadQueue() {
        return QueueBuilder.durable(FINANCE_UPLOAD_QUEUE)
                .withArgument(X_DEAD_LETTER_EXCHANGE, DLX_EXCHANGE)
                .withArgument(X_DEAD_LETTER_ROUTING_KEY, FINANCE_UPLOAD_DLQ)
                .build();
    }

    @Bean public Queue inflationUploadQueue() {
        return QueueBuilder.durable(INFLATION_UPLOAD_QUEUE)
                .withArgument(X_DEAD_LETTER_EXCHANGE, DLX_EXCHANGE)
                .withArgument(X_DEAD_LETTER_ROUTING_KEY, INFLATION_UPLOAD_DLQ)
                .build();
    }

    @Bean public Queue financeReprocessQueue() {
        return QueueBuilder.durable(FINANCE_REPROCESS_QUEUE)
                .withArgument(X_DEAD_LETTER_EXCHANGE, DLX_EXCHANGE)
                .withArgument(X_DEAD_LETTER_ROUTING_KEY, FINANCE_REPROCESS_DLQ)
                .build();
    }

    @Bean public Queue financeUploadDlq()    { return QueueBuilder.durable(FINANCE_UPLOAD_DLQ).build(); }
    @Bean public Queue inflationUploadDlq()  { return QueueBuilder.durable(INFLATION_UPLOAD_DLQ).build(); }
    @Bean public Queue financeReprocessDlq() { return QueueBuilder.durable(FINANCE_REPROCESS_DLQ).build(); }

    @Bean public Binding financeUploadBinding() {
        return BindingBuilder.bind(financeUploadQueue()).to(mainExchange()).with(FINANCE_UPLOAD_QUEUE);
    }
    @Bean public Binding inflationUploadBinding() {
        return BindingBuilder.bind(inflationUploadQueue()).to(mainExchange()).with(INFLATION_UPLOAD_QUEUE);
    }
    @Bean public Binding financeReprocessBinding() {
        return BindingBuilder.bind(financeReprocessQueue()).to(mainExchange()).with(FINANCE_REPROCESS_QUEUE);
    }
    @Bean public Binding financeUploadDlqBinding() {
        return BindingBuilder.bind(financeUploadDlq()).to(deadLetterExchange()).with(FINANCE_UPLOAD_DLQ);
    }
    @Bean public Binding inflationUploadDlqBinding() {
        return BindingBuilder.bind(inflationUploadDlq()).to(deadLetterExchange()).with(INFLATION_UPLOAD_DLQ);
    }
    @Bean public Binding financeReprocessDlqBinding() {
        return BindingBuilder.bind(financeReprocessDlq()).to(deadLetterExchange()).with(FINANCE_REPROCESS_DLQ);
    }

    @Bean public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter) {

        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(initialInterval);
        backOff.setMultiplier(multiplier);
        backOff.setMaxInterval(initialInterval * (long) Math.pow(multiplier, maxAttempts - 1));

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(maxAttempts));
        retryTemplate.setBackOffPolicy(backOff);

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrentConsumers(concurrency);
        factory.setPrefetchCount(1);
        factory.setMessageConverter(messageConverter);
        factory.setAdviceChain(
                RetryInterceptorBuilder.stateless()
                        .retryOperations(retryTemplate)
                        .recoverer(new RejectAndDontRequeueRecoverer())
                        .build()
        );
        return factory;
    }
}
