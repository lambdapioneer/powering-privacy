package uk.cam.energy.arkworkskt

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class ArkworksKtTest {

    @Before
    fun setup() {
        ArkworksKt.init()
    }

    @Test
    fun init_whenInitCalled_thenNothingThrows() {
        ArkworksKt.init()
    }

    @Test
    fun bench_whenSmokeTestSetupSmallWithIterations_thenNothingThrows() {
        val ptrPostSetup = ArkworksKt.benchSetup(numConstraints = 10, numVariables = 10)
        try {
            for (proveIter in 0..3) {
                val ptrPostProof = ArkworksKt.benchProve(ptrPostSetup)
                try {
                    for (verifyIter in 0..3) {
                        val verified = ArkworksKt.benchVerify(ptrPostProof)
                        assertThat(verified).isEqualTo(1) // 1 -> true
                    }
                } finally {
                    ArkworksKt.benchFreePostProof(ptrPostProof)
                }
            }
        } finally {
            ArkworksKt.benchFreePostSetup(ptrPostSetup)
        }
    }

    @Test
    fun bench_whenSmokeTestSetupLarge_thenNothingThrows() {
        val ptrPostSetup = ArkworksKt.benchSetup(numConstraints = 16_000, numVariables = 16_000)
        try {
            val ptrPostProof = ArkworksKt.benchProve(ptrPostSetup)
            try {
                val verified = ArkworksKt.benchVerify(ptrPostProof)
                assertThat(verified).isEqualTo(1) // 1 -> true
            } finally {
                ArkworksKt.benchFreePostProof(ptrPostProof)
            }
        } finally {
            ArkworksKt.benchFreePostSetup(ptrPostSetup)
        }
    }
}
