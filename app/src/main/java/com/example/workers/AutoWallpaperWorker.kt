package com.example.workers

import android.app.WallpaperManager
import android.graphics.BitmapFactory
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.PicsumApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

class AutoWallpaperWorker(
    appContext: android.content.Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val page = ((System.currentTimeMillis() / 60_000L) % 1000L + 1L).toInt()
            val photo = PicsumApi.instance.getPhotos(page = page, limit = 30).randomOrNull()
                ?: return@withContext Result.retry()

            val imageUrl = "https://picsum.photos/id/${photo.id}/1080/1920"
            val bitmap = URL(imageUrl).openConnection().apply {
                connectTimeout = 15_000
                readTimeout = 25_000
            }.getInputStream().use(BitmapFactory::decodeStream)
                ?: return@withContext Result.retry()

            WallpaperManager.getInstance(applicationContext).setBitmap(
                bitmap,
                null,
                true,
                WallpaperManager.FLAG_SYSTEM
            )
            bitmap.recycle()
            Result.success()
        } catch (error: Exception) {
            Log.e("AtyafAutoWallpaper", "Automatic wallpaper update failed", error)
            Result.retry()
        }
    }
}
