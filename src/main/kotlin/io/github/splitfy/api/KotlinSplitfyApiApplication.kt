package io.github.splitfy.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class KotlinSplitfyApiApplication

fun main(args: Array<String>) {
	runApplication<KotlinSplitfyApiApplication>(*args)
}
