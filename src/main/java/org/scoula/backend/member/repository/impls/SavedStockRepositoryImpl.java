package org.scoula.backend.member.repository.impls;

import org.scoula.backend.member.domain.Company;
import org.scoula.backend.member.domain.Member;
import org.scoula.backend.member.domain.SavedStock;
import org.scoula.backend.member.repository.SavedStockJpaRepository;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SavedStockRepositoryImpl {

	private final SavedStockJpaRepository savedStockJpaRepository;

	public Optional<List<SavedStock>> findAllByMember(Member member) {
		return savedStockJpaRepository.findAllByMember(member);
	}

	public SavedStock save(SavedStock savedStock){
		return savedStockJpaRepository.save(savedStock);
	}

	public Optional<SavedStock> findById(Long givenId) {
		return savedStockJpaRepository.findById(givenId);
	}

}
