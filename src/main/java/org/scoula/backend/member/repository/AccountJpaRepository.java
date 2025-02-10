package org.scoula.backend.member.repository;

import org.scoula.backend.member.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountJpaRepository extends JpaRepository<Account, Long> {

}
