package org.scoula.backend.member.service.reposiotry;

import lombok.RequiredArgsConstructor;
import org.scoula.backend.member.domain.Member;
import org.scoula.backend.member.domain.SavedStock;
import org.scoula.backend.member.repository.SavedStockJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

public interface SavedStockRepository {

	public Optional<List<SavedStock>> findAllByMember(Member member);

	public SavedStock save(SavedStock savedStock);

	public Optional<SavedStock> findById(Long givenId);

}
