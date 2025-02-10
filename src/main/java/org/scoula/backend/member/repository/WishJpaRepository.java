package org.scoula.backend.member.repository;

import org.scoula.backend.member.domain.Wish;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishJpaRepository extends JpaRepository<Wish, Long> {

}
