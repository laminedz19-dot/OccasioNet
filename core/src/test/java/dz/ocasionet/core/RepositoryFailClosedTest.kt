package dz.ocasionet.core

import dz.ocasionet.core.repository.OccasioNetRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryFailClosedTest {
    private fun repositoryWithoutBackend() = OccasioNetRepository(serviceProvider = { null })

    @Test
    fun `signup without Supabase does not create a local authenticated user`() = runBlocking {
        val repository = repositoryWithoutBackend()
        val result = repository.signUpUser(
            email = "user@example.test",
            password = "test-password",
            fullName = "Test User",
            phone = "0550000000"
        )
        assertTrue(result.isFailure)
        assertTrue(repository.currentUser.value == null)
    }

    @Test
    fun `signin without Supabase does not create a local authenticated user`() = runBlocking {
        val repository = repositoryWithoutBackend()
        val result = repository.signInUser("user@example.test", "incorrect-password")
        assertTrue(result.isFailure)
        assertTrue(repository.currentUser.value == null)
    }

    @Test
    fun `receipt submission without an authenticated user cannot create a payment request`() = runBlocking {
        val repository = repositoryWithoutBackend()
        val result = repository.uploadReceiptAndSubmitPaymentRequest(
            paymentMethod = "ccp",
            fileName = "receipt.jpg",
            mimeType = "image/jpeg",
            fileBytes = byteArrayOf(1, 2, 3),
            transactionReference = "",
            userNote = ""
        )
        assertTrue(result.isFailure)
        assertTrue(repository.myPaymentRequests.value.isEmpty())
    }

    @Test
    fun `listing publication without an authenticated user does not create a local listing`() = runBlocking {
        val repository = repositoryWithoutBackend()
        val result = repository.publishListingWithApprovedReceipt(
            paymentRequestId = "not-a-real-payment",
            categoryId = 1,
            wilayaCode = 16,
            communeId = 1601,
            title = "A test listing",
            description = "This is only a test listing description.",
            priceDzd = 1000,
            condition = "good",
            contactPhone = "0550000000"
        )
        assertTrue(result.isFailure)
        assertTrue(repository.myListings.value.isEmpty())
    }
}
