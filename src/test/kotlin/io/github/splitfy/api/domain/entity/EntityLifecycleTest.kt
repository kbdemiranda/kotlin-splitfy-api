package io.github.splitfy.api.domain.entity

import io.github.splitfy.api.domain.enums.ProfileName
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.LocalTime

class EntityLifecycleTest {

    @Test
    fun `profile lifecycle callbacks set timestamps`() {
        val profile = Profile(name = ProfileName.ADMIN)

        profile.prePersist()
        val createdAt = profile.createdAt
        profile.preUpdate()

        assertNotNull(createdAt)
        assertNotNull(profile.updatedAt)
    }

    @Test
    fun `user lifecycle callbacks set timestamps`() {
        val user = User(
            name = "User",
            email = "user@example.com",
            password = "secret",
        )

        user.prePersist()
        val createdAt = user.createdAt
        user.preUpdate()

        assertNotNull(createdAt)
        assertNotNull(user.updatedAt)
    }

    @Test
    fun `password reset token lifecycle callbacks set timestamps`() {
        val token = PasswordResetToken(
            user = User(name = "User", email = "user@example.com", password = "secret"),
            tokenHash = "hash",
            expiresAt = LocalDateTime.now().plusMinutes(15),
        )

        token.prePersist()
        val createdAt = token.createdAt
        token.preUpdate()

        assertNotNull(createdAt)
        assertNotNull(token.updatedAt)
    }

    @Test
    fun `email schedule setting lifecycle callbacks set timestamps`() {
        val setting = EmailScheduleSetting(scheduleKey = "KPI_SUMMARY_EMAIL")

        setting.prePersist()
        val createdAt = setting.createdAt
        setting.preUpdate()

        assertNotNull(createdAt)
        assertNotNull(setting.updatedAt)
    }

    @Test
    fun `email schedule occurrence lifecycle callbacks set timestamps`() {
        val setting = EmailScheduleSetting(scheduleKey = "KPI_SUMMARY_EMAIL")
        val occurrence = EmailScheduleOccurrence(
            emailScheduleSetting = setting,
            dayOfWeek = 1,
            executionTime = LocalTime.of(9, 30),
        )

        occurrence.prePersist()
        val createdAt = occurrence.createdAt
        occurrence.preUpdate()

        assertNotNull(createdAt)
        assertNotNull(occurrence.updatedAt)
    }
}
