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
     * Returns photographs from the live Picsum Photos API.
     * Picsum does not provide reliable subject or city metadata, so we never
     * invent categories, authors, or geographic matches for its photographs.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun fetchRealWallpapers(locationQuery: String = "الكل"): List<WallpaperItem> =
        withContext(Dispatchers.IO) {
            try {
                val page = nextPhotoPage.getAndUpdate { current -> if (current >= 1000) 1 else current + 1 }
                PicsumApi.instance.getPhotos(page = page, limit = 60)
                    .filter { it.id.isNotBlank() && it.downloadUrl.isNotBlank() }
                    .map { photo ->
                        WallpaperItem(
                            id = "picsum_${photo.id}",
                            title = "صورة فوتوغرافية #${photo.id}",
                            category = "تصوير فوتوغرافي",
                            imageUrl = "https://picsum.photos/id/${photo.id}/1080/1920",
                            sourcePlatform = "Picsum Photos",
                            author = photo.author.ifBlank { "مصور غير معروف" }
                        )
                    }
            } catch (e: Exception) {
                Log.e("WallpaperRepo", "Unable to load photographs from Picsum Photos", e)
                emptyList()
            }
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
