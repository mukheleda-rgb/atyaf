package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.workers.AutoWallpaperScheduler

private data class IntervalOption(val minutes: Long, val label: String)
private data class ImageSourceOption(val id: String, val title: String, val detail: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onSignOut: () -> Unit = {}) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("atyaf_preferences", Context.MODE_PRIVATE) }

    val sources = listOf(
        ImageSourceOption("openverse", "Openverse", "صور مرخصة من مصادر متعددة"),
        ImageSourceOption("wikimedia", "Wikimedia Commons", "صور ووسائط حرة مع بيانات الترخيص"),
        ImageSourceOption("picsum", "Picsum Photos", "صور فوتوغرافية عشوائية"),
        ImageSourceOption("nasa", "NASA Images", "الفضاء والعلوم"),
        ImageSourceOption("met", "The Met Museum", "أعمال فنية ضمن الملكية العامة"),
        ImageSourceOption("artic", "Art Institute of Chicago", "مجموعة فنية مفتوحة"),
        ImageSourceOption("cleveland", "Cleveland Museum of Art", "أعمال CC0 مفتوحة"),
        ImageSourceOption("loc", "Library of Congress", "صور تاريخية ووثائق"),
        ImageSourceOption("custom", "خادم صور مخصص", "واجهة موحدة لمصادر إضافية")
    )
    val categories = listOf("الكل", "طبيعة", "فضاء", "عمارة", "حيوانات", "فن", "بحر", "سيارات", "تقنية")
    var selectedSources by remember {
        mutableStateOf(preferences.getStringSet("selected_image_sources", sources.map { it.id }.toSet())?.toSet() ?: sources.map { it.id }.toSet())
    }
    var selectedCategories by remember {
        mutableStateOf(preferences.getStringSet("selected_image_categories", setOf("الكل"))?.toSet() ?: setOf("الكل"))
    }
    var customServer by remember { mutableStateOf(preferences.getString("custom_image_server", "") ?: "") }
    var autoEnabled by remember { mutableStateOf(preferences.getBoolean("auto_wallpaper_enabled", false)) }
    var wifiOnly by remember { mutableStateOf(preferences.getBoolean("wifi_only", true)) }
    var intervalMinutes by remember { mutableLongStateOf(preferences.getLong("auto_wallpaper_interval", 180L)) }

    val intervals = listOf(
        IntervalOption(30, "30 دقيقة"), IntervalOption(60, "ساعة"),
        IntervalOption(180, "3 ساعات"), IntervalOption(360, "6 ساعات"),
        IntervalOption(720, "12 ساعة"), IntervalOption(1440, "24 ساعة")
    )

    fun saveImagePreferences(showToast: Boolean = false) {
        preferences.edit()
            .putStringSet("selected_image_sources", selectedSources)
            .putStringSet("selected_image_categories", selectedCategories)
            .putString("custom_image_server", customServer.trim().trimEnd('/'))
            .apply()
        if (showToast) Toast.makeText(context, "تم حفظ إعدادات مصادر الصور", Toast.LENGTH_SHORT).show()
    }

    fun persistAndSchedule(enabled: Boolean, interval: Long, wifi: Boolean) {
        preferences.edit().putBoolean("auto_wallpaper_enabled", enabled)
            .putLong("auto_wallpaper_interval", interval).putBoolean("wifi_only", wifi).apply()
        if (enabled) AutoWallpaperScheduler.schedule(context, interval, wifi)
        else AutoWallpaperScheduler.cancel(context)
    }

    Scaffold(topBar = {
        TopAppBar(title = {
            Text("الإعدادات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        })
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("مصادر الصور والخوادم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("اختر مصدراً واحداً أو عدة مصادر لعرض الصور منها.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    sources.forEach { source ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = source.id in selectedSources,
                                onCheckedChange = { checked ->
                                    selectedSources = if (checked) selectedSources + source.id else selectedSources - source.id
                                    if (selectedSources.isEmpty()) selectedSources = setOf("openverse")
                                    saveImagePreferences()
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(source.title, fontWeight = FontWeight.SemiBold)
                                Text(source.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    HorizontalDivider()
                    Text("عنوان خادم الصور المخصص", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = customServer,
                        onValueChange = { customServer = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("https://example.com/api") },
                        supportingText = { Text("يجب أن يوفر GET /search?q=...&limit=... ويعيد results مع imageUrl وtitle وid.") }
                    )
                    Button(onClick = { saveImagePreferences(true) }, modifier = Modifier.fillMaxWidth()) {
                        Text("حفظ مصادر الصور")
                    }
                    Text("يمكن إضافة خدمات مثل Unsplash وPexels وPixabay عبر خادم وسيط يضيف مفاتيح API بأمان؛ لا تضع المفاتيح السرية داخل التطبيق.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("فئات الصور", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("يمكن تحديد أكثر من فئة. اختيار «الكل» يعرض جميع الفئات.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        categories.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { category ->
                                    val selected = category in selectedCategories
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            selectedCategories = when (category) {
                                                "الكل" -> setOf("الكل")
                                                else -> (selectedCategories - "الكل").let { current ->
                                                    if (category in current) (current - category).ifEmpty { setOf("الكل") }
                                                    else current + category
                                                }
                                            }
                                            saveImagePreferences()
                                        },
                                        label = { Text(category) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Autorenew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text("تغيير الخلفية تلقائياً", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Switch(checked = autoEnabled, onCheckedChange = {
                            autoEnabled = it
                            persistAndSchedule(it, intervalMinutes, wifiOnly)
                        })
                    }
                    Text("تُحدّث أطياف خلفية الهاتف وفق الجدول الذي تختاره.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("الفاصل الزمني", fontWeight = FontWeight.SemiBold)
                    }
                    intervals.chunked(3).forEach { rowOptions ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowOptions.forEach { option ->
                                FilterChip(
                                    selected = intervalMinutes == option.minutes,
                                    onClick = { intervalMinutes = option.minutes; persistAndSchedule(autoEnabled, option.minutes, wifiOnly) },
                                    label = { Text(option.label) }, enabled = autoEnabled, modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Wi-Fi فقط", fontWeight = FontWeight.SemiBold)
                            Text("يمنع التنزيل عبر بيانات الهاتف.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it; persistAndSchedule(autoEnabled, intervalMinutes, it) }, enabled = autoEnabled)
                    }
                }
            }
            Text("تتطلب مصادر الصور اتصالاً بالإنترنت. تختلف التراخيص وحدود الطلبات حسب المنصة؛ راجع ترخيص الصورة قبل إعادة استخدامها خارج التطبيق.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
