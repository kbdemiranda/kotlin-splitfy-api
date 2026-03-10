package io.github.splitfy.api.service.email

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@ConfigurationProperties(prefix = "splitfy.email.schedule.cache")
class EmailScheduleCacheProperties {
    var maxAge: Duration = Duration.ofHours(24)
}
