package fi.torma.ovi

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import java.util.LinkedList

class LogsScreen(carContext: CarContext, private var logs: LinkedList<String>) :
    Screen(carContext) {
    override fun onGetTemplate(): Template {
        val clearAction = Action.Builder().setTitle("Clear").setOnClickListener {
            logs.clear()
            invalidate()
        }.build()

        val actionStrip = ActionStrip.Builder().addAction(clearAction).build()

        val itemListBuilder = ItemList.Builder()

        if (logs.isNotEmpty()) {
            logs.forEach { line ->
                itemListBuilder.addItem(Row.Builder().setTitle(line).build())
            }
        } else {
            itemListBuilder.addItem(Row.Builder().setTitle("No logs").build())
        }

        return ListTemplate.Builder()
            .setLoading(false)
            .setSingleList(itemListBuilder.build())
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStrip)
            .setTitle("Logs")
            .build()
    }
}