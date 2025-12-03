/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sample.gdpr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import androidx.annotation.IdRes
import androidx.fragment.app.DialogFragment
import cloud.flashcat.android.Flashcat
import cloud.flashcat.android.privacy.TrackingConsent
import cloud.flashcat.android.sample.Preferences
import cloud.flashcat.android.sample.R
import cloud.flashcat.android.sample.TrackingConsentChangeListener

internal class GdprDialogFragment : DialogFragment() {
    lateinit var trackingConsentSelector: RadioGroup
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_gdpr, container, false)
        trackingConsentSelector = rootView.findViewById(R.id.tracking_consent_selector)
        @Suppress("CheckInternal") // not a Kotlin check
        trackingConsentSelector.check(
            resolveButtonIdFromConsent(
                Preferences.defaultPreferences(requireContext()).getTrackingConsent()
            )
        )
        trackingConsentSelector.setOnCheckedChangeListener { _, checkedId ->
            val trackingConsent = when (checkedId) {
                R.id.pending -> TrackingConsent.PENDING
                R.id.granted -> TrackingConsent.GRANTED
                else -> TrackingConsent.NOT_GRANTED
            }
            Flashcat.setTrackingConsent(trackingConsent)
            Preferences.defaultPreferences(requireContext()).setTrackingConsent(trackingConsent)
            (activity as? TrackingConsentChangeListener)?.onTrackingConsentChanged(trackingConsent)
        }
        return rootView
    }

    @IdRes
    private fun resolveButtonIdFromConsent(trackingConsent: TrackingConsent): Int {
        return when (trackingConsent) {
            TrackingConsent.PENDING -> R.id.pending
            TrackingConsent.GRANTED -> R.id.granted
            TrackingConsent.NOT_GRANTED -> R.id.not_granted
        }
    }
}
