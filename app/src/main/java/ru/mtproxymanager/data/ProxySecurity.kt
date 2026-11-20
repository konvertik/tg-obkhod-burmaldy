package ru.mtproxymanager.data

enum class SecurityLevel(val label: String) {
    HIGH("высокая"),
    MEDIUM("средняя"),
    LOW("низкая"),
}

data class SecurityAssessment(val level: SecurityLevel, val reasons: List<String>)

/**
 * Оценка того, насколько трафик к прокси трудно распознать провайдеру.
 * Оценивается только тип секрета и формат адреса, без сетевых запросов.
 */
object ProxySecurity {

    fun assess(proxy: ProxyEntity): SecurityAssessment {
        val reasons = mutableListOf<String>()
        val level = when {
            proxy.secret.startsWith("ee") -> {
                reasons += "FakeTLS: трафик маскируется под TLS"
                SecurityLevel.HIGH
            }
            proxy.secret.startsWith("dd") -> {
                reasons += "Секрет с padding: трафик выглядит случайным"
                SecurityLevel.MEDIUM
            }
            else -> {
                reasons += "Обычный секрет: такой трафик легче распознать провайдеру"
                SecurityLevel.LOW
            }
        }
        if (proxy.server.all { it.isDigit() || it == '.' }) {
            reasons += "Сервер задан IP-адресом, без домена"
        }
        if (proxy.port !in STANDARD_PORTS) {
            reasons += "Нестандартный порт ${proxy.port}"
        }
        return SecurityAssessment(level, reasons)
    }

    private val STANDARD_PORTS = setOf(443, 8443)
}
