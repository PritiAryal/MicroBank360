package com.priti.dataseederservice.client;

import com.priti.dataseederservice.dto.AccountRequest;
import com.priti.dataseederservice.dto.AccountResponse;
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
public class AccountServiceClient {
    private final WebClient.Builder webClientBuilder;
    private final ReactiveCircuitBreaker accountServiceCircuitBreaker;

    @Value("${services.account-service.name:account-service}")
    private String serviceName;

    @Value("${services.account-service.fallback-url:http://localhost:8081}")
    private String fallbackUrl;

    public Mono<AccountResponse> createAccount(AccountRequest accountRequest) {
        WebClient webClient = webClientBuilder.build();

        return webClient.post()
                .uri("http://" + serviceName + "/account")
                .bodyValue(accountRequest)
                .retrieve()
                .bodyToMono(AccountResponse.class)
                .transform(accountServiceCircuitBreaker::run)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .doOnSuccess(account -> log.debug("Successfully created account: {}", account.getId()))
                .doOnError(error -> log.error("Error creating account: {}", error.getMessage()));
    }

    public Flux<AccountResponse> createAccountsBatch(List<AccountRequest> accounts) {
        return Flux.fromIterable(accounts)
                .flatMap(this::createAccount, 15) // Process 15 at a time
                .doOnNext(account -> log.debug("Created account batch item: {}", account.getId()));
    }

    public Flux<AccountResponse> getAllAccounts() {
        WebClient webClient = webClientBuilder.build();

        return webClient.get()
                .uri("http://" + serviceName + "/account")
                .retrieve()
                .bodyToFlux(AccountResponse.class)
                .transform(accountServiceCircuitBreaker::run)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .doOnComplete(() -> log.debug("Successfully retrieved all accounts"));
    }

    public Flux<AccountResponse> getAccountsByCustomerId(Long customerId) {
        WebClient webClient = webClientBuilder.build();

        return webClient.get()
                .uri("http://" + serviceName + "/account/customer/{customerId}", customerId)
                .retrieve()
                .bodyToFlux(AccountResponse.class)
                .transform(accountServiceCircuitBreaker::run)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .doOnComplete(() -> log.debug("Retrieved accounts for customer: {}", customerId));
    }

    public Mono<Long> getAccountCount() {
        return getAllAccounts()
                .count()
                .doOnSuccess(count -> log.debug("Account count: {}", count));
    }

    public Mono<Void> deleteAllAccounts() {
        return getAllAccounts()
                .flatMap(account -> deleteAccount(account.getId()))
                .then()
                .doOnSuccess(v -> log.info("All accounts deleted"));
    }

    private Mono<Void> deleteAccount(Long accountId) {
        WebClient webClient = webClientBuilder.build();

        return webClient.delete()
                .uri("http://" + serviceName + "/account/{id}", accountId)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.debug("Deleted account: {}", accountId));
    }
}
