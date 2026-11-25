package com.example.workers

import android.app.WallpaperManager
import android.graphics.BitmapFactory
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.WallpaperRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

class AutoWallpaperWorker(
    appContext: android.content.Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val wallpaper = WallpaperRepository(applicationContext).fetchRealWallpapers().randomOrNull()
                ?: return@withContext Result.retry()
            val bitmap = URL(wallpaper.imageUrl).openConnection().apply {
                connectTimeout = 15000
                readTimeout = 25000
            }.getInputStream().use { input -> BitmapFactory.decodeStream(input) }
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
