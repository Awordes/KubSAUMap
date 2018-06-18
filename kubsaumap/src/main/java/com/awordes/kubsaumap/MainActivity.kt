package com.awordes.kubsaumap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import org.jeo.map.Style
import org.jeo.vector.VectorDataset
import org.json.JSONArray
import org.json.JSONObject
import org.oscim.android.canvas.AndroidBitmap
import org.oscim.core.GeoPoint
import org.oscim.map.Map
import org.oscim.layers.tile.vector.VectorTileLayer
import org.oscim.tiling.source.mapfile.MapFileTileSource
import org.oscim.core.MapPosition
import org.oscim.event.Gesture
import org.oscim.event.GestureListener
import org.oscim.event.MotionEvent
import org.oscim.layers.OSMIndoorLayer
import org.oscim.layers.marker.ItemizedLayer
import org.oscim.layers.marker.MarkerItem
import org.oscim.layers.marker.MarkerSymbol
import org.oscim.layers.tile.buildings.BuildingLayer
import org.oscim.layers.tile.buildings.S3DBTileLayer
import org.oscim.layers.tile.vector.labeling.LabelLayer
import org.oscim.test.JeoTest
import org.oscim.theme.VtmThemes
import org.oscim.utils.IOUtils
import org.oscim.theme.styles.TextStyle
import org.oscim.tiling.TileSource
import java.io.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity(),
        ItemizedLayer.OnItemGestureListener<MarkerItem> {

    ////////////////////////////////////////
    private var mMap: Map? = null

    private var mBaseLayer: VectorTileLayer? = null
    private var mTileSource: MapFileTileSource? = null
    private var mIndoorLayer: OSMIndoorLayer? = null
    private var mMarkerLayer: ItemizedLayer<MarkerItem>? = null
    private var mLocationMarkerLayer: ItemizedLayer<MarkerItem>? = null
    ////////////////////////////////////////

    private var mapEventsReceiver: MapEventsReceiver? = null

    var thisPosition: MapPosition = MapPosition()

    private var lastTap: Array<Double> = Array(2, {0.0})

    private var myLocation: Array<Double> = Array(2, {0.0})

    private var buttons: Array<Button?> = arrayOf(null)

    private var kubsauData: JSONObject? = null

    private var floor: Int = 0

    private var isIndoorLayerExist = false

    private var matchParentValue = 0

    private var locationManager: LocationManager? = null

    private var locationListener: LocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location?) {
            myLocation[0] = location!!.latitude
            myLocation[1] = location.longitude
            addLocation(myLocation)
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

        override fun onProviderEnabled(provider: String?) {}

        override fun onProviderDisabled(provider: String?) {}

    }

    //private var typeface = Typeface.createFromAsset(assets, "fonts/blisspro-regular.otf")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        matchParentValue = slidePanel.layoutParams.height
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        buttons = arrayOf(buttonFloor0, buttonFloor1, buttonFloor2, buttonFloor3,
                buttonFloor4, buttonFloor5, buttonFloor6, buttonFloor7)

        //Floating button
        fab.setOnClickListener { view ->
            Snackbar.make(view, "",
                                    Snackbar.LENGTH_SHORT)
                    .setAction("Action", null).show()
        }

        //Код для MapView
        ////////////////////////////////////////
        mMap = mapView.map()
        mTileSource = MapFileTileSource()

        mapEventsReceiver = MapEventsReceiver(mMap!!) //обработка кликов на карту
        mMap!!.layers().add(mapEventsReceiver)        //

        var inputStreamBaseMap: InputStream? = null
        var outputStreamBaseMap: OutputStream? = null
        try {
            inputStreamBaseMap = assets.open("kubsau0.map")
            val outFile = File(getExternalFilesDir(null), "kubsau0.map")
            outputStreamBaseMap = FileOutputStream(outFile)
            copyFile(inputStreamBaseMap, outputStreamBaseMap)
            mTileSource!!.setMapFile(outFile.absolutePath)
            mBaseLayer = mMap!!.setBaseMap(mTileSource)

            //эти цифры подобраны вручную и я не знаю, откуда эти цифры можно получить Х)
            mapView.map().viewport().setMapLimit(0.60809, 0.35950, 0.60815, 0.35957)
            mapView.map().viewport().maxZoomLevel = 20
            mapView.map().viewport().minZoomLevel = 14
        }
        catch (e: IOException) {
            e.printStackTrace()
        }
        finally {
            IOUtils.closeQuietly(inputStreamBaseMap)
            IOUtils.closeQuietly(outputStreamBaseMap)
        }

        mMap!!.setTheme(VtmThemes.DEFAULT)

        //mMap!!.layers().add(BuildingLayer(mMap, mBaseLayer))
        //mMap!!.layers().add(S3DBTileLayer(mMap, mTileSource))
        mMap!!.layers().add(LabelLayer(mMap, mBaseLayer))
        ////////////////////////////////////////



        kubsauData = JSONObject(loadJsonFromAsset("kubsau_data.json"))
                .getJSONObject("Кубанский Государственный Аграрный Университет")

        searchView.setOnSearchClickListener {
            slidePanel.layoutParams.height = matchParentValue
        }

        searchView.setOnQueryTextListener(object: SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                slidePanel.layoutParams.height = matchParentValue
                if (newText == null) return false
                viewSearchResult(searchInJsonObjectKubSAU(kubsauData!!, newText))
                return true
            }
        })

        searchResult.onItemClickListener = AdapterView.OnItemClickListener { _, view, _, _ ->
            val text: String = (view as TextView).text.toString()
            showMessage(text)
            slidePanel.layoutParams.height = 700
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(searchView.windowToken, 0)
            searchResult.visibility = View.GONE
            infoPanel.visibility = View.VISIBLE
        }

        mMap!!.events.bind(Map.UpdateListener { _, mapPosition ->
            if (floor == -1 ) return@UpdateListener
            val zoomLimit = 16

            if (mapPosition.zoomLevel > zoomLimit && !isIndoorLayerExist) {
                addIndoorLayer()
                isIndoorLayerExist = true
                return@UpdateListener
            }
            if (mapPosition.zoomLevel <= zoomLimit && isIndoorLayerExist) {
                removeIndoorLayer()
                isIndoorLayerExist = false
            }
        })
    }

    fun addMarker(coord: Array<Double>) {
        mMap!!.layers().remove(mMarkerLayer)
        var bitmapPoi: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.marker)
        bitmapPoi = Bitmap.createScaledBitmap(bitmapPoi, 60, 60, true)
        val symbol = MarkerSymbol(AndroidBitmap(bitmapPoi),
                MarkerSymbol.HotspotPlace.BOTTOM_CENTER)
        mMarkerLayer = ItemizedLayer<MarkerItem>(mMap, ArrayList<MarkerItem>(), symbol, this)
        mMap!!.layers().add(mMarkerLayer)
        val long = coord[1]
        val lat = coord[0]
        val pts: List<MarkerItem> = listOf(MarkerItem(lat.toString() + "/" + long.toString(), "",
                GeoPoint(lat, long)))
        mMarkerLayer!!.addItems(pts)
        mMap!!.updateMap(true)
    }

    fun addLocation (coord: Array<Double>) {
        mMap!!.layers().remove(mLocationMarkerLayer)
        var bitmapPoi: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.location)
        bitmapPoi = Bitmap.createScaledBitmap(bitmapPoi, 60, 60, true)
        val symbol = MarkerSymbol(AndroidBitmap(bitmapPoi),
                MarkerSymbol.HotspotPlace.BOTTOM_CENTER)
        mLocationMarkerLayer = ItemizedLayer<MarkerItem>(mMap, ArrayList<MarkerItem>(), symbol, this)
        mMap!!.layers().add(mLocationMarkerLayer)
        val long = coord[1]
        val lat = coord[0]
        val pts: List<MarkerItem> = listOf(MarkerItem(lat.toString() + "/" + long.toString(), "",
                GeoPoint(lat, long)))
        mLocationMarkerLayer!!.addItems(pts)
        mMap!!.updateMap(true)

    }

    @Throws (IOException::class)
    private fun copyFile(inputStream:InputStream, outputStream:OutputStream) {
        val buffer = ByteArray(1024)
        var read: Int
        read = inputStream.read(buffer)
        while (read != -1) {
            outputStream.write(buffer, 0, read)
            read = inputStream.read(buffer)
        }
    }

    private fun viewSearchResult(results: ArrayList<String>?) {
        if (results == null) return
        searchResult.adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, results)
    }

    private fun searchInJsonObjectKubSAU(kubSAUJson: JSONObject, searchString: String) : ArrayList<String>? {
        if (searchString == "" && searchString == " ") return null

        val results = ArrayList<String>()

        //поиск в ректорате//
        var jsonArray = kubSAUJson["Ректорат"] as JSONArray
        for (i in 0 until jsonArray.length()) {
            var value = (jsonArray[i] as JSONObject).getString("Имя")
            if (value.toLowerCase().indexOf(searchString.toLowerCase()) != -1) results.add(value)

            value = (jsonArray[i] as JSONObject).getString("Должность")
            if (value.toLowerCase().indexOf(searchString.toLowerCase()) != -1) results.add(value)
        }

        //поиск в корпусах//
        jsonArray = kubSAUJson["Корпусы"] as JSONArray
        for (i in 0 until jsonArray.length()) {
            //чтобы когда написано эк выводился экономические факультет
            val korpusName = (jsonArray[i] as JSONObject).getString("Название")
            val korpusShort = (jsonArray[i] as JSONObject).getString("Сокращение")

            if (korpusName.toLowerCase().indexOf(searchString.toLowerCase()) != -1 ||
                    korpusShort.toLowerCase().indexOf(searchString.toLowerCase()) != -1)
                results.add(korpusName)

            //поиск по аудиториям//
            val rooms = (jsonArray[i] as JSONObject)["Аудитории"] as JSONArray
            for (j in 0 until rooms.length()) {
                val roomName = (rooms[j] as JSONObject).getString("Название")

                if (roomName.toLowerCase().indexOf(searchString.toLowerCase()) != -1)
                    results.add(roomName + korpusShort)
            }
        }

        //поиск в факультетах//
        jsonArray = kubSAUJson["Факультеты"] as JSONArray
        for (i in 0 until jsonArray.length()) {
            val fakName = (jsonArray[i] as JSONObject).getString("Название")
            val fakShort = (jsonArray[i] as JSONObject).getString("Сокращение")

            if (fakName.toLowerCase().indexOf(searchString.toLowerCase()) != -1 ||
                    fakShort.toLowerCase().indexOf(searchString.toLowerCase()) != -1)
                results.add(fakName)

            //поиск в кафедрах//
            val kafedri = (jsonArray[i] as JSONObject)["Кафедры"] as JSONArray
            for (j in 0 until kafedri.length()) {
                val kafName = (kafedri[j] as JSONObject).getString("Название")
                val kafShort = (kafedri[j] as JSONObject).getString("Сокращение")

                if (kafName.toLowerCase().indexOf(searchString.toLowerCase()) != -1 ||
                        kafShort.toLowerCase().indexOf(searchString.toLowerCase()) != -1)
                    results.add(kafName)

                //поиск в сотрудниках кафедры//
                val sotrudniki = (kafedri[j] as JSONObject)["Сотрудники кафедры"] as JSONArray
                for (k in 0 until sotrudniki.length()) {
                    val sotrName = (sotrudniki[k] as JSONObject).getString("Имя")
                    if (sotrName.toLowerCase().indexOf(searchString.toLowerCase()) != -1)
                        results.add(sotrName)
                }
            }
        }
        return results
    }

    private fun tapInRoom () : String {
        var result = ""

        val corpuses = kubsauData!!["Корпусы"] as JSONArray
        for (i in 0 until corpuses.length()) {
            val rooms = (corpuses[i] as JSONObject)["Аудитории"] as JSONArray
            val corpusShort = (corpuses[i] as JSONObject).getString("Сокращение")

            for (j in 0 until rooms.length()) {
                val roomName = (rooms[j] as JSONObject).getString("Название")

                if (floor == 1 && roomName.length > 2) continue
                else if (floor.toString() != roomName[0].toString()) continue

                val coordinates: Array<Array<Double>> = arrayOf(Array(4, {0.0}), Array(4, {0.0}))
                val jSONcoordinates = (rooms[j] as JSONObject)["Координаты"] as JSONArray

                for (k in 0 until (jSONcoordinates).length()) {
                    val pointX = (jSONcoordinates[k] as JSONObject).getString("x")
                    val pointY = (jSONcoordinates[k] as JSONObject).getString("y")
                    coordinates[0][k] = pointX.toDouble()
                    coordinates[1][k] = pointY.toDouble()
                }
                if (isInRoom(coordinates)) {
                    result = roomName + corpusShort
                    showMessage(result)
                }
            }
        }
        return result
    }

    private fun isInRoom(p: Array<Array<Double>>) : Boolean {
        var result = false
        val size = p[0].size
        var j = size - 1
        for (i in 0 until size) {
            if((((p[1][i] <= lastTap[1]) && (lastTap[1] < p[1][j])) || ((p[1][j] <= lastTap[1]) && (lastTap[1] < p[1][i]))) &&
                    (lastTap[0] > (p[0][j] - p[0][i]) * (lastTap[1] - p[1][i]) / (p[1][j] - p[1][i]) + p[0][i])) {
                result = !result
            }
            j = i
        }
        return  result
    }

    private fun showMessage(text: String) {
        Snackbar.make(fab, text, Snackbar.LENGTH_SHORT)
                .setAction("Action", null).show()
    }

    @Throws (IOException::class)
    private fun loadJsonFromAsset( filePath: String): String {
        val inputStream: InputStream = assets.open(filePath)
        val buffer = ByteArray(inputStream.available())
        inputStream.read(buffer)
        inputStream.close()
        return String(buffer, charset("UTF-8"))
    }

    private fun loadJson(inputStream: InputStream) {
        //showToast("got data")
        val data: VectorDataset = JeoTest.readGeoJson(inputStream)
        val style: Style = JeoTest.getStyle()
        val scale: Float = resources.displayMetrics.density
        val textStyle: TextStyle = TextStyle.builder()
                .isCaption(true)
                .priority(0)
                .fontSize(16 * scale).color(Color.BLACK)
                .strokeWidth(2.2f * scale).strokeColor(Color.WHITE)
                .build()
        mIndoorLayer = OSMIndoorLayer(mMap, data, style, textStyle)
        mIndoorLayer!!.activeLevels[floor + 1] = true
        mMap!!.layers().add(mIndoorLayer)
        mMap!!.updateMap(true)
    }

    private fun removeIndoorLayer () {
        val removingFloors: ArrayList<OSMIndoorLayer> = ArrayList()
        for (layer in mMap!!.layers()) {
            if (layer is OSMIndoorLayer)
                removingFloors.add(layer)
        }

        for (floor in removingFloors) {
            mMap!!.layers().remove(floor)
        }
        mMap!!.updateMap(true)
        mIndoorLayer = null
    }

    private fun addIndoorLayer () {
        val i: Int = floor

        for (button in buttons) {
            button?.visibility = View.GONE
            button?.setBackgroundColor(Color.WHITE)
        }
        buttonFloor_1.visibility = View.GONE
        buttonFloor_1.setBackgroundColor(Color.WHITE)

        when (i) {
            7 -> {
                buttons[i]?.visibility = View.VISIBLE
                buttons[i - 2]?.visibility = View.VISIBLE
                buttons[i - 3]?.visibility = View.VISIBLE
            }
            0 -> {
                buttons[i + 1]?.visibility = View.VISIBLE
                buttons[i]?.visibility = View.VISIBLE
                buttonFloor_1.visibility = View.VISIBLE
            }
            -1 -> {
                buttonFloor0.visibility = View.VISIBLE
                buttonFloor1.visibility = View.VISIBLE
                buttonFloor_1.visibility = View.VISIBLE
            }
            else -> {
                buttons[i + 1]?.visibility = View.VISIBLE
                buttons[i]?.visibility = View.VISIBLE
                buttons[i - 1]?.visibility = View.VISIBLE
            }
        }

        if (i in 0..7)
            buttons[i]?.setBackgroundColor(Color.parseColor("#BDBDBD"))
        else if (i == -1) buttonFloor_1.setBackgroundColor(Color.parseColor("#BDBDBD"))

        if (i <= 0 || i > 2) {
            removeIndoorLayer()
            return
        }

        mMap!!.addTask({
            var inputStream: InputStream? = null
            try {
                removeIndoorLayer()
                inputStream = assets.open("kubsau"+ i.toString() + ".geojson")
                loadJson(inputStream)
                isIndoorLayerExist = true
            }
            catch (e: IOException) {
                e.printStackTrace()
            }
            finally {
                IOUtils.closeQuietly(inputStream)
            }
        })
    }

    fun onClick (v: View) {
        floor = (v as Button).text.toString().toInt()
        addIndoorLayer()
        isIndoorLayerExist = true
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        mMap!!.setMapPosition(45.04215, 38.9262, (1 shl 16).toDouble())

        try {
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                                            1000 * 10,
                                            10f,
                                            locationListener)

            locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                                            1000 * 10,
                                            10f,
                                            locationListener)
        }
        catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onResume()
        locationManager?.removeUpdates(locationListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onResume()
        locationManager?.removeUpdates(locationListener)
    }

    override fun onItemLongPress(index: Int, item: MarkerItem?): Boolean {
        return false
    }

    override fun onItemSingleTapUp(index: Int, item: MarkerItem?): Boolean {
        return false
    }

    inner class MapEventsReceiver(map: Map): org.oscim.layers.Layer(map) , GestureListener {

        override fun onGesture(g: Gesture?, e: MotionEvent?): Boolean {
            if (g is Gesture.Tap) {
                val tupPos: GeoPoint = mMap.viewport().fromScreenPoint(e!!.x, e.y)
                lastTap = arrayOf(tupPos.latitude, tupPos.longitude)
                addMarker(lastTap)
                tapInRoom()
            }
            if (g is Gesture.LongPress) {
                lastTap = arrayOf(e!!.x.toDouble(), e.y.toDouble())
            }
            if (g is Gesture.TripleTap) {
                lastTap = arrayOf(e!!.x.toDouble(), e.y.toDouble())
            }
            return false
        }
    }
}