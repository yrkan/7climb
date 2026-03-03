package io.github.climbintelligence.datatypes.fit

import io.hammerhead.karooext.models.DeveloperField
import io.hammerhead.karooext.models.FieldValue
import io.hammerhead.karooext.models.FitEffect
import io.hammerhead.karooext.models.WriteToRecordMesg
import io.hammerhead.karooext.models.WriteToSessionMesg

/**
 * FIT file recording field definitions and data builder.
 * Used by ClimbIntelligenceExtension.startFit() to record custom developer fields.
 */
object ClimbFitRecording {

    private const val FLOAT32: Short = 136
    private const val UINT8: Short = 2
    private const val UINT16: Short = 132
    private const val SINT32: Short = 133

    val wPrimeBalanceField = DeveloperField(
        fieldDefinitionNumber = 0,
        fitBaseTypeId = FLOAT32,
        fieldName = "W_Prime_Balance",
        units = "joules"
    )

    val wPrimePercentField = DeveloperField(
        fieldDefinitionNumber = 1,
        fitBaseTypeId = UINT8,
        fieldName = "W_Prime_Percent",
        units = "percent"
    )

    val pacingStatusField = DeveloperField(
        fieldDefinitionNumber = 2,
        fitBaseTypeId = UINT8,
        fieldName = "Pacing_Status",
        units = "enum"
    )

    val targetPowerField = DeveloperField(
        fieldDefinitionNumber = 3,
        fitBaseTypeId = UINT16,
        fieldName = "Target_Power",
        units = "watts"
    )

    val prDeltaField = DeveloperField(
        fieldDefinitionNumber = 4,
        fitBaseTypeId = SINT32,
        fieldName = "PR_Delta",
        units = "seconds"
    )

    val normalizedPowerField = DeveloperField(
        fieldDefinitionNumber = 5,
        fitBaseTypeId = UINT16,
        fieldName = "Normalized_Power",
        units = "watts"
    )

    val tssField = DeveloperField(
        fieldDefinitionNumber = 6,
        fitBaseTypeId = FLOAT32,
        fieldName = "TSS",
        units = "score"
    )

    val matchCountField = DeveloperField(
        fieldDefinitionNumber = 7,
        fitBaseTypeId = UINT8,
        fieldName = "Match_Count",
        units = "count"
    )

    fun buildRecordValues(
        wPrimeBalance: Double,
        wPrimePercent: Double,
        pacingAdviceOrdinal: Int,
        targetPower: Int,
        prDeltaMs: Long?,
        hasPR: Boolean,
        normalizedPower: Int = 0,
        tss: Double = 0.0,
        matchCount: Int = 0
    ): WriteToRecordMesg {
        val values = mutableListOf(
            FieldValue(wPrimeBalanceField, wPrimeBalance),
            FieldValue(wPrimePercentField, wPrimePercent.coerceIn(0.0, 100.0)),
            FieldValue(pacingStatusField, pacingAdviceOrdinal.toDouble()),
            FieldValue(targetPowerField, targetPower.toDouble())
        )

        if (hasPR && prDeltaMs != null) {
            values.add(FieldValue(prDeltaField, (prDeltaMs / 1000).toDouble()))
        }

        if (normalizedPower > 0) {
            values.add(FieldValue(normalizedPowerField, normalizedPower.toDouble()))
        }
        if (tss > 0) {
            values.add(FieldValue(tssField, tss))
        }
        if (matchCount > 0) {
            values.add(FieldValue(matchCountField, matchCount.toDouble()))
        }

        return WriteToRecordMesg(values)
    }

    fun buildSessionValues(
        wPrimeBalance: Double,
        wPrimePercent: Double
    ): WriteToSessionMesg {
        return WriteToSessionMesg(
            listOf(
                FieldValue(wPrimeBalanceField, wPrimeBalance),
                FieldValue(wPrimePercentField, wPrimePercent.coerceIn(0.0, 100.0))
            )
        )
    }
}
