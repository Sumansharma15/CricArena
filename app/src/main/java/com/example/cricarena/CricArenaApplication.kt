package com.example.cricarena

import android.app.Application
import com.example.cricarena.ui.fantasyteam.FantasyMatchSession

class CricArenaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FantasyMatchSession.init(this)
    }
}
