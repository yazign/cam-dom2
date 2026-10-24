package com.mycams.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Camera(
    val id: Long,
    val name: String,
    val host: String,
    val port: Int = 554,
    val user: String = "admin",
    val password: String = "",
    val channel: Int = 1,
    val customUrl: String = ""
) {
    /** Адрес потока. sub = true — облегчённый поток (для сетки), false — основной (HD). */
    fun url(sub: Boolean): String {
        val s = if (sub) 1 else 0
        if (customUrl.isNotBlank()) {
            return customUrl.trim()
                .replace("stream=0", "stream=$s")
                .replace("stream=1", "stream=$s")
        }
        // Стандартный формат камер и регистраторов Xiongmai (XMEye)
        return "rtsp://$host:$port/user=$user&password=$password&channel=$channel&stream=$s.sdp?real_stream"
    }
}

/** Камеры дома 2 — добавляются при первом запуске. */
val DEFAULT_CAMERAS = listOf(
    Camera(1, "Аптека", "192.168.1.123"),
    Camera(2, "За домом", "192.168.1.129"),
    Camera(3, "Качели, волейбол", "192.168.1.127"),
    Camera(4, "Веранда, кострище", "192.168.1.125"),
    Camera(5, "Кормушка для птиц", "192.168.1.133"),
    Camera(6, "Дверь, глазок", "192.168.1.126"),
    Camera(7, "Столб у дома", "192.168.1.124"),
    Camera(8, "Парковка", "192.168.1.130"),
    Camera(9, "Мастерская 4K", "192.168.1.136")
)

class Storage(context: Context) {
    private val prefs = context.getSharedPreferences("cameras", Context.MODE_PRIVATE)

    fun load(): List<Camera> {
        val json = prefs.getString("list", null)
        if (json == null) {
            save(DEFAULT_CAMERAS)
            return DEFAULT_CAMERAS
        }
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Camera(
                id = o.getLong("id"),
                name = o.optString("name"),
                host = o.optString("host"),
                port = o.optInt("port", 554),
                user = o.optString("user", "admin"),
                password = o.optString("password"),
                channel = o.optInt("channel", 1),
                customUrl = o.optString("customUrl")
            )
        }
    }

    fun save(list: List<Camera>) {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(
                JSONObject()
                    .put("id", c.id).put("name", c.name).put("host", c.host)
                    .put("port", c.port).put("user", c.user).put("password", c.password)
                    .put("channel", c.channel).put("customUrl", c.customUrl)
            )
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }
}

/** Сохраняет снимок в Галерею, папка Pictures/Cameras. */
fun saveSnapshot(context: Context, bitmap: Bitmap, cameraName: String): Boolean {
    val safe = cameraName.replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').ifEmpty { "camera" }
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "${safe}_$stamp.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Cameras")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return false
    return context.contentResolver.openOutputStream(uri)?.use {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)
    } ?: false
}
