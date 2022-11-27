package com.polware.weatherv2

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.*
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.polware.weatherv2.Constants.API_KEY
import com.polware.weatherv2.Constants.LANG
import com.polware.weatherv2.Constants.PREFERENCE_NAME
import com.polware.weatherv2.Constants.UNITS
import com.polware.weatherv2.Constants.WEATHER_RESPONSE_DATA
import com.polware.weatherv2.databinding.ActivityMainBinding
import com.polware.weatherv2.models.WeatherResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mySharedPreferences: SharedPreferences
    private var progressDialog: Dialog? = null
    private var latitude: Double = 0.0
    private var longitude: Double = -0.0

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mySharedPreferences = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
        // Si el usuario no tiene Internet, carga datos guardados en Shared Preference
        setupMainView()

        if (!isLocationEnabled()) {
            Snackbar.make(binding.root, "Service location is turned OFF",
                Snackbar.LENGTH_LONG)
                .setAction("Activate") {
                    val intentSettings = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    startActivity(intentSettings)
                }
                .setActionTextColor(Color.GREEN)
                .show()
        }
        else {
            //Toast.makeText(this, "Service location is turned ON", Toast.LENGTH_SHORT).show()
            // Ask multiple permissions using Dexter
            Dexter.withContext(this).withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                .withListener(object: MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                        if (report.areAllPermissionsGranted()){
                            requestLocationData()
                            //Toast.makeText(this@TouristPlaceActivity, "Location permission is granted", Toast.LENGTH_SHORT).show()
                        }
                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(this@MainActivity,
                                "You have denied the location service, please activate it to use the application", Toast.LENGTH_LONG).show()
                        }
                    }
                    override fun onPermissionRationaleShouldBeShown(permissions: MutableList<PermissionRequest>,
                                                                    token: PermissionToken) {
                        showRationalDialogForPermissions()
                    }
                }).onSameThread().check()
        }

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.action_refresh -> {
                requestLocationData()
                true
            }
            R.id.action_search -> {
                searchWeatherByCity()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun isLocationEnabled(): Boolean {
        // Provides access to system location services
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val locationRequest = LocationRequest()
        locationRequest.priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallBack, Looper.myLooper())
    }

    private val locationCallBack = object: LocationCallback() {
        @RequiresApi(Build.VERSION_CODES.N)
        override fun onLocationResult(locationResult: LocationResult) {
            val lastLocation: Location = locationResult.lastLocation!!
            latitude = lastLocation.latitude
            Log.i("Current Latitude: ", "$latitude")
            longitude = lastLocation.longitude
            Log.i("Current Longitude: ", "$longitude")
            getWeatherDetails()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun getWeatherDetails() {
        if (Constants.isNetworkAvailable(this)) {
            showProgressDialog()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = RetrofitBuilder.retrofitService.getWeatherByLocation(latitude,
                        longitude, API_KEY, UNITS, LANG)
                    val weatherResponse = response.body()
                    // Se crea una variable para almacenar en Shared Preferences
                    val responseJsonString = Gson().toJson(weatherResponse)
                    val editor = mySharedPreferences.edit()
                    editor.putString(WEATHER_RESPONSE_DATA, responseJsonString)
                    editor.apply()
                    Log.i("Coroutine task: ", "Result: $weatherResponse")
                    withContext(Dispatchers.Main){
                        //Toast.makeText(this@MainActivity, "Response Successful", Toast.LENGTH_SHORT).show()
                        hideProgressDialog()
                        setupMainView()
                    }
                }
                catch (e: HttpException) {
                    Toast.makeText(this@MainActivity, "Something else wrong!", Toast.LENGTH_SHORT).show()
                    Log.e("HttpException: ", "${e.message}")
                    hideProgressDialog()
                }
            }
        }
        else {
            Toast.makeText(this, "No Internet connection available", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun searchWeatherByCity() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Buscar Ciudad")
        builder.setMessage("Por favor ingrese el nombre:")
        val myInput = EditText(this)
        myInput.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(myInput)
        builder.setPositiveButton("Buscar"){
                dialog, _ ->
            val cityName: String = myInput.text.toString()
            showProgressDialog()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = RetrofitBuilder.retrofitService.getWeatherByCity(cityName,
                        API_KEY, UNITS, LANG)
                    val weatherResponse = response.body()
                    val responseCode = response.code()
                    Log.i("CoroutineCity: ", "Result: $weatherResponse")
                    withContext(Dispatchers.Main){
                        hideProgressDialog()
                        if (responseCode == 200)
                            loadCityWeatherResults(weatherResponse!!)
                        else {
                            Toast.makeText(this@MainActivity, "City not found or invalid search", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }
                    }
                }
                catch (e: HttpException){
                    Toast.makeText(this@MainActivity, "Something else wrong!", Toast.LENGTH_SHORT).show()
                    Log.e("HttpException: ", "${e.message}")
                }
            }
        }
        builder.setNegativeButton("Cancelar"){
            dialog, _ ->
            dialog.dismiss()
        }
        val dialogue: AlertDialog = builder.create()
        dialogue.show()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun loadCityWeatherResults(weatherData: WeatherResponse) {
        for (i in weatherData.weather!!.indices){
            Log.i("Weather: ", weatherData.weather.toString())
            binding.tvMain.text = weatherData.weather[i].main
            binding.tvMainDescription.text = weatherData.weather[i].description
            // De acuerdo al idioma que tenga el usuario se muestra la unidad de medida
            val units = getMeasureUnits(application.resources.configuration.locales.toString())
            binding.tvTemp.text = StringBuilder().append(weatherData.main!!.temp).append(units).toString()
            binding.tvHumidity.text = StringBuilder().append(weatherData.main.humidity.toString())
                .append(" %").toString()
            binding.tvMaxTemp.text = StringBuilder().append(weatherData.main.tempMax.toString())
                .append(" Max").toString()
            binding.tvMinTemp.text = StringBuilder().append(weatherData.main.tempMin.toString())
                .append(" Min").toString()
            binding.tvSpeed.text = weatherData.wind!!.speed.toString()
            binding.tvName.text = weatherData.name
            binding.tvCountry.text = weatherData.sys!!.country
            binding.tvSunriseTime.text = unixTimestampConversion(weatherData.sys!!.sunrise)
            binding.tvSunsetTime.text = unixTimestampConversion(weatherData.sys!!.sunset)

            // Habilitamos los íconos personalizados según el listado en: https://openweathermap.org/weather-conditions
            when(weatherData.weather[i].icon) {
                "01d" -> binding.ivMain.setImageResource(R.drawable.sunny)
                "02d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "03d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "04d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "04n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "10d" -> binding.ivMain.setImageResource(R.drawable.rain)
                "11d" -> binding.ivMain.setImageResource(R.drawable.storm)
                "13d" -> binding.ivMain.setImageResource(R.drawable.snowflake)
                "01n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "02n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "03n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "10n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "11n" -> binding.ivMain.setImageResource(R.drawable.rain)
                "13n" -> binding.ivMain.setImageResource(R.drawable.snowflake)
            }
        }
    }

    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this).setMessage("You have turned off permission required for" +
                " this feature. It can be enabled under the Applications Settings")
            .setPositiveButton("Go To Settings") {
                    _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException){
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") {
                    dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showProgressDialog(){
        progressDialog = Dialog(this)
        progressDialog!!.setContentView(R.layout.dialog_custom_progress)
        progressDialog!!.show()
    }

    private fun hideProgressDialog(){
        if (progressDialog != null){
            progressDialog!!.dismiss()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun setupMainView() {
        val weatherSharedPreferences = mySharedPreferences.getString(WEATHER_RESPONSE_DATA, "")
        if (!weatherSharedPreferences.isNullOrEmpty()){
            val weatherData = Gson().fromJson(weatherSharedPreferences, WeatherResponse::class.java)
            for (i in weatherData.weather!!.indices){
                Log.i("Weather: ", weatherData.weather.toString())
                binding.tvMain.text = weatherData.weather[i].main
                binding.tvMainDescription.text = weatherData.weather[i].description
                // De acuerdo al idioma que tenga el usuario se muestra la unidad de medida
                val units = getMeasureUnits(application.resources.configuration.locales.toString())
                binding.tvTemp.text = StringBuilder().append(weatherData.main!!.temp).append(units).toString()
                binding.tvHumidity.text = StringBuilder().append(weatherData.main.humidity.toString())
                    .append(" %").toString()
                binding.tvMaxTemp.text = StringBuilder().append(weatherData.main.tempMax.toString())
                    .append(" Max").toString()
                binding.tvMinTemp.text = StringBuilder().append(weatherData.main.tempMin.toString())
                    .append(" Min").toString()
                binding.tvSpeed.text = weatherData.wind!!.speed.toString()
                binding.tvName.text = weatherData.name
                binding.tvCountry.text = weatherData.sys!!.country
                binding.tvSunriseTime.text = unixTimestampConversion(weatherData.sys!!.sunrise)
                binding.tvSunsetTime.text = unixTimestampConversion(weatherData.sys!!.sunset)

                // Habilitamos los íconos personalizados según el listado en: https://openweathermap.org/weather-conditions
                when(weatherData.weather[i].icon) {
                    "01d" -> binding.ivMain.setImageResource(R.drawable.sunny)
                    "02d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "03d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "04d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "04n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "10d" -> binding.ivMain.setImageResource(R.drawable.rain)
                    "11d" -> binding.ivMain.setImageResource(R.drawable.storm)
                    "13d" -> binding.ivMain.setImageResource(R.drawable.snowflake)
                    "01n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "02n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "03n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "10n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "11n" -> binding.ivMain.setImageResource(R.drawable.rain)
                    "13n" -> binding.ivMain.setImageResource(R.drawable.snowflake)
                }
            }
        }
    }

    private fun getMeasureUnits(value: String): String? {
        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value)
            value = "°F"
        return value
    }

    private fun unixTimestampConversion(timex: Long): String? {
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

}