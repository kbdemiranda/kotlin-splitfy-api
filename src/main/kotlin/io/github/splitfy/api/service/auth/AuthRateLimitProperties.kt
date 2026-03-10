package io.github.splitfy.api.service.auth

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "splitfy.security.rate-limit")
class AuthRateLimitProperties {
    var loginByIpPerMinute: Int = 30
    var loginByEmailPerMinute: Int = 10
    var forgotByIpPerHour: Int = 20
    var forgotByEmailPerHour: Int = 5
    var resetByIpPerHour: Int = 20
    var resetByTokenPerHour: Int = 10
}
