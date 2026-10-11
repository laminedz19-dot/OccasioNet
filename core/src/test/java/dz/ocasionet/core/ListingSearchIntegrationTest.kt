package dz.ocasionet.core

import dz.ocasionet.core.model.ListingFilter
import dz.ocasionet.core.model.ListingItem
import dz.ocasionet.core.model.ListingSortOrder
import dz.ocasionet.core.repository.OccasioNetRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class ListingSearchIntegrationTest {
    private val repository = OccasioNetRepository(dataStoreRepository = null)
    private val listings = listOf(
        listing("older-cheap", "هاتف مستعمل", "هاتف بحالة جيدة", 16, 12_000, "2026-10-01T10:00:00Z"),
        listing("newer-cheap", "حاسوب محمول", "حاسوب سريع", 31, 12_000, "2026-10-03T10:00:00Z"),
        listing("newer-expensive", "هاتف جديد", "هاتف مع ضمان", 16, 48_000, "2026-10-04T10:00:00Z")
    )

    @Test
    fun `search filters by title and location then sorts matching results by price`() {
        val results = repository.filterListings(
            listings,
            ListingFilter(query = "هاتف", wilayaCode = 16, sortOrder = ListingSortOrder.PRICE_LOW_TO_HIGH)
        )
        assertEquals(listOf("older-cheap", "newer-expensive"), results.map { it.id })
    }

    @Test
    fun `price range filters results and newest sort wins`() {
        val results = repository.filterListings(
            listings,
            ListingFilter(minPriceDzd = 10_000, maxPriceDzd = 20_000, sortOrder = ListingSortOrder.NEWEST)
        )
        assertEquals(listOf("newer-cheap", "older-cheap"), results.map { it.id })
    }

    @Test
    fun `descending price sort remains deterministic for matching prices`() {
        val results = repository.filterListings(
            listings,
            ListingFilter(sortOrder = ListingSortOrder.PRICE_HIGH_TO_LOW)
        )
        assertEquals(listOf("newer-expensive", "newer-cheap", "older-cheap"), results.map { it.id })
    }

    private fun listing(
        id: String,
        title: String,
        description: String,
        wilayaCode: Int,
        price: Long,
        createdAt: String
    ) = ListingItem(
        id = id,
        sellerId = "seller-$id",
        paymentRequestId = "payment-$id",
        categoryId = 1,
        wilayaCode = wilayaCode,
        communeId = wilayaCode * 100 + 1,
        title = title,
        description = description,
        priceDzd = price,
        condition = "good",
        createdAt = createdAt
    )
}
