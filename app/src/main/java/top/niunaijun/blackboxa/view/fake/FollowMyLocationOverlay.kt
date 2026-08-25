package top.niunaijun.blackboxa.view.fake


import android.app.Activity
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import java.io.File
import java.util.Locale
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import top.niunaijun.blackbox.entity.location.BLocation
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.databinding.ActivityOsmdroidBinding
import top.niunaijun.blackboxa.util.inflate



class FollowMyLocationOverlay : AppCompatActivity() {
    val TAG: String = "FollowMyLocationOverlay"

    private val REQUEST_PERMISSIONS_REQUEST_CODE = 1

    private val binding: ActivityOsmdroidBinding by inflate()

    lateinit var startPoint: GeoPoint

    private val defaultTileSource = object : OnlineTileSourceBase(
        "AutoNavi",
        3,
        19,
        256,
        ".png",
        arrayOf(
            "https://webrd01.is.autonavi.com/appmaptile?",
            "https://webrd02.is.autonavi.com/appmaptile?",
            "https://webrd03.is.autonavi.com/appmaptile?",
            "https://webrd04.is.autonavi.com/appmaptile?"
        )
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "${getBaseUrl()}lang=zh_cn&size=1&scale=1&style=8&x=$x&y=$y&z=$zoom"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureOsmDroid()

        setContentView(binding.root)
        binding.map.setTileSource(defaultTileSource)
        binding.map.setMultiTouchControls(true)
        binding.map.setUseDataConnection(true)

        val location: BLocation? = intent.getParcelableExtra("location")

        startPoint = if (location == null) {
            GeoPoint(30.2736, 120.1563)
        } else {
            GeoPoint(location.latitude, location.longitude)
        }


        val startMarker = Marker(binding.map)
        startMarker.position = startPoint
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        startMarker.setInfoWindow(null)
        startMarker.setOnMarkerClickListener { _, _ -> true }

        binding.map.overlays.add(startMarker)
        val mReceive: MapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                updateSelectedLocation(p, startMarker)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                updateSelectedLocation(p, startMarker)
                return true
            }
        }
        binding.map.overlays.add(MapEventsOverlay(mReceive))
        binding.locationText.text = formatLocation(startPoint)
        binding.cancelButton.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        binding.saveButton.setOnClickListener {
            finishWithResult(startPoint)
        }

        val mapController = binding.map.controller
        mapController.setZoom(12.5)

        mapController.setCenter(startPoint)
    }

    private fun configureOsmDroid() {
        val config = Configuration.getInstance()
        val basePath = File(cacheDir, "osmdroid")
        val tileCache = File(basePath, "tiles")
        basePath.mkdirs()
        tileCache.mkdirs()

        config.load(this, PreferenceManager.getDefaultSharedPreferences(this))
        config.userAgentValue = packageName
        config.osmdroidBasePath = basePath
        config.osmdroidTileCache = tileCache
    }

    override fun onBackPressed() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    override fun onResume() {
        super.onResume()
        
        
        
        
        binding.map.onResume() 
    }

    override fun onPause() {
        super.onPause()
        
        
        
        
        binding.map.onPause()  
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
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
                REQUEST_PERMISSIONS_REQUEST_CODE
            )
        }
    }

    private fun finishWithResult(geoPoint: GeoPoint) {
        intent.putExtra("latitude", geoPoint.latitude)
        intent.putExtra("longitude", geoPoint.longitude)
        setResult(Activity.RESULT_OK, intent)
        val imm: InputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        window.peekDecorView()?.run {
            imm.hideSoftInputFromWindow(windowToken, 0)
        }
        finish()
    }

    private fun updateSelectedLocation(geoPoint: GeoPoint, marker: Marker) {
        startPoint = geoPoint
        marker.position = geoPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        binding.locationText.text = formatLocation(geoPoint)
        binding.map.invalidate()
    }

    private fun formatLocation(geoPoint: GeoPoint): String {
        return getString(
            R.string.selected_location_format,
            String.format(Locale.US, "%.6f", geoPoint.latitude),
            String.format(Locale.US, "%.6f", geoPoint.longitude)
        )
    }

}
