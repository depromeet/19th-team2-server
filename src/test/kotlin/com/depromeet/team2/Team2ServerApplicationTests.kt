package com.depromeet.team2

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.ZoneId
import kotlin.test.assertEquals

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class Team2ServerApplicationTests {
    @Test
    fun contextLoads() {
    }

    @Test
    fun `기본 타임존은 Asia_Seoul 이다`() {
        assertEquals(ZoneId.of("Asia/Seoul"), ZoneId.systemDefault())
    }
}
