package com.sadad.ye.data.local

import androidx.room.*
import com.sadad.ye.models.Customer
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE userId = :userId")
    fun getCustomers(userId: String): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE customerId = :customerId LIMIT 1")
    suspend fun getCustomerById(customerId: String): Customer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer)

    @Update
    suspend fun updateCustomer(customer: Customer)

    @Delete
    suspend fun deleteCustomer(customer: Customer)

    @Query("SELECT * FROM customers WHERE isSynced = 0")
    suspend fun getUnsyncedCustomers(): List<Customer>

    @Query("UPDATE customers SET isSynced = 1 WHERE customerId IN (:ids)")
    suspend fun markAsSynced(ids: List<String>)
}
