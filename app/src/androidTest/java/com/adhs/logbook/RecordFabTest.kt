package com.adhs.logbook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class RecordFabTest {
    @get:Rule val ui=createComposeRule()
    private val previous=Locale.getDefault()
    @Before fun english() { Locale.setDefault(Locale.ENGLISH) }
    @After fun restore() { Locale.setDefault(previous) }

    private fun show(actions: List<RecordAction>) = ui.setContent {
        MaterialTheme {
            var expanded by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxSize()) { RecordFab(actions,expanded,{ expanded=it },Modifier.align(Alignment.BottomEnd)) }
        }
    }

    @Test fun singleActionOpensDirectly() {
        var opened=0
        show(listOf(RecordAction("Add observation",Icons.Outlined.EditNote) { opened++ }))
        ui.onNodeWithContentDescription("Add observation").performClick()
        assertEquals(1,opened)
    }

    @Test fun twoActionsExpandIntoLabelledMenu() {
        val opened=mutableListOf<String>()
        show(listOf(RecordAction("Add observation",Icons.Outlined.EditNote) { opened+="observation" },RecordAction("Add measurement",Icons.Outlined.MonitorHeart) { opened+="measurement" }))
        ui.onNodeWithText("Add measurement").assertDoesNotExist()
        ui.onNodeWithContentDescription("Add observation or measurement").performClick()
        ui.onNodeWithText("Add observation").assertIsDisplayed()
        ui.onNodeWithText("Add measurement").assertIsDisplayed().assertHasClickAction().performClick()
        assertEquals(listOf("measurement"),opened)
        ui.onNodeWithText("Add measurement").assertDoesNotExist()
        ui.onNodeWithContentDescription("Add observation or measurement").assertIsDisplayed()
    }
}
