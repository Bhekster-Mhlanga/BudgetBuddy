package com.budgetbuddy.app

data class User(
    val id: Int = 0,
    val username: String,
    val passwordHash: String
)

data class Category(
    val id: Int = 0,
    val name: String,
    val colour: String = "#4CAF50",
    val userId: Int
)

data class Expense(
    val id: Int = 0,
    val amount: Double,
    val description: String,
    val date: String,
    val categoryId: Int,
    val categoryName: String = "",
    val photoPath: String? = null,
    val photoBlob: ByteArray? = null,   // photo stored in SQLite as BLOB
    val userId: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Expense

        if (id != other.id) return false
        if (amount != other.amount) return false
        if (categoryId != other.categoryId) return false
        if (userId != other.userId) return false
        if (description != other.description) return false
        if (date != other.date) return false
        if (categoryName != other.categoryName) return false
        if (photoPath != other.photoPath) return false
        if (!(photoBlob contentEquals other.photoBlob)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + amount.hashCode()
        result = 31 * result + categoryId
        result = 31 * result + userId
        result = 31 * result + description.hashCode()
        result = 31 * result + date.hashCode()
        result = 31 * result + categoryName.hashCode()
        result = 31 * result + (photoPath?.hashCode() ?: 0)
        result = 31 * result + (photoBlob?.contentHashCode() ?: 0)
        return result
    }
}

data class Badge(
    val id: String,
    val name: String,
    val description: String,
    var earned: Boolean = false
)