package com.depromeet.team2.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.domain.JavaParameterizedType
import com.tngtech.archunit.core.domain.JavaType
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import jakarta.persistence.Entity
import org.junit.jupiter.api.Test
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.RestController

class ArchitectureTest {
    private val classes: JavaClasses =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("com.depromeet.team2")

    @Test
    fun `Controller 는 DTO 만 반환한다`() {
        methods()
            .that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(RestController::class.java)
            .or()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(Controller::class.java)
            .and()
            .arePublic()
            .should(
                object : ArchCondition<JavaMethod>("return only DTO (..dto.. or ..payload..) types") {
                    override fun check(
                        method: JavaMethod,
                        events: ConditionEvents,
                    ) {
                        if (!isDtoType(method.returnType)) {
                            events.add(
                                SimpleConditionEvent.violated(
                                    method,
                                    "${method.fullName} 이 DTO 가 아닌 ${method.returnType.name} 을 반환합니다",
                                ),
                            )
                        }
                    }
                },
            ).allowEmptyShould(true)
            .check(classes)
    }

    @Test
    fun `Service 는 엔티티를 반환하지 않는다`() {
        methods()
            .that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(Service::class.java)
            .and()
            .arePublic()
            .should(
                object : ArchCondition<JavaMethod>("not return @Entity types") {
                    override fun check(
                        method: JavaMethod,
                        events: ConditionEvents,
                    ) {
                        if (containsEntity(method.returnType)) {
                            events.add(
                                SimpleConditionEvent.violated(
                                    method,
                                    "${method.fullName} 이 엔티티를 반환합니다 (${method.returnType.name}). DTO 로 바꿔 주세요",
                                ),
                            )
                        }
                    }
                },
            ).allowEmptyShould(true)
            .check(classes)
    }

    @Test
    fun `Service 파일 안에 data class 를 정의하지 않는다`() {
        classes()
            .that(
                object : DescribedPredicate<JavaClass>("declared in a *Service.kt file") {
                    override fun test(javaClass: JavaClass): Boolean =
                        javaClass.source
                            .flatMap { it.fileName }
                            .map { it.endsWith("Service.kt") }
                            .orElse(false)
                },
            ).should(
                object : ArchCondition<JavaClass>("not be a data class") {
                    override fun check(
                        javaClass: JavaClass,
                        events: ConditionEvents,
                    ) {
                        val isDataClass =
                            javaClass.methods.any { it.name == "copy" } &&
                                javaClass.methods.any { it.name == "component1" }
                        if (isDataClass) {
                            events.add(
                                SimpleConditionEvent.violated(
                                    javaClass,
                                    "${javaClass.name} 이 Service 파일 안에 정의돼 있습니다. dto 패키지로 옮겨 주세요",
                                ),
                            )
                        }
                    }
                },
            ).allowEmptyShould(true)
            .check(classes)
    }

    private fun isDtoType(type: JavaType): Boolean {
        val raw = type.toErasure()
        return when {
            raw.name in setOf("void", "kotlin.Unit") -> {
                true
            }

            raw.isAssignableTo(ResponseEntity::class.java) ||
                raw.isAssignableTo(Collection::class.java) -> {
                typeArguments(type).all(::isDtoType)
            }

            raw.packageName.split(".").any { it in setOf("dto", "payload") } -> {
                typeArguments(type).all(::isDtoType)
            }

            else -> {
                false
            }
        }
    }

    private fun containsEntity(type: JavaType): Boolean =
        type.toErasure().isAnnotatedWith(Entity::class.java) ||
            typeArguments(type).any(::containsEntity)

    private fun typeArguments(type: JavaType): List<JavaType> =
        (type as? JavaParameterizedType)?.actualTypeArguments.orEmpty()
}
