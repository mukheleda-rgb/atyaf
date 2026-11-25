package com.example.ui.screens

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.WallpaperItem
import com.example.data.WallpaperRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlendScreen(repository: WallpaperRepository, wallpapers: List<WallpaperItem>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedItem1 by remember { mutableStateOf(wallpapers.getOrNull(0)) }
    var selectedItem2 by remember { mutableStateOf(wallpapers.getOrNull(1)) }
    var blendedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isBlending by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "دمج خلفيتين",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "اختر صورتين من المعرض، ثم امزجهما واحفظ النتيجة كخلفية.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Selection row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Item 1
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = selectedItem1?.imageUrl ?: "",
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        Text(
                            text = "الخلفية الأولى",
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(4.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                // Item 2
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = selectedItem2?.imageUrl ?: "",
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        Text(
                            text = "الخلفية الثانية",
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(4.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "اختر الصور من المعرض",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (wallpapers.isEmpty()) {
                Text(
                    "لا توجد صور متاحة. افتح الصفحة الرئيسية وحمّل الصور أولاً.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(wallpapers, key = { it.id }) { photo ->
                        Card(
                            modifier = Modifier.width(118.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column {
                                AsyncImage(
                                    model = photo.imageUrl,
                                    contentDescription = photo.title,
                                    modifier = Modifier.fillMaxWidth().height(104.dp),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                Text(
                                    photo.title,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    TextButton(onClick = { selectedItem1 = photo }) {
                                        Text(if (selectedItem1?.id == photo.id) "الأولى ✓" else "الأولى")
                                    }
                                    TextButton(onClick = { selectedItem2 = photo }) {
                                        Text(if (selectedItem2?.id == photo.id) "الثانية ✓" else "الثانية")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Result preview or blend action
            if (blendedBitmap != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                ) {
                    Image(
                        bitmap = blendedBitmap!!.asImageBitmap(),
                        contentDescription = "Blended Wallpaper",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        WallpaperManager.getInstance(context).setBitmap(
                                            blendedBitmap,
                                            null,
                                            true,
                                            WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                                        )
                                    }
                                    Toast.makeText(context, "تم تعيين الخلفية المدمجة بنجاح!", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "فشل تعيين الخلفية: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .height(50.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Wallpaper, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تعيين الخلفية المدمجة", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            if (selectedItem1 != null && selectedItem2 != null) {
                                isBlending = true
                                scope.launch {
                                    try {
                                        val loader = ImageLoader(context)
                                        val req1 = ImageRequest.Builder(context).data(selectedItem1!!.imageUrl).allowHardware(false).build()
                                        val req2 = ImageRequest.Builder(context).data(selectedItem2!!.imageUrl).allowHardware(false).build()
                                        
                                        val res1 = loader.execute(req1)
                                        val res2 = loader.execute(req2)

                                        val bmp1 = (res1.drawable as? BitmapDrawable)?.bitmap
                                        val bmp2 = (res2.drawable as? BitmapDrawable)?.bitmap

                                        if (bmp1 != null && bmp2 != null) {
                                            blendedBitmap = repository.blendBitmaps(bmp1, bmp2)
                                        } else {
                                            Toast.makeText(context, "تعذر تحميل صور الدمج.", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "خطأ في الدمج: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isBlending = false
                                    }
                                }
                            }
                        },
                        enabled = !isBlending && selectedItem1 != null && selectedItem2 != null,
                        modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isBlending) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        } else {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("بدء عملية الدمج الاحترافي", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}
