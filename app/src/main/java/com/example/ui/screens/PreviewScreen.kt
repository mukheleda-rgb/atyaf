package com.example.ui.screens

import android.app.WallpaperManager
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.WallpaperItem
import com.example.data.WallpaperRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PreviewScreen(
    item: WallpaperItem,
    items: List<WallpaperItem>,
    repository: WallpaperRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val favorites by repository.observeFavorites().collectAsState(initial = emptyList())
    val isFavorite = favorites.any { it.id == currentItem.id }
    var isApplying by remember { mutableStateOf(false) }
    val safeItems = remember(items, item) { (items.ifEmpty { listOf(item) }).let { list -> if (list.any { it.id == item.id }) list else listOf(item) + list } }
    var currentIndex by remember(item.id, safeItems) { mutableIntStateOf(safeItems.indexOfFirst { it.id == item.id }.coerceAtLeast(0)) }
    val currentItem = safeItems[currentIndex.coerceIn(0, safeItems.lastIndex)]
    var dragDistance by remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).pointerInput(safeItems, currentIndex) {
        detectHorizontalDragGestures(
            onHorizontalDrag = { _, amount -> dragDistance += amount },
            onDragEnd = {
                if (abs(dragDistance) > 55f) {
                    currentIndex = (currentIndex + if (dragDistance < 0) 1 else -1).coerceIn(0, safeItems.lastIndex)
                }
                dragDistance = 0f
            },
            onDragCancel = { dragDistance = 0f }
        )
    }) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(currentItem.imageUrl.ifBlank { R.drawable.ic_launcher_background })
                .crossfade(true)
                .build(),
            contentDescription = currentItem.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                repository.setFavorite(currentItem, !isFavorite).onFailure {
                                    Toast.makeText(
                                        context,
                                        it.localizedMessage ?: "تعذر تحديث المفضلة",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "المفضلة",
                            tint = if (isFavorite) Color(0xFFFF5577) else Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            val shareIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(
                                    android.content.Intent.EXTRA_TEXT,
                                    "شاهد هذه الخلفية من أطياف: ${currentItem.imageUrl}"
                                )
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "مشاركة الخلفية"))
                        },
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = Color.White)
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.72f),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        currentItem.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "المصدر: ${currentItem.sourcePlatform} • المصور: ${currentItem.author}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFCBD5E1)
                    )
                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                isApplying = true
                                try {
                                    val loader = coil.ImageLoader(context)
                                    val request = ImageRequest.Builder(context)
                                        .data(currentItem.imageUrl)
                                        .allowHardware(false)
                                        .build()
                                    val result = loader.execute(request)
                                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap

                                    if (bitmap != null) {
                                        withContext(Dispatchers.IO) {
                                            WallpaperManager.getInstance(context).setBitmap(
                                                bitmap,
                                                null,
                                                true,
                                                WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                                            )
                                        }
                                        Toast.makeText(
                                            context,
                                            "تم تعيين الخلفية للشاشة الرئيسية وشاشة القفل",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        Toast.makeText(context, "تعذر تحميل صورة الخلفية", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (error: Exception) {
                                    Toast.makeText(
                                        context,
                                        "فشل تعيين الخلفية: ${error.localizedMessage}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } finally {
                                    isApplying = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp).testTag("set_wallpaper_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isApplying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("تعيين كخلفية للشاشة", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
