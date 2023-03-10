package com.example.airquality2

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.airquality2.databinding.ActivityMainBinding
import android.Manifest
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.airquality2.retrofit.AirQualityResponse
import com.example.airquality2.retrofit.AirQualityService
import com.example.airquality2.retrofit.RetrofitConnection
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class MainActivity : AppCompatActivity() {
    var mInterstitialAd : InterstitialAd? = null

    var latitude: Double = 0.0
    var longitude: Double = 0.0

    lateinit var binding: ActivityMainBinding

    private val PERMISSIONS_REQUEST_CODE = 100

    var REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION)

    lateinit var getGPSPermissionLauncher: ActivityResultLauncher<Intent>
    lateinit var locationProvider: LocationProvider

    val startMapActivityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult(), object : ActivityResultCallback<ActivityResult> {
        override fun onActivityResult(result: ActivityResult?) {
            if (result?.resultCode ?: 0 == Activity.RESULT_OK) {
                latitude = result?.data?.getDoubleExtra("latitude", 0.0) ?: 0.0
                longitude = result?.data?.getDoubleExtra("longitude", 0.0) ?: 0.0
                updateUI()
            }
        }
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAllPermissions()
        updateUI()
        setRefreshButton()
        setFeb()

        setBannerAds()
    }

    override fun onResume() {
        super.onResume()
        setInterstitialAds()
    }

    private fun setFeb() {
        binding.fab.setOnClickListener {
            if (mInterstitialAd != null) {
                mInterstitialAd!!.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d("ads log", "?????? ????????? ???????????????.")

                        val intent = Intent(this@MainActivity, MapActivity::class.java)
                        intent.putExtra("currentLat", latitude)
                        intent.putExtra("currentLng", longitude)
                        startMapActivityResult.launch(intent)
                    }

                    override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                        Log.d("ads log", "?????? ????????? ????????? ??? ??????????????????.")
                    }

                    override fun onAdShowedFullScreenContent() {
                        Log.d("ads log", "?????? ????????? ??????????????? ???????????????.")
                        mInterstitialAd = null
                    }
                }
                mInterstitialAd!!.show(this@MainActivity)
            } else {
                Log.d("InterstitialAd", "?????? ????????? ???????????? ???????????????.")
                Toast.makeText(this@MainActivity, "?????? ??? ?????? ??????????????????", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setRefreshButton() {
        binding.btnRefresh.setOnClickListener {
            updateUI()
        }
    }

    private fun updateUI() {
        locationProvider = com.example.airquality2.LocationProvider(this@MainActivity)

        if (latitude == 0.0 || longitude == 0.0) {
            latitude = locationProvider.getLocationLatitude()
            longitude = locationProvider.getLocationLongitude()
        }

        Log.d("??????","lat: $latitude, lon: $longitude")
        if (latitude != 0.0 || longitude != 0.0) {
            val address = getCurrentAddress(latitude, longitude)
            address?.let {
                binding.tvLocationTitle.text = "${it.thoroughfare}"
                binding.tvLocationSubtitle.text = "${it.countryName} ${it.adminArea}"
            }
            getAirQualityData(latitude, longitude)
        } else {
            Toast.makeText(this@MainActivity, "??????, ?????? ????????? ????????? ??? ????????????. ??????????????? ???????????????." , Toast.LENGTH_LONG).show()
        }
    }

    private fun getAirQualityData(latitude: Double, longitude: Double) {
        val retrofitAPI = RetrofitConnection.getInstance().create(AirQualityService::class.java)

        retrofitAPI.getAirQualityData(latitude.toString(), longitude.toString(), "6adddc22-eab9-4f28-9389-30e7049fe085").enqueue(object : Callback<AirQualityResponse> {
            override fun onResponse(call: Call<AirQualityResponse>, response: Response<AirQualityResponse>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "?????? ?????? ???????????? ??????!", Toast.LENGTH_SHORT).show()
                    response.body()?.let { updateAirUI(it) }
                } else {
                    Toast.makeText(this@MainActivity, "??????????????? ??????????????????.", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<AirQualityResponse>, t: Throwable) {
                t.printStackTrace()
                Toast.makeText(this@MainActivity, "??????????????? ??????????????????.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateAirUI(airQualityData: AirQualityResponse) {
        val pollutionData = airQualityData.data.current.pollution

        binding.tvCount.text = pollutionData.aqius.toString()

        val dateTime = ZonedDateTime.parse(pollutionData.ts).withZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime()
        val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        binding.tvCheckTime.text = dateTime.format(dateFormatter).toString()

        when (pollutionData.aqius) {
            in 0..50 -> {
                binding.tvTitle.text = "??????"
                binding.imgBg.setImageResource(R.drawable.bg_good)
            }
            in 51..150 -> {
                binding.tvTitle.text = "??????"
                binding.imgBg.setImageResource(R.drawable.bg_soso)
            }
            in 151..200 -> {
                binding.tvTitle.text = "??????"
                binding.imgBg.setImageResource(R.drawable.bg_bad)
            }
            else -> {
                binding.tvTitle.text = "?????? ??????"
                binding.imgBg.setImageResource(R.drawable.bg_worst)
            }
        }
    }

    private fun checkAllPermissions() {
        if (!isLocationServicesAvailable()) {
            showDialogForLocationServiceSetting();
        } else {
            isRunTimePermissionsGranted();
        }
    }

    fun isRunTimePermissionsGranted() {
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (hasFineLocationPermission != PackageManager.PERMISSION_GRANTED || hasCoarseLocationPermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this@MainActivity, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE)
        }
    }

    fun isLocationServicesAvailable(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE && grantResults.size == REQUIRED_PERMISSIONS.size) {
            var checkResult = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    checkResult = false
                    break
                }
            }
            if (checkResult) {
                updateUI()
            } else {
                Toast.makeText(this@MainActivity, "???????????? ?????????????????????. ?????? ?????? ???????????? ???????????? ??????????????????", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun showDialogForLocationServiceSetting() {
        getGPSPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                if (isLocationServicesAvailable()) {
                    isRunTimePermissionsGranted()
                } else {
                    Toast.makeText(this@MainActivity, "?????? ???????????? ????????? ??? ????????????.", Toast.LENGTH_LONG).show()
                }
                finish()
            }
        }

        val builder: AlertDialog.Builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("?????? ????????? ????????????")
        builder.setMessage("?????? ???????????? ?????? ????????????. ???????????? ?????? ????????? ??? ????????????.")

        builder.setCancelable(true)
        builder.setPositiveButton("??????", DialogInterface.OnClickListener { dialog, id ->
            val callGPSSettingIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            getGPSPermissionLauncher.launch(callGPSSettingIntent)
        })

        builder.setNegativeButton("??????", DialogInterface.OnClickListener { dialog, id ->
            dialog.cancel()
            Toast.makeText(this@MainActivity, "???????????? ???????????????(GPS) ?????? ??? ??????????????????.", Toast.LENGTH_SHORT).show()
            finish()
        })

        builder.create().show()
    }

    fun getCurrentAddress(latitude: Double, longitude: Double): Address? {
        val geocoder = Geocoder(this, Locale.getDefault())
        val addresses: List<Address>?

        addresses = try {
            geocoder.getFromLocation(latitude, longitude, 7)
        } catch (ioException: IOException) {
            Toast.makeText(this, "???????????? ????????? ?????????????????????.", Toast.LENGTH_LONG).show()
            return null
        }

        if (addresses == null || addresses.size == 0) {
            Toast.makeText(this, "????????? ???????????? ???????????????.", Toast.LENGTH_LONG).show()
            return null
        }

        val address: Address = addresses[0]
        return address
    }

    private fun setBannerAds() {
        MobileAds.initialize(this)
        val adRequest = AdRequest.Builder().build()
        binding.adView.loadAd(adRequest)

        binding.adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.d("ads log", "?????? ????????? ?????? ???????????????.")
            }

            override fun onAdFailedToLoad(p0: LoadAdError) {
                Log.d("ads log", "?????? ????????? ?????? ??????????????????.")
            }

            override fun onAdOpened() {
                Log.d("ads log", "?????? ????????? ??????????????????.")
            }

            override fun onAdClosed() {
                Log.d("ads log", "?????? ????????? ???????????????.")
            }
        }
    }

    private fun setInterstitialAds() {
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(this, "ca-app-pub-3940256099942544/8691691433", adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(p0: LoadAdError) {
                Log.d("ads log", "?????? ????????? ?????? ??????????????????.${p0.responseInfo}")
                mInterstitialAd = null
            }

            override fun onAdLoaded(p0: InterstitialAd) {
                Log.d("ads log", "?????? ????????? ?????????????????????.")
                mInterstitialAd = p0
            }
        })
    }
}