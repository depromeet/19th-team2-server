package com.depromeet.team2.architecture

import com.depromeet.team2.global.exception.ErrorCode
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ErrorCodeTest {
    private val errorCodes: List<ErrorCode> =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("com.depromeet.team2")
            .filter { it.isEnum && it.isAssignableTo(ErrorCode::class.java) }
            .flatMap { it.reflect().enumConstants.map { constant -> constant as ErrorCode } }

    @Test
    fun `에러 코드는 도메인_사유 형식이다`() {
        val invalid = errorCodes.map { it.code }.filterNot { Regex("^[A-Z]+(_[A-Z0-9]+)+$").matches(it) }
        assertTrue(invalid.isEmpty(), "형식이 맞지 않는 에러 코드 (예: MEMBER_NOT_FOUND): $invalid")
    }

    @Test
    fun `에러 코드는 서로 겹치지 않는다`() {
        val duplicated = errorCodes.groupBy { it.code }.filterValues { it.size > 1 }.keys
        assertTrue(duplicated.isEmpty(), "여러 enum 에 같은 에러 코드가 있습니다: $duplicated")
    }
}
