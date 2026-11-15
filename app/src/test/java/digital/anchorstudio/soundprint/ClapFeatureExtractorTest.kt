package digital.anchorstudio.soundprint

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ClapFeatureExtractorTest {
    @Test fun repeatPadMatchesClapProcessorRepeatpadForNonDivisibleAudio() {
        val result = ClapFeatureExtractor.repeatPad(floatArrayOf(1f,2f,3f,4f,5f,6f),10)
        assertArrayEquals(floatArrayOf(1f,2f,3f,4f,5f,6f,0f,0f,0f,0f),result,0f)
    }

    @Test fun repeatPadRepeatsWholeCopiesBeforeZeroPadding() {
        val result = ClapFeatureExtractor.repeatPad(floatArrayOf(0.25f,-0.25f),10)
        assertArrayEquals(floatArrayOf(0.25f,-0.25f,0.25f,-0.25f,0.25f,-0.25f,0.25f,-0.25f,0.25f,-0.25f),result,0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun repeatPadRejectsEmptyAudio() {
        ClapFeatureExtractor.repeatPad(floatArrayOf(),10)
    }

    @Test fun clapMelFeaturesStayWithinTheExpectedEightyDbRange() {
        val samples = FloatArray(48_000 * 10) { index ->
            (0.2 * kotlin.math.sin(2.0 * Math.PI * 440.0 * index / 48_000.0)).toFloat()
        }
        val features = ClapFeatureExtractor.extract(samples)
        val peak = features.maxOrNull() ?: error("No CLAP features returned")
        val floor = features.minOrNull() ?: error("No CLAP features returned")
        assert(floor >= peak - 80.001f)
        assert(features.all { it.isFinite() })
    }
}
