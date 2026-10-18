package fr.maisonos.core

/** Explicit local events; no external sensor is pretended connected. */
class RuleEngine {
    private val last = mutableMapOf<String, Long>()
    @Synchronized fun match(rules: List<LocalRule>, trigger: String, nowMillis: Long): List<LocalRule> = rules.filter { rule ->
        val previous = last[rule.id]
        val accepted = rule.enabled && rule.trigger == trigger && (previous == null || nowMillis - previous >= rule.cooldownSeconds * 1000L)
        if (accepted) last[rule.id] = nowMillis
        accepted
    }
}
