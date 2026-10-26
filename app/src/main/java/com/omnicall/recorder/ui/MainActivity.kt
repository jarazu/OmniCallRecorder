package com.omnicall.recorder.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.omnicall.recorder.OmniCallApplication
import com.omnicall.recorder.R
import com.omnicall.recorder.data.model.CallRecord
import com.omnicall.recorder.databinding.ActivityMainBinding
import com.omnicall.recorder.util.AudioPlayerManager
import com.omnicall.recorder.util.DateTimeUtils
import com.omnicall.recorder.util.FileUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: RecordingsAdapter
    private val playerManager = AudioPlayerManager()

    private var currentFilterType: String? = null // null means All
    private var observeJob: Job? = null
    private var activePlayingRecord: CallRecord? = null

    private val playerHandler = Handler(Looper.getMainLooper())
    private val playerUpdateRunnable = object : Runnable {
        override fun run() {
            if (playerManager.isPlaying) {
                val current = playerManager.currentPosition
                val total = playerManager.duration
                if (total > 0) {
                    binding.playerSeekBar.progress = (current * 1000L / total).toInt()
                    binding.tvPlayerTime.text = "${DateTimeUtils.formatDuration(current.toLong())} / ${DateTimeUtils.formatDuration(total.toLong())}"
                }
                playerHandler.postDelayed(this, 300)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
        loadRecordings()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatusCard()
    }

    private fun updateServiceStatusCard() {
        val allGranted = PermissionsActivity.areAllEssentialPermissionsGranted(this)
        if (allGranted) {
            binding.ivStatusDot.setImageResource(R.drawable.ic_check_circle)
            binding.tvBannerTitle.text = getString(R.string.status_service_active)
            binding.tvBannerTitle.setTextColor(ContextCompat.getColor(this, R.color.status_green))
            binding.tvBannerSubtitle.text = getString(R.string.loudspeaker_tip)
            binding.btnPermissionsSetup.visibility = View.GONE
        } else {
            binding.ivStatusDot.setImageResource(R.drawable.ic_alert_circle)
            binding.tvBannerTitle.text = getString(R.string.status_service_inactive)
            binding.tvBannerTitle.setTextColor(ContextCompat.getColor(this, R.color.status_amber))
            binding.tvBannerSubtitle.text = getString(R.string.setup_required_hint)
            binding.btnPermissionsSetup.visibility = View.VISIBLE
        }
    }

    private fun setupRecyclerView() {
        adapter = RecordingsAdapter(
            onPlayClick = { record -> handlePlayPause(record) },
            onShareClick = { record -> FileUtils.shareAudioFile(this, record.filePath) },
            onDeleteClick = { record -> confirmDelete(record) }
        )

        binding.rvRecordings.layoutManager = LinearLayoutManager(this)
        binding.rvRecordings.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnPermissionsSetup.setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }

        binding.cardStatusBanner.setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }

        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            when {
                checkedIds.contains(R.id.chipWhatsApp) -> {
                    currentFilterType = "WHATSAPP"
                }
                checkedIds.contains(R.id.chipCellular) -> {
                    currentFilterType = "CELLULAR"
                }
                else -> {
                    currentFilterType = null
                }
            }
            loadRecordings(binding.etSearch.text?.toString() ?: "")
        }

        binding.etSearch.addTextChangedListener { editable ->
            loadRecordings(editable?.toString() ?: "")
        }

        // Bottom Player controls
        binding.btnPlayerPlayPause.setOnClickListener {
            if (playerManager.isPlaying) {
                playerManager.pause()
                binding.btnPlayerPlayPause.setImageResource(R.drawable.ic_play)
                playerHandler.removeCallbacks(playerUpdateRunnable)
            } else {
                playerManager.resume()
                binding.btnPlayerPlayPause.setImageResource(R.drawable.ic_pause)
                playerHandler.post(playerUpdateRunnable)
            }
        }

        binding.btnPlayerClose.setOnClickListener {
            stopAndHidePlayer()
        }

        binding.playerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val total = playerManager.duration
                    val newPos = (progress * total.toLong() / 1000L).toInt()
                    playerManager.seekTo(newPos)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun loadRecordings(query: String = "") {
        observeJob?.cancel()
        observeJob = lifecycleScope.launch {
            val dao = OmniCallApplication.database.callRecordDao()
            val flow = when {
                query.isNotBlank() -> dao.searchRecordings(query)
                currentFilterType != null -> dao.getRecordingsByType(currentFilterType!!)
                else -> dao.getAllRecordings()
            }

            flow.collectLatest { list ->
                adapter.submitList(list)
                if (list.isEmpty()) {
                    binding.emptyStateLayout.visibility = View.VISIBLE
                    binding.rvRecordings.visibility = View.GONE
                } else {
                    binding.emptyStateLayout.visibility = View.GONE
                    binding.rvRecordings.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun handlePlayPause(record: CallRecord) {
        if (activePlayingRecord?.id == record.id && playerManager.isPlaying) {
            playerManager.pause()
            adapter.currentPlayingId = null
            binding.btnPlayerPlayPause.setImageResource(R.drawable.ic_play)
            playerHandler.removeCallbacks(playerUpdateRunnable)
        } else if (activePlayingRecord?.id == record.id) {
            playerManager.resume()
            adapter.currentPlayingId = record.id
            binding.btnPlayerPlayPause.setImageResource(R.drawable.ic_pause)
            playerHandler.post(playerUpdateRunnable)
        } else {
            val file = File(record.filePath)
            if (!file.exists()) {
                Toast.makeText(this, "Audio file does not exist on disk", Toast.LENGTH_SHORT).show()
                return
            }

            activePlayingRecord = record
            val success = playerManager.play(record.filePath) {
                stopAndHidePlayer()
            }

            if (success) {
                adapter.currentPlayingId = record.id
                binding.bottomPlayerCard.visibility = View.VISIBLE
                binding.tvPlayerCaller.text = "${record.callType}: ${record.callerName}"
                binding.btnPlayerPlayPause.setImageResource(R.drawable.ic_pause)
                playerHandler.post(playerUpdateRunnable)
            }
        }
    }

    private fun stopAndHidePlayer() {
        playerHandler.removeCallbacks(playerUpdateRunnable)
        playerManager.stop()
        activePlayingRecord = null
        adapter.currentPlayingId = null
        binding.bottomPlayerCard.visibility = View.GONE
    }

    private fun confirmDelete(record: CallRecord) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Call Recording")
            .setMessage("Are you sure you want to delete this recording for ${record.callerName}?")
            .setPositiveButton("Delete") { _, _ ->
                if (activePlayingRecord?.id == record.id) {
                    stopAndHidePlayer()
                }
                lifecycleScope.launch {
                    OmniCallApplication.database.callRecordDao().deleteRecord(record)
                    val file = File(record.filePath)
                    if (file.exists()) {
                        file.delete()
                    }
                    Toast.makeText(this@MainActivity, "Recording deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        playerHandler.removeCallbacks(playerUpdateRunnable)
        playerManager.release()
        super.onDestroy()
    }
}
