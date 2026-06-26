package com.example.diary.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Insert
    suspend fun insert(entry: DiaryEntry): Long

    @Update
    suspend fun update(entry: DiaryEntry)

    @Delete
    suspend fun delete(entry: DiaryEntry)

    @Query("SELECT * FROM diary_entries ORDER BY date DESC")
    fun getAll(): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE id = :id")
    suspend fun getById(id: Long): DiaryEntry?

    @Query("SELECT * FROM diary_entries WHERE isHidden = 0 ORDER BY date DESC")
    fun getVisible(): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE isHidden = 1 ORDER BY date DESC")
    fun getHidden(): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE (title LIKE '%' || :keyword || '%' OR content LIKE '%' || :keyword || '%') AND isHidden = :includeHidden ORDER BY date DESC")
    fun searchByKeyword(keyword: String, includeHidden: Boolean): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE date BETWEEN :startDate AND :endDate AND isHidden = :includeHidden ORDER BY date DESC")
    fun searchByDateRange(startDate: Long, endDate: Long, includeHidden: Boolean): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getAllInRange(startDate: Long, endDate: Long): List<DiaryEntry>

    @Query("SELECT * FROM diary_entries WHERE category = :category AND isHidden = :includeHidden ORDER BY date DESC")
    fun getByCategory(category: String, includeHidden: Boolean): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE reminderType != 'none' AND reminderTime > 0")
    suspend fun getEntriesWithReminders(): List<DiaryEntry>
}
