package com.example.kahoot.host

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.kahoot.R
import com.example.kahoot.main.RoleSelectionFragment
import com.example.kahoot.utils.ScreenTimeManager
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.auth.FirebaseAuth

class HostProfileFragment : Fragment() {
    private lateinit var emailTextView: TextView
    private lateinit var screenTimeChart: BarChart
    private val auth = FirebaseAuth.getInstance()
    private val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_host_profile, container, false)

        emailTextView = view.findViewById(R.id.hostEmailTextView)
        screenTimeChart = view.findViewById(R.id.screenTimeChart)

        setupUI(view)
        setupChart()
        loadScreenTimeData()

        return view
    }

    private fun setupUI(view: View) {
        val currentUser = auth.currentUser
        emailTextView.text = "Email: ${currentUser?.email}"
        
        view.findViewById<Button>(R.id.hostLogoutButton).setOnClickListener {
            auth.signOut()
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.container, RoleSelectionFragment())
                .commit()
        }

        view.findViewById<Button>(R.id.simulateDataButton).setOnClickListener {
            Toast.makeText(requireContext(), "Simulating data...", Toast.LENGTH_SHORT).show()
            
            ScreenTimeManager.simulateScreenTimeData(requireContext()) {
                loadScreenTimeData()
            }
        }
    }

    private fun setupChart() {
        screenTimeChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setDrawBorders(false)
            setNoDataText("No chart data available")
            setNoDataTextColor(requireContext().getColor(R.color.purple_500))
            setPinchZoom(false)
            setScaleEnabled(false)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textColor = Color.BLACK
                textSize = 12f
                setDrawAxisLine(true)
            }

            axisLeft.apply {
                setDrawGridLines(true)
                textColor = Color.BLACK
                textSize = 12f
                axisMinimum = 0f
                setDrawAxisLine(true)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "${value.toInt()}h"
                    }
                }
            }

            axisRight.isEnabled = false
            animateY(1000)
            setExtraOffsets(10f, 10f, 10f, 10f)
        }
    }

    private fun loadScreenTimeData() {
        ScreenTimeManager.getScreenTimeData { screenTimes ->
            requireActivity().runOnUiThread {
                if (screenTimes.isEmpty()) {
                    screenTimeChart.setNoDataText("Start using the app to see your screen time!")
                    screenTimeChart.invalidate()
                    return@runOnUiThread
                }

                val entries = screenTimes.mapIndexed { index, data ->
                    BarEntry(index.toFloat(), (data.totalTime / (1000f * 60 * 60)).toFloat())
                }

                val dataSet = BarDataSet(entries, "Screen Time").apply {
                    color = requireContext().getColor(R.color.purple_500)
                    valueTextColor = Color.BLACK
                    valueTextSize = 12f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "${value.toInt()}h"
                        }
                    }
                }

                val barData = BarData(dataSet).apply {
                    barWidth = 0.7f
                }
                screenTimeChart.data = barData

                val labels = screenTimes.map { monthNames[it.month] }
                screenTimeChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
                screenTimeChart.xAxis.labelRotationAngle = -45f

                screenTimeChart.invalidate()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ScreenTimeManager.startSession()
    }

    override fun onPause() {
        super.onPause()
        ScreenTimeManager.endSession()
    }
}
