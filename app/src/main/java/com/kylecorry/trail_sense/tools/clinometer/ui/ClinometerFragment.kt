package com.kylecorry.trail_sense.tools.clinometer.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.kylecorry.andromeda.alerts.dialog
import com.kylecorry.andromeda.alerts.toast
import com.kylecorry.andromeda.core.system.Resources
import com.kylecorry.andromeda.core.time.Throttle
import com.kylecorry.andromeda.core.ui.setCompoundDrawables
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.fragments.observe
import com.kylecorry.andromeda.markdown.MarkdownService
import com.kylecorry.andromeda.pickers.Pickers
import com.kylecorry.andromeda.sense.clinometer.Clinometer
import com.kylecorry.andromeda.sense.clinometer.IClinometer
import com.kylecorry.andromeda.sense.orientation.DeviceOrientation
import com.kylecorry.sol.math.SolMath.cosDegrees
import com.kylecorry.sol.math.SolMath.normalizeAngle
import com.kylecorry.sol.science.geology.AvalancheRisk
import com.kylecorry.sol.science.geology.Geology
import com.kylecorry.sol.units.Distance
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.FragmentClinometerBinding
import com.kylecorry.trail_sense.shared.*
import com.kylecorry.trail_sense.shared.CustomUiUtils.getPrimaryMarkerColor
import com.kylecorry.trail_sense.shared.DistanceUtils.toRelativeDistance
import com.kylecorry.trail_sense.shared.permissions.alertNoCameraPermission
import com.kylecorry.trail_sense.shared.permissions.requestCamera
import com.kylecorry.trail_sense.shared.sensors.SensorService
import com.kylecorry.trail_sense.tools.augmented_reality.ARLine
import com.kylecorry.trail_sense.tools.augmented_reality.ARLineLayer
import com.kylecorry.trail_sense.tools.augmented_reality.ARMarker
import com.kylecorry.trail_sense.tools.augmented_reality.ARMarkerLayer
import com.kylecorry.trail_sense.tools.augmented_reality.CanvasCircle
import com.kylecorry.trail_sense.tools.augmented_reality.position.ARPoint
import com.kylecorry.trail_sense.tools.augmented_reality.position.SphericalARPoint
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ClinometerFragment : BoundFragment<FragmentClinometerBinding>() {

    private val sensorService by lazy { SensorService(requireContext()) }
    private val orientation by lazy { sensorService.getOrientation() }
    private val cameraClinometer by lazy { Clinometer(orientation, isAugmentedReality = true) }
    private val sideClinometer by lazy { Clinometer(orientation, isAugmentedReality = false) }
    private val deviceOrientation by lazy { sensorService.getDeviceOrientationSensor() }
    private val prefs by lazy { UserPreferences(requireContext()) }
    private val markdown by lazy { MarkdownService(requireContext()) }
    private val formatter by lazy { FormatService.getInstance(requireContext()) }
    private val throttle = Throttle(20)

    private lateinit var clinometer: IClinometer

    // Lock
    private val minimumHoldDuration = Duration.ofMillis(200)
    private val minimumHoldAngle = 0.5f
    private var lockedAngle1: Float? = null
    private var lockedIncline1: Float? = null
    private var lockedAngle2: Float? = null
    private var lockedIncline2: Float? = null
    private var lockStartTime: Instant? = null
    private var hadLock = false
    private var isHolding = false

    private var distanceAway: Distance? = null
    private var knownHeight: Distance? = null

    private var useCamera = true

    // Augmented reality
    private val markerLayer = ARMarkerLayer()
    private val lineLayer = ARLineLayer()
    private var startMarker: ARPoint? = null
    private var endMarker: ARPoint? = null

    private val isAugmentedReality by lazy {
        prefs.clinometer.useAugmentedReality
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clinometer = getClinometer()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toast(getString(R.string.set_inclination_instructions))

        CustomUiUtils.setButtonState(binding.clinometerTitle.leftButton, false)
        CustomUiUtils.setButtonState(binding.clinometerTitle.rightButton, false)

        binding.cameraViewHolder.clipToOutline = true
        binding.camera.setScaleType(PreviewView.ScaleType.FILL_CENTER)
        binding.camera.setShowTorch(false)

        binding.clinometerTitle.leftButton.setOnClickListener {
            if (useCamera) {
                startSideClinometer()
            } else {
                startCameraClinometer(true)
            }
        }

        binding.clinometerTitle.rightButton.setOnClickListener {
            askForHeightOrDistance()
        }

        binding.root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                updateLockState(PressState.Down)
            } else if (event.action == MotionEvent.ACTION_UP) {
                updateLockState(PressState.Up)
            }
            true
        }

        if (isAugmentedReality) {
            binding.arView.setLayers(listOf(lineLayer, markerLayer))
        }
        binding.arView.showReticle = false
        binding.arView.showPosition = false
        binding.arView.isVisible = isAugmentedReality

        binding.arView.bind(binding.camera)

        observe(sideClinometer) { updateUI() }
        observe(cameraClinometer) { updateUI() }
        observe(deviceOrientation) { updateUI() }
    }

    private fun startSideClinometer() {
        binding.camera.stop()
        binding.arView.stop()
        binding.clinometerTitle.leftButton.setImageResource(R.drawable.ic_camera)
        CustomUiUtils.setButtonState(binding.clinometerTitle.leftButton, false)
        useCamera = false
        clinometer = getClinometer()
    }

    private fun startCameraClinometer(showAlert: Boolean) {
        requestCamera { hasPermission ->
            if (hasPermission) {
                useCamera = true
                binding.camera.start(
                    readFrames = false, shouldStabilizePreview = false
                )
                if (isAugmentedReality) {
                    binding.arView.start(false)
                }
                binding.clinometerTitle.leftButton.setImageResource(R.drawable.ic_phone_portrait)
                CustomUiUtils.setButtonState(binding.clinometerTitle.leftButton, false)
                clinometer = getClinometer()
            } else {
                startSideClinometer()
                if (showAlert) {
                    alertNoCameraPermission()
                }
            }
        }
    }

    fun updateLockState(pressState: PressState) {
        if (pressState == PressState.Down) {
            onTouchDown()
        } else {
            onTouchUp()
        }
    }

    private fun getCurrentAngle(): Float {
        // TODO: This should just be clinometer.angle
        return normalizeAngle(-clinometer.angle + 180f)
    }

    private fun askForHeightOrDistance() {
        Pickers.item(
            requireContext(), getString(R.string.measure), listOf(
                getString(R.string.height), getString(R.string.distance)
            ), when {
                distanceAway != null -> 0
                knownHeight != null -> 1
                else -> -1
            }
        ) {
            if (it != null) {
                when (it) {
                    0 -> measureHeightPrompt()
                    1 -> measureDistancePrompt()
                }
            }
        }
    }

    private fun measureHeightPrompt() {
        val units = formatter.sortDistanceUnits(DistanceUtils.hikingDistanceUnits)
        CustomUiUtils.pickDistance(
            requireContext(),
            units,
            distanceAway,
            getString(R.string.clinometer_measure_height_title)
        ) { distance, _ ->
            if (distance != null) {
                distanceAway = distance
                knownHeight = null
                CustomUiUtils.setButtonState(binding.clinometerTitle.rightButton, true)
                if (!prefs.clinometer.measureHeightInstructionsSent) {
                    dialog(
                        getString(R.string.instructions), markdown.toMarkdown(
                            getString(
                                R.string.clinometer_measure_height_instructions,
                                formatter.formatDistance(distance, 2, false)
                            )
                        ), cancelText = null
                    ) {
                        prefs.clinometer.measureHeightInstructionsSent = true
                    }
                }
            }
        }
    }

    private fun measureDistancePrompt() {
        val units = formatter.sortDistanceUnits(DistanceUtils.humanDistanceUnits)

        CustomUiUtils.pickDistance(
            requireContext(),
            units,
            knownHeight,
            getString(R.string.clinometer_measure_distance_title),
            hint = getString(R.string.height),
            showFeetAndInches = true
        ) { distance, _ ->
            if (distance != null) {
                knownHeight = distance
                distanceAway = null
                CustomUiUtils.setButtonState(binding.clinometerTitle.rightButton, true)
                if (!prefs.clinometer.measureDistanceInstructionsSent) {
                    dialog(
                        getString(R.string.instructions), markdown.toMarkdown(
                            getString(
                                R.string.clinometer_measure_distance_instructions,
                                formatter.formatDistance(distance, 2, false)
                            )
                        ), cancelText = null
                    ) {
                        prefs.clinometer.measureDistanceInstructionsSent = true
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (distanceAway == null && knownHeight == null) {
            distanceAway = prefs.clinometer.baselineDistance
            CustomUiUtils.setButtonState(
                binding.clinometerTitle.rightButton, distanceAway != null
            )
        }

        if (useCamera) {
            startCameraClinometer(false)
        } else {
            startSideClinometer()
        }
    }

    override fun onPause() {
        super.onPause()
        if (useCamera) {
            binding.camera.stop()
            binding.arView.stop()
        }
    }

    override fun onDestroyView() {
        binding.arView.unbind()
        super.onDestroyView()
    }

    private fun getClinometer(): IClinometer {
        return if (useCamera) {
            cameraClinometer
        } else {
            sideClinometer
        }
    }

    private fun updateUI() {

        if (throttle.isThrottled()) {
            return
        }

        val locked = lockedAngle1 != null

        binding.clinometerTitle.title.setCompoundDrawables(
            Resources.dp(requireContext(), 24f).toInt(),
            right = if (locked) R.drawable.lock else null
        )

        CustomUiUtils.setImageColor(
            binding.clinometerTitle.title, Resources.androidTextColorPrimary(requireContext())
        )

        if (!isOrientationValid() && !locked) {
            binding.clinometerInstructions.isVisible = !useCamera
            binding.cameraClinometerInstructions.isVisible = useCamera
            binding.cameraViewHolder.isVisible = false
            binding.clinometer.isInvisible = true
            return
        }

        binding.clinometerInstructions.isVisible = false
        binding.cameraClinometerInstructions.isVisible = false
        binding.cameraViewHolder.isVisible = useCamera
        binding.clinometer.isInvisible = useCamera

        val angle = lockedAngle2 ?: (if (!isHolding) lockedAngle1 else null) ?: getCurrentAngle()
        val incline =
            lockedIncline2 ?: (if (!isHolding) lockedIncline1 else null) ?: clinometer.incline

        val avalancheRisk = Geology.getAvalancheRisk(incline)

        binding.clinometer.angle = angle
        binding.cameraClinometer.inclination = incline

        binding.clinometerTitle.title.text = formatter.formatDegrees(incline)
        binding.avalancheRisk.title = getAvalancheRiskString(avalancheRisk)

        binding.clinometerTitle.subtitle.text =
            getString(R.string.slope_amount, formatter.formatPercentage(getSlopePercent(incline)))

        val distanceAway = distanceAway
        val knownHeight = knownHeight

        when {
            distanceAway != null -> {
                binding.estimatedHeight.description = getString(R.string.height)
                binding.estimatedHeight.title = formatter.formatDistance(
                    getHeight(distanceAway).toRelativeDistance(), 1, false
                )
            }

            knownHeight != null -> {
                binding.estimatedHeight.description = getString(R.string.distance)
                binding.estimatedHeight.title = formatter.formatDistance(
                    getDistance(knownHeight).toRelativeDistance(), 1, false
                )
            }

            else -> {
                binding.estimatedHeight.title = getString(R.string.distance_unset)
            }
        }

        updateARLine()
    }

    private fun updateARLine() {
        if (isAugmentedReality && startMarker != null && (isHolding || endMarker != null)) {
            lineLayer.setLines(
                listOf(
                    ARLine(
                        listOfNotNull(
                            startMarker, endMarker ?: SphericalARPoint(
                                binding.arView.azimuth,
                                binding.arView.inclination,
                                isTrueNorth = prefs.compass.useTrueNorth,
                                distance = distanceAway?.meters()?.distance ?: 10f
                            )
                        ), Color.WHITE, 1f
                    )
                )
            )
        } else {
            lineLayer.clearLines()
        }
    }

    private fun getSlopePercent(incline: Float): Float {
        return Geology.getSlopeGrade(incline)
    }

    private fun getAvalancheRiskString(risk: AvalancheRisk): String {
        return when (risk) {
            AvalancheRisk.Low -> getString(R.string.low)
            AvalancheRisk.Moderate -> getString(R.string.moderate)
            AvalancheRisk.High -> getString(R.string.high)
        }
    }

    private fun isOrientationValid(): Boolean {
        val invalidOrientations = if (useCamera) {
            listOf(
                DeviceOrientation.Orientation.Landscape,
                DeviceOrientation.Orientation.LandscapeInverse
            )
        } else {
            listOf(DeviceOrientation.Orientation.Flat, DeviceOrientation.Orientation.FlatInverse)
        }

        return !invalidOrientations.contains(deviceOrientation.orientation)
    }

    override fun generateBinding(
        layoutInflater: LayoutInflater, container: ViewGroup?
    ): FragmentClinometerBinding {
        return FragmentClinometerBinding.inflate(layoutInflater, container, false)
    }

    private fun getBottomIncline(): Float {
        val locked1 = lockedIncline1 ?: 0f
        val locked2 = lockedIncline2 ?: clinometer.incline
        return min(locked1, locked2)
    }

    private fun getTopIncline(): Float {
        val locked1 = lockedIncline1 ?: 0f
        val locked2 = lockedIncline2 ?: clinometer.incline
        return max(locked1, locked2)
    }

    private fun getHeight(distanceAway: Distance): Distance {
        return Geology.getHeightFromInclination(
            distanceAway, getBottomIncline(), getTopIncline()
        )
    }

    private fun getDistance(height: Distance): Distance {
        return Geology.getDistanceFromInclination(
            height, getBottomIncline(), getTopIncline()
        )
    }

    private fun onTouchDown() {
        if (!isOrientationValid() || isHolding) {
            return
        }

        isHolding = true
        hadLock = lockedAngle1 != null
        clearLock()
        lockedAngle1 = getCurrentAngle()
        lockedIncline1 = clinometer.incline
        lockStartTime = Instant.now()

        // Update the UI
        binding.cameraClinometer.startInclination = lockedIncline1
        binding.clinometer.startAngle = lockedAngle1
        startMarker = addMarker()
    }

    private fun onTouchUp() {
        if (!isOrientationValid() || !isHolding) {
            return
        }

        // Determine if the user did a sweep or single angle
        val deltaAngle = abs(clinometer.incline - (lockedIncline1 ?: 0f))
        val deltaTime = Duration.between(lockStartTime, Instant.now())
        if (deltaAngle < minimumHoldAngle || deltaTime < minimumHoldDuration) {

            // These aren't needed for a single angle mode
            binding.cameraClinometer.startInclination = null
            binding.clinometer.startAngle = null

            // If there was a lock, clear it instead of setting a new one
            if (hadLock) {
                clearLock()
            }

            isHolding = false
            return
        }

        lockedAngle2 = getCurrentAngle()
        lockedIncline2 = clinometer.incline

        // Update UI
        endMarker = addMarker()

        isHolding = false
    }

    private fun clearLock() {
        lockedAngle1 = null
        lockedIncline1 = null
        lockedAngle2 = null
        lockedIncline2 = null
        lockStartTime = null

        // Update UI
        startMarker = null
        endMarker = null
        lineLayer.clearLines()
        markerLayer.clearMarkers()
        binding.cameraClinometer.startInclination = null
        binding.clinometer.startAngle = null
    }

    private fun addMarker(): ARPoint {
        // Distance away is distance from device to the object at 0 inclination
        // Calculate the distance away using the hypotenuse of the triangle
        val adjacent = distanceAway?.meters()?.distance ?: 10f
        val hypotenuse = adjacent / cosDegrees(clinometer.incline)
        val point = SphericalARPoint(
            binding.arView.azimuth,
            binding.arView.inclination,
            isTrueNorth = prefs.compass.useTrueNorth,
            distance = hypotenuse,
            angularDiameter = 1f
        )
        markerLayer.addMarker(
            ARMarker(
                point, CanvasCircle(Resources.getPrimaryMarkerColor(requireContext()))
            )
        )
        return point
    }


}
