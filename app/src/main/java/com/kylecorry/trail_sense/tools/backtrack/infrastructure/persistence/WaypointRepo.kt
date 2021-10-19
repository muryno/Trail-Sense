package com.kylecorry.trail_sense.tools.backtrack.infrastructure.persistence

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.database.AppDatabase
import com.kylecorry.trail_sense.shared.paths.PathPoint
import com.kylecorry.trail_sense.tools.backtrack.domain.WaypointEntity
import java.time.Instant

class WaypointRepo private constructor(context: Context) : IWaypointRepo {

    private val waypointDao = AppDatabase.getInstance(context).waypointDao()
    private val prefs = UserPreferences(context)

    override fun getWaypoints() = waypointDao.getAll()

    override suspend fun getWaypointsSync() = waypointDao.getAllSync()

    override suspend fun getWaypoint(id: Long) = waypointDao.get(id)

    override fun getWaypointsByPath(pathId: Long): LiveData<List<PathPoint>> {
        val all = waypointDao.getAllInPath(pathId)
        return Transformations.map(all) { it.map { w -> w.toPathPoint() } }
    }

    override suspend fun deleteWaypoint(waypoint: WaypointEntity) = waypointDao.delete(waypoint)

    override suspend fun getLastPathId(): Long? = waypointDao.getLastPathId()

    override suspend fun deletePath(pathId: Long) = waypointDao.deleteByPath(pathId)

    override suspend fun moveToPath(fromPathId: Long, toPathId: Long) =
        waypointDao.changePath(fromPathId, toPathId)

    override suspend fun clean() {
        waypointDao.deleteOlderThan(Instant.now().minus(prefs.navigation.backtrackHistory).toEpochMilli())
    }

    override suspend fun addWaypoint(waypoint: WaypointEntity) {
        if (waypoint.id != 0L) {
            waypointDao.update(waypoint)
        } else {
            waypointDao.insert(waypoint)
        }
    }

    companion object {
        private var instance: WaypointRepo? = null

        @Synchronized
        fun getInstance(context: Context): WaypointRepo {
            if (instance == null) {
                instance = WaypointRepo(context.applicationContext)
            }
            return instance!!
        }
    }

}