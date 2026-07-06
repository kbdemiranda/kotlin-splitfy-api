package io.github.splitfy.api.repository

import io.github.splitfy.api.domain.entity.PaymentConfirmation
import io.github.splitfy.api.domain.enums.PaymentConfirmationStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.YearMonth

@Repository
interface PaymentConfirmationRepository : JpaRepository<PaymentConfirmation, Long> {
    fun findBySubscriberIdAndReferenceMonthAndDeletedAtIsNull(
        subscriberId: Long,
        referenceMonth: YearMonth
    ): List<PaymentConfirmation>

    fun findBySubscriberIdInAndReferenceMonthAndDeletedAtIsNull(
        subscriberIds: List<Long>,
        referenceMonth: YearMonth
    ): List<PaymentConfirmation>

    fun findBySubscriberIdAndPlatformIdAndReferenceMonthAndDeletedAtIsNull(
        subscriberId: Long,
        platformId: Long,
        referenceMonth: YearMonth
    ): PaymentConfirmation?

    fun findByIdAndDeletedAtIsNull(id: Long): PaymentConfirmation?

    fun findBySubscriberIdInAndPlatformIdAndStatusAndDeletedAtIsNull(
        subscriberIds: List<Long>,
        platformId: Long,
        status: PaymentConfirmationStatus
    ): List<PaymentConfirmation>

    @Query(
        """
        select pc
        from PaymentConfirmation pc
        where pc.referenceMonth = :referenceMonth
          and pc.deletedAt is null
        """
    )
    fun findByReferenceMonthAndDeletedAtIsNull(
        @Param("referenceMonth") referenceMonth: YearMonth
    ): List<PaymentConfirmation>

    @Query(
        """
        select pc
        from PaymentConfirmation pc
        join fetch pc.subscriber s
        join fetch pc.platform p
        where pc.status = :status
          and pc.deletedAt is null
          and s.deletedAt is null
          and p.deletedAt is null
        order by pc.requestedAt desc
        """
    )
    fun findAllPendingWithDetails(@Param("status") status: PaymentConfirmationStatus): List<PaymentConfirmation>

    @Query(
        """
        select pc
        from PaymentConfirmation pc
        join fetch pc.subscriber s
        join fetch pc.platform p
        where pc.status = :status
          and pc.referenceMonth = :referenceMonth
          and pc.deletedAt is null
          and s.deletedAt is null
          and p.deletedAt is null
        order by pc.requestedAt desc
        """
    )
    fun findAllPendingWithDetailsByReferenceMonth(
        @Param("status") status: PaymentConfirmationStatus,
        @Param("referenceMonth") referenceMonth: YearMonth
    ): List<PaymentConfirmation>
}
