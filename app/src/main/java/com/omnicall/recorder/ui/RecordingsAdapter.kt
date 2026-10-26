package com.omnicall.recorder.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.omnicall.recorder.R
import com.omnicall.recorder.data.model.CallRecord
import com.omnicall.recorder.databinding.ItemRecordingBinding
import com.omnicall.recorder.util.DateTimeUtils

class RecordingsAdapter(
    private val onPlayClick: (CallRecord) -> Unit,
    private val onShareClick: (CallRecord) -> Unit,
    private val onDeleteClick: (CallRecord) -> Unit
) : ListAdapter<CallRecord, RecordingsAdapter.RecordViewHolder>(DiffCallback) {

    var currentPlayingId: Long? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val binding = ItemRecordingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RecordViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RecordViewHolder(
        private val binding: ItemRecordingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(record: CallRecord) {
            val context: Context = binding.root.context

            // Format Caller and Phone
            binding.tvCallerName.text = record.callerName.ifBlank { "Unknown Caller" }
            binding.tvPhoneNumber.text = if (record.phoneNumber.isNotBlank()) {
                "${record.direction} • ${record.phoneNumber}"
            } else {
                "${record.direction} Call"
            }

            // Timestamp & Duration
            binding.tvTimestamp.text = DateTimeUtils.formatTimestamp(record.timestamp)
            val durationStr = DateTimeUtils.formatDuration(record.durationMs)
            val sizeStr = DateTimeUtils.formatFileSize(record.fileSizeBytes)
            binding.tvDuration.text = "⏱ $durationStr  •  $sizeStr"

            // Badge styling
            if (record.callType == "CELLULAR") {
                binding.badgeLayout.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_cellular)
                binding.ivAppIcon.setImageResource(R.drawable.ic_phone)
                binding.tvAppType.text = "Cellular"
                binding.tvAppType.setTextColor(ContextCompat.getColor(context, R.color.badge_cellular))
            } else {
                binding.badgeLayout.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_whatsapp)
                binding.ivAppIcon.setImageResource(R.drawable.ic_whatsapp)
                binding.tvAppType.text = "WhatsApp"
                binding.tvAppType.setTextColor(ContextCompat.getColor(context, R.color.badge_whatsapp))
            }

            // Play/Pause icon toggle
            val isPlaying = currentPlayingId == record.id
            binding.btnPlayPause.setIconResource(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )

            binding.btnPlayPause.setOnClickListener { onPlayClick(record) }
            binding.btnShare.setOnClickListener { onShareClick(record) }
            binding.btnDelete.setOnClickListener { onDeleteClick(record) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<CallRecord>() {
        override fun areItemsTheSame(oldItem: CallRecord, newItem: CallRecord): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CallRecord, newItem: CallRecord): Boolean {
            return oldItem == newItem
        }
    }
}
