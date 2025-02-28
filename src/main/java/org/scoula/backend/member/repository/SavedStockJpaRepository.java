package org.scoula.backend.member.repository;

import org.scoula.backend.member.domain.Member;
import org.scoula.backend.member.domain.SavedStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

public interface SavedStockJpaRepository extends JpaRepository<SavedStock, Long> {
    Optional<List<SavedStock>> findAllByMember(Member member);
}
