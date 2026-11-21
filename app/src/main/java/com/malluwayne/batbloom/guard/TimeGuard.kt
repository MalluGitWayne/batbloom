package com.malluwayne.batbloom.guard

/**
 * Time Guard: daily time limits for chosen apps. When an app passes its limit you get a reminder (and another at
 * one and a half times the limit). Pure part: the stored format and when to remind.
 */
object TimeGuard {
    /** Stored as "pkg=30;other.pkg=45". */
    fun parse(s: String): Map<String, Int> = s.split(';').mapNotNull { part ->
        val i = part.lastIndexOf('=')
        if (i <= 0) null else part.substring(0, i).trim().takeIf { it.isNotEmpty() }?.let { k ->
            part.substring(i + 1).trim().toIntOrNull()?.takeIf { it > 0 }?.let { k to it }
        }
    }.toMap()

    fun format(m: Map<String, Int>): String = m.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }

    /** 0 = under the limit, 1 = past the limit, 2 = past 1.5× the limit. */
    fun level(usedMinutes: Int, limit: Int): Int = when {
        limit <= 0 -> 0
        usedMinutes * 2 >= limit * 3 -> 2
        usedMinutes >= limit -> 1
        else -> 0
    }

    /** Remind only when the level goes up (each level once a day). */
    fun shouldRemind(level: Int, alreadyReminded: Int): Boolean = level > alreadyReminded
}
