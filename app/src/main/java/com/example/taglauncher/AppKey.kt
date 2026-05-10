package com.example.taglauncher

object AppKey {
    private const val SEP = '#'

    fun build(pkg: String, userSerial: Long): String =
        if (userSerial == 0L) pkg else "$pkg$SEP$userSerial"

    fun pkgOf(key: String): String = key.substringBefore(SEP)

    fun serialOf(key: String): Long {
        val idx = key.indexOf(SEP)
        if (idx < 0) return 0L
        return key.substring(idx + 1).toLongOrNull() ?: 0L
    }

    fun isWorkApp(key: String): Boolean = key.indexOf(SEP) >= 0
}
