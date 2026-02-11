package io.github.splitfy.api.domain.enums

enum class ServiceType(val description: String) {
    STREAMING_VIDEO("Video Streaming"),
    STREAMING_MUSIC("Music Streaming"),
    SOFTWARE("Software"),
    GAMES("Games"),
    NEWS("News"),
    CLOUD_STORAGE("Cloud Storage"),
    FITNESS("Fitness");

    override fun toString(): String = description
}
