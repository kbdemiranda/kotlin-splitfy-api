package io.github.splitfy.api.domain.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "email_schedule_settings",
    uniqueConstraints = [UniqueConstraint(columnNames = ["schedule_key"])]
)
data class EmailScheduleSetting(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "schedule_key", nullable = false, unique = true, length = 100)
    var scheduleKey: String,

    @Column(name = "description")
    var description: String? = null,

    @Column(name = "is_enabled", nullable = false)
    var isEnabled: Boolean = true,

    @Column(name = "timezone", nullable = false, length = 64)
    var timezone: String = "America/Sao_Paulo",

    @OneToMany(mappedBy = "emailScheduleSetting", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var occurrences: MutableList<EmailScheduleOccurrence> = mutableListOf(),

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null,

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null,
) {

    @PrePersist
    fun prePersist() {
        val now = LocalDateTime.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun preUpdate() {
        updatedAt = LocalDateTime.now()
    }
}
