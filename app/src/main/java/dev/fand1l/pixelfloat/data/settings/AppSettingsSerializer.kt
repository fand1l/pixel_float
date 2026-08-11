package dev.fand1l.pixelfloat.data.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * JSON-backed typed DataStore serializer.
 *
 * Chosen over Preferences DataStore because the settings model is nested (calibration
 * profiles), and over Proto DataStore because protoc on every CI run plus hand-edited
 * .proto files is hostile to a phone-only workflow. The serialization plugin ships with
 * Kotlin itself, so this costs no extra build machinery.
 */
object AppSettingsSerializer : Serializer<AppSettings> {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    override val defaultValue: AppSettings = AppSettings()

    override suspend fun readFrom(input: InputStream): AppSettings =
        try {
            json.decodeFromString(AppSettings.serializer(), input.readBytes().decodeToString())
        } catch (error: SerializationException) {
            throw CorruptionException("settings.json is not readable", error)
        }

    override suspend fun writeTo(t: AppSettings, output: OutputStream) {
        output.write(json.encodeToString(AppSettings.serializer(), t).encodeToByteArray())
    }
}
