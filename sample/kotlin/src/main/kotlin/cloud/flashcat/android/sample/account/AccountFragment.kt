/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sample.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import cloud.flashcat.android.Datadog
import cloud.flashcat.android.sample.Preferences
import cloud.flashcat.android.sample.R
import cloud.flashcat.android.trace.withinSpan
import com.google.android.material.snackbar.Snackbar

internal class AccountFragment : Fragment(), View.OnClickListener {

    lateinit var idField: EditText
    lateinit var nameField: EditText
    lateinit var accountRoleField: EditText
    lateinit var accountAgeField: EditText

    // region Fragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView: View = inflater.inflate(R.layout.fragment_account, container, false)
        idField = rootView.findViewById(R.id.account_id)
        nameField = rootView.findViewById(R.id.account_name)
        accountRoleField = rootView.findViewById(R.id.account_role)
        accountAgeField = rootView.findViewById(R.id.account_age)
        rootView.findViewById<View>(R.id.save_account).setOnClickListener(this)
        return rootView
    }

    override fun onResume() {
        super.onResume()
        val preferences = Preferences.defaultPreferences(requireContext())
        idField.setText(preferences.getAccountId())
        nameField.setText(preferences.getAccountName())
        accountRoleField.setText(preferences.getAccountRole())
        accountAgeField.setText(preferences.getAccountAge().toString())
    }

    // endregion

    // region View.OnClickListener

    override fun onClick(v: View) {
        withinSpan("updateAccountInfo") {
            val id: String = idField.text.toString()
            val name: String = nameField.text.toString()
            val role: String = accountRoleField.text.toString()
            val age: Int = Integer.valueOf(accountAgeField.text.toString())
            Preferences.defaultPreferences(requireContext())
                .setAccountInfo(id, name, role, age)
            Datadog.setAccountInfo(id, name, emptyMap())
            Datadog.addAccountExtraInfo(
                mapOf<String, Any>(
                    ROLE_KEY to role,
                    AGE_KEY to age
                )
            )
            logMessage("Updated account info")
        }
        Snackbar.make(view ?: v.rootView, "Account info updated", Snackbar.LENGTH_SHORT).show()
    }

    // endregion

    companion object {
        internal const val ROLE_KEY = "role"
        internal const val AGE_KEY = "age"
    }
}
