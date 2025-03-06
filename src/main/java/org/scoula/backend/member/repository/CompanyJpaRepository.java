package org.scoula.backend.member.repository;

import java.util.List;
import java.util.Optional;

import org.scoula.backend.member.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyJpaRepository extends JpaRepository<Company, Long> {

	List<Company> findByIsuNmContainingOrIsuAbbrvContaining(String isuNm, String isuAbbrv);

	Optional<Company> findByIsuSrtCd(String isuSrtCd);

}
