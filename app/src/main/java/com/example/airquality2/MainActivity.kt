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
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.airquality2.retrofit.AirQualityResponse
import com.example.airquality2.retrofit.AirQualityService
import com.example.airquality2.retrofit.RetrofitConnection
import java.io.IOException
import java.util.Locale
import javax.security.auth.callback.Callback

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding

    private val PERMISSIONS_REQUEST_CODE = 100

    var REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION)

    lateinit var getGPSPermissionLauncher: ActivityResultLauncher<Intent>

    lateinit var locationProvider: LocationProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAllPermissions()
        updateUI()
    }

    private fun updateUI() {
        locationProvider = com.example.airquality2.LocationProvider(this@MainActivity)

        val latitude: Double = locationProvider.getLocationLatitude()
        val longitude: Double = locationProvider.getLocationLongitude()

        if (latitude != 0.0 || longitude != 0.0) {
            val address = getCurrentAddress(latitude, longitude)
            address?.let {
                binding.tvLocationTitle.text = "${it.thoroughfare}"
                binding.tvLocationSubtitle.text = "${it.countryName} ${it.adminArea}"
            }
            getAirQualityData(latitude, longitude)
        } else {
            Toast.makeText(this@MainActivity, "위도, 경도 정보를 가져올 수 없습니다. 새로고침을 눌러주세요." , Toast.LENGTH_LONG).show()
        }
    }

    private fun getAirQualityData(latitude: Double, longitude: Double) {
        val retrofitAPI = RetrofitConnection.getInstance().create(AirQualityService::class.java)

        retrofitAPI.getAirQualityData(latitude.toString(), longitude.toString(), "6adddc22-eab9-4f28-9389-30e7049fe085").enqueue(object : Callback<AirQualityResponse> {

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
                Toast.makeText(this@MainActivity, "퍼미션이 거부되었습니다. 앱을 다시 실행하여 퍼미션을 허용해주세요", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(this@MainActivity, "위치 서비스를 사용할 수 없습니다.", Toast.LENGTH_LONG).show()
                }
                finish()
            }
        }

        val builder: AlertDialog.Builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("위치 서비스 비활성화")
        builder.setMessage("위치 서비스가 꺼져 있습니다. 설정해야 앱을 사용할 수 있습니다.")

        builder.setCancelable(true)
        builder.setPositiveButton("설정", DialogInterface.OnClickListener { dialog, id ->
            val callGPSSettingIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            getGPSPermissionLauncher.launch(callGPSSettingIntent)
        })

        builder.setNegativeButton("취소", DialogInterface.OnClickListener { dialog, id ->
            dialog.cancel()
            Toast.makeText(this@MainActivity, "기기에서 위치서비스(GPS) 설정 후 사용해주세요.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "지오코더 서비스 사용불가합니다.", Toast.LENGTH_LONG).show()
            return null
        }

        if (addresses == null || addresses.size == 0) {
            Toast.makeText(this, "주소가 발견되지 않았습니다.", Toast.LENGTH_LONG).show()
            return null
        }

        val address: Address = addresses[0]
        return address
    }
}