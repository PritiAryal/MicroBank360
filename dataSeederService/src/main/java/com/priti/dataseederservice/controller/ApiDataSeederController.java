package com.priti.dataseederservice.controller;

import com.priti.dataseederservice.service.ApiDataGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;


@RestController
@RequestMapping("/seed")
@RequiredArgsConstructor
@Slf4j
public class ApiDataSeederController {
    private final ApiDataGenerationService dataGenerationService;

    @PostMapping("/customers/{count}")
    public Mono<ResponseEntity<Map<String, Object>>> generateCustomers(@PathVariable int count) {
        if (count <= 0 || count > 50000) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Count must be between 1 and 50,000")));
        }

        long startTime = System.currentTimeMillis();

        return dataGenerationService.generateCustomers(count)
                .map(customers -> {
                    long endTime = System.currentTimeMillis();
                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "Successfully generated " + customers.size() + " customers via API");
                    response.put("count", customers.size());
                    response.put("executionTimeMs", endTime - startTime);
                    response.put("customersPerSecond", customers.size() / ((endTime - startTime) / 1000.0));
                    return ResponseEntity.ok(response);
                })
                .onErrorReturn(ResponseEntity.status(500)
                        .body(Map.of("error", "Failed to generate customers")));
    }

    @PostMapping("/accounts")
    public Mono<ResponseEntity<Map<String, Object>>> generateAccountsForExistingCustomers(
            @RequestParam(defaultValue = "1") int minAccountsPerCustomer,
            @RequestParam(defaultValue = "4") int maxAccountsPerCustomer) {

        if (minAccountsPerCustomer <= 0 || maxAccountsPerCustomer <= 0 ||
                minAccountsPerCustomer > maxAccountsPerCustomer || maxAccountsPerCustomer > 10) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid account range. Min and max must be between 1-10, min <= max")));
        }

        long startTime = System.currentTimeMillis();

        return dataGenerationService.generateAccountsForExistingCustomers(minAccountsPerCustomer, maxAccountsPerCustomer)
                .map(result -> {
                    long endTime = System.currentTimeMillis();
                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "Successfully generated accounts for existing customers");
                    response.put("customersProcessed", result.get("customersProcessed"));
                    response.put("accountsGenerated", result.get("accountsGenerated"));
                    response.put("executionTimeMs", endTime - startTime);
                    return ResponseEntity.ok(response);
                })
                .onErrorReturn(ResponseEntity.status(500)
                        .body(Map.of("error", "Failed to generate accounts for existing customers")));
    }

    @PostMapping("/full-dataset")
    public Mono<ResponseEntity<Map<String, Object>>> generateFullDataset(
            @RequestParam(defaultValue = "1000") int customerCount,
            @RequestParam(defaultValue = "1") int minAccountsPerCustomer,
            @RequestParam(defaultValue = "4") int maxAccountsPerCustomer) {

        return dataGenerationService.generateFullDataset(
                        customerCount, minAccountsPerCustomer, maxAccountsPerCustomer)
                .map(result -> ResponseEntity.ok(result))
                .onErrorReturn(ResponseEntity.status(500)
                        .body(Map.of("error", "Failed to generate full dataset")));
    }

    // NEW: Export JMeter test data CSV
    @GetMapping("/export/jmeter-data.csv")
    public Mono<ResponseEntity<String>> exportJMeterData() {
        return dataGenerationService.exportJMeterData()
                .map(csvContent -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"jmeter_testdata.csv\"")
                        .header(HttpHeaders.CONTENT_TYPE, "text/csv")
                        .body(csvContent))
                .onErrorReturn(ResponseEntity.status(500)
                        .body("Error generating JMeter data CSV"));
    }

    // NEW: Export customers CSV
    @GetMapping("/export/customers.csv")
    public Mono<ResponseEntity<String>> exportCustomers() {
        return dataGenerationService.exportCustomersCSV()
                .map(csvContent -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"customers.csv\"")
                        .header(HttpHeaders.CONTENT_TYPE, "text/csv")
                        .body(csvContent))
                .onErrorReturn(ResponseEntity.status(500)
                        .body("Error generating customers CSV"));
    }

    // NEW: Export accounts CSV
    @GetMapping("/export/accounts.csv")
    public Mono<ResponseEntity<String>> exportAccounts() {
        return dataGenerationService.exportAccountsCSV()
                .map(csvContent -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"accounts.csv\"")
                        .header(HttpHeaders.CONTENT_TYPE, "text/csv")
                        .body(csvContent))
                .onErrorReturn(ResponseEntity.status(500)
                        .body("Error generating accounts CSV"));
    }


    @GetMapping("/stats")
    public Mono<ResponseEntity<Map<String, Object>>> getStatistics() {
        return dataGenerationService.getStatistics()
                .map(stats -> ResponseEntity.ok(stats))
                .onErrorReturn(ResponseEntity.status(500)
                        .body(Map.of("error", "Failed to get statistics")));
    }

    @DeleteMapping("/cleanup")
    public Mono<ResponseEntity<Map<String, String>>> cleanupAllData() {
        long startTime = System.currentTimeMillis();

        return dataGenerationService.clearAllData()
                .map(v -> {
                    long endTime = System.currentTimeMillis();
                    Map<String, String> response = new HashMap<>();
                    response.put("message", "All test data cleared successfully via APIs");
                    response.put("executionTimeMs", String.valueOf(endTime - startTime));
                    return ResponseEntity.ok(response);
                })
                .onErrorReturn(ResponseEntity.status(500)
                        .body(Map.of("error", "Failed to cleanup data")));
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, String>>> healthCheck() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "api-first-data-seeder-service");
        health.put("approach", "Microservice API-First");
        health.put("timestamp", java.time.LocalDateTime.now().toString());
        return Mono.just(ResponseEntity.ok(health));
    }
}
