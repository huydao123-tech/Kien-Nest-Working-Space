package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Query("select c from Customer c where c.contact.phone = :phone")
    Optional<Customer> findByPhone(@Param("phone") String phone);

    /** UC01: khách ĐÃ có tài khoản đăng nhập bằng email (không phân biệt hoa thường). */
    @Query("select c from Customer c where lower(c.contact.email) = lower(:email) and c.passwordHash is not null")
    Optional<Customer> findAccountByEmail(@Param("email") String email);

    List<Customer> findAllByOrderByIdAsc();
}
