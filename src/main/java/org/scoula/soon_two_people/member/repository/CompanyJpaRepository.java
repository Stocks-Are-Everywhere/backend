package org.scoula.soon_two_people.member.repository;

import org.scoula.soon_two_people.member.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyJpaRepository extends JpaRepository<Company, Long> {
	
}
