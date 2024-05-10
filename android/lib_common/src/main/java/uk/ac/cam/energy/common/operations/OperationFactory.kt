package uk.ac.cam.energy.common.operations

import android.content.Context

object OperationFactory {

    private val operations = HashMap<String, (Context, String, Pause, Map<String, String>) -> Operation>()

    init {
        register("idle", ::IdleOperation)

        register("crypto-keygen", ::CryptoKeyGenOperation)
        register("crypto-sign", ::CryptoSignOperation)
        register("crypto-verify", ::CryptoVerifyOperation)
        register("crypto-hash", ::CryptoHashAlgorithmOperation)
        register("crypto-sphinx", ::CryptoSphinxOperation)

        register("network-single", ::NetworkSingleSocketOperation)
        register("network-multi", ::NetworkContinuousSocketOperation)

        register("loopix", ::LoopixOperation)

        register("snark-prove", ::SnarkProofOperation)
        register("snark-verify", ::SnarkVerifyOperation)
    }

    private fun register(
        name: String,
        factory: (Context, String, Pause, Map<String, String>) -> Operation
    ) {
        operations[name] = factory
    }

    fun create(
        name: String,
        context: Context,
        identifier: String,
        pause: Pause,
        args: Map<String, String> = emptyMap()
    ) = operations[name]!!(context, identifier, pause, args)
}
