package com.awordes.kubsaumap

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.SearchView
import android.widget.Toast
import org.jeo.map.Style
import org.jeo.vector.VectorDataset
import org.json.JSONArray
import org.json.JSONObject
import org.oscim.android.MapPreferences
import org.oscim.android.MapView
import org.oscim.map.Map
import org.oscim.layers.tile.vector.VectorTileLayer
import org.oscim.tiling.source.mapfile.MapFileTileSource
import org.slf4j.LoggerFactory
import org.oscim.core.MapPosition
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
import java.io.*
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity(),
        ItemizedLayer.OnItemGestureListener<MarkerItem>,
        SearchView.OnQueryTextListener {

    ////////////////////////////////////////
    private var mMapView: MapView? = null
    private var mMap: Map? = null
    private var mPref: MapPreferences? = null
    ////////////////////////////////////////


    ////////////////////////////////////////
    private val log = LoggerFactory.getLogger(MainActivity::class.java)
    private var mBaseLayer: VectorTileLayer? = null
    private var mTileSource: MapFileTileSource? = null
    private var mIndoorLayer: OSMIndoorLayer? = null
    ////////////////////////////////////////

    private var mapEventsReceiver: MapEventsReceiver? = null

    var thisPosition: MapPosition = MapPosition()

    var lastTap: Array<Float>? = null

    private var buttons: Array<Button>? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        buttons = arrayOf(buttonFloor0, buttonFloor1, buttonFloor2, buttonFloor3,
                buttonFloor4, buttonFloor5, buttonFloor6, buttonFloor7)
        //buttons!![-1] = buttonFloor_1

        //Floating button
        fab.setOnClickListener { view ->
            Snackbar.make(view, thisPosition.longitude.toString() + " " +
                                    thisPosition.latitude.toString(),
                                    Snackbar.LENGTH_SHORT)
                    .setAction("Action", null).show()
        }

        //Код для MapView
        ////////////////////////////////////////
        mMapView = mapView
        mMap = mMapView!!.map()
        mPref = MapPreferences(packageName, this)
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
            mMapView!!.map().viewport().setMapLimit(0.60809, 0.35950, 0.60815, 0.35957)
            mMapView!!.map().viewport().maxZoomLevel = 20
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
            var inputStream: InputStream? = null
            try {
                inputStream = assets.open("kubsau1.geojson")
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
        ////////////////////////////////////////

        val jsonObject = JSONObject(loadJsonFromAsset("kubsau_data.json"))
                .getJSONObject("Кубанский Государственный Аграрный Университет")


        searchView.setOnQueryTextListener(object: SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText == null) return false
                searchInJsonObjectKubSAU(jsonObject, newText)
                return true
            }

        })

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

    private fun searchInJsonObjectKubSAU(kubSAUJson: JSONObject, searchString: String) {

        if (searchString == "" &&
                searchString == " ") return

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
        searchResult.adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, results)
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
        mMap!!.layers().add(mIndoorLayer)
        //showToast("data ready")
        mMap!!.updateMap(true)
        for (j in 0 until mIndoorLayer!!.activeLevels.size) {
            mIndoorLayer!!.activeLevels[j] = true
        }
        mIndoorLayer!!.update()
    }

    private fun showToast(text: String) {
        val ctx: Context = this
        runOnUiThread({
            Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
        })
    }

    fun onClick (v: View) {
        val i: Int = (v as Button).text.toString().toInt()

        for (button in buttons!!) {
            button.visibility = View.GONE
            button.setBackgroundColor(Color.WHITE)
        }
        buttonFloor_1.visibility = View.GONE
        buttonFloor_1.setBackgroundColor(Color.WHITE)

        when (i) {
            7 -> {
                buttons!![i].visibility = View.VISIBLE
                buttons!![i - 2].visibility = View.VISIBLE
                buttons!![i - 3].visibility = View.VISIBLE
            }
            0 -> {
                buttons!![i + 1].visibility = View.VISIBLE
                buttons!![i].visibility = View.VISIBLE
                buttonFloor_1.visibility = View.VISIBLE
            }
            -1 -> {
                buttonFloor0.visibility = View.VISIBLE
                buttonFloor1.visibility = View.VISIBLE
                buttonFloor_1.visibility = View.VISIBLE
            }
            else -> {
                buttons!![i + 1].visibility = View.VISIBLE
                buttons!![i].visibility = View.VISIBLE
                buttons!![i - 1].visibility = View.VISIBLE
            }
        }

        if (i in 0..7)
        buttons!![i].setBackgroundColor(Color.parseColor("#BDBDBD"))
        else if (i == -1) buttonFloor_1.setBackgroundColor(Color.parseColor("#BDBDBD"))

        mMap!!.layers().remove(mIndoorLayer)

        if (i <= 0 || i > 2) {

            mMap!!.updateMap(true)
            return
        }

        mMap!!.addTask({
            var inputStream: InputStream? = null
            try {
                inputStream = assets.open("kubsau"+ i.toString() + ".geojson")
                loadJson(inputStream)
            }
            catch (e: IOException) {
                e.printStackTrace()
            }
            finally {
                IOUtils.closeQuietly(inputStream)
            }
        })

        for (j in 0 until mIndoorLayer!!.activeLevels.size) {
            mIndoorLayer!!.activeLevels[j] = true
        }

        mIndoorLayer!!.update()
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
    }

    override fun onItemLongPress(index: Int, item: MarkerItem?): Boolean {
        return false
    }

    override fun onItemSingleTapUp(index: Int, item: MarkerItem?): Boolean {
        return false
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        return false
    }

    inner class MapEventsReceiver(map: Map): org.oscim.layers.Layer(map) , GestureListener {

        override fun onGesture(g: Gesture?, e: MotionEvent?): Boolean {
            if (g is Gesture.Tap) {
                lastTap = arrayOf(e!!.x, e.y)
                mMapView!!.map().getMapPosition(thisPosition)
            }
            if (g is Gesture.LongPress) {
                lastTap = arrayOf(e!!.x, e.y)
            }
            if (g is Gesture.TripleTap) {
                lastTap = arrayOf(e!!.x, e.y)
            }
            return false
        }
    }
}