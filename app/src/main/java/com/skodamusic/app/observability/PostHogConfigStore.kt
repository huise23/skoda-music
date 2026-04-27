package com.skodamusic.app.observability

import android.content.Context

data class PostHogConfig(
    val enabled: Boolean,
    val host: String,
    val projectApiKey: String,
    val projectId: String,
    val region: String,
    val environment: String
)

object PostHogConfigStore {
    private const val PREFS_NAME = "posthog_observability"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOST = "host"
    private const val KEY_PROJECT_API_KEY = "project_api_key"
    private const val KEY_PROJECT_ID = "project_id"
    private const val KEY_REGION = "region"
    private const val KEY_ENVIRONMENT = "environment"

    // Embedded defaults (can be overridden by prefs at runtime).
    private const val DEFAULT_ENABLED = true
    private const val DEFAULT_HOST = "https://us.i.posthog.com"
    private const val DEFAULT_PROJECT_API_KEY = "phc_wPMBC5C8pCscinCMjqbcFryREP5sKACufHzYiAWxtig6"
    private const val DEFAULT_PROJECT_ID = "399199"
    private const val DEFAULT_REGION = "us_cloud"
    private const val DEFAULT_ENVIRONMENT = "prod"

    fun read(context: Context): PostHogConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return PostHogConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED),
            host = prefs.getString(KEY_HOST, DEFAULT_HOST).orEmpty().trim().ifEmpty { DEFAULT_HOST },
            projectApiKey = prefs.getString(KEY_PROJECT_API_KEY, DEFAULT_PROJECT_API_KEY)
                .orEmpty()
                .trim()
                .ifEmpty { DEFAULT_PROJECT_API_KEY },
            projectId = prefs.getString(KEY_PROJECT_ID, DEFAULT_PROJECT_ID)
                .orEmpty()
                .trim()
                .ifEmpty { DEFAULT_PROJECT_ID },
            region = prefs.getString(KEY_REGION, DEFAULT_REGION)
                .orEmpty()
                .trim()
                .ifEmpty { DEFAULT_REGION },
            environment = prefs.getString(KEY_ENVIRONMENT, DEFAULT_ENVIRONMENT)
                .orEmpty()
                .trim()
                .ifEmpty { DEFAULT_ENVIRONMENT }
        )
    }

    fun hasEffectiveConfig(config: PostHogConfig): Boolean {
        return config.enabled && config.host.isNotBlank() && config.projectApiKey.isNotBlank()
    }
}
