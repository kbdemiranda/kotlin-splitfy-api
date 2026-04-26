package io.github.splitfy.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class KotlinSplitfyApiApplication

fun main(args: Array<String>) {
	runApplication<KotlinSplitfyApiApplication>(*args)
}
