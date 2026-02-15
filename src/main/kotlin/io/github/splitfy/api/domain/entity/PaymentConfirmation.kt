package io.github.splitfy.api.domain.entity

import io.github.splitfy.api.domain.converter.YearMonthAttributeConverter
import io.github.splitfy.api.domain.enums.PaymentConfirmationStatus
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime
import java.time.YearMonth

@Entity
@Table(
    name = "payment_confirmations",
    uniqueConstraints = [
        UniqueConstraint(
            columnNames = ["subscriber_id", "platform_id", "reference_month"]
        )
    ]
)
data class PaymentConfirmation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(optional = false)
    @JoinColumn(name = "subscriber_id")
    val subscriber: Subscriber,

    @ManyToOne(optional = false)
    @JoinColumn(name = "platform_id")
    val platform: Platform,

    @Convert(converter = YearMonthAttributeConverter::class)
    @Column(name = "reference_month", nullable = false, length = 7)
    val referenceMonth: YearMonth,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    val status: PaymentConfirmationStatus,

    @Column(name = "requested_by_email", nullable = false)
    val requestedByEmail: String,

    @Column(name = "requested_at", nullable = false)
    val requestedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "validated_by_email")
    val validatedByEmail: String? = null,

    @Column(name = "validated_at")
    val validatedAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    val updatedAt: LocalDateTime? = null,

    @Column(name = "deleted_at")
    val deletedAt: LocalDateTime? = null
)
