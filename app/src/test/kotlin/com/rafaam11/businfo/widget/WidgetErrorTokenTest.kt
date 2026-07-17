package com.rafaam11.businfo.widget

import com.rafaam11.businfo.domain.BusDataError
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetErrorTokenTest {
    @Test fun `writes stable explicit tokens`() {
        assertEquals(
            listOf(
                "invalid_credential",
                "rate_limited",
                "network_unavailable",
                "service_unavailable",
                "malformed_response",
            ),
            listOf(
                BusDataError.InvalidCredential,
                BusDataError.RateLimited,
                BusDataError.NetworkUnavailable,
                BusDataError.ServiceUnavailable,
                BusDataError.MalformedResponse,
            ).map(WidgetErrorToken::serialize),
        )
    }

    @Test fun `parses new tokens and legacy simple names`() {
        assertEquals(BusDataError.InvalidCredential, WidgetErrorToken.parse("invalid_credential"))
        assertEquals(BusDataError.InvalidCredential, WidgetErrorToken.parse("InvalidCredential"))
    }
}
