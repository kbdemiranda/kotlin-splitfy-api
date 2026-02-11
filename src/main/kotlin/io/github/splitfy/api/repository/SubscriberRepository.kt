package io.github.splitfy.api.repository

import io.github.splitfy.api.domain.entity.Subscriber
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SubscriberRepository : JpaRepository<Subscriber, Long>
