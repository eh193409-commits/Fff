package com.example.sara.control

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import kotlin.math.roundToInt

class VolumeManager(private val context: Context) {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun getSnapshot(): VolumeSnapshot {
        val mediaPct = streamPercent(AudioManager.STREAM_MUSIC)
        val ringPct = streamPercent(AudioManager.STREAM_RING)
        val notifPct = streamPercent(AudioManager.STREAM_NOTIFICATION)
        val alarmPct = streamPercent(AudioManager.STREAM_ALARM)

        val ringerMode = audioManager.ringerMode
        val ringerLabel = when (ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            else -> "Normal"
        }

        return VolumeSnapshot(
            mediaPercent = mediaPct,
            ringtonePercent = ringPct,
            notificationPercent = notifPct,
            alarmPercent = alarmPct,
            isMediaMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC) || mediaPct == 0,
            isRingerMuted = ringerMode != AudioManager.RINGER_MODE_NORMAL || ringPct == 0,
            ringerModeLabel = ringerLabel
        )
    }

    fun increaseVolume(stream: AudioStreamType = AudioStreamType.MEDIA): ControlResult {
        return try {
            val androidStream = toAndroidStream(stream)
            audioManager.adjustStreamVolume(
                androidStream,
                AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI
            )
            val newPct = streamPercent(androidStream)
            ControlResult.Success(
                spokenResponse = "${stream.label} volume increased to $newPct percent.",
                detail = "${stream.label}: $newPct%"
            )
        } catch (e: SecurityException) {
            ControlResult.PermissionRequired(
                permission = PermissionType.NOTIFICATION_POLICY,
                spokenResponse = "Android requires Do Not Disturb access before changing ${stream.label.lowercase()} volume from Silent mode.",
                explanation = e.localizedMessage ?: "Notification policy access required"
            )
        } catch (e: Exception) {
            ControlResult.Error(
                spokenResponse = "I couldn't increase the ${stream.label.lowercase()} volume.",
                technicalReason = e.localizedMessage ?: "AudioManager error"
            )
        }
    }

    fun decreaseVolume(stream: AudioStreamType = AudioStreamType.MEDIA): ControlResult {
        return try {
            val androidStream = toAndroidStream(stream)
            audioManager.adjustStreamVolume(
                androidStream,
                AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
            val newPct = streamPercent(androidStream)
            ControlResult.Success(
                spokenResponse = "${stream.label} volume lowered to $newPct percent.",
                detail = "${stream.label}: $newPct%"
            )
        } catch (e: SecurityException) {
            ControlResult.PermissionRequired(
                permission = PermissionType.NOTIFICATION_POLICY,
                spokenResponse = "Do Not Disturb access is required to modify ${stream.label.lowercase()} volume.",
                explanation = e.localizedMessage ?: "Notification policy access required"
            )
        } catch (e: Exception) {
            ControlResult.Error(
                spokenResponse = "I couldn't lower the ${stream.label.lowercase()} volume.",
                technicalReason = e.localizedMessage ?: "AudioManager error"
            )
        }
    }

    fun setVolumePercent(
        percent: Int,
        stream: AudioStreamType = AudioStreamType.MEDIA
    ): ControlResult {
        val clamped = percent.coerceIn(0, 100)
        val androidStream = toAndroidStream(stream)
        return try {
            val max = audioManager.getStreamMaxVolume(androidStream).coerceAtLeast(1)
            val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                audioManager.getStreamMinVolume(androidStream)
            } else 0
            val targetIndex = min + ((max - min) * (clamped / 100f)).roundToInt()
            audioManager.setStreamVolume(androidStream, targetIndex, AudioManager.FLAG_SHOW_UI)
            val actualPct = streamPercent(androidStream)
            ControlResult.Success(
                spokenResponse = "${stream.label} volume set to $actualPct percent.",
                detail = "${stream.label}: $actualPct%"
            )
        } catch (e: SecurityException) {
            ControlResult.PermissionRequired(
                permission = PermissionType.NOTIFICATION_POLICY,
                spokenResponse = "Please grant Do Not Disturb access so I can adjust ${stream.label.lowercase()} volume.",
                explanation = "Android protects ringer/notification DND changes behind Notification Policy Access."
            )
        } catch (e: Exception) {
            ControlResult.Error(
                spokenResponse = "I couldn't set the ${stream.label.lowercase()} volume.",
                technicalReason = e.localizedMessage ?: "setStreamVolume failed"
            )
        }
    }

    fun mute(stream: AudioStreamType = AudioStreamType.MEDIA): ControlResult {
        val androidStream = toAndroidStream(stream)
        return try {
            if (stream == AudioStreamType.RINGTONE && !notificationManager.isNotificationPolicyAccessGranted) {
                return ControlResult.PermissionRequired(
                    permission = PermissionType.NOTIFICATION_POLICY,
                    spokenResponse = "To mute the ringtone cleanly, Android requires Do Not Disturb access.",
                    explanation = "Muting the ringer changes the device Do Not Disturb state on Android 7+."
                )
            }
            audioManager.adjustStreamVolume(
                androidStream,
                AudioManager.ADJUST_MUTE,
                AudioManager.FLAG_SHOW_UI
            )
            ControlResult.Success("${stream.label} audio muted.")
        } catch (e: SecurityException) {
            ControlResult.PermissionRequired(
                permission = PermissionType.NOTIFICATION_POLICY,
                spokenResponse = "Android requires Do Not Disturb access to mute ${stream.label.lowercase()}.",
                explanation = e.localizedMessage ?: "SecurityException"
            )
        }
    }

    fun unmute(stream: AudioStreamType = AudioStreamType.MEDIA): ControlResult {
        val androidStream = toAndroidStream(stream)
        return try {
            if (stream == AudioStreamType.RINGTONE && !notificationManager.isNotificationPolicyAccessGranted) {
                return ControlResult.PermissionRequired(
                    permission = PermissionType.NOTIFICATION_POLICY,
                    spokenResponse = "To unmute the ringtone, Android requires Do Not Disturb access.",
                    explanation = "Unmuting the ringer exits Do Not Disturb mode on Android 7+."
                )
            }
            audioManager.adjustStreamVolume(
                androidStream,
                AudioManager.ADJUST_UNMUTE,
                AudioManager.FLAG_SHOW_UI
            )
            ControlResult.Success("${stream.label} audio unmuted.")
        } catch (e: SecurityException) {
            ControlResult.PermissionRequired(
                permission = PermissionType.NOTIFICATION_POLICY,
                spokenResponse = "Android requires Do Not Disturb access to unmute ${stream.label.lowercase()}.",
                explanation = e.localizedMessage ?: "SecurityException"
            )
        }
    }

    private fun streamPercent(androidStream: Int): Int {
        val max = audioManager.getStreamMaxVolume(androidStream).coerceAtLeast(1)
        val current = audioManager.getStreamVolume(androidStream)
        return ((current.toFloat() / max.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    }

    private fun toAndroidStream(stream: AudioStreamType): Int = when (stream) {
        AudioStreamType.MEDIA -> AudioManager.STREAM_MUSIC
        AudioStreamType.RINGTONE -> AudioManager.STREAM_RING
        AudioStreamType.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
        AudioStreamType.ALARM -> AudioManager.STREAM_ALARM
    }
}
