package fr.familia.app.core.place

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoFailureLogTest {
    @Test
    fun noPhotoAnswerIsNotReportedAsAFailure() {
        assertFalse(PhotoFailureLog.isReportable(noPhotoLine(withPoint = true)))
        assertFalse(PhotoFailureLog.isReportable(noPhotoLine(withPoint = false)))
    }

    @Test
    fun realRefusalsAreStillReported() {
        assertTrue(PhotoFailureLog.isReportable(photoRefusalLine(429, "fr.wikipedia.org")))
        assertTrue(PhotoFailureLog.isReportable("délai dépassé"))
    }

    @Test
    fun aPhoneWithoutNetworkIsNotReported() {
        assertFalse(PhotoFailureLog.isReportable("UnknownHostException"))
        assertFalse(PhotoFailureLog.isReportable("ConnectException"))
        assertFalse(PhotoFailureLog.isReportable("NoRouteToHostException"))
        assertFalse(PhotoFailureLog.isReportable("image UnknownHostException: Unable to resolve host"))
        assertTrue(PhotoFailureLog.isReportable("SSLHandshakeException"))
        assertTrue(PhotoFailureLog.isReportable("image HttpException: 403"))
    }
}
