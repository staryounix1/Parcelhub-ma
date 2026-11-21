package com.example

import com.example.data.carriers.StatusMapper
import com.example.data.carriers.WebhookSignatureVerifier
import com.example.domain.model.UnifiedParcelStatus
import com.example.domain.util.MoroccanPhoneNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParcelHubCoreUnitTest {

    @Test
    fun `normalizes moroccan 06 and 07 numbers to +212 E164`() {
        assertEquals("+212661482910", MoroccanPhoneNormalizer.normalize("06 61 48 29 10"))
        assertEquals("+212708193455", MoroccanPhoneNormalizer.normalize("0708-193-455"))
        assertEquals("+212612334455", MoroccanPhoneNormalizer.normalize("00212612334455"))
        assertEquals("+212655901234", MoroccanPhoneNormalizer.normalize("+2120655901234"))
        assertTrue(MoroccanPhoneNormalizer.isValidMoroccanMobile("0661482910"))
        assertFalse(MoroccanPhoneNormalizer.isValidMoroccanMobile("0522112233"))
    }

    @Test
    fun `status mapper converts carrier statuses and prevents out-of-order regression`() {
        assertEquals(UnifiedParcelStatus.DELIVERED, StatusMapper.map("LIVRÉ"))
        assertEquals(UnifiedParcelStatus.OUT_FOR_DELIVERY, StatusMapper.map("en_cours"))
        assertEquals(UnifiedParcelStatus.RETURNED, StatusMapper.map("RETOUR"))

        // Delivered is terminal: stale PICKED_UP webhook must be rejected
        assertFalse(
            StatusMapper.shouldApplyTransition(
                currentStatus = UnifiedParcelStatus.DELIVERED,
                incomingStatus = UnifiedParcelStatus.PICKED_UP,
                currentUpdatedMillis = 2000L,
                incomingEventMillis = 1000L
            )
        )

        // Forward transition should succeed
        assertTrue(
            StatusMapper.shouldApplyTransition(
                currentStatus = UnifiedParcelStatus.PICKED_UP,
                incomingStatus = UnifiedParcelStatus.OUT_FOR_DELIVERY,
                currentUpdatedMillis = 1000L,
                incomingEventMillis = 2000L
            )
        )
    }

    @Test
    fun `webhook hmac sha256 signature verification succeeds and fails accurately`() {
        val secret = "whsec_parcelhub_ma"
        val payload = "OZN-849201|LIVRE|evt_1"
        val sig = WebhookSignatureVerifier.computeHmacSha256(secret, payload)

        assertTrue(WebhookSignatureVerifier.verifySignature(secret, payload, sig))
        assertFalse(WebhookSignatureVerifier.verifySignature(secret, payload, "tampered_sig"))
    }
}
