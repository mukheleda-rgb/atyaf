package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.workers.AutoWallpaperScheduler
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

private data class IntervalOption(val minutes: Long, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onSignOut: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("atyaf_preferences", Context.MODE_PRIVATE)
    }
    val currentUser = Firebase.auth.currentUser

    var autoEnabled by remember { mutableStateOf(preferences.getBoolean("auto_wallpaper_enabled", false)) }
    var wifiOnly by remember { mutableStateOf(preferences.getBoolean("wifi_only", true)) }
    var intervalMinutes by remember { mutableLongStateOf(preferences.getLong("auto_wallpaper_interval", 180L)) }

    val intervals = listOf(
        IntervalOption(30, "30 دقيقة"),
        IntervalOption(60, "ساعة"),
        IntervalOption(180, "3 ساعات"),
        IntervalOption(360, "6 ساعات"),
        IntervalOption(720, "12 ساعة"),
        IntervalOption(1440, "24 ساعة")
    )

    fun persistAndSchedule(enabled: Boolean, interval: Long, wifi: Boolean) {
        preferences.edit()
            .putBoolean("auto_wallpaper_enabled", enabled)
            .putLong("auto_wallpaper_interval", interval)
            .putBoolean("wifi_only", wifi)
            .apply()

        if (enabled) {
            AutoWallpaperScheduler.schedule(context, interval, wifi)
        } else {
            AutoWallpaperScheduler.cancel(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "الإعدادات",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("حسابك", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        currentUser?.email ?: "لا يوجد حساب مسجل",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            Firebase.auth.signOut()
                            onSignOut()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("تسجيل الخروج")
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Autorenew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "تغيير الخلفية تلقائياً",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = autoEnabled,
                            onCheckedChange = { enabled ->
                                autoEnabled = enabled
                                persistAndSchedule(enabled, intervalMinutes, wifiOnly)
                                Toast.makeText(
                                    context,
                                    if (enabled) "تم تفعيل التغيير التلقائي" else "تم إيقاف التغيير التلقائي",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "تُحدّث أطياف خلفية الهاتف من الصور المتاحة عبر الإنترنت وفق الجدول الذي تختاره.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("الفاصل الزمني", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(10.dp))

                    intervals.chunked(3).forEach { rowOptions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowOptions.forEach { option ->
                                FilterChip(
                                    selected = intervalMinutes == option.minutes,
                                    onClick = {
                                        intervalMinutes = option.minutes
                                        persistAndSchedule(autoEnabled, option.minutes, wifiOnly)
                                    },
                                    label = { Text(option.label) },
                                    enabled = autoEnabled,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowOptions.size < 3) {
                                repeat(3 - rowOptions.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Wi-Fi فقط", fontWeight = FontWeight.SemiBold)
                            Text(
                                "يمنع تنزيل الصور عبر بيانات الهاتف.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = wifiOnly,
                            onCheckedChange = { enabled ->
                                wifiOnly = enabled
                                persistAndSchedule(autoEnabled, intervalMinutes, enabled)
                            },
                            enabled = autoEnabled
                        )
                    }
                }
            }

            Text(
                "ملاحظة: قد يؤخر Android تنفيذ التغيير التلقائي لتوفير البطارية. كما يتطلب اتصالاً بالإنترنت.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
