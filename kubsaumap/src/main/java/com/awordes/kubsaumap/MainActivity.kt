package com.awordes.kubsaumap

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import kotlinx.android.synthetic.main.activity_main.*
import android.view.View
import android.widget.Button
import android.widget.Toast
import android.widget.ToggleButton
import org.jeo.map.Layer
import org.jeo.map.Style
import org.jeo.vector.VectorDataset
import org.oscim.android.MapPreferences
import org.oscim.android.MapView
import org.oscim.map.Map
import org.oscim.layers.LocationLayer
import org.oscim.layers.TileGridLayer
import org.oscim.layers.tile.vector.VectorTileLayer
import org.oscim.tiling.source.mapfile.MapFileTileSource
import org.slf4j.LoggerFactory
import org.oscim.android.cache.TileCache
import org.oscim.core.BoundingBox
import org.oscim.core.GeoPoint
import org.oscim.core.MapPosition
import org.oscim.core.MercatorProjection
import org.oscim.event.Gesture
import org.oscim.event.GestureListener
import org.oscim.event.MotionEvent
import org.oscim.layers.OSMIndoorLayer
import org.oscim.layers.marker.ItemizedLayer
import org.oscim.layers.marker.MarkerItem
import org.oscim.layers.tile.vector.labeling.LabelLayer
import org.oscim.test.JeoTest
import org.oscim.theme.VtmThemes
import org.oscim.utils.IOUtils
import org.oscim.theme.styles.TextStyle
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import java.io.*
import java.util.*

class MainActivity : AppCompatActivity(),
        ItemizedLayer.OnItemGestureListener<MarkerItem>
        /*NavigationView.OnNavigationItemSelectedListener*/ {

    ////////////////////////////////////////
    private var mMapView: MapView? = null
    private var mMap: Map? = null
    private var mPref: MapPreferences? = null
    ////////////////////////////////////////

    //MyLocationListener

    ////////////////////////////////////////
    private val log = LoggerFactory.getLogger(MainActivity::class.java)
    private val USE_CACHE = false

    private var mBaseLayer: VectorTileLayer? = null
    private var mTileSource: MapFileTileSource? = null
    private var mGridLayer: TileGridLayer? = null
    private var locationLayer: LocationLayer? = null
    private var mIndoorLayer: OSMIndoorLayer? = null

    private var mCache: TileCache? = null
    ////////////////////////////////////////

    private var mapEventsReceiver: MapEventsReceiver? = null

    var pos: MapPosition = MapPosition()

    var lastTap: Array<Float>? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //setSupportActionBar(toolbar)


        fab.setOnClickListener { view ->
            Snackbar.make(view, pos!!.longitude.toString() + " " + pos!!.latitude.toString(), Snackbar.LENGTH_SHORT)
                    .setAction("Action", null).show()
        }


        /*Навигация слева
        val toggle = ActionBarDrawerToggle(
                this, drawer_layout, toolbar, R.string.navigation_drawer_open,
                R.string.navigation_drawer_close)
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()

        nav_view.setNavigationItemSelectedListener(this)*/

        //Код для MapView
        ////////////////////////////////////////
        mMapView = mapView
        mMap = mMapView!!.map()
        mPref = MapPreferences(packageName, this)
        mTileSource = MapFileTileSource()

        mapEventsReceiver = MapEventsReceiver(mMap!!)
        mMap!!.layers().add(mapEventsReceiver) //обработка кликов на карту

        var inputStreamBaseMap: InputStream? = null
        var outputStreamBaseMap: OutputStream? = null
        try {
            inputStreamBaseMap = assets.open("kubsau0.map")
            var outFile = File(getExternalFilesDir(null), "kubsau0.map")
            outputStreamBaseMap = FileOutputStream(outFile)
            copyFile(inputStreamBaseMap, outputStreamBaseMap)
            mTileSource!!.setMapFile(outFile.absolutePath)
            mBaseLayer = mMap!!.setBaseMap(mTileSource)
            var minX: Double = 45.0397
            var minY: Double = 38.9118
            var maxX: Double = 45.0587
            var maxY: Double = 38.9342
            mMapView!!.map().viewport().maxZoomLevel = 20

            //выше переменные boundingBox карты, эти цифры подобраны вручную и я не знаю что это за значения Х)
            mMapView!!.map().viewport().setMapLimit(0.60809, 0.35950, 0.60815, 0.35957)
            mMapView!!.map().viewport().minZoomLevel = 14
        }
        catch (e: IOException) {
            e.printStackTrace()
        }
        finally {
            IOUtils.closeQuietly(inputStreamBaseMap)
            IOUtils.closeQuietly(outputStreamBaseMap)
        }

        mMap!!.addTask({
            //showToast("load data")
            var inputStream: InputStream? = null
            try {
                inputStream = assets.open("kubsau2.geojson")
                loadJson(inputStream)
            }
            catch (e: IOException) {
                e.printStackTrace()
            }
            finally {
                IOUtils.closeQuietly(inputStream)
            }
        })

        mMap!!.setTheme(VtmThemes.DEFAULT)
        mMap!!.layers().add(LabelLayer(mMap, mBaseLayer))



        //this.locationLayer = LocationLayer(mMap)
        //locationLayer!!.locationRenderer.setShader("location_1_reverse")
        //mMap!!.layers().add(locationLayer)
        ////////////////////////////////////////
    }
