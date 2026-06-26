package com.example.diary.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diary_entries")
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val date: Long = System.currentTimeMillis(),
    val imagePaths: String = "",
    val isHidden: Boolean = false,
    val category: String = "",
    val reminderType: String = "none",
    val reminderTime: Long = 0,
    val reminderInterval: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
