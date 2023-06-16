package uk.ac.cam.energy.socketclient

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import uk.ac.cam.energy.common.operations.Operation
import uk.ac.cam.energy.socketclient.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private val pauses = createPausesList()

    private lateinit var binding: ActivityMainBinding
    private lateinit var operations: List<Operation>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        operations = createOperationsList(this)

        binding.spinnerOperation.let { spinner ->
            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                operations.map { it.name }
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
        }

        binding.spinnerPauses.let { spinner ->
            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                pauses.map { it.name }
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
        }

        binding.buttonStart.setOnClickListener {
            val selectedPause = getSelectedPause()
            val selectedOperation = getSelectedOperation()
            startWorkload(WorkLoad(selectedPause.pauseMs, selectedOperation.name))
        }

        binding.buttonStop.setOnClickListener {
            stopService(Intent(this, ForegroundService::class.java))
        }
    }

    private fun startWorkload(workLoad: WorkLoad) {
        Log.i("MainActivity", "workload=$workLoad")
        val intent = Intent(this, ForegroundService::class.java)
        workLoad.addToExtras(intent)

        startService(intent)
    }

    private fun getSelectedPause(): Pause {
        val selectedString = binding.spinnerPauses.selectedItem as String
        return pauses.first { it.name == selectedString }
    }

    private fun getSelectedOperation(): Operation {
        val selectedString = binding.spinnerOperation.selectedItem as String
        return operations.first { it.name == selectedString }
    }
}
