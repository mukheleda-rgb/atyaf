package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class WallpaperRepository(private val db: FirebaseFirestore?, private val auth: FirebaseAuth?, private val appContext: Context) {

    constructor(context: Context) : this(null, null, context.applicationContext)

    private val localPrefs = appContext.getSharedPreferences("atyaf_local_data", Context.MODE_PRIVATE)
    private val favoritesState = MutableStateFlow(loadLocalFavorites())

    private fun loadLocalFavorites(): List<WallpaperItem> = runCatching {
        val array = JSONArray(localPrefs.getString("favorites", "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            WallpaperItem(
                id = item.optString("id"),
                title = item.optString("title"),
                category = item.optString("category"),
                imageUrl = item.optString("imageUrl"),
                sourcePlatform = item.optString("sourcePlatform"),
                author = item.optString("author"),
                isFavorite = true
            )
        }
    }.getOrDefault(emptyList())

    private fun persistLocalFavorites(items: List<WallpaperItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("category", item.category)
                put("imageUrl", item.imageUrl)
                put("sourcePlatform", item.sourcePlatform)
                put("author", item.author)
            })
        }
        localPrefs.edit().putString("favorites", array.toString()).apply()
    }
    private val nextPhotoPage = AtomicInteger(1)

    /**
     * Loads from the locally selected public image providers. Source selection,
     * category selection and custom server URL are stored on this device only.
     */
    suspend fun fetchRealWallpapers(locationQuery: String = "الكل"): List<WallpaperItem> =
        withContext(Dispatchers.IO) {
            val prefs = appContext.getSharedPreferences("atyaf_preferences", Context.MODE_PRIVATE)
            val allSources = listOf("openverse", "wikimedia", "picsum", "nasa", "met", "artic", "cleveland", "loc", "custom")
            val selectedSources = prefs.getStringSet("selected_image_sources", allSources.toSet())?.toSet() ?: allSources.toSet()
            val allCategories = linkedMapOf(
                "الكل" to "",
                "طبيعة" to "nature landscape",
                "فضاء" to "space galaxy nebula",
                "عمارة" to "architecture buildings",
                "حيوانات" to "wildlife animals",
                "فن" to "art illustration",
                "بحر" to "ocean sea underwater",
                "سيارات" to "cars automotive",
                "تقنية" to "technology"
            )
            val selectedCategories = prefs.getStringSet("selected_image_categories", setOf("الكل"))?.toSet()
                ?.filter { it in allCategories }?.ifEmpty { listOf("الكل") } ?: listOf("الكل")
            val customServer = prefs.getString("custom_image_server", "")?.trim().orEmpty().trimEnd('/')
            val queries = if ("الكل" in selectedCategories) listOf("الكل" to "") else selectedCategories.map { it to allCategories.getValue(it) }
            val output = mutableListOf<WallpaperItem>()

            fun encoded(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
            fun json(url: String): JSONObject {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.setRequestProperty("User-Agent", "AtyafAndroid/1.0 (wallpaper browser)")
                return try {
                    if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
                    JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                } finally { connection.disconnect() }
            }
            fun add(id: String, title: String, category: String, image: String, source: String, author: String) {
                if (id.isNotBlank() && image.startsWith("https://")) {
                    output.add(WallpaperItem(
                        id = "${source.lowercase().replace(" ", "_")}_${id}",
                        title = title.ifBlank { "صورة من $source" },
                        category = category.ifBlank { "غير مصنف" },
                        imageUrl = image,
                        sourcePlatform = source,
                        author = author.ifBlank { "غير معروف" }
                    ))
                }
            }

            for ((category, categoryQuery) in queries) {
                val query = listOf(locationQuery.takeIf { it != "الكل" }.orEmpty(), categoryQuery).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "wallpaper photography" }
                if ("picsum" in selectedSources && category == "الكل") runCatching {
                    val page = nextPhotoPage.getAndUpdate { if (it >= 1000) 1 else it + 1 }
                    PicsumApi.instance.getPhotos(page = page, limit = 40).forEach {
                        add(it.id, "صورة فوتوغرافية #${it.id}", "تصوير فوتوغرافي", "https://picsum.photos/id/${it.id}/1080/1920", "Picsum Photos", it.author)
                    }
                }
                if ("openverse" in selectedSources) runCatching {
                    val data = json("https://api.openverse.org/v1/images/?q=${encoded(query)}&page_size=30")
                    val results = data.optJSONArray("results") ?: JSONArray()
                    for (i in 0 until results.length()) {
                        val item = results.optJSONObject(i) ?: continue
                        add(item.optString("id"), item.optString("title"), category, item.optString("thumbnail").ifBlank { item.optString("url") }, "Openverse", item.optString("creator"))
                    }
                }
                if ("wikimedia" in selectedSources) runCatching {
                    val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=${encoded(query)}&gsrnamespace=6&gsrlimit=30&prop=imageinfo&iiprop=url|extmetadata&iiurlwidth=1000&format=json&origin=*"
                    val pages = json(url).optJSONObject("query")?.optJSONObject("pages") ?: JSONObject()
                    val keys = pages.keys()
                    while (keys.hasNext()) {
                        val item = pages.optJSONObject(keys.next()) ?: continue
                        val info = item.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                        val meta = info.optJSONObject("extmetadata")
                        add(item.optString("pageid"), item.optString("title").removePrefix("File:"), category, info.optString("thumburl").ifBlank { info.optString("url") }, "Wikimedia Commons", meta?.optJSONObject("Artist")?.optString("value").orEmpty())
                    }
                }
                if ("artic" in selectedSources) runCatching {
                    val url = "https://api.artic.edu/api/v1/artworks/search?q=${encoded(query)}&limit=30&fields=id,title,artist_title,image_id,is_public_domain"
                    val rows = json(url).optJSONArray("data") ?: JSONArray()
                    for (i in 0 until rows.length()) {
                        val item = rows.optJSONObject(i) ?: continue
                        val imageId = item.optString("image_id")
                        if (imageId.isNotBlank() && item.optBoolean("is_public_domain")) add(item.optString("id"), item.optString("title"), category, "https://www.artic.edu/iiif/2/$imageId/full/843,/0/default.jpg", "Art Institute of Chicago", item.optString("artist_title"))
                    }
                }
                if ("cleveland" in selectedSources) runCatching {
                    val rows = json("https://openaccess-api.clevelandart.org/api/artworks/?q=${encoded(query)}&cc0=1&limit=30").optJSONArray("data") ?: JSONArray()
                    for (i in 0 until rows.length()) {
                        val item = rows.optJSONObject(i) ?: continue
                        val images = item.optJSONObject("images")
                        val image = images?.optJSONObject("web")?.optString("url").orEmpty().ifBlank { images?.optJSONObject("print")?.optString("url").orEmpty() }
                        add(item.optString("id"), item.optString("title"), category, image, "Cleveland Museum of Art", item.optString("creators"))
                    }
                }
                if ("met" in selectedSources) runCatching {
                    val ids = json("https://collectionapi.metmuseum.org/public/collection/v1.1/search?q=${encoded(query)}&hasImages=true&isPublicDomain=true")
                        .optJSONArray("objectIDs") ?: JSONArray()
                    for (i in 0 until minOf(ids.length(), 12)) {
                        val id = ids.optInt(i)
                        val item = json("https://collectionapi.metmuseum.org/public/collection/v1/objects/$id")
                        if (item.optBoolean("isPublicDomain")) add(id.toString(), item.optString("title"), category, item.optString("primaryImageSmall").ifBlank { item.optString("primaryImage") }, "The Met Museum", item.optString("artistDisplayName"))
                    }
                }
                if ("nasa" in selectedSources) runCatching {
                    val rows = json("https://images-api.nasa.gov/search?q=${encoded(query)}&media_type=image&page_size=30")
                        .optJSONObject("collection")?.optJSONArray("items") ?: JSONArray()
                    for (i in 0 until rows.length()) {
                        val item = rows.optJSONObject(i) ?: continue
                        val data = item.optJSONArray("data")?.optJSONObject(0) ?: continue
                        val links = item.optJSONArray("links")
                        val image = links?.optJSONObject(0)?.optString("href").orEmpty()
                        add(data.optString("nasa_id"), data.optString("title"), category, image, "NASA", data.optString("photographer").ifBlank { data.optString("center") })
                    }
                }
                if ("loc" in selectedSources) runCatching {
                    val rows = json("https://www.loc.gov/photos/?fo=json&q=${encoded(query)}&c=30").optJSONArray("results") ?: JSONArray()
                    for (i in 0 until rows.length()) {
                        val item = rows.optJSONObject(i) ?: continue
                        val image = item.optJSONArray("image_url")?.optString(0).orEmpty()
                        add(item.optString("id"), item.optString("title"), category, image, "Library of Congress", item.optString("contributor"))
                    }
                }
                if ("custom" in selectedSources && customServer.isNotBlank()) runCatching {
                    val rows = json("$customServer/search?q=${encoded(query)}&limit=30").optJSONArray("results") ?: JSONArray()
                    for (i in 0 until rows.length()) {
                        val item = rows.optJSONObject(i) ?: continue
                        add(item.optString("id"), item.optString("title"), item.optString("category").ifBlank { category }, item.optString("imageUrl"), item.optString("sourcePlatform").ifBlank { "الخادم المخصص" }, item.optString("author"))
                    }
                }
            }
            output.distinctBy { it.id }.shuffled().take(180)
        }

    /** Favorites are stored only on this device; no account or cloud sync is used. */
    fun observeFavorites(): Flow<List<WallpaperItem>> = favoritesState.asStateFlow()

    suspend fun setFavorite(item: WallpaperItem, favorite: Boolean): Result<Unit> = runCatching {
        require(item.id.isNotBlank()) { "معرّف الخلفية غير صالح" }
        val updated = if (favorite) {
            favoritesState.value.filterNot { it.id == item.id } + item.copy(isFavorite = true)
        } else {
            favoritesState.value.filterNot { it.id == item.id }
        }
        persistLocalFavorites(updated)
        favoritesState.value = updated
    }

    // Blend two bitmaps into a composite wallpaper.
    fun blendBitmaps(bmp1: Bitmap, bmp2: Bitmap): Bitmap {
        val width = 1080
        val height = 1920
        val scaled1 = Bitmap.createScaledBitmap(bmp1, width, height, true)
        val scaled2 = Bitmap.createScaledBitmap(bmp2, width, height, true)

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { result ->
            val canvas = Canvas(result)
            canvas.drawBitmap(scaled1, 0f, 0f, Paint())
            canvas.drawBitmap(scaled2, 0f, 0f, Paint().apply { alpha = 128 })
        }
    }

    // Generate an AI wallpaper using Firebase AI Logic.
    suspend fun generateAiWallpaper(promptText: String): Bitmap? {
        require(promptText.isNotBlank()) { "اكتب وصفاً للصورة أولاً" }

        val flowStream = flow {
            val config = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
                imageConfig = ImageConfig.builder()
                    .setAspectRatio(AspectRatio.PORTRAIT_9x16)
                    .setImageSize(ImageSize.SIZE_1K)
                    .build()
            }
            val model = Firebase.ai.generativeModel(
                modelName = "gemini-nano-banana-2.1",
                generationConfig = config
            )
            val response = model.generateContent(
                "High quality phone wallpaper, vertical 9:16, high detail, $promptText"
            )
            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()

            imagePart?.image?.let { emit(it) }
                ?: throw IllegalStateException("لم يتم إرجاع صورة من نموذج الذكاء الاصطناعي.")
        }.flowOn(Dispatchers.IO)

        return flowStream.firstOrNull()
    }

}
