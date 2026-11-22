package com.jia.academy.app.learning

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineContentChecksumTest {
    @Test fun usesCanonicalSha256HexForUtf8Content() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            OfflineContentChecksum.sha256("abc"))
    }

    @Test fun contentChangesProduceDifferentChecksums() {
        assertNotEquals(OfflineContentChecksum.sha256("lesson revision 1"),
            OfflineContentChecksum.sha256("lesson revision 2"))
    }

    private fun assertNotEquals(expected: String, actual: String) {
        org.junit.Assert.assertNotEquals(expected, actual)
    }
}
