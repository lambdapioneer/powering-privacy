package uk.ac.cam.energy.common.operations

import android.content.Context
import uk.ac.cam.energy.common.getWithDefault
import uk.cam.energy.arkworkskt.ArkworksKt
import java.lang.IllegalStateException

class SnarkVerifyOperation(
    context: Context,
    name: String,
    pause: Pause,
    args: Map<String, String>
) : Operation(context, name, pause) {
    private val iterations = args.getWithDefault("iterations", "1").toInt()
    private val numConstraints = args.getWithDefault("num_constraints", "10").toInt()
    private val numVariables = args.getWithDefault("num_variables", "10").toInt()

    private var ptrPostSetup: Long? = null
    private var ptrPostProof: Long? = null

    override fun before(): Boolean {
        ArkworksKt.init()
        ptrPostSetup = ArkworksKt.benchSetup(
            numConstraints = numConstraints,
            numVariables = numVariables,
        )
        ptrPostProof = ArkworksKt.benchProve(ptrPostSetup!!)
        return true
    }

    override fun run() {
        for (i in 0..iterations) {
            val verified = ArkworksKt.benchVerify(ptrPostProof!!)
            check(verified == 1) { "$i-th Verification failed ($verified), but should always succeed" }
        }
    }

    override fun after() {
        ArkworksKt.benchFreePostProof(ptrPostProof!!)
        ptrPostProof = null
        ArkworksKt.benchFreePostSetup(ptrPostSetup!!)
        ptrPostSetup = null
    }

    override fun debug(): String {
        return "iterations=$iterations&num_constraints=$numConstraints&num_variables=$numVariables"
    }
}


class SnarkProofOperation(
    context: Context,
    name: String,
    pause: Pause,
    args: Map<String, String>
) : Operation(context, name, pause) {
    private val iterations = args.getWithDefault("iterations", "1").toInt()
    private val numConstraints = args.getWithDefault("num_constraints", "10").toInt()
    private val numVariables = args.getWithDefault("num_variables", "1").toInt()

    private var ptrPostSetup: Long? = null

    override fun before(): Boolean {
        ArkworksKt.init()
        ptrPostSetup = ArkworksKt.benchSetup(
            numConstraints = numConstraints,
            numVariables = numVariables,
        )
        return true
    }

    override fun run() {
        for (i in 0..iterations) {
            val ptrPostProof = ArkworksKt.benchProve(ptrPostSetup!!)
            ArkworksKt.benchFreePostProof(ptrPostProof)
        }
    }

    override fun after() {
        ArkworksKt.benchFreePostSetup(ptrPostSetup!!)
        ptrPostSetup = null
    }

    override fun debug(): String {
        return "iterations=$iterations&num_constraints=$numConstraints&num_variables=$numVariables"
    }
}
