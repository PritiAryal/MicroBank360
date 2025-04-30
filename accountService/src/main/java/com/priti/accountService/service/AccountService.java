package com.priti.accountService.service;
import com.priti.accountService.model.Account;

import java.math.BigDecimal;
import java.util.List;

public interface AccountService {
    Account createAccount(Account account);
    Account getAccountById(Long id);
    List<Account> getAllAccounts();
    List<Account> getAccountsOfCustomer(Long customerId);
    Account updateAccountBalance(Long id, BigDecimal balance);
    void deleteAccount(Long id);
}
