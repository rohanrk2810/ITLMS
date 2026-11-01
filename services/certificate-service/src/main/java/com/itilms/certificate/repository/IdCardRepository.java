package com.itilms.certificate.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.itilms.certificate.entity.IdCard;
import com.itilms.certificate.entity.IdCardPersonType;
import com.itilms.certificate.entity.IdCardStatus;

@Repository
public interface IdCardRepository extends JpaRepository<IdCard, Long>, JpaSpecificationExecutor<IdCard> {

    Optional<IdCard> findByCardNo(String cardNo);

    List<IdCard> findByUserIdOrderByIssuedOnDescIdDesc(Long userId);

    /** Cards still in force for this person, so a new card can replace them. */
    List<IdCard> findByPersonTypeAndPersonIdAndStatus(IdCardPersonType type, Long personId, IdCardStatus status);

    List<IdCard> findByPersonTypeAndFullNameIgnoreCaseAndStatusAndPersonIdIsNull(
            IdCardPersonType type, String fullName, IdCardStatus status);

    /** From a database sequence, so two cards issued at once never share a number. */
    @Query(value = "SELECT nextval('id_card_seq')", nativeQuery = true)
    long nextSequence();
}
