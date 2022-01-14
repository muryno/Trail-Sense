package com.kylecorry.trail_sense.shared.views

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.kylecorry.andromeda.core.system.Resources


class SimpleLineChart(
    private val chart: LineChart,
    emptyText: String = ""
) {

    private var onValueSelectedListener: OnChartValueSelectedListener? = null

    init {
        chart.description.isEnabled = false
        chart.setTouchEnabled(false)
        chart.isDragEnabled = false
        chart.setScaleEnabled(false)
        chart.setDrawGridBackground(false)
        chart.setDrawBorders(false)

        chart.xAxis.setDrawLabels(false)
        chart.axisRight.setDrawLabels(false)

        val primaryColor = Resources.androidTextColorPrimary(chart.context)
        val r = primaryColor.red
        val g = primaryColor.green
        val b = primaryColor.blue

        chart.xAxis.setDrawGridLines(false)
        chart.axisLeft.setDrawGridLines(true)
        chart.axisLeft.gridColor = Color.argb(50, r, g, b)
        chart.axisLeft.textColor = Color.argb(150, r, g, b)
        chart.xAxis.gridColor = Color.argb(50, r, g, b)
        chart.xAxis.textColor = Color.argb(150, r, g, b)
        chart.axisRight.setDrawGridLines(false)
        chart.xAxis.setDrawAxisLine(false)
        chart.axisLeft.setDrawAxisLine(false)
        chart.axisRight.setDrawAxisLine(false)
        chart.setNoDataText(emptyText)
        chart.setNoDataTextColor(primaryColor)
    }

    fun configureYAxis(
        minimum: Float? = null,
        maximum: Float? = null,
        granularity: Float? = null,
        labelCount: Int? = null,
        drawGridLines: Boolean = true,
        labelFormatter: ((value: Float) -> String)? = null
    ) {
        if (minimum != null) {
            chart.axisLeft.axisMinimum = minimum
        } else {
            chart.axisLeft.resetAxisMinimum()
        }

        if (maximum != null) {
            chart.axisLeft.axisMaximum = maximum
        } else {
            chart.axisLeft.resetAxisMaximum()
        }

        if (granularity != null) {
            chart.axisLeft.granularity = granularity
        } else {
            chart.axisLeft.isGranularityEnabled = false
        }

        if (labelCount != null && labelCount != 0) {
            chart.axisLeft.setDrawLabels(true)
            chart.axisLeft.setLabelCount(labelCount, true)
        } else if (labelCount == 0) {
            chart.axisLeft.setDrawLabels(false)
        } else {
            chart.axisLeft.setDrawLabels(true)
            chart.axisLeft.setLabelCount(6, false)
        }

        if (labelFormatter == null) {
            chart.axisLeft.valueFormatter = null
        } else {
            chart.axisLeft.valueFormatter = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                    return labelFormatter.invoke(value)
                }
            }
        }

        chart.axisLeft.setDrawGridLines(drawGridLines)
    }

    fun configureXAxis(
        minimum: Float? = null,
        maximum: Float? = null,
        granularity: Float? = null,
        labelCount: Int? = null,
        drawGridLines: Boolean = false,
        labelFormatter: ((value: Float) -> String)? = null
    ) {
        if (minimum != null) {
            chart.xAxis.axisMinimum = minimum
        } else {
            chart.xAxis.resetAxisMinimum()
        }

        if (maximum != null) {
            chart.xAxis.axisMaximum = maximum
        } else {
            chart.xAxis.resetAxisMaximum()
        }

        if (granularity != null) {
            chart.xAxis.granularity = granularity
        } else {
            chart.xAxis.isGranularityEnabled = false
        }

        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM

        if (labelCount != null && labelCount != 0) {
            chart.xAxis.setDrawLabels(true)
            chart.xAxis.setLabelCount(labelCount, true)
        } else if (labelCount == 0) {
            chart.xAxis.setDrawLabels(false)
        } else {
            chart.xAxis.setDrawLabels(true)
            chart.xAxis.setLabelCount(6, false)
        }

        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                return labelFormatter?.invoke(value) ?: ""
            }
        }

        chart.xAxis.setDrawGridLines(drawGridLines)
    }

    fun plot(
        data: List<Pair<Float, Float>>,
        @ColorInt color: Int,
        filled: Boolean = false,
        circles: Boolean = false,
        cubic: Boolean = true
    ) {
        val values = data.map { Entry(it.first, it.second) }

        val set1 = LineDataSet(values, "Set 1")
        set1.color = color
        set1.fillAlpha = 180
        set1.lineWidth = 3f
        set1.setDrawValues(false)
        set1.fillColor = color
        set1.setCircleColor(color)
        set1.setDrawCircleHole(false)
        set1.setDrawCircles(circles)
        set1.circleRadius = 1.5f
        set1.setDrawFilled(filled)

        if (cubic) {
            set1.mode = LineDataSet.Mode.CUBIC_BEZIER
            set1.cubicIntensity = 0.005f
        }

        val lineData = LineData(set1)
        chart.data = lineData
        chart.legend.isEnabled = false
        chart.notifyDataSetChanged()
        chart.invalidate()
    }

    fun plotIndexed(data: List<Float>, @ColorInt color: Int, filled: Boolean = false) {
        plot(data.mapIndexed { index, value -> index.toFloat() to value }, color, filled)
    }

    fun setOnValueSelectedListener(listener: ((point: Point?) -> Unit)?) {
        if (listener == null) {
            chart.setTouchEnabled(false)
            chart.setOnChartValueSelectedListener(null)
        } else {
            chart.setTouchEnabled(true)
            onValueSelectedListener = object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e == null) {
                        listener.invoke(null)
                        return
                    }
                    val datasetIndex = h?.dataSetIndex ?: 0
                    val pointIndex = chart.data.dataSets[datasetIndex].getEntryIndex(e)
                    listener.invoke(Point(datasetIndex, pointIndex, e.x, e.y))
                }

                override fun onNothingSelected() {
                    listener.invoke(null)
                }

            }
            chart.setOnChartValueSelectedListener(onValueSelectedListener)
        }
    }

    fun getPoint(datasetIdx: Int, entryIdx: Int): Point {
        val entry = chart.lineData.getDataSetByIndex(datasetIdx).getEntryForIndex(entryIdx)
        val point =  chart.getPixelForValues(entry.x, entry.y, YAxis.AxisDependency.LEFT)
        return Point(datasetIdx, entryIdx, point.x.toFloat(), point.y.toFloat())
    }

    data class Point(val datasetIndex: Int, val pointIndex: Int, val x: Float, val y: Float)

}