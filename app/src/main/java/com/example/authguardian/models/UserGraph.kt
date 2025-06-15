package com.example.authguardian.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class UserGraph(
    @DocumentId val id: String = "",
    val userId: String = "", // The ID of the guardian who saved this graph
    val childId: String = "", // The ID of the child this graph is about
    val title: String = "",
    val startDate: Timestamp = Timestamp.now(),
    val endDate: Timestamp = Timestamp.now(),
    val imageUrl: String? = null, // URL to the saved image of the graph (in Firebase Storage)
    val generatedAt: Timestamp = Timestamp.now()
)