package com.gaotong.idledungeon.viewmodel

import com.gaotong.idledungeon.model.QuestType

// 检查当前主线任务是否完成; 完成则标完成、index+1、公会等级+1、亮红点.
// while 循环是为了处理一次状态更新让多个任务连环完成的极端情况.
fun advanceQuestsIfNeeded(state: GameUiState): GameUiState {
    var s = state
    while (true) {
        val idx = s.gameState.mainQuestIndex
        if (idx >= s.quests.size) break
        val q = s.quests[idx]
        val complete = when (q.type) {
            QuestType.EXPLORE_ROOMS -> s.gameState.roomsExplored >= q.targetCount
            QuestType.DEFEAT_BOSS   -> q.targetFloor in s.gameState.bossesDefeated
            QuestType.FIND_ITEM     -> q.targetItemId != null && q.targetItemId in s.gameState.foundItemIds
        }
        if (!complete) break

        val newQuests = s.quests.toMutableList()
        newQuests[idx] = q.copy(isCompleted = true)
        val oldGuild = s.gameState.guild
        s = s.copy(
            quests = newQuests,
            gameState = s.gameState.copy(
                mainQuestIndex = idx + 1,
                guild = oldGuild.copy(
                    guildRank     = oldGuild.guildRank + 1,
                    maxCarryItems = oldGuild.maxCarryItems + 5
                ),
                hasUnreadQuestUpdate = true
            )
        )
    }
    return s
}
