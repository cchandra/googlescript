package com.charlie.scriptwatch

data class ScriptConfig(
    val name: String,
    val scriptId: String,
    val deploymentId: String,
    val functionName: String,
    val extraScopes: String = ""
)

data class ScriptProcess(
    val functionName: String,
    val status: String,
    val type: String,
    val startTime: String,
    val duration: String
)

data class MetricsSummary(
    val total: Long = 0,
    val failed: Long = 0
)
