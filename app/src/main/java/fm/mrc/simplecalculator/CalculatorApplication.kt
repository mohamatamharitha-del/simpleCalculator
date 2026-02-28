package fm.mrc.simplecalculator

import android.app.Application
import fm.mrc.simplecalculator.utils.CrashHandler

class CalculatorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize the global crash handler
        CrashHandler.init(this)
    }
}
