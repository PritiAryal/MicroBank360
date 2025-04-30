package com.priti.customerService.service;

import com.priti.customerService.model.Account;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(url = "http://localhost:8081", value = "Account-Client")
public interface AccountClient {
    @GetMapping("/account/customer/{customerId}")
    List<Account> getAccountsOfCustomer(@PathVariable("customerId") Long id);
}
