package dev.sajancodes.hardblock.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.preferences by preferencesDataStore("onboarding")

@Singleton
class OnboardingPreferences @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val acknowledged = booleanPreferencesKey("standard_limitations_acknowledged")
    val hasAcknowledged = context.preferences.data.map { it[acknowledged] ?: false }
    suspend fun acknowledge() { context.preferences.edit { it[acknowledged] = true } }
}
