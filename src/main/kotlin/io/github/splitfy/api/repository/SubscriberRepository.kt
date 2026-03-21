package io.github.splitfy.api.repository

import io.github.splitfy.api.domain.entity.Subscriber
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SubscriberRepository : JpaRepository<Subscriber, Long> {

    fun findByDeletedAtIsNull(pageable: Pageable): Page<Subscriber>

    fun findByDeletedAtIsNullAndNameContainingIgnoreCase(name: String, pageable: Pageable): Page<Subscriber>

    fun findByFinancialResponsibleSubscriberIdAndDeletedAtIsNull(financialResponsibleSubscriberId: Long): List<Subscriber>
}
