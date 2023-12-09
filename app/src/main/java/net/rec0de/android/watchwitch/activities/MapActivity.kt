package net.rec0de.android.watchwitch.activities

import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.TypedValue
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import net.rec0de.android.watchwitch.R
import net.rec0de.android.watchwitch.servicehandlers.health.db.DatabaseWrangler
import org.osmdroid.config.Configuration
import org.osmdroid.config.Configuration.getInstance
import org.osmdroid.library.BuildConfig
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay


// based on https://github.com/osmdroid/osmdroid/wiki/How-to-use-the-osmdroid-library-(Kotlin)
class MapActivity : AppCompatActivity() {
    private val REQUEST_PERMISSIONS_REQUEST_CODE = 1
    private lateinit var map : MapView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = BuildConfig.LIBRARY_PACKAGE_NAME
        setContentView(R.layout.activity_map)

        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        val mapController = map.controller
        mapController.setZoom(4.5)

        val scaleBarOverlay = ScaleBarOverlay(map)
        scaleBarOverlay.setCentred(false)
        scaleBarOverlay.setScaleBarOffset(30, 30)
        map.overlays.add(scaleBarOverlay)
    }

    override fun onStart() {
        super.onStart()
        val b = intent.extras
        if (b != null) {
            val seriesId = b.getInt("seriesId")
            loadTrack(seriesId)
        }
    }

    private fun loadTrack(id: Int) {
        val track = DatabaseWrangler.getLocationSeries(id)!!

        val points = track.points.map { GeoPoint(it.first, it.second) }

        val typedValue = TypedValue()
        val color = if (this.theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)) {
            typedValue.data
        }
        else
            Color.MAGENTA

        val paint = Paint()
        paint.strokeWidth = 10F
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.strokeCap = Paint.Cap.ROUND
        paint.isAntiAlias = true

        val line = Polyline()
        line.setPoints(points)
        line.outlinePaint.set(paint)
        map.overlays.add(line)

        zoomToBounds(computeArea(points))
    }

    // based on https://stackoverflow.com/a/39786450
    private fun zoomToBounds(box: BoundingBox?) {
        if (map.height > 0) {
            map.zoomToBoundingBox(box, false, 40)
        } else {
            val vto = map.viewTreeObserver
            vto.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    map.zoomToBoundingBox(box, false, 40)
                    val vto2 = map.viewTreeObserver
                    vto2.removeOnGlobalLayoutListener(this)
                }
            })
        }
    }

    // based on https://stackoverflow.com/a/39786450
    private fun computeArea(points: List<GeoPoint>): BoundingBox {
        var north = 0.0
        var south = 0.0
        var west = 0.0
        var east = 0.0
        for (i in points.indices) {
            val lat = points[i].latitude
            val lon = points[i].longitude
            if (i == 0 || lat > north) north = lat
            if (i == 0 || lat < south) south = lat
            if (i == 0 || lon < west) west = lon
            if (i == 0 || lon > east) east = lon
        }
        return BoundingBox(north, east, south, west)
    }

    override fun onResume() {
        super.onResume()
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        map.onResume() //needed for compass, my location overlays, v6.0.0 and up
    }

    override fun onPause() {
        super.onPause()
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().save(this, prefs);
        map.onPause()  //needed for compass, my location overlays, v6.0.0 and up
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val permissionsToRequest = ArrayList<String>()
        var i = 0
        while (i < grantResults.size) {
            permissionsToRequest.add(permissions[i])
            i++
        }
        if (permissionsToRequest.size > 0) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_PERMISSIONS_REQUEST_CODE)
        }
    }
}