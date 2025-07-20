package com.priti.dataseederservice.service.impl;

import com.priti.dataseederservice.client.AccountServiceClient;
import com.priti.dataseederservice.client.CustomerServiceClient;
import com.priti.dataseederservice.dto.AccountRequest;
import com.priti.dataseederservice.dto.AccountResponse;
import com.priti.dataseederservice.dto.CustomerRequest;
import com.priti.dataseederservice.dto.CustomerResponse;
import com.priti.dataseederservice.service.ApiDataGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiDataGenerationServiceImpl implements ApiDataGenerationService {
    private final CustomerServiceClient customerServiceClient;
    private final AccountServiceClient accountServiceClient;
    private final Faker faker = new Faker();

    @Value("${seeding.batch-size:100}")
    private int batchSize;

    @Value("${seeding.max-concurrent-requests:50}")
    private int maxConcurrentRequests;

    @Value("${seeding.request-delay-ms:10}")
    private int requestDelayMs;

    private final Set<String> usedEmails = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> usedAccountNumbers = Collections.synchronizedSet(new HashSet<>());

    private final List<String> accountTypes = Arrays.asList(
            "SAVINGS", "CURRENT", "FIXED_DEPOSIT", "SALARY", "BUSINESS"
    );

    @Override
    public Mono<List<CustomerResponse>> generateCustomers(int count) {
        log.info("Starting generation of {} customers via API", count);

        return Flux.range(0, count)
                .map(i -> generateCustomerRequest())
                .buffer(batchSize) // Process in batches
                .flatMap(batch ->
                        customerServiceClient.createCustomersBatch(batch)
                                .collectList()
                                .delayElement(Duration.ofMillis(requestDelayMs)) // Rate limiting
                )
                .flatMap(Flux::fromIterable)
                .collectList()
                .doOnSuccess(customers ->
                        log.info("Successfully generated {} customers via API", customers.size()))
                .doOnError(error ->
                        log.error("Error generating customers via API: {}", error.getMessage()));
    }

    @Override
    public Mono<List<AccountResponse>> generateAccountsForCustomers(
            List<Long> customerIds, int minAccountsPerCustomer, int maxAccountsPerCustomer) {

        log.info("Starting generation of accounts for {} customers via API", customerIds.size());

        return Flux.fromIterable(customerIds)
                .flatMap(customerId -> {
                    int accountCount = ThreadLocalRandom.current()
                            .nextInt(minAccountsPerCustomer, maxAccountsPerCustomer + 1);

                    return Flux.range(0, accountCount)
                            .map(i -> generateAccountRequest(customerId));
                })
                .buffer(batchSize) // Process in batches
                .flatMap(batch ->
                        accountServiceClient.createAccountsBatch(batch)
                                .collectList()
                                .delayElement(Duration.ofMillis(requestDelayMs)) // Rate limiting
                )
                .flatMap(Flux::fromIterable)
                .collectList()
                .doOnSuccess(accounts ->
                        log.info("Successfully generated {} accounts via API", accounts.size()))
                .doOnError(error ->
                        log.error("Error generating accounts via API: {}", error.getMessage()));
    }


    @Override
    public Mono<Map<String, Object>> generateAccountsForExistingCustomers(
            int minAccountsPerCustomer, int maxAccountsPerCustomer) {

        log.info("Starting generation of accounts for existing customers: {}-{} accounts per customer",
                minAccountsPerCustomer, maxAccountsPerCustomer);

        return customerServiceClient.getAllCustomerIds()
                .collectList()
                .flatMap(customerIds -> {
                    if (customerIds.isEmpty()) {
                        Map<String, Object> result = new HashMap<>();
                        result.put("customersProcessed", 0);
                        result.put("accountsGenerated", 0);
                        result.put("message", "No existing customers found");
                        return Mono.just(result);
                    }

                    return generateAccountsForCustomers(customerIds, minAccountsPerCustomer, maxAccountsPerCustomer)
                            .map(accounts -> {
                                Map<String, Object> result = new HashMap<>();
                                result.put("customersProcessed", customerIds.size());
                                result.put("accountsGenerated", accounts.size());
                                result.put("averageAccountsPerCustomer",
                                        accounts.size() / (double) customerIds.size());
                                return result;
                            });
                })
                .doOnSuccess(result ->
                        log.info("Successfully generated accounts for existing customers: {} customers, {} accounts",
                                result.get("customersProcessed"), result.get("accountsGenerated")))
                .doOnError(error ->
                        log.error("Error generating accounts for existing customers: {}", error.getMessage()));
    }

    @Override
    public Mono<Map<String, Object>> generateFullDataset(
            int customerCount, int minAccountsPerCustomer, int maxAccountsPerCustomer) {

        log.info("Starting full dataset generation: {} customers, {}-{} accounts per customer",
                customerCount, minAccountsPerCustomer, maxAccountsPerCustomer);

        long startTime = System.currentTimeMillis();

        return generateCustomers(customerCount)
                .flatMap(customers -> {
                    List<Long> customerIds = customers.stream()
                            .map(CustomerResponse::getId)
                            .toList();

                    return generateAccountsForCustomers(customerIds, minAccountsPerCustomer, maxAccountsPerCustomer)
                            .map(accounts -> {
                                long endTime = System.currentTimeMillis();
                                long executionTime = endTime - startTime;

                                Map<String, Object> result = new HashMap<>();
                                result.put("customersGenerated", customers.size());
                                result.put("accountsGenerated", accounts.size());
                                result.put("totalRecords", customers.size() + accounts.size());
                                result.put("executionTimeMs", executionTime);
                                result.put("executionTimeMinutes", executionTime / 60000.0);
                                result.put("recordsPerSecond", (customers.size() + accounts.size()) / (executionTime / 1000.0));
                                result.put("customers", customers);
                                result.put("accounts", accounts);

                                return result;
                            });
                })
                .doOnSuccess(result ->
                        log.info("Successfully generated full dataset: {} customers, {} accounts",
                                result.get("customersGenerated"), result.get("accountsGenerated")))
                .doOnError(error ->
                        log.error("Error generating full dataset: {}", error.getMessage()));
    }

    @Override
    public Mono<String> exportJMeterData() {
        log.info("Exporting JMeter test data CSV");

        return Mono.zip(
                        customerServiceClient.getAllCustomers().collectList(),
                        accountServiceClient.getAllAccounts().collectList()
                ).map(tuple -> {
                    List<CustomerResponse> customers = tuple.getT1();
                    List<AccountResponse> accounts = tuple.getT2();

                    StringBuilder csv = new StringBuilder();
                    csv.append("customerId,customerName,customerEmail,customerPhone,accountId,accountNumber,accountType,balance\n");

                    Map<Long, CustomerResponse> customerMap = customers.stream()
                            .collect(Collectors.toMap(CustomerResponse::getId, Function.identity()));

                    for (AccountResponse account : accounts) {
                        CustomerResponse customer = customerMap.get(account.getCustomerId());
                        if (customer != null) {
                            csv.append(String.format("%d,%s,%s,%s,%d,%s,%s,%.2f\n",
                                    customer.getId(),
                                    escapeCsvField(customer.getName()),
                                    escapeCsvField(customer.getEmail()),
                                    escapeCsvField(customer.getPhone()),
                                    account.getId(),
                                    escapeCsvField(account.getAccountNumber()),
                                    escapeCsvField(account.getAccountType()),
                                    account.getBalance()));
                        }
                    }

                    return csv.toString();
                })
                .doOnSuccess(csv -> log.info("Successfully exported JMeter test data CSV"))
                .doOnError(error -> log.error("Error exporting JMeter test data: {}", error.getMessage()));
    }

    @Override
    public Mono<String> exportCustomersCSV() {
        log.info("Exporting customers CSV");

        return customerServiceClient.getAllCustomers()
                .collectList()
                .map(customers -> {
                    StringBuilder csv = new StringBuilder();
                    csv.append("id,name,email,phone\n");

                    for (CustomerResponse customer : customers) {
                        csv.append(String.format("%d,%s,%s,%s\n",
                                customer.getId(),
                                escapeCsvField(customer.getName()),
                                escapeCsvField(customer.getEmail()),
                                escapeCsvField(customer.getPhone())));
                    }

                    return csv.toString();
                })
                .doOnSuccess(csv -> log.info("Successfully exported customers CSV"))
                .doOnError(error -> log.error("Error exporting customers CSV: {}", error.getMessage()));
    }


    @Override
    public Mono<String> exportAccountsCSV() {
        log.info("Exporting accounts CSV");

        return accountServiceClient.getAllAccounts()
                .collectList()
                .map(accounts -> {
                    StringBuilder csv = new StringBuilder();
                    csv.append("id,accountNumber,accountType,balance,customerId\n");

                    for (AccountResponse account : accounts) {
                        csv.append(String.format("%d,%s,%s,%.2f,%d\n",
                                account.getId(),
                                escapeCsvField(account.getAccountNumber()),
                                escapeCsvField(account.getAccountType()),
                                account.getBalance(),
                                account.getCustomerId()));
                    }

                    return csv.toString();
                })
                .doOnSuccess(csv -> log.info("Successfully exported accounts CSV"))
                .doOnError(error -> log.error("Error exporting accounts CSV: {}", error.getMessage()));
    }

    private String escapeCsvField(String field) {
        if (field == null) return "";
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }



    private CustomerRequest generateCustomerRequest() {
        // Generate unique email
        String email;
        int attempts = 0;
        do {
            email = faker.internet().emailAddress().toLowerCase();
            attempts++;
        } while (usedEmails.contains(email) && attempts < 10);

        if (attempts >= 10) {
            email = UUID.randomUUID().toString() + "@" + faker.internet().domainName();
        }
        usedEmails.add(email);

        return new CustomerRequest(
                faker.name().fullName(),
                email,
                generateRealisticPhoneNumber()
        );
    }

    private AccountRequest generateAccountRequest(Long customerId) {
        // Generate unique account number
        String accountNumber;
        int attempts = 0;
        do {
            accountNumber = "ACC" + faker.number().digits(10);
            attempts++;
        } while (usedAccountNumbers.contains(accountNumber) && attempts < 10);

        if (attempts >= 10) {
            accountNumber = "ACC" + System.currentTimeMillis() + faker.number().digits(3);
        }
        usedAccountNumbers.add(accountNumber);

        String accountType = accountTypes.get(
                ThreadLocalRandom.current().nextInt(accountTypes.size()));

        return new AccountRequest(
                accountNumber,
                accountType,
                generateRealisticBalance(accountType),
                customerId
        );
    }

    private String generateRealisticPhoneNumber() {
        String[] formats = {
                "+1-%d-%d-%d",
                "+91-%d-%d-%d",
                "+44-%d-%d-%d",
                "%d-%d-%d"
        };

        String format = formats[ThreadLocalRandom.current().nextInt(formats.length)];
        return String.format(format,
                faker.number().numberBetween(100, 999),
                faker.number().numberBetween(100, 999),
                faker.number().numberBetween(1000, 9999));
    }

    private BigDecimal generateRealisticBalance(String accountType) {
        double min, max;

        switch (accountType) {
            case "SAVINGS" -> {
                min = 100.0;
                max = 75000.0;
            }
            case "CURRENT" -> {
                min = 1000.0;
                max = 150000.0;
            }
            case "FIXED_DEPOSIT" -> {
                min = 10000.0;
                max = 1000000.0;
            }
            case "SALARY" -> {
                min = 500.0;
                max = 50000.0;
            }
            case "BUSINESS" -> {
                min = 5000.0;
                max = 500000.0;
            }
            default -> {
                min = 100.0;
                max = 10000.0;
            }
        }

        double balance = ThreadLocalRandom.current().nextDouble(min, max);
        return BigDecimal.valueOf(balance).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public Mono<Map<String, Object>> getStatistics() {
        return Mono.zip(
                customerServiceClient.getCustomerCount(),
                accountServiceClient.getAccountCount()
        ).map(tuple -> {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalCustomers", tuple.getT1());
            stats.put("totalAccounts", tuple.getT2());
            stats.put("uniqueEmails", usedEmails.size());
            stats.put("uniqueAccountNumbers", usedAccountNumbers.size());
            stats.put("lastGenerated", new Date());
            return stats;
        });
    }

    @Override
    public Mono<Void> clearAllData() {
        log.info("Clearing all test data via APIs");
        return accountServiceClient.deleteAllAccounts()
                .then(customerServiceClient.deleteAllCustomers())
                .doOnSuccess(v -> {
                    usedEmails.clear();
                    usedAccountNumbers.clear();
                    log.info("Successfully cleared all test data");
                });
    }
}
