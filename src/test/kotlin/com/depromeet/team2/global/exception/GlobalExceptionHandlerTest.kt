package com.depromeet.team2.global.exception

import com.depromeet.team2.TestcontainersConfiguration
import com.depromeet.team2.global.payload.ApiResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@SpringBootTest
@AutoConfigureMockMvc
@Import(GlobalExceptionHandlerTest.TestApi::class, TestcontainersConfiguration::class)
class GlobalExceptionHandlerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `성공 응답은 공통 형식으로 감싼다`() {
        mockMvc.get("/test/ok").andExpect {
            status { isOk() }
            jsonPath("$.isSuccess") { value(true) }
            jsonPath("$.code") { value("OK") }
            jsonPath("$.result.name") { value("abel") }
        }
    }

    @Test
    fun `싱글톤 비즈니스 예외는 에러 코드대로 응답한다`() {
        mockMvc.get("/test/business").andExpect {
            status { isNotFound() }
            jsonPath("$.isSuccess") { value(false) }
            jsonPath("$.code") { value("TEST_NOT_FOUND") }
            jsonPath("$.detail") { doesNotExist() }
        }
    }

    @Test
    fun `상세 예외는 4xx 일 때만 detail 을 싣는다`() {
        mockMvc.get("/test/detail-4xx").andExpect {
            status { isNotFound() }
            jsonPath("$.detail") { value("id=3") }
        }
        mockMvc.get("/test/detail-5xx").andExpect {
            status { isInternalServerError() }
            jsonPath("$.detail") { doesNotExist() }
        }
    }

    @Test
    fun `검증 실패는 필드별 에러를 싣는다`() {
        mockMvc
            .post("/test/valid") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name": ""}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("COMMON_INVALID_REQUEST") }
                jsonPath("$.fieldErrors[0].field") { value("name") }
            }
    }

    @Test
    fun `body 의 enum 값이 틀리면 필드와 허용값을 알려 준다`() {
        mockMvc
            .post("/test/valid") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name": "a", "type": "WRONG"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.fieldErrors[0].field") { value("type") }
                jsonPath("$.fieldErrors[0].reason") { value("허용되지 않는 값입니다. 허용값: A, B") }
            }
    }

    @Test
    fun `깨진 JSON 은 COMMON_INVALID_JSON`() {
        mockMvc
            .post("/test/valid") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name": """
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("COMMON_INVALID_JSON") }
            }
    }

    @Test
    fun `없는 경로와 잘못된 메서드`() {
        mockMvc.get("/test/nothing").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("COMMON_ENDPOINT_NOT_FOUND") }
        }
        mockMvc.get("/test/valid").andExpect {
            status { isMethodNotAllowed() }
            jsonPath("$.code") { value("COMMON_METHOD_NOT_ALLOWED") }
            header { string("Allow", "POST") }
        }
    }

    @Test
    fun `예상 못 한 예외는 내부 정보 없이 500`() {
        mockMvc.get("/test/unexpected").andExpect {
            status { isInternalServerError() }
            jsonPath("$.code") { value("COMMON_INTERNAL_ERROR") }
            jsonPath("$.message") { value("서버 내부 오류가 발생했습니다.") }
        }
    }

    enum class TestErrorCode(
        override val status: HttpStatus,
        override val message: String,
    ) : ErrorCode {
        TEST_NOT_FOUND(HttpStatus.NOT_FOUND, "없음"),
        TEST_EXTERNAL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "외부 실패"),
    }

    object TestNotFoundException : CodeException(TestErrorCode.TEST_NOT_FOUND)

    enum class Type { A, B }

    data class TestRequest(
        @field:NotBlank val name: String,
        val type: Type? = null,
    )

    data class TestResponse(
        val name: String,
    )

    @TestConfiguration
    @RestController
    class TestApi {
        @GetMapping("/test/ok")
        fun ok() = ApiResponse.ok(TestResponse("abel"))

        @GetMapping("/test/business")
        fun business(): Nothing = throw TestNotFoundException

        @GetMapping("/test/detail-4xx")
        fun detail4xx(): Nothing = throw DynamicException(TestErrorCode.TEST_NOT_FOUND, "id=3")

        @GetMapping("/test/detail-5xx")
        fun detail5xx(): Nothing =
            throw DynamicException(TestErrorCode.TEST_EXTERNAL_FAILED, "secret", RuntimeException())

        @PostMapping("/test/valid")
        fun valid(
            @Valid @RequestBody request: TestRequest,
        ) = ApiResponse.ok(TestResponse(request.name))

        @GetMapping("/test/unexpected")
        fun unexpected(): Nothing = error("db password leaked?")
    }
}
