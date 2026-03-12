import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

//Plugins 
plugins{
    java
    id("org.springframework.boot")  version "3.5.11"
    id("io.spring.dependency-management") version "1.1.7"
    id("jacoco")
}

//Project Coordinates
group = "com.mprs"
version = "1.0.0-SNAPSHOT"

//Java Toolchain
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

//Dependency Versions
val jjwtVersion = "0.12.5"
val springdocVersion = "2.8.8"
val flywayVersion = "10.12.0"
val commonsCsvVersion = "1.11.0"
val jaccoVersion = "0.8.12"

//Repositories
repositories{
    mavenCentral()
}

//Dependencies
dependencies{
    //Web Layer
    implementation("org.springframework.boot:spring-boot-starter-web")

    //Spring Security
    implementation("org.springframework.boot:spring-boot-starter-security")

    //Bean Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    //JPA / Hibernate
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // PostgreSQL Driver
    runtimeOnly("org.postgresql:postgresql")

    //Flyway DB Migration
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")

    //JWT
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    //Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    //SpringDoc / Swagger UI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

    //Apache Commons CSV
    implementation("org.apache.commons:commons-csv:$commonsCsvVersion")

    //Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-security-test")

    //H2 in-memory
    testRuntimeOnly("com.h2database:h2")
    runtimeOnly("com.h2database:h2")

    //Lombok in tests
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}

//Annotation Processors
configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

//Test Configuration
tasks.withType<Test> {
    useJUnitPlatform()      //JUnit 5

    testLogging {
        events (
            TestLogEvent.PASSED,
            TestLogEvent.FAILED,
            TestLogEvent.SKIPPED
        )
        exceptionFormat = TestExceptionFormat.FULL
        showStandardStreams = false
    }

    finalizedBy(tasks.jacocoTestReport)
}

//JaCoCo
tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required = true 
        html.required = true
        csv.required = false
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

//Jar Manifest
tasks.bootJar {
    archiveFileName = "mprs.jar"
    manifest {
        attributes["Implementation-Title"] = "Merchant Payment Reconcilation System"
        attributes["Implementation-Version"] = version
    }
}

