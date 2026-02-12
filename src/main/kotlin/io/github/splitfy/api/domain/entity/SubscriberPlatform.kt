package io.github.splitfy.api.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "subscribers_platforms")
data class SubscriberPlatform(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(optional = false)
    @JoinColumn(name = "subscriber_id")
    val subscriber: Subscriber,

    @ManyToOne(optional = false)
    @JoinColumn(name = "platform_id")
    val platform: Platform,

    @Column(name = "subscribed_at", nullable = false)
    val subscribedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "unsubscribed_at")
    val unsubscribedAt: LocalDateTime? = null,

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    val updatedAt: LocalDateTime? = null,

    @Column(name = "deleted_at")
    val deletedAt: LocalDateTime? = null
)
