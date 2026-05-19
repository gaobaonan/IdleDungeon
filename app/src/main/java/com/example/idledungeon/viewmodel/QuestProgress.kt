package com.example.idledungeon.viewmodel

import com.example.idledungeon.model.QuestType

/**
 * 任务推进检查: 看当前主线任务是否完成. 完成则:
 *   1. 把该任务标 isCompleted = true
 *   2. mainQuestIndex +1
 *   3. 公会等级 +1, maxCarryItems +5 (移除手动投资后的代替)
 *   4. hasUnreadQuestUpdate = true (城镇公会图标会显示红感叹号)
 *
 * 用 while 循环以处理"一次状态更新让多个任务连环完成"的极端情况
 * (例如同一回合同时拿到剑和击败 boss).
 */
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
