package io.github.splitfy.api.domain.entity

import io.github.splitfy.api.domain.converter.MonthDayAttributeConverter
import io.github.splitfy.api.domain.enums.BillingCycle
import io.github.splitfy.api.domain.enums.ServiceType
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.MonthDay
import java.util.UUID

@Entity
@Table(name = "platforms")
data class Platform(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "platform_token")
    val platformToken: UUID,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "price", nullable = false)
    val price: BigDecimal,

    @Column(name = "url")
    val url: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false)
    val serviceType: ServiceType,

    @Column(name = "total_slots", nullable = false)
    val totalSlots: Int,

    @Column(name = "available_slots", nullable = false)
    val availableSlots: Int,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    val updatedAt: LocalDateTime? = null,

    @Column(name = "deleted_at")
    val deletedAt: LocalDateTime? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false)
    val billingCycle: BillingCycle = BillingCycle.MONTHLY,

    @Convert(converter = MonthDayAttributeConverter::class)
    @Column(name = "billing_date")
    val billingDate: MonthDay? = null
) {

    @PrePersist
    @PreUpdate
    fun validateBillingDate() {
        if (billingCycle == BillingCycle.ANNUAL && billingDate == null) {
            throw IllegalStateException("billingDate is required when billingCycle is ANNUAL")
        }
    }
}
