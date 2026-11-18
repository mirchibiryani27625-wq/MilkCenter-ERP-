package com.example.util

import com.example.data.model.RateChartConfig
import java.math.BigDecimal
import java.math.RoundingMode

object RateCalculator {

    data class CalculationResult(
        val ratePerLiter: Double,
        val totalAmount: Double,
        val isMachineMismatch: Boolean = false,
        val mismatchDifference: Double = 0.0,
        val warningMessage: String? = null
    )

    fun calculate(
        quantity: Double,
        fat: Double,
        snf: Double,
        waterPercentage: Double,
        rateChart: RateChartConfig,
        machineAmount: Double? = null
    ): CalculationResult {
        if (quantity <= 0.0) {
            return CalculationResult(0.0, 0.0)
        }

        val fatDiff = fat - rateChart.baseFat
        val snfDiff = snf - rateChart.baseSnf

        var rate = rateChart.baseRate + (fatDiff * rateChart.fatMultiplier) + (snfDiff * rateChart.snfMultiplier)

        if (waterPercentage > 0.0) {
            rate -= (waterPercentage * rateChart.waterDeductionPerPercent)
        }

        // Keep a minimum reasonable floor
        if (rate < 15.0) rate = 15.0

        val roundedRate = BigDecimal(rate).setScale(2, RoundingMode.HALF_UP).toDouble()
        val computedTotal = BigDecimal(quantity * roundedRate).setScale(2, RoundingMode.HALF_UP).toDouble()

        var isMismatch = false
        var diff = 0.0
        var warning: String? = null

        if (machineAmount != null && machineAmount > 0.0) {
            diff = Math.abs(computedTotal - machineAmount)
            if (diff > 1.0) {
                isMismatch = true
                warning = "चेतावनी: मशीन राशि (₹$machineAmount) और गणना राशि (₹$computedTotal) में ₹${String.format("%.2f", diff)} का अंतर है!"
            }
        }

        return CalculationResult(
            ratePerLiter = roundedRate,
            totalAmount = computedTotal,
            isMachineMismatch = isMismatch,
            mismatchDifference = diff,
            warningMessage = warning
        )
    }
}
