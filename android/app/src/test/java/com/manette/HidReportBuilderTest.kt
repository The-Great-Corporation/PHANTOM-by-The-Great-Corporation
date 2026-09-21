package com.manette

import com.manette.hid.HidReportBuilder
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests unitaires JVM pour [HidReportBuilder] (P1-1 & P2-8).
 */
class HidReportBuilderTest {

    private val builder = HidReportBuilder()

    @Test
    fun `buildReport produit exactement 8 octets`() {
        val report = builder.buildReport(0)
        assertEquals(8, report.size)
    }

    @Test
    fun `boutons sont correctement encodes sur 16 bits`() {
        // Appui simultané sur A (bit 0) et Back (bit 6) -> 0x01 or 0x40 = 0x41
        val buttonsMask = HidReportBuilder.BTN_A or HidReportBuilder.BTN_BACK
        val report = builder.buildReport(buttonsMask)

        assertEquals(0x41.toByte(), report[0])
        assertEquals(0x00.toByte(), report[1])

        // Appui sur D-pad Up (bit 10) -> octet 1 contient (1 shl (10 - 8)) = 1 shl 2 = 0x04
        val dpadMask = HidReportBuilder.BTN_DPAD_UP
        val reportDpad = builder.buildReport(dpadMask)
        assertEquals(0x00.toByte(), reportDpad[0])
        assertEquals(0x04.toByte(), reportDpad[1])
    }

    @Test
    fun `axes de stick sont bornes a -127 et 127`() {
        val reportMax = builder.buildReport(
            buttonsMask = 0,
            leftStickX = 1.0f,
            leftStickY = -1.0f,
            rightStickX = 0.5f,
            rightStickY = 0f
        )
        assertEquals(127.toByte(), reportMax[2])   // LX
        assertEquals((-127).toByte(), reportMax[3]) // LY
        assertEquals((0.5f * 127).toInt().toByte(), reportMax[4]) // RX
        assertEquals(0.toByte(), reportMax[5])     // RY
    }

    @Test
    fun `gachettes analogiques sont converties sur 0 a 255`() {
        val reportTriggers = builder.buildReport(
            buttonsMask = 0,
            leftTrigger = 1.0f,
            rightTrigger = 0.5f
        )
        // 1.0f * 255 = 255 -> -1 en signé Byte
        assertEquals(255.toByte(), reportTriggers[6])
        assertEquals((0.5f * 255).toInt().toByte(), reportTriggers[7])
    }
}
