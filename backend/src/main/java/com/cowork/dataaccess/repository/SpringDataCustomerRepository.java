package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataCustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByContactPhone(String phone);
}
