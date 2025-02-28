package org.scoula.backend.member.repository;

import org.scoula.backend.member.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyJpaRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByIsuSrtCd(String isuSrtCd);
}
