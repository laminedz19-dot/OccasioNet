package dz.ocasionet.admin

import dz.ocasionet.admin.data.AdminPaymentQueue
import dz.ocasionet.core.model.PaymentRequestItem
import org.junit.Assert.assertEquals
import org.junit.Test

class AdminPaymentQueueIntegrationTest {
    private val requests = listOf(
        request("old-ccp", "user-a", "CCP-REF-001", "ccp", "2026-10-01T08:00:00Z"),
        request("new-bank", "user-b", "BM-REF-002", "baridimob", "2026-10-03T08:00:00Z"),
        request("mid-ccp", "user-c", "CCP-REF-003", "ccp", "2026-10-02T08:00:00Z")
    )

    @Test
    fun `queue prioritizes oldest request by default`() {
        assertEquals(listOf("old-ccp", "mid-ccp", "new-bank"), AdminPaymentQueue.filterAndSort(requests).map { it.id })
    }

    @Test
    fun `queue search matches transaction reference and payment method`() {
        val results = AdminPaymentQueue.filterAndSort(requests, query = "ref-003", paymentMethod = "ccp")
        assertEquals(listOf("mid-ccp"), results.map { it.id })
    }

    @Test
    fun `queue supports newest-first ordering`() {
        assertEquals(
            listOf("new-bank", "mid-ccp", "old-ccp"),
            AdminPaymentQueue.filterAndSort(requests, oldestFirst = false).map { it.id }
        )
    }

    @Test
    fun `waiting label reports elapsed hours and minutes`() {
        assertEquals(
            "بانتظار المراجعة منذ 1 ساعة و30 دقيقة",
            AdminPaymentQueue.waitingLabel("1970-01-01T00:00:00Z", nowMillis = 5_400_000L)
        )
    }

    @Test
    fun `waiting label handles invalid timestamps without crashing`() {
        assertEquals("مدة الانتظار غير متاحة", AdminPaymentQueue.waitingLabel("not-a-date", nowMillis = 10_000L))
    }

    private fun request(id: String, userId: String, reference: String, method: String, createdAt: String) =
        PaymentRequestItem(
            id = id,
            userId = userId,
            amountDzd = 500,
            paymentMethod = method,
            receiptStoragePath = "$userId/receipt.jpg",
            receiptMimeType = "image/jpeg",
            receiptSizeBytes = 120_000,
            transactionReference = reference,
            createdAt = createdAt
        )
}
