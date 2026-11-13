package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.groups.GroupDetail
import com.example.ui.screens.GroupDetailScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupDetailTabStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectingMembersTabWhileGroupDetailsLoadingDoesNotResetTab() {
        var groupDetail by mutableStateOf<GroupDetail?>(null)

        composeRule.setContent {
            GroupDetailScreen(
                currentUid = "user_123",
                groupId = "group_test_id",
                groupDetail = groupDetail,
                fallbackUsername = "test-group",
                fallbackName = "Test Group",
                fallbackRole = "member",
                tasks = emptyList(),
                members = emptyList(),
                doneTaskIds = emptySet(),
                isLoading = false,
                onBack = {},
                onAddTaskClick = {},
                onEditTaskClick = {},
                onDeleteTaskClick = {},
                onToggleTaskDone = {},
                onSetMemberRole = { _, _ -> },
                onRemoveMember = {},
                onLeaveGroupClick = {},
                onDeleteGroupClick = {}
            )
        }

        // Tap the Members tab while groupDetail is null (still loading)
        composeRule.onNodeWithTag("group_tab_members").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("group_tab_members").assertIsSelected()

        // Asynchronously simulate group details finishing loading from server
        groupDetail = GroupDetail(
            id = "group_test_id",
            name = "Loaded Group Name",
            username = "test-group",
            ownerId = "user_123",
            joinCode = "TEST12"
        )
        composeRule.waitForIdle()

        // Verify that the Members tab is still selected and has NOT reset back to Tasks (tab 0).
        composeRule.onNodeWithTag("group_tab_members").assertIsSelected()
    }
}
