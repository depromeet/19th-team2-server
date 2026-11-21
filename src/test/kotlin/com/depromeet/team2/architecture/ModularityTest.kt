package com.depromeet.team2.architecture

import com.depromeet.team2.Team2ServerApplication
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

class ModularityTest {
    private val modules = ApplicationModules.of(Team2ServerApplication::class.java)

    @Test
    fun `도메인 간 의존 규칙을 지킨다`() {
        modules.verify()
    }

    @Test
    fun `도메인 의존성 맵과 모듈 캔버스를 만든다`() {
        Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml()
            .writeModuleCanvases(Documenter.CanvasOptions.defaults().revealInternals())
    }
}
