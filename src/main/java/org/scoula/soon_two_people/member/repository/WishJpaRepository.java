package org.scoula.soon_two_people.member.repository;

import org.scoula.soon_two_people.member.domain.Wish;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishJpaRepository extends JpaRepository<Wish, Long> {

}
