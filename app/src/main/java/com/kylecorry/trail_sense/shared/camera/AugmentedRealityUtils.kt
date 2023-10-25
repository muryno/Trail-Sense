package com.kylecorry.trail_sense.shared.camera

import com.kylecorry.andromeda.core.units.PixelCoordinate
import com.kylecorry.sol.math.SolMath
import com.kylecorry.sol.math.SolMath.toRadians
import com.kylecorry.sol.math.geometry.Size
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object AugmentedRealityUtils {

    // TODO: Take in full device orientation / quaternion
    /**
     * Gets the pixel coordinate of a point on the screen given the bearing and azimuth.
     * @param bearing The compass bearing in degrees of the point
     * @param azimuth The compass bearing in degrees that the user is facing (center of the screen)
     * @param altitude The altitude of the point in degrees
     * @param inclination The inclination of the device in degrees
     * @param size The size of the view in pixels
     * @param fov The field of view of the camera in degrees
     */
    fun getPixel(
        bearing: Float,
        azimuth: Float,
        altitude: Float,
        inclination: Float,
        size: Size,
        fov: Size
    ): PixelCoordinate {
        val diagonalFov = hypot(fov.width, fov.height)
        val diagonalSize = hypot(size.width, size.height)
        val radius = diagonalSize / (sin((diagonalFov / 2f).toRadians()) * 2f)

        val newBearing = SolMath.deltaAngle(azimuth, bearing)
        val newAltitude = altitude - inclination

        val rectangular = toRectangular(
            newBearing,
            newAltitude,
            radius
        )

        var x = size.width / 2f + rectangular.x
        // If the coordinate is off the screen, ensure it is not drawn
        if (newBearing > fov.width / 2f){
            x += size.width
        } else if (newBearing < -fov.width / 2f){
            x -= size.width
        }

        var y = size.height / 2f + rectangular.y
        // If the coordinate is off the screen, ensure it is not drawn
        if (newAltitude > fov.height / 2f){
            y += size.height
        } else if (newAltitude < -fov.height / 2f){
            y -= size.height
        }

        return PixelCoordinate(x, y)
    }


    private fun toRectangular(
        bearing: Float,
        altitude: Float,
        radius: Float
    ): PixelCoordinate {
        // X and Y are flipped
        val x = sin(bearing.toRadians()) * cos(altitude.toRadians()) * radius
        val y = cos(bearing.toRadians()) * sin(altitude.toRadians()) * radius
        return PixelCoordinate(x, y)
    }

}
