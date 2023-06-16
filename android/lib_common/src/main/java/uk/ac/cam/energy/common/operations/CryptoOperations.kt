package uk.ac.cam.energy.common.operations

import android.content.Context
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import uk.ac.cam.energy.common.*
import uk.cam.energy.sphinxkt.SphinxKt
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec


//
// Asymmetric Algorithms
//

data class AsymmetricAlgorithm(
    val identifier: String,
    val keyGeneratorAlgorithmName: String,
    val keySpec: String,
    val signatureAlgorithmName: String
)

object AsymmetricAlgorithms {
    val all = arrayOf(
        AsymmetricAlgorithm("rsa1024", "RSA", "1024", "SHA256withRSA"),
        AsymmetricAlgorithm("rsa2048", "RSA", "2048", "SHA256withRSA"),
        AsymmetricAlgorithm("rsa4096", "RSA", "4096", "SHA256withRSA"),
        AsymmetricAlgorithm("dsa1024", "DSA", "1024", "SHA256withDSA"),
        AsymmetricAlgorithm("dsa2048", "DSA", "2048", "SHA256withDSA"),
        AsymmetricAlgorithm("dsa3072", "DSA", "3072", "SHA256withDSA"),
        AsymmetricAlgorithm("ecdsa224", "EC", "secp224r1", "SHA256withECDSA"),
        AsymmetricAlgorithm("ecdsa256", "EC", "secp256r1", "SHA256withECDSA"),
        AsymmetricAlgorithm("ecdsa384", "EC", "secp384r1", "SHA256withECDSA"),
        AsymmetricAlgorithm("ecdsa521", "EC", "secp521r1", "SHA256withECDSA")
    )
    val identifiers = all.map { it.identifier }.toTypedArray()

    fun get(identifier: String): AsymmetricAlgorithm {
        return all.find { it.identifier == identifier }!!
    }
}

abstract class CryptoAsymmetricOperation(
    context: Context,
    name: String,
    pause: Pause,
    args: Map<String, String>,
) : Operation(context, name, pause) {

    private val algorithmIdentifier = args.getWithList("alg", AsymmetricAlgorithms.identifiers)
    protected val algorithm = AsymmetricAlgorithms.get(algorithmIdentifier)

    private val contentLength = args.getWithDefault("content_length_bytes", "1024").toInt()
    protected val content = randomArray(contentLength)

    protected lateinit var keyPair: KeyPair

    override fun before(): Boolean {
        genKeyPair()
        return true
    }

    protected fun genKeyPair() {
        val keyPairGenerator = KeyPairGenerator.getInstance(algorithm.keyGeneratorAlgorithmName)
        when (algorithm.keyGeneratorAlgorithmName) {
            "RSA" -> keyPairGenerator.initialize(algorithm.keySpec.toInt())
            "DSA" -> keyPairGenerator.initialize(algorithm.keySpec.toInt())
            "EC" -> keyPairGenerator.initialize(ECGenParameterSpec(algorithm.keySpec))
            else -> throw IllegalArgumentException("Unknown keyGeneratorAlgorithmName ${algorithm.keyGeneratorAlgorithmName}")
        }
        keyPair = keyPairGenerator.genKeyPair()
    }

    override fun debug() = "alg=$algorithmIdentifier" +
            "&content_length_bytes=$contentLength"
}

class CryptoKeyGenOperation(
    context: Context,
    name: String,
    pause: Pause,
    args: Map<String, String>
) : CryptoAsymmetricOperation(context, name, pause, args) {

    override fun before(): Boolean {
        // genKeyPair() moved to run()
        return false
    }

    override fun run() {
        genKeyPair()
    }
}

class CryptoSignOperation(
    context: Context,
    name: String,
    pause: Pause,
    args: Map<String, String>
) : CryptoAsymmetricOperation(context, name, pause, args) {

    override fun run() {
        val signer = Signature.getInstance(algorithm.signatureAlgorithmName)
        signer.initSign(keyPair.private)
        signer.update(content)
        signer.sign()
    }
}

class CryptoVerifyOperation(
    context: Context,
    name: String,
    pause: Pause,
    args: Map<String, String>
) : CryptoAsymmetricOperation(context, name, pause, args) {

    lateinit var signature: ByteArray

    override fun before(): Boolean {
        super.before()
        genSignature()
        return true
    }

    private fun genSignature() {
        val signer = Signature.getInstance(algorithm.signatureAlgorithmName)
        signer.initSign(keyPair.private)
        signer.update(content)
        signature = signer.sign()
    }

    override fun run() {
        val verifier = Signature.getInstance(algorithm.signatureAlgorithmName)
        verifier.initVerify(keyPair.public)
        verifier.update(content)

        val signatureVerified = verifier.verify(signature)
        if (!signatureVerified) {
            throw RuntimeException("Wrong signature")
        }

    }
}

