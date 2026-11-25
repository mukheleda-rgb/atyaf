package com.example.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class WallpaperItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val category: String = "غير مصنف",
    val imageUrl: String = "",
    val sourcePlatform: String = "غير محدد",
    val author: String = "غير معروف",
    val isFavorite: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class AutoChangeSettings(
    val intervalHours: Int = 3,
    val enabled: Boolean = false,
    val wifiOnly: Boolean = true,
    val category: String = "الكل"
)