/*
    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        when (item.itemId) {
            R.id.action_settings -> return true
            else -> return super.onOptionsItemSelected(item)
        }
    }*/
/*
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            R.id.nav_camera -> {
                // Handle the camera action
            }
            R.id.nav_gallery -> {

            }
            R.id.nav_slideshow -> {

            }
            R.id.nav_manage -> {

            }
            R.id.nav_share -> {

            }
            R.id.nav_send -> {

            }
        }

        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }*/

    @Throws (IOException::class)
    private fun copyFile(inputStream:InputStream, outputStream:OutputStream) {
        var buffer = ByteArray(1024)
        var read = 0
        read = inputStream.read(buffer)
        while (read != -1) {
            outputStream.write(buffer, 0, read)
            read = inputStream.read(buffer)
        }
    }

    private fun loadJson(inputStream: InputStream) {
        //showToast("got data")
        var data: VectorDataset = JeoTest.readGeoJson(inputStream)
        var style: Style = JeoTest.getStyle()
        var scale: Float = resources.displayMetrics.density
        var textStyle: TextStyle = TextStyle.builder()
                .isCaption(true)
                .priority(0)
                .fontSize(16 * scale).color(Color.BLACK)
                .strokeWidth(2.2f * scale).strokeColor(Color.WHITE)
                .build()
        mIndoorLayer = OSMIndoorLayer(mMap, data, style, textStyle)
        mMap!!.layers().add(mIndoorLayer)
        showToast("data ready")
        mMap!!.updateMap(true)
        mIndoorLayer!!.activeLevels[0] = true
        mIndoorLayer!!.update()
    }

    private fun showToast(text: String) {
        var ctx: Context = this
        runOnUiThread({
            Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
        })
    }

    fun onClick (v: View) {
        if (mIndoorLayer == null) return
        var i = 0


        //if ((v as Button).background(Color.parseColor("#BDBDBD")))
        if (((v as Button).background as ColorDrawable).color == Color.parseColor("#BDBDBD"))
            v.setBackgroundColor(Color.WHITE)
        else
            v.setBackgroundColor(Color.parseColor("#BDBDBD"))
        i = (v).text.toString().toInt() + 1
        mIndoorLayer!!.activeLevels[i] = mIndoorLayer!!.activeLevels[i] xor true


        log.debug(Arrays.toString(mIndoorLayer!!.activeLevels))
        mIndoorLayer!!.update()

    }

    fun updateLoc(v: View) {

    }

    override fun onResume() {
        super.onResume()
        mPref!!.load(mMapView!!.map())
        mMapView!!.onResume()
        mMap!!.setMapPosition(45.04215, 38.9262, (1 shl 16).toDouble())
    }

    override fun onPause() {
        mPref!!.save(mMapView!!.map())
        mMapView!!.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        mMapView!!.onDestroy()
        super.onDestroy()
        if (mCache != null) mCache!!.dispose()
    }


    override fun onItemLongPress(index: Int, item: MarkerItem?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onItemSingleTapUp(index: Int, item: MarkerItem?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    inner class MapEventsReceiver(map: Map): org.oscim.layers.Layer(map) , GestureListener {


        override fun onGesture(g: Gesture?, e: MotionEvent?): Boolean {
            if (g is Gesture.Tap) {
                var p: GeoPoint = mMap.viewport().fromScreenPoint(e!!.x, e.y)
                lastTap = arrayOf(e.x, e.y)

                mMapView!!.map().getMapPosition(pos!!)
            }
            if (g is Gesture.LongPress) {
                var p: GeoPoint = mMap.viewport().fromScreenPoint(e!!.x, e.y)
                lastTap = arrayOf(e.x, e.y)
            }
            if (g is Gesture.TripleTap) {
                var p: GeoPoint = mMap.viewport().fromScreenPoint(e!!.x, e.y)
                lastTap = arrayOf(e.x, e.y)
            }
            return false
        }

    }

}
