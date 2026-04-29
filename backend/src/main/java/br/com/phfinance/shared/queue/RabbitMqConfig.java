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

    @Value("${app.queue.consumer.concurrency:1}")
    private int concurrency;

    @Value("${app.queue.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${app.queue.retry.initial-interval:5000}")
    private long initialInterval;

    @Value("${app.queue.retry.multiplier:5.0}")
    private double multiplier;

    @Bean DirectExchange mainExchange() {
        return new DirectExchange(MAIN_EXCHANGE, true, false);
    }

    @Bean DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean Queue financeUploadQueue() {
        return QueueBuilder.durable(FINANCE_UPLOAD_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", FINANCE_UPLOAD_DLQ)
                .build();
    }

    @Bean Queue inflationUploadQueue() {
        return QueueBuilder.durable(INFLATION_UPLOAD_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", INFLATION_UPLOAD_DLQ)
                .build();
    }

    @Bean Queue financeReprocessQueue() {
        return QueueBuilder.durable(FINANCE_REPROCESS_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", FINANCE_REPROCESS_DLQ)
                .build();
    }

    @Bean Queue financeUploadDlq()    { return QueueBuilder.durable(FINANCE_UPLOAD_DLQ).build(); }
    @Bean Queue inflationUploadDlq()  { return QueueBuilder.durable(INFLATION_UPLOAD_DLQ).build(); }
    @Bean Queue financeReprocessDlq() { return QueueBuilder.durable(FINANCE_REPROCESS_DLQ).build(); }

    @Bean Binding financeUploadBinding() {
        return BindingBuilder.bind(financeUploadQueue()).to(mainExchange()).with(FINANCE_UPLOAD_QUEUE);
    }
    @Bean Binding inflationUploadBinding() {
        return BindingBuilder.bind(inflationUploadQueue()).to(mainExchange()).with(INFLATION_UPLOAD_QUEUE);
    }
    @Bean Binding financeReprocessBinding() {
        return BindingBuilder.bind(financeReprocessQueue()).to(mainExchange()).with(FINANCE_REPROCESS_QUEUE);
    }
    @Bean Binding financeUploadDlqBinding() {
        return BindingBuilder.bind(financeUploadDlq()).to(deadLetterExchange()).with(FINANCE_UPLOAD_DLQ);
    }
    @Bean Binding inflationUploadDlqBinding() {
        return BindingBuilder.bind(inflationUploadDlq()).to(deadLetterExchange()).with(INFLATION_UPLOAD_DLQ);
    }
    @Bean Binding financeReprocessDlqBinding() {
        return BindingBuilder.bind(financeReprocessDlq()).to(deadLetterExchange()).with(FINANCE_REPROCESS_DLQ);
    }

    @Bean Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter) {

        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(initialInterval);
        backOff.setMultiplier(multiplier);
        backOff.setMaxInterval(initialInterval * (long) Math.pow(multiplier, maxAttempts));

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(maxAttempts));
        retryTemplate.setBackOffPolicy(backOff);

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrentConsumers(concurrency);
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
