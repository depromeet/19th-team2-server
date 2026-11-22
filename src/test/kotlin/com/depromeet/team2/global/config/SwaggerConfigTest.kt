package com.depromeet.team2.global.config

import com.depromeet.team2.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@Import(SwaggerConfigTest.TestApi::class, TestcontainersConfiguration::class)
class SwaggerConfigTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `API 문서 JSON 을 내려준다`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.info.title") { value("Team2 API") }
        }
    }

    @Test
    fun `API 문서 JSON 의 경로는 이름순이다`() {
        val body =
            mockMvc
                .get("/v3/api-docs")
                .andReturn()
                .response.contentAsString
        val paths = listOf("/test/swagger/a", "/test/swagger/b")
        assertEquals(paths, paths.sortedBy { body.indexOf("\"$it\":") })
    }

    @Test
    fun `서버 주소는 상대 경로라 프록시 뒤에서도 연 주소를 따라간다`() {
        mockMvc.get("/v3/api-docs").andExpect {
            jsonPath("$.servers.length()") { value(1) }
            jsonPath("$.servers[0].url") { value("/") }
        }
    }

    @Test
    fun `Swagger UI 는 태그와 API 를 이름순으로 정렬한다`() {
        mockMvc.get("/v3/api-docs/swagger-config").andExpect {
            jsonPath("$.tagsSorter") { value("alpha") }
            jsonPath("$.operationsSorter") { value("alpha") }
        }
    }

    @Test
    fun `Swagger UI 화면을 내려준다`() {
        mockMvc.get("/swagger-ui/index.html").andExpect {
            status { isOk() }
        }
    }

    @TestConfiguration
    @RestController
    class TestApi {
        @GetMapping("/test/swagger/b")
        fun b() = Unit

        @GetMapping("/test/swagger/a")
        fun a() = Unit
    }
}
