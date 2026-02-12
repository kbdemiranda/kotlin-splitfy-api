package io.github.splitfy.api.security

import io.github.splitfy.api.repository.SubscriberRepository
import org.springframework.stereotype.Component

@Component("subscriberSecurity")
class SubscriberSecurity(
    private val subscriberRepository: SubscriberRepository,
) {

    fun isOwner(subscriberId: Long, userEmail: String): Boolean {
        val subscriber = subscriberRepository.findById(subscriberId).orElse(null) ?: return false
        if (subscriber.deletedAt != null) {
            return false
        }

        return subscriber.email.equals(userEmail, ignoreCase = true)
    }
}
