package com.kylecorry.trail_sense.weather.domain.sealevel

import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.weather.domain.sealevel.kalman.KalmanSeaLevelCalibrationSettings
import com.kylecorry.trail_sense.weather.domain.sealevel.kalman.KalmanSeaLevelCalibrationStrategy

class SeaLevelCalibrationFactory {

    fun create(prefs: UserPreferences): ISeaLevelCalibrationStrategy {

        if (!prefs.weather.useSeaLevelPressure) {
            return NullSeaLevelCalibrationStrategy()
        }

        if (prefs.altimeterMode == UserPreferences.AltimeterMode.Override) {
            return SimpleSeaLevelCalibrationStrategy(prefs.weather.seaLevelFactorInTemp)
        }

        return KalmanSeaLevelCalibrationStrategy(
            KalmanSeaLevelCalibrationSettings(
                prefs.weather.altitudeOutlier,
                prefs.weather.altitudeSmoothing,
                prefs.weather.pressureSmoothing,
                prefs.weather.seaLevelFactorInTemp,
                prefs.weather.useAltitudeVariance
            )
        )
    }

}