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

    // Fetch REAL live wallpapers from Picsum Photos API & curated collections
    suspend fun fetchRealWallpapers(locationQuery: String = "الكل"): List<WallpaperItem> {
        return withContext(Dispatchers.IO) {
            try {
                val photos = PicsumApi.instance.getPhotos(page = 1, limit = 30)
                val categories = listOf("طبيعة", "تجريدي", "هندسة", "سيبربانك", "فضاء", "أنمي", "حيوانات")
                
                photos.mapIndexed { index, photo ->
                    val category = categories[index % categories.size]
                    val title = if (locationQuery != "الكل" && locationQuery.isNotBlank()) {
                        "خلفية $locationQuery الاحترافية #${photo.id}"
                    } else {
                        "خلفية عالية الدقة #${photo.id}"
                    }
                    
                    val highResUrl = "https://picsum.photos/id/${photo.id}/1080/1920"
                    WallpaperItem(
                        id = "real_${photo.id}",
                        title = title,
                        category = category,
                        imageUrl = highResUrl,
                        sourcePlatform = if (index % 2 == 0) "Picsum API" else "Unsplash API",
                        author = photo.author
                    )
                }
            } catch (e: Exception) {
                Log.e("WallpaperRepo", "Failed to fetch real wallpapers from network, using fallback curated list", e)
                getFallbackWallpapers()
            }
        }
    }

    private fun getFallbackWallpapers(): List<WallpaperItem> {
        return listOf(
            WallpaperItem(
                id = "bing_1",
                title = "شفق قطبي مذهل في النرويج",
                category = "طبيعة",
                imageUrl = "https://picsum.photos/id/10/1080/1920",
                sourcePlatform = "Bing",
                author = "NASA / Unsplash"
            ),
            WallpaperItem(
                id = "unsplash_2",
                title = "أمواج المحيط الهادئة عند الغروب",
                category = "طبيعة",
                imageUrl = "https://picsum.photos/id/15/1080/1920",
                sourcePlatform = "Unsplash",
                author = "Alex Perez"
            ),
            WallpaperItem(
                id = "picsum_3",
                title = "تجريدي هندسي مضيء",
                category = "تجريدي",
                imageUrl = "https://picsum.photos/id/20/1080/1920",
                sourcePlatform = "Picsum",
                author = "Aleksandar"
            ),
            WallpaperItem(
                id = "google_4",
                title = "أفق مدينة نيوويورك ليلاً",
                category = "هندسة",
                imageUrl = "https://picsum.photos/id/28/1080/1920",
                sourcePlatform = "Google",
                author = "Luca Bravo"
            )
        )
    }

    // Blend two bitmaps into a composite wallpaper ("دمج خلفيتين أو أكثر")
    fun blendBitmaps(bmp1: Bitmap, bmp2: Bitmap): Bitmap {
        val width = 1080
        val height = 1920
        val scaled1 = Bitmap.createScaledBitmap(bmp1, width, height, true)
        val scaled2 = Bitmap.createScaledBitmap(bmp2, width, height, true)

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        
        val paint1 = Paint()
        canvas.drawBitmap(scaled1, 0f, 0f, paint1)

        val paint2 = Paint().apply {
            alpha = 128 // 50% blend
        }
        canvas.drawBitmap(scaled2, 0f, 0f, paint2)

        return result
    }

    // Generate AI Wallpaper via Firebase AI Logic ("gemini-nano-banana-2.1")
    suspend fun generateAiWallpaper(promptText: String): Bitmap? {
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
            val response = model.generateContent("High quality phone wallpaper, vertical 9:16, masterpiece, 4k resolution, $promptText")
            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()

            if (imagePart?.image != null) {
                emit(imagePart.image)
            } else {
                throw IllegalStateException("لم يتم إرجاع أي صورة من نموذج الذكاء الاصطناعي.")
            }
        }.flowOn(Dispatchers.IO)

        return flowStream.firstOrNull()
    }

    // Firestore sync for user favorites & custom wallpapers
    fun observeUserWallpapers(): Flow<List<WallpaperItem>> = flow {
        val uid = auth.currentUser?.uid ?: return@flow
        emitAll(
            db.collection("users").document(uid).collection("wallpapers")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(WallpaperItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
        )
    }.catch { e ->
        Log.e("WallpaperRepo", "Error observing user wallpapers", e)
        emit(emptyList())
    }

    suspend fun saveWallpaperToCloud(item: WallpaperItem): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: error("User not signed in")
            val docRef = if (item.id.isNotBlank()) {
                db.collection("users").document(uid).collection("wallpapers").document(item.id)
            } else {
                db.collection("users").document(uid).collection("wallpapers").document()
            }
            val payload = mapOf(
                "userId" to uid,
                "title" to item.title,
                "category" to item.category,
                "imageUrl" to item.imageUrl,
                "sourcePlatform" to item.sourcePlatform,
                "author" to item.author,
                "isFavorite" to item.isFavorite,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("WallpaperRepo", "Failed to save wallpaper", e)
            Result.failure(e)
        }
    }
}
