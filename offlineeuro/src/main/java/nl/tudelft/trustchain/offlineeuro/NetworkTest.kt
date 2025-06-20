package nl.tudelft.trustchain.offlineeuro

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import androidx.core.content.getSystemService

class NetworkTest {
    @SuppressLint("DefaultLocale")
    companion object {
        fun getLocalIpAddress(requireContext: Context): String? {
            val wifiManager = requireContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipAddress = wifiInfo.ipAddress
            return String.format(
                "%d.%d.%d.%d",
                (ipAddress and 0xff),
                (ipAddress shr 8 and 0xff),
                (ipAddress shr 16 and 0xff),
                (ipAddress shr 24 and 0xff)
            )
        }
    }
}
