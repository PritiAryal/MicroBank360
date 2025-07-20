package com.priti.dataseederservice.service;

import com.priti.dataseederservice.dto.AccountResponse;
import com.priti.dataseederservice.dto.CustomerResponse;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ApiDataGenerationService {
    Mono<List<CustomerResponse>> generateCustomers(int count);
    Mono<List<AccountResponse>> generateAccountsForCustomers(
            List<Long> customerIds, int minAccountsPerCustomer, int maxAccountsPerCustomer);
    Mono<Map<String, Object>> generateAccountsForExistingCustomers(
            int minAccountsPerCustomer, int maxAccountsPerCustomer);
    Mono<Map<String, Object>> generateFullDataset(
            int customerCount, int minAccountsPerCustomer, int maxAccountsPerCustomer);
    Mono<Map<String, Object>> getStatistics();
    Mono<Void> clearAllData();
    Mono<String> exportJMeterData();
    Mono<String> exportCustomersCSV();
    Mono<String> exportAccountsCSV();
}