//
// Hash Algorithms
//

data class HashAlgorithm(
    val identifier: String,
)

object HashAlgorithms {
    val all = arrayOf(
        HashAlgorithm("sha256"),
        HashAlgorithm("sha512"),
        HashAlgorithm("pbkdf2"),
        HashAlgorithm("argon2"),
    )
    val identifiers = all.map { it.identifier }.toTypedArray()

    fun get(identifier: String): HashAlgorithm {
        return all.find { it.identifier == identifier }!!
    }
}

data class Argon2ModeEntry(
    val identifier: String,
    val mode: Argon2Mode
)

object Argon2Modes {
    val all = arrayOf(
        Argon2ModeEntry("d", Argon2Mode.ARGON2_D),
        Argon2ModeEntry("i", Argon2Mode.ARGON2_I),
        Argon2ModeEntry("id", Argon2Mode.ARGON2_ID),
    )
    val identifiers = all.map { it.identifier }.toTypedArray()

    fun get(identifier: String): Argon2ModeEntry {
        return all.find { it.identifier == identifier }!!
    }
}

class CryptoHashAlgorithmOperation(
    context: Context,
    name: String,
    pause: Pause,
    args: Map<String, String>,
) : Operation(context, name, pause) {

    private val defaultPasswordAndKeyLength = 64

    private val algorithmIdentifier = args.getWithList("alg", HashAlgorithms.identifiers)
    private val algorithm = HashAlgorithms.get(algorithmIdentifier)

    private val contentLength = args.getWithDefault("content_length_bytes", "1024").toInt()
    private val content = randomArray(contentLength)

    private val password = randomArray(defaultPasswordAndKeyLength)
    private val passwordChar = randomPassword(defaultPasswordAndKeyLength)
    private val salt = randomArray(defaultPasswordAndKeyLength)

    private val pbkdf2Iterations = args.getWithDefault("pbkdf2_iterations", "4096").toInt()

    private var argon2KtInstance: Argon2Kt? = null
    private val argon2Parallelism = args.getWithDefault("argon2_parallelism", "1").toInt()
    private val argon2TCostKib = args.getWithDefault("argon2_tcost", "1").toInt()
    private val argon2MCostKib = args.getWithDefault("argon2_mcost_kib", "4096").toInt()
    private val argon2Mode = Argon2Modes.get(
        args.getWithDefaultAndList("argon2_mode", "id", Argon2Modes.identifiers)
    )

    override fun debug() = "alg=$algorithmIdentifier" +
            "&content_length_bytes=$contentLength" +
            "&pbkdf2_iterations=$pbkdf2Iterations" +
            "&argon2_parallelism=$argon2Parallelism" +
            "&argon2_tcost=$argon2TCostKib" +
            "&argon2_mcost_kib=$argon2MCostKib" +
            "&argon2_mode=${argon2Mode.identifier}"

    override fun before(): Boolean {
        if (algorithmIdentifier == "argon2") {
            argon2KtInstance = Argon2Kt()
            return true
        } else {
            return false
        }
    }

    override fun run() {
        when (algorithm.identifier) {
            "sha256" -> runMessageDigest("SHA256")
            "sha512" -> runMessageDigest("SHA512")
            "pbkdf2" -> runPbkdf2()
            "argon2" -> runArgon2()
        }
    }

    private fun runMessageDigest(messageDigestAlgName: String) {
        MessageDigest.getInstance(messageDigestAlgName).also { md ->
            md.reset()
            md.update(content)
            md.digest()
        }
    }

    private fun runPbkdf2() {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSha1")
        val spec = PBEKeySpec(
            passwordChar,
            salt,
            pbkdf2Iterations,
            defaultPasswordAndKeyLength
        )
        factory.generateSecret(spec)!!
    }

    private fun runArgon2() {
        argon2KtInstance!!.hash(
            mode = argon2Mode.mode,
            password = password,
            salt = salt,
            parallelism = argon2Parallelism,
            tCostInIterations = argon2TCostKib,
            mCostInKibibyte = argon2MCostKib
        ).rawHashAsByteArray()
    }
}

class CryptoSphinxOperation(
    context: Context,
    name: String,
    pause: Pause,
    args: Map<String, String>,
) : Operation(context, name, pause) {

    private val iterations = args.getWithDefault("iterations", "1").toInt()
    private var ptr: Long? = null

    override fun before(): Boolean {
        SphinxKt.init()
        ptr = SphinxKt.benchSetup()
        return true
    }

    override fun run() {
        SphinxKt.benchRun(ptr!!, iterations)
    }

    override fun after() {
        SphinxKt.benchFree(ptr!!)
        ptr = null
    }

    override fun debug(): String {
        return "iterations=$iterations"
    }
}
