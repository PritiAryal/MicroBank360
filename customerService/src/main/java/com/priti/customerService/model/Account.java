package com.priti.customerService.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Account {
    private Long id;
    private String accountNumber;
    private String accountType; // e.g., SAVINGS, CURRENT
    private BigDecimal balance;

    private Long customerId; // Foreign key to Customer

    private LocalDateTime createdAt;
}
