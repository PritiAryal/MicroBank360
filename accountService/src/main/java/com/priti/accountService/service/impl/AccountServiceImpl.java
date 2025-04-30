package com.priti.accountService.service.impl;

import com.priti.accountService.model.Account;
import com.priti.accountService.repository.AccountRepository;
import com.priti.accountService.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountServiceImpl implements AccountService {
    @Autowired
    private AccountRepository accountRepository;

    @Override
    public Account createAccount(Account account) {
        return this.accountRepository.save(account);
    }

    @Override
    public Account getAccountById(Long id) {
        return this.accountRepository.findById(id).orElseThrow(() -> new RuntimeException("Account not found"));
    }

    @Override
    public List<Account> getAllAccounts() {
        return this.accountRepository.findAll();
    }

    @Override
    public Account updateAccountBalance(Long id, BigDecimal balance) {
        Account account = this.accountRepository.findById(id).orElse(null);
        if(account != null) {
            account.setBalance(balance);
            return this.accountRepository.save(account);
        }
        else{
            return null;
        }
    }

    @Override
    public void deleteAccount(Long id) {
        this.accountRepository.deleteById(id);
    }
}
