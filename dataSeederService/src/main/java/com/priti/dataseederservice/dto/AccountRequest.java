package com.priti.dataseederservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountRequest {
    private String accountNumber;
    private String accountType;
    private BigDecimal balance;
    private Long customerId;
}
