package com.pleosfriends

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.Executors

/** Parked vs moving uses AAOS CarUxRestrictions (Google GEAR_SELECTION / distraction optimization).
 * Pleos Vehicle 2.3.2's Gear API is @Deprecated toward that Google property, so it is not used here.
 * One low-frequency subscription per process, with no Activity reference.
 * Pleos 2.1's explicit unregister can deadlock during Activity destruction, so an Activity
 * must neither own nor synchronously unregister this application-scoped connection.
 */
internal object DrivingPolicy {
    var known by mutableStateOf(false); private set
    var restricted by mutableStateOf(true); private set
    private var started=false
    private var car:Car?=null
    private var manager:CarUxRestrictionsManager?=null
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor { Thread(it,"Pleos-driving-policy").apply { isDaemon=true } }
    private val listener=CarUxRestrictionsManager.OnUxRestrictionsChangedListener { value ->
        main.post { known=true;restricted=value.isRequiresDistractionOptimization }
    }
    @Synchronized fun initialize(context:Context) {
        if(started)return
        started=true
        runCatching {
            car=Car.createCar(context.applicationContext,main,Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connection,ready ->
                if(!ready) { main.post { known=false;restricted=true };worker.execute { manager=null } }
                else worker.execute {
                    runCatching {
                        val next=connection.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                        if(manager!==next) { next.registerListener(listener);manager=next }
                        listener.onUxRestrictionsChanged(next.currentCarUxRestrictions)
                    }.onFailure { main.post { known=false;restricted=true } }
                }
            }
        }.onFailure { known=false;restricted=true }
    }
}
