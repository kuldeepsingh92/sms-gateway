package com.utility.smsgateway

import android.app.Application
import com.utility.smsgateway.data.repository.GatewayRepository

class GatewayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        GatewayRepository.updateLocalIp()
    }
}

