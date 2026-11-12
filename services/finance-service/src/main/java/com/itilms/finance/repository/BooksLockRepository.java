package com.itilms.finance.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.itilms.finance.entity.BooksLock;

public interface BooksLockRepository extends JpaRepository<BooksLock, Short> {
}
