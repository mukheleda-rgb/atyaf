package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import com.google.firebase.Firebase
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

class WallpaperRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(context.applicationContext.getString(com.example.R.string.firestore_database_id))
    )

    private val auth = Firebase.auth

    /**
     * Returns photographs from the live Picsum Photos API.
     * Picsum does not provide reliable subject or city metadata, so we never
     * invent categories, authors, or geographic matches for its photographs.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun fetchRealWallpapers(locationQuery: String = "الكل"): List<WallpaperItem> =
        withContext(Dispatchers.IO) {
            try {
                PicsumApi.instance.getPhotos(page = 1, limit = 60)
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

    /** Live Firestore stream of the signed-in user's saved favorites. */
    fun observeFavorites(): Flow<List<WallpaperItem>> = flow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            emit(emptyList())
            return@flow
        }

        emitAll(
            db.collection("users").document(uid).collection("favorites")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { document ->
                        document.toObject(
                            WallpaperItem::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )?.copy(id = document.id, isFavorite = true)
                    }
                }
        )
    }.catch { error ->
        Log.e("WallpaperRepo", "Unable to observe favorites", error)
        emit(emptyList())
    }

    /** Adds or removes a favorite in the current user's own Firestore collection. */
    suspend fun setFavorite(item: WallpaperItem, favorite: Boolean): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: error("يجب تسجيل الدخول أولاً")
            require(item.id.isNotBlank()) { "معرّف الخلفية غير صالح" }

            val document = db.collection("users").document(uid)
                .collection("favorites").document(item.id)

            if (favorite) {
                val payload = mapOf(
                    "userId" to uid,
                    "title" to item.title,
                    "category" to item.category,
                    "imageUrl" to item.imageUrl,
                    "sourcePlatform" to item.sourcePlatform,
                    "author" to item.author,
                    "isFavorite" to true,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                document.set(payload).await()
            } else {
                document.delete().await()
            }
            Result.success(Unit)
        } catch (error: Exception) {
            Log.e("WallpaperRepo", "Unable to update favorite", error)
            Result.failure(error)
        }
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

    // Cloud storage for wallpapers explicitly saved by the user.
    suspend fun saveWallpaperToCloud(item: WallpaperItem): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: error("يجب تسجيل الدخول أولاً")
            val docRef = db.collection("users").document(uid)
                .collection("wallpapers")
                .document(item.id.ifBlank { db.collection("_ids").document().id })

            val payload = mapOf(
                "userId" to uid,
                "title" to item.title,
                "category" to item.category,
                "imageUrl" to item.imageUrl,
                "sourcePlatform" to item.sourcePlatform,
                "author" to item.author,
                "isFavorite" to item.isFavorite,
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (error: Exception) {
            Log.e("WallpaperRepo", "Failed to save wallpaper", error)
            Result.failure(error)
        }
    }
}
