package uk.ac.cam.energy.autopilot.ui

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.ac.cam.energy.autopilot.LogManager
import uk.ac.cam.energy.autopilot.ScenariosManager
import uk.ac.cam.energy.autopilot.databinding.ActivityMainBinding
import uk.ac.cam.energy.autopilot.services.ForegroundRunnerService
import uk.ac.cam.energy.autopilot.services.RunConfig
import uk.ac.cam.energy.autopilot.services.RunnerStatus
import uk.ac.cam.energy.autopilot.services.StatusListener
import uk.ac.cam.energy.common.execution.ScenarioFileParser
import uk.ac.cam.energy.common.execution.getGlobalUsbSignaller


class MainActivity : AppCompatActivity(), StatusListener {

    private val runnerDispatcher = Dispatchers.Default
    private lateinit var binding: ActivityMainBinding

    private var mRunnerService: ForegroundRunnerService? = null
    private var mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            mRunnerService = (binder as ForegroundRunnerService.RunnerBinder).getService()
            mRunnerService?.registerStatusListener(this@MainActivity)
        }

        override fun onServiceDisconnected(className: ComponentName) {
            mRunnerService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonSync.setOnClickListener { MainScope().launch { sync() } }
        binding.buttonInitUsb.setOnClickListener { MainScope().launch { initUsb() } }
        binding.buttonStart.setOnClickListener { MainScope().launch { startService() } }
        binding.buttonStop.setOnClickListener { MainScope().launch { stopService() } }

        refreshScenarioSpinner()
        setupSpinnerPersistence()

        // check storage permissions
        val readPermission = ContextCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE)
        if (readPermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(READ_EXTERNAL_STORAGE), 0)
        }
    }

    private suspend fun initUsb() {
        binding.buttonInitUsb.isEnabled = false
        withContext(runnerDispatcher) {
            getGlobalUsbSignaller(applicationContext).initialize()
        }
        binding.buttonInitUsb.isEnabled = true
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService()
    }

    private suspend fun sync() {
        binding.buttonSync.isEnabled = false
        withContext(runnerDispatcher) {
            try {
                val scenariosManager = ScenariosManager(applicationContext)
                scenariosManager.removeScenarios()
                scenariosManager.downloadScenarios()
                log("downloaded scenarios")

                val logManager = LogManager(applicationContext)
                logManager.uploadAllResults()
                log("uploaded results")
            } catch (e: Exception) {
                log("download failed: $e")
            }
        }
        refreshScenarioSpinner()
        binding.buttonSync.isEnabled = true
    }

    private fun startService() {
        ensureScenarioFileValid(getCurrentScenarioName())
        binding.buttonStart.isEnabled = false

        val runConfig = RunConfig(
            useUsb = binding.switchUseUsb.isChecked,
            executionType = when {
                binding.radioButtonWakelock.isChecked -> RunConfig.EXECUTION_WAKELOCK
                binding.radioButtonAlarmManager.isChecked -> RunConfig.EXECUTION_ALARM_MANAGER
                else -> throw IllegalArgumentException()
            },
            scenarioFileName = getCurrentScenarioName()
        )

        try {
            // start (if not already started)
            val intent = Intent(this, ForegroundRunnerService::class.java)
            runConfig.addToExtras(intent)

            startService(intent)

            // then bind to observe
            val bound = bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE)
            log("bound: $bound")
        } catch (e: Exception) {
            log("setup failed: $e")
            e.printStackTrace()
        } finally {
            binding.buttonStart.isEnabled = true
        }
    }

    private fun stopService() {
        // unbind
        unbindService()

        // then stop
        val intent = Intent(this, ForegroundRunnerService::class.java)
        stopService(intent)
    }

    private fun unbindService() {
        if (mRunnerService != null) {
            mRunnerService?.registerStatusListener(null)
            mRunnerService = null
            unbindService(mServiceConnection)
        }
    }

    private fun ensureScenarioFileValid(scenarioFileName: String) {
        val sequenceFile = ScenariosManager(applicationContext).getFileForName(scenarioFileName)
        val operations = ScenarioFileParser(applicationContext).parseFromStorage(sequenceFile)
        if (operations.isEmpty()) {
            throw RuntimeException("sequence file is empty")
        }
    }

    private fun refreshScenarioSpinner() {
        val entries = ScenariosManager(applicationContext).listScenarios()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, entries)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerScenario.adapter = adapter

        // set to last selected scenario
        val lastSelected = getSharedPreferences("main", Context.MODE_PRIVATE)
            .getString("last_scenario", null)
        lastSelected?.let {
            val index = entries.indexOf(it)
            if (index >= 0) binding.spinnerScenario.setSelection(index)
        }
    }

    private fun getCurrentScenarioName(): String {
        val item = binding.spinnerScenario.selectedItem
        return (item as String)
    }

    override fun onNewStatus(status: RunnerStatus) {
        MainScope().launch {
            binding.checkboxUsbConnected.isChecked = status.step1_usbConnected
            binding.checkboxWaitForSync.isChecked = status.step2_sending
            binding.checkboxUsbDisconnected.isChecked = status.step3_usbDisconnected
            binding.checkboxOperationsStarted.isChecked = status.step5_startedOperations
            binding.checkboxOperationsFinished.isChecked = status.step6_finishedOperations
            binding.textLastMessage.text = status.lastMessage
        }
    }

    private fun setupSpinnerPersistence() {
        val spinner = binding.spinnerScenario
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                // update preference to last selected item
                getSharedPreferences("main", MODE_PRIVATE)
                    .edit()
                    .putString("last_scenario", binding.spinnerScenario.selectedItem as String)
                    .apply()
            }
        }
    }

    private fun log(message: String) {
        runOnUiThread {
            Log.i("MainActivity", message)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
