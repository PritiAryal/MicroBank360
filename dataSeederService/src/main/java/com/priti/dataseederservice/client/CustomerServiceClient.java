package com.priti.dataseederservice.client;

import com.priti.dataseederservice.dto.CustomerRequest;
import com.priti.dataseederservice.dto.CustomerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerServiceClient {
    private final WebClient.Builder webClientBuilder;
    private final ReactiveCircuitBreaker customerServiceCircuitBreaker;

    @Value("${services.customer-service.name:customer-service}")
    private String serviceName;

    @Value("${services.customer-service.fallback-url:http://localhost:8080}")
    private String fallbackUrl;

    public Mono<CustomerResponse> createCustomer(CustomerRequest customerRequest) {
        WebClient webClient = webClientBuilder.build();

        return webClient.post()
                .uri("http://" + serviceName + "/customer")
                .bodyValue(customerRequest)
                .retrieve()
                .bodyToMono(CustomerResponse.class)
                .transform(customerServiceCircuitBreaker::run)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .doOnSuccess(customer -> log.debug("Successfully created customer: {}", customer.getId()))
                .doOnError(error -> log.error("Error creating customer: {}", error.getMessage()));
    }

    public Flux<CustomerResponse> createCustomersBatch(List<CustomerRequest> customers) {
        return Flux.fromIterable(customers)
                .flatMap(this::createCustomer, 10) // Process 10 at a time
                .doOnNext(customer -> log.debug("Created customer batch item: {}", customer.getId()));
    }

    public Flux<CustomerResponse> getAllCustomers() {
        WebClient webClient = webClientBuilder.build();

        return webClient.get()
                .uri("http://" + serviceName + "/customer")
                .retrieve()
                .bodyToFlux(CustomerResponse.class)
                .transform(customerServiceCircuitBreaker::run)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .doOnComplete(() -> log.debug("Successfully retrieved all customers"));
    }

    public Flux<Long> getAllCustomerIds() {
        return getAllCustomers()
                .map(CustomerResponse::getId)
                .doOnComplete(() -> log.debug("Successfully retrieved all customer IDs"));
    }

    public Mono<Long> getCustomerCount() {
        return getAllCustomers()
                .count()
                .doOnSuccess(count -> log.debug("Customer count: {}", count));
    }

    public Mono<Void> deleteAllCustomers() {
        return getAllCustomers()
                .flatMap(customer -> deleteCustomer(customer.getId()))
                .then()
                .doOnSuccess(v -> log.info("All customers deleted"));
    }

    private Mono<Void> deleteCustomer(Long customerId) {
        WebClient webClient = webClientBuilder.build();

        return webClient.delete()
                .uri("http://" + serviceName + "/customer/{id}", customerId)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.debug("Deleted customer: {}", customerId));
    }
}
