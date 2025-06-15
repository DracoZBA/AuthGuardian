package com.example.authguardian.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class MeltdownEvent(
    @DocumentId val id: String = "",
    val childId: String = "",
    val startTime: Timestamp = Timestamp.now(),
    val endTime: Timestamp? = null, // Can be null if event is ongoing
    val durationSeconds: Long = 0L, // <--- ADD THIS FIELD
    val intensity: Int = 0, // e.g., 1-5 or other scale
    val notes: String? = null
)