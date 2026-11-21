package com.depromeet.team2.global.config

import com.depromeet.team2.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.options
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootTest
@AutoConfigureMockMvc
@Import(CorsConfigTest.TestApi::class, TestcontainersConfiguration::class)
class CorsConfigTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `허용된 origin 의 사전 요청은 통과한다`() {
        mockMvc
            .options("/test/cors") {
                header("Origin", "http://localhost:3000")
                header("Access-Control-Request-Method", "GET")
            }.andExpect {
                status { isOk() }
                header { string("Access-Control-Allow-Origin", "http://localhost:3000") }
                header { string("Access-Control-Allow-Credentials", "true") }
            }
    }

    @Test
    fun `허용되지 않은 origin 은 막는다`() {
        mockMvc
            .options("/test/cors") {
                header("Origin", "http://evil.com")
                header("Access-Control-Request-Method", "GET")
            }.andExpect {
                status { isForbidden() }
                header { doesNotExist("Access-Control-Allow-Origin") }
            }
    }

    @TestConfiguration
    @RestController
    class TestApi {
        @GetMapping("/test/cors")
        fun cors() = Unit
    }
}
