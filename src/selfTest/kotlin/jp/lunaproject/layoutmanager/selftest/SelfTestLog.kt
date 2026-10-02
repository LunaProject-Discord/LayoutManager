package jp.lunaproject.layoutmanager.selftest

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE

/**
 * Result log read by the `selfTest` Gradle task. Each line is written immediately so a crash still
 * leaves the results so far. Lines starting with `FAIL` fail the task; `DONE` marks a finished phase.
 */
internal class SelfTestLog(private val file: Path) {
    private var passed = 0
    private var expected = 0
    private var failed = 0

    init {
        Files.createDirectories(file.parent)
        Files.deleteIfExists(file)
    }

    fun info(message: String) = write(message)

    fun check(name: String, ok: Boolean) {
        if (ok) passed++ else failed++
        write((if (ok) "PASS " else "FAIL ") + name)
    }

    /** A difference that is a documented limitation, e.g. floating bounds with the public API engine. */
    fun expected(name: String) {
        expected++
        write("EXPECTED $name")
    }

    fun fail(name: String, details: String) {
        failed++
        write("FAIL $name\n$details")
    }

    fun done() = write("DONE pass=$passed expected=$expected fail=$failed")

    @Synchronized
    private fun write(line: String) {
        Files.writeString(file, line + "\n", CREATE, APPEND)
    }
}
