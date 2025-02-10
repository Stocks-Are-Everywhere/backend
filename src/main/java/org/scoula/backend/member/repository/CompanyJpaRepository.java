package org.scoula.backend.member.repository;

import org.scoula.backend.member.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyJpaRepository extends JpaRepository<Company, Long> {

}
