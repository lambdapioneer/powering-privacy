package uk.ac.cam.energy.common.operations

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test

class TestCryptoOperations {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    val fastCryptoAlgorithmIdentifiers = listOf(
        "rsa1024", "rsa2048", // skipping slow "rsa4096"
        "dsa1024", "dsa2048", // skipping slow "dsa3072"
        "ecdsa224", "ecdsa256", "ecdsa384", "ecdsa521",
    )

    @Test
    fun testKeyGenOperation_whenExecutingFastOnes_thenNoneThrows() {
        for (algorithmIdentifier in fastCryptoAlgorithmIdentifiers) {
            Log.i("TestCryptoOperations", "algorithmIdentifier=$algorithmIdentifier")

            val operation = CryptoKeyGenOperation(
                context = context,
                name = "test",
                pause = TEST_PAUSE,
                args = mapOf(Pair("alg", algorithmIdentifier))
            )
            operation.runCompletely()
        }
    }

    @Test
    fun testSignOperation_whenExecutingFastOnes_thenNoneThrows() {
        for (algorithmIdentifier in fastCryptoAlgorithmIdentifiers) {
            Log.i("TestCryptoOperations", "algorithmIdentifier=$algorithmIdentifier")

            val operation = CryptoSignOperation(
                context = context,
                name = "test",
                pause = TEST_PAUSE,
                args = mapOf(Pair("alg", algorithmIdentifier))
            )
            operation.runCompletely()
        }
    }

    @Test
    fun testVerifyOperation_whenExecutingFastOnes_thenNoneThrows() {
        for (algorithmIdentifier in fastCryptoAlgorithmIdentifiers) {
            Log.i("TestCryptoOperations", "algorithmIdentifier=$algorithmIdentifier")

            val operation = CryptoVerifyOperation(
                context = context,
                name = "test",
                pause = TEST_PAUSE,
                args = mapOf(Pair("alg", algorithmIdentifier))
            )
            operation.runCompletely()
        }
    }

    @Test
    fun testVerifyOperation_whenSha256or512_thenDoesNotThrow() {
        for (algorithmIdentifier in listOf("sha256", "sha512")) {
            Log.i("TestCryptoOperations", "algorithmIdentifier=$algorithmIdentifier")

            val operation = CryptoHashAlgorithmOperation(
                context = context,
                name = "test",
                pause = TEST_PAUSE,
                args = mapOf(Pair("alg", algorithmIdentifier))
            )
            operation.runCompletely()
        }
    }

    @Test
    fun testVerifyOperation_whenPbkdf2_thenDoesNotThrow() {
        for (iterations in listOf(1024, 2048, 4096)) {
            Log.i("TestCryptoOperations", "iterations=$iterations")

            val operation = CryptoHashAlgorithmOperation(
                context = context,
                name = "test",
                pause = TEST_PAUSE,
                args = mapOf(Pair("alg", "pbkdf2"), Pair("pbkdf2_iterations", "$iterations"))
            )
            operation.runCompletely()
        }
    }

    @Test
    fun testVerifyOperation_whenArgon2_thenDoesNotThrow() {
        for (p in listOf(1, 4)) {
            for (tcost in listOf(1, 2)) {
                for (mcost in listOf(1024, 2048)) {
                    for (mode in listOf("i", "d", "id")) {
                        Log.i("TestCryptoOperations", "p=$p,tcost=$tcost,mcost=$mcost,mode=$mode")

                        val operation = CryptoHashAlgorithmOperation(
                            context = context,
                            name = "test",
                            pause = TEST_PAUSE,
                            args = mapOf(
                                Pair("alg", "argon2"),
                                Pair("argon2_parallelism", "$p"),
                                Pair("argon2_tcost", "$tcost"),
                                Pair("argon2_mcost_kib", "$mcost"),
                                Pair("argon2_mode", mode),
                            )
                        )
                        operation.runCompletely()
                    }
                }
            }
        }
    }

    @Test
    fun testSphinxOperation_whenRunningMultipleTimes_thenDoesNotThrow() {
        for (iterations in listOf(1, 256)) {
            val operation = CryptoSphinxOperation(
                context = context,
                name = "test",
                pause = TEST_PAUSE,
                args = mapOf(Pair("iterations", "$iterations"))
            )
            operation.runCompletely()
        }
    }

}
