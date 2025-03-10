package org.scoula.backend.member.repository.impls;

import org.scoula.backend.member.domain.Member;
import org.scoula.backend.member.domain.SavedStock;
import org.scoula.backend.member.repository.SavedStockJpaRepository;
import org.scoula.backend.member.service.reposiotry.SavedStockRepository;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SavedStockRepositoryImpl implements SavedStockRepository {

	private final SavedStockJpaRepository savedStockJpaRepository;

	@Override
	public Optional<List<SavedStock>> findAllByMember(Member member) {
		return savedStockJpaRepository.findAllByMember(member);
	}

	@Override
	public SavedStock save(SavedStock savedStock){
		return savedStockJpaRepository.save(savedStock);
	}

	@Override
	public Optional<SavedStock> findById(Long givenId) {
		return savedStockJpaRepository.findById(givenId);
	}

}
