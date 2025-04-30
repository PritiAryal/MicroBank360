package com.priti.accountService.controller;

import com.priti.accountService.model.Account;
import com.priti.accountService.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/account")
public class AccountController {
    @Autowired
    private AccountService accountService;

    @GetMapping
    public List<Account> getAccount() {
        return this.accountService.getAllAccounts();
    }

    @GetMapping("/{id}")
    public Account getAccountById(@PathVariable Long id) {
        return this.accountService.getAccountById(id);
    }

    @PostMapping
    public Account createAccount(@RequestBody Account account) {
        return this.accountService.createAccount(account);
    }

    @PutMapping("/{id}/{balance}")
    public Account updateAccountBalance(@PathVariable Long id, @PathVariable BigDecimal balance) {
        return this.accountService.updateAccountBalance(id, balance);
    }

    @DeleteMapping("/{id}")
    public void deleteAccount(@PathVariable Long id) {
        this.accountService.deleteAccount(id);
    }

}
