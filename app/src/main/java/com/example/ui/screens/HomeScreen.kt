package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.WallpaperItem
import com.example.data.WallpaperRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: WallpaperRepository,
    onWallpaperClick: (WallpaperItem, List<WallpaperItem>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var searchQuery by remember { mutableStateOf("") }
    var wallpapers by remember { mutableStateOf<List<WallpaperItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }

    val favorites by repository.observeFavorites().collectAsState(initial = emptyList())
    val favoriteIds = remember(favorites) { favorites.map { it.id }.toSet() }

    suspend fun loadWallpapers() {
        isLoading = true
        loadError = false
        val result = repository.fetchRealWallpapers()
        wallpapers = result
        loadError = result.isEmpty()
        isLoading = false
    }

    LaunchedEffect(Unit) { loadWallpapers() }

    val filteredWallpapers = remember(searchQuery, wallpapers) {
        wallpapers.filter { item ->
            searchQuery.isBlank() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.author.contains(searchQuery, ignoreCase = true) ||
                item.id.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 155.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "أطياف",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "اكتشف صوراً جديدة واجعلها خلفيتك",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { scope.launch { loadWallpapers() } },
                            enabled = !isLoading
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث الصور")
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("ابحث باسم المصور أو رقم الصورة") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().testTag("search_wallpapers_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("معرض الصور", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "${filteredWallpapers.size} صورة",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (isLoading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (loadError) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyGalleryState(
                        title = "تعذر تحميل الصور حالياً",
                        message = "تحقق من اتصال الإنترنت ثم حاول مرة أخرى.",
                        actionLabel = "إعادة المحاولة",
                        onAction = { scope.launch { loadWallpapers() } }
                    )
                }
            } else if (filteredWallpapers.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyGalleryState(
                        title = "لا توجد نتائج",
                        message = "جرّب كلمة بحث مختلفة.",
                        actionLabel = "مسح البحث",
                        onAction = { searchQuery = "" }
                    )
                }
            } else {
                items(filteredWallpapers, key = { it.id }) { item ->
                    WallpaperCard(
                        item = item,
                        isFavorite = item.id in favoriteIds,
                        onClick = { onWallpaperClick(item, filteredWallpapers) },
                        onFavoriteClick = {
                            val shouldFavorite = item.id !in favoriteIds
                            scope.launch {
                                repository.setFavorite(item, shouldFavorite).onFailure {
                                    snackbarHostState.showSnackbar(
                                        it.localizedMessage ?: "تعذر تحديث المفضلة"
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyGalleryState(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Default.ImageSearch,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
fun WallpaperCard(
    item: WallpaperItem,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth().testTag("wallpaper_item_card"),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clickable(onClick = onClick)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopEnd)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.48f))
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                        tint = if (isFavorite) Color(0xFFFF5577) else Color.White
                    )
                }

                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                    color = Color.Black.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        item.sourcePlatform,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    "بواسطة ${item.author}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
