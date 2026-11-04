package io.roadsignal.consumer

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import io.roadsignal.cmt.CmtIntegrationInfo
import io.roadsignal.sdk.RoadSignal

class ConsumerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sdk = RoadSignal.get(applicationContext)
        setContentView(
            TextView(this).apply {
                text =
                    "RoadSignal ${RoadSignal.VERSION}\nPublished AAR + transitive core resolved.\nCMT integration: ${CmtIntegrationInfo.MODE}\nNo consent; no sensing starts."
                append("\n" + JavaContractSmoke.describe(this@ConsumerActivity))
                textSize = 22f
                setPadding(32, 64, 32, 32)
            },
        )
    }
}
