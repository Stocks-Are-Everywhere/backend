package org.scoula.soon_two_people.member.repository;

import org.scoula.soon_two_people.member.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountJpaRepository extends JpaRepository<Account, Long> {

}
