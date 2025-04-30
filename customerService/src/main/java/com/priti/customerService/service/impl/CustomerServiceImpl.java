package com.priti.customerService.service.impl;

import com.priti.customerService.model.Customer;
import com.priti.customerService.repository.CustomerRepository;
import com.priti.customerService.service.AccountClient;
import com.priti.customerService.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CustomerServiceImpl implements CustomerService {
    @Autowired
    private AccountClient accountClient;

    @Autowired
    CustomerRepository customerRepository;

    @Override
    public Customer createCustomer(Customer customer) {
        return this.customerRepository.save(customer);
    }
    @Override
    public Customer getCustomerById(Long id) {
        //return this.customerRepository.findById(id).orElseThrow(() -> new RuntimeException("Customer not found"));
        Customer customer = this.customerRepository.findById(id).orElseThrow(() -> new RuntimeException("Customer not found"));
        customer.setAccounts(this.accountClient.getAccountsOfCustomer(customer.getId()));
        return customer;
    }
    @Override
    public List<Customer> getAllCustomers() {
        //return this.customerRepository.findAll();
        List<Customer> customers = this.customerRepository.findAll();
        List<Customer> newCustomers = customers.stream().map(customer -> {
            customer.setAccounts(this.accountClient.getAccountsOfCustomer(customer.getId()));
            return customer;
        }).collect(Collectors.toList());
        return newCustomers;
    }
    @Override
    public Customer updateCustomer(Long id, Customer customer) {
        Customer existingCustomer = this.customerRepository.findById(id).orElse(null);
        if(existingCustomer != null) {
            existingCustomer.setName(customer.getName());
            existingCustomer.setEmail(customer.getEmail());
            return this.customerRepository.save(existingCustomer);
        }
        else{
            return null;
        }
    }
    @Override
    public void deleteCustomer(Long id) {
        this.customerRepository.deleteById(id);
    }
}
