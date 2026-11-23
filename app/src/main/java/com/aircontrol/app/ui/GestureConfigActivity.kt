package com.aircontrol.app.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aircontrol.app.databinding.ActivityGestureConfigBinding
import com.aircontrol.app.model.GestureBinding
import com.aircontrol.app.model.GestureType
import com.aircontrol.app.model.PhoneAction
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Gesture Configuration screen — lets the user remap each GestureType to any PhoneAction.
 *
 * Shows a RecyclerView-style list (via LinearLayout + dynamic views) of all gestures
 * with a Spinner for the action on each row. Changes are saved to DataStore on press Save.
 */
class GestureConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGestureConfigBinding
    private val vm: MainViewModel by viewModels()

    private val allGestures = GestureType.values().filter { it != GestureType.UNKNOWN }
    private val allActions  = PhoneAction.values().toList()

    // Local mutable map for staging edits
    private val staged = mutableMapOf<GestureType, PhoneAction>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGestureConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Gesture Settings"

        lifecycleScope.launch {
            vm.gestureBindings.collectLatest { bindings ->
                staged.clear()
                for (b in bindings) {
                    runCatching {
                        staged[GestureType.valueOf(b.gesture)] = PhoneAction.valueOf(b.action)
                    }
                }
                // Fill defaults for any missing
                for (g in allGestures) {
                    if (staged[g] == null) staged[g] = PhoneAction.NONE
                }
                buildRows()
            }
        }

        binding.btnSave.setOnClickListener { save() }
        binding.btnReset.setOnClickListener { reset() }
    }

    private fun buildRows() {
        binding.containerGestures.removeAllViews()
        val inflater = layoutInflater
        for (gesture in allGestures) {
            val row = inflater.inflate(
                com.aircontrol.app.R.layout.item_gesture_row,
                binding.containerGestures, false
            )
            val tvGesture  = row.findViewById<android.widget.TextView>(com.aircontrol.app.R.id.tvGesture)
            val spinAction = row.findViewById<android.widget.Spinner>(com.aircontrol.app.R.id.spinnerAction)
            val swEnabled  = row.findViewById<android.widget.Switch>(com.aircontrol.app.R.id.swEnabled)

            tvGesture.text = gesture.name.replace('_', ' ')

            val actionNames = allActions.map { it.name.replace('_', ' ') }
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, actionNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinAction.adapter = adapter

            val current = staged[gesture] ?: PhoneAction.NONE
            spinAction.setSelection(allActions.indexOf(current).coerceAtLeast(0))
            swEnabled.isChecked = current != PhoneAction.NONE

            spinAction.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                    staged[gesture] = allActions[pos]
                }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }
            swEnabled.setOnCheckedChangeListener { _, checked ->
                if (!checked) staged[gesture] = PhoneAction.NONE
            }

            binding.containerGestures.addView(row)
        }
    }

    private fun save() {
        val bindings = staged.map { (g, a) ->
            GestureBinding(g.name, a.name, a != PhoneAction.NONE)
        }
        vm.saveBindings(bindings)
        Toast.makeText(this, "Gesture map saved ✓", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun reset() {
        staged.clear()
        for ((g, a) in com.aircontrol.app.gesture.GestureEngine.defaultMap()) {
            staged[g] = a
        }
        buildRows()
    }

    override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
}
