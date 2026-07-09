package com.watercantracker.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.watercantracker.app.MainActivity
import com.watercantracker.app.data.local.WaterCanDatabase
import com.watercantracker.app.data.repository.MemberRepository
import com.watercantracker.app.data.repository.PaymentRepository

class WhoPaysNextWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db          = WaterCanDatabase.getInstance(context)
        val memberRepo  = MemberRepository(db.memberDao(), db.paymentDao())
        val paymentRepo = PaymentRepository(db.paymentDao())

        val lastPayment   = paymentRepo.getLastPayment()
        val result        = memberRepo.resolveNextPayer(lastPayment?.paidByMemberId)
        val nextPayerName = result.member?.name
        val activeCount   = db.memberDao().getActiveMembers().size

        provideContent {
            WidgetContent(nextPayerName = nextPayerName, activeCount = activeCount)
        }
    }
}

@Composable
private fun WidgetContent(nextPayerName: String?, activeCount: Int) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF0B5D6E)))
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "\uD83D\uDCA7 Next to Pay",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFADE8EB)),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            if (nextPayerName != null) {
                Text(
                    text = nextPayerName,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "$activeCount active members",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFADE8EB)),
                        fontSize = 10.sp
                    )
                )
            } else {
                Text(
                    text = "No members added",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFADE8EB)),
                        fontSize = 14.sp
                    )
                )
            }
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                text = "Tap to record payment \u2192",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFE8893B)),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

class WhoPaysNextWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WhoPaysNextWidget()
}
