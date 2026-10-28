package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.InvoiceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices ORDER BY createdAt DESC")
    fun getAllInvoices(): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE id = :id LIMIT 1")
    suspend fun getInvoiceById(id: Long): InvoiceEntity?

    @Query("""
        SELECT * FROM invoices 
        WHERE supplierName LIKE '%' || :query || '%' 
           OR supplierVatNumber LIKE '%' || :query || '%'
           OR customerName LIKE '%' || :query || '%'
           OR invoiceNumber LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
    """)
    fun searchInvoices(query: String): Flow<List<InvoiceEntity>>

    @Query("SELECT COUNT(*) FROM invoices")
    fun getInvoiceCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(vatAmount), 0.0) FROM invoices")
    fun getTotalVat(): Flow<Double>

    @Query("SELECT COALESCE(SUM(totalWithTax), 0.0) FROM invoices")
    fun getTotalAmount(): Flow<Double>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: InvoiceEntity): Long

    @Update
    suspend fun updateInvoice(invoice: InvoiceEntity)

    @Delete
    suspend fun deleteInvoice(invoice: InvoiceEntity)

    @Query("DELETE FROM invoices WHERE id = :id")
    suspend fun deleteInvoiceById(id: Long)
}
