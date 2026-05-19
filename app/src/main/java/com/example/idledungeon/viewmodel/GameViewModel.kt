package com.example.idledungeon.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.idledungeon.data.ItemRegistry
import com.example.idledungeon.data.SaveRepository
import com.example.idledungeon.data.SpellRegistry
import com.example.idledungeon.data.database.GameDatabase
import com.example.idledungeon.engine.ExplorationEngine
import com.example.idledungeon.model.*
import com.example.idledungeon.util.L
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/* ═══════════════════════════════════════════════════════════════════════════
 *  本文件用到的 Kotlin / Android / 协程 / Flow 关键语法速查
 *  按类别列出. 看代码遇到不认识的, 回这里查.
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * ── Kotlin 基础 ────────────────────────────────────────────────────────────
 *   package / import         包声明 / 导入. import xxx.* 表示导入该包下所有类.
 *   class                    类定义.
 *   object                   单例对象 (本文件未直接用, 但调了 ItemRegistry 等单例).
 *   data class               "数据类". 自动生成 equals/hashCode/copy/toString. 见文件底 GameUiState.
 *   companion object         类的"静态成员"区. 通过 GameViewModel.MAIN_QUESTS 访问, 不需要实例.
 *   val / var                val=只读(不可重赋值), var=可读写. 默认尽量用 val.
 *   private / public         可见性. public 是默认值, 不写就是 public.
 *   fun foo()                函数声明.
 *   fun foo() = expr         单表达式函数: = 后是返回值, 不需要 return / 大括号.
 *   嵌套函数                  fun 体内还能再写 fun. 见 generateRecruitableHeroes 里的 primary/secondary.
 *   Unit                     类似 Java 的 void, 表示"无返回值".
 *   init { }                 类的初始化块. 实例创建时立即执行 (在所有 val/var 初始化之后).
 *   = 默认参数                fun foo(x: Int = 5). 调用方可省略.
 *   构造函数参数               class Foo(val x: Int) : Bar(x) — 紧跟类名的 (...) 是主构造函数.
 *   : 继承 / 实现             class A : B() — 继承类要带 (); class A : I — 实现接口不带.
 *
 * ── 空安全 (NPE 防护) ──────────────────────────────────────────────────────
 *   T?                       可空类型. Hero? = 可能是 null 的 Hero. T = 不可能为 null.
 *   x?.foo                   安全调用. x 为 null 整个表达式 null, 不会崩.
 *   x ?: default             Elvis 操作符. 左边 null 时用右边. 例 hero.baseStats[stat] ?: 0.
 *   x?.let { it... }         x 不为 null 时执行 lambda, it 是 x. 用于"非空时再做事".
 *   x!!                      强制断言非空 (本文件没用, 一般也不该用).
 *
 * ── 字符串 ─────────────────────────────────────────────────────────────────
 *   "Hi $name"               字符串模板. $变量 直接插入.
 *   "${a + b}"               复杂表达式必须用 ${}.
 *   "..." + "..."            字符串拼接 (本文件 startExpedition 的 log 用了).
 *
 * ── 数据类操作 ─────────────────────────────────────────────────────────────
 *   hero.copy(name = "X")    返回一个改了某字段的新对象, 其它字段照旧. data class 自动生成.
 *   stat to value            中缀函数 to, 创建一个 Pair. 主要用于 mapOf(a to 1, b to 2).
 *   (stat, req) ->           解构. 把 Pair / Map.Entry 拆成两个变量, 可在 lambda 参数中用.
 *
 * ── 控制流 ─────────────────────────────────────────────────────────────────
 *   when (x) { v -> ...; else -> ... }    switch 升级版. 是表达式, 有返回值.
 *   when { 条件1 -> ...; 条件2 -> ... }     无参 when, 各分支是任意布尔条件.
 *   if (...) a else b                      也是表达式, 可直接当返回值用.
 *   for (i in collection)                  标准 for-each.
 *   try { } catch (e: Exception) { }       异常处理.
 *   return                   从函数返回.
 *   return@labelName         从带标签的 lambda 返回 (而非外层函数). 见 _uiState.update { return@update state }.
 *
 * ── lambda (匿名函数) ──────────────────────────────────────────────────────
 *   { x -> body }            显式参数.
 *   { body 用 it }           单参数时省略写法, it 自动指代参数.
 *   .apply { ... }           lambda 内 this=接收者, 返回该接收者本身. 用于"链式配置".
 *   .let { it... }           lambda 内 it=接收者, 返回 lambda 的结果. 用于"映射 + 非空守卫".
 *   .also { it... }          类似 let 但返回接收者本身 (本文件未用).
 *
 * ── 集合: 不可变 vs 可变 ───────────────────────────────────────────────────
 *   listOf / mapOf / setOf                  创建不可变集合 (没有 add/remove 方法).
 *   emptyList / emptyMap / emptySet         空的不可变集合.
 *   mutableListOf / mutableMapOf            创建可变集合.
 *   .toMutableList() / .toMutableMap()      把不可变拷贝成可变 (然后才能 add/removeAt).
 *   .toSet()                                转成 Set, 让 in 查询从 O(n) 提到 O(1).
 *
 * ── 集合: 操作 ─────────────────────────────────────────────────────────────
 *   list[i]                  索引访问. 越界抛异常.
 *   map[key]                 取值, key 不存在返回 null.
 *   list + x                 不可变操作: 返回追加 x 后的新 list, 原 list 不动.
 *   list - x                 返回移除元素 x 的新 list (只移第一个匹配).
 *   map + (key to value)     返回加了/覆盖该键值对的新 map.
 *   map - key                返回移除该 key 的新 map.
 *   list.size                元素个数.
 *   list.isEmpty() / isNotEmpty()           是否为空 / 是否非空.
 *   string.isBlank()         字符串是否为空白 (含全空格).
 *
 * ── 集合: 高阶函数 (lambda 驱动) ───────────────────────────────────────────
 *   .find { 谓词 }                   找第一个满足的, 找不到返回 null.
 *   .filter { } / .filterNot { }    过滤 / 反过滤, 返回新集合.
 *   .map { x -> 新值 }              一对一映射, 返回新集合.
 *   .any { 谓词 } / .all { 谓词 }    至少一个 / 所有都满足, 返回 Boolean.
 *   .count { 谓词 }                 数符合的个数.
 *   .fold(初始) { acc, x -> 新acc }  累加器. 从初始值起, 把每个元素并进去, 返回最终值.
 *   .first() / .firstOrNull()       第一个元素 (空集合 first 抛异常, OrNull 返回 null).
 *   .shuffled().take(n)             随机抽 n 个 (返回新 list, 不改原 list).
 *   .indexOfFirst { 谓词 } / .indexOf(x)    找下标, 找不到返回 -1.
 *   .associateBy { it.id }          List<X> → Map<id, X>. 项目里 RouterMap 之类常用.
 *
 * ── 集合: 可变操作 ─────────────────────────────────────────────────────────
 *   mutList.add(x) / .removeAt(idx) / .remove(x)        增删. 仅 mutable 才有.
 *   mutMap[key] = value                                 赋值 / 添加.
 *
 * ── 范围 / 数字 ────────────────────────────────────────────────────────────
 *   1..10                    IntRange, 1 到 10 (含两端).
 *   1 until 10               1 到 9 (不含右端).
 *   .random()                Range/Collection 的扩展, 随机抽一个.
 *   .coerceAtMost(n)         不超过 n. 例 (3 + shopLevel).coerceAtMost(pool.size).
 *   .coerceAtLeast(n)        不小于 n.
 *   .coerceIn(a, b)          限制在 [a, b].
 *
 * ── 枚举 ───────────────────────────────────────────────────────────────────
 *   MyEnum.entries           所有枚举值的 List (Kotlin 1.9+ 标准, 取代旧 .values()).
 *
 * ── 集合构造工厂 ───────────────────────────────────────────────────────────
 *   List(n) { i -> ... }     创建长度 n 的列表, lambda 给每个下标 i 生成元素. 见 generateRecruitableHeroes.
 *
 * ── 注解 ───────────────────────────────────────────────────────────────────
 *   @Volatile                多线程读写的字段必须加, 强制每次读主内存最新值, 防止 CPU 缓存导致看到旧值.
 *   @OptIn(FlowPreview::class)    显式同意使用预览中的 API (debounce 当前是 preview).
 *
 * ── ViewModel / Android ────────────────────────────────────────────────────
 *   AndroidViewModel(app)    ViewModel 子类, 持有 Application 引用 (可拿 Context, 用来开 DB / 文件等).
 *   viewModelScope           ViewModel 自带的协程作用域. ViewModel 销毁时自动取消所有协程.
 *   Application              整个 App 进程的 Context.
 *
 * ── 协程 (kotlinx.coroutines) ──────────────────────────────────────────────
 *   suspend                  标记可挂起函数. 只能在协程里或另一个 suspend 函数里调.
 *   launch { }               作用域上启动一个协程. 不阻塞调用方.
 *   try/catch in 协程        和普通 try 一样使用; 协程异常不 catch 会沿作用域向上传播.
 *   delay(ms)                可挂起的 sleep (本文件未用, 引擎里用).
 *
 * ── Flow ───────────────────────────────────────────────────────────────────
 *   MutableStateFlow(初值)   "可变状态流". 永远有当前值, 新订阅者立刻收到当前值.
 *   StateFlow                只读视图.
 *   .asStateFlow()           把 MutableStateFlow 包成只读 StateFlow 暴露给外部.
 *   .value                   同步读当前值 (StateFlow 才有, SharedFlow 没有).
 *   .update { 旧 -> 新 }      原子地把状态替换成 lambda 返回的新值. 多线程并发安全.
 *   MutableSharedFlow        "可变事件流". 不保留当前值, 每个事件只发一次. 一次性事件用.
 *   SharedFlow / .asSharedFlow()   同上, 只读视图.
 *   .tryEmit(x)              不阻塞地发一个事件; 队列满时按 onBufferOverflow 策略处理.
 *   extraBufferCapacity = N  缓冲槽数量.
 *   BufferOverflow.DROP_OLDEST     队列满时挤掉最旧的事件 (vs SUSPEND / DROP_LATEST).
 *   .collect { x -> ... }    订阅. 是 suspend 函数, 会一直跑到 Flow 关闭 (StateFlow 永不关).
 *   .drop(n)                 跳过前 n 个值后再 emit 给下游.
 *   .debounce(ms)            抖动期内只取最后一个值. 例 _uiState.debounce(500) 把 500ms 内的快速变化合并.
 *
 * ── Java 互操作 ────────────────────────────────────────────────────────────
 *   System.currentTimeMillis()    取当前时间戳 (毫秒).
 *   java.util.Calendar            Java 的日历 API. 注意 MONTH 是 0-11.
 *
 * ── 项目内部依赖 (见对应文件) ──────────────────────────────────────────────
 *   GameUiState              整个 UI 状态聚合 (data class), 见文件底部.
 *   GameState                持久化部分: 玩家名 / 公会 / 任务进度 / 图鉴 / 商店日期键. (model/GameModels.kt)
 *   Hero / Item / Quest / Monster / Spell / Trait     游戏核心数据类型. (model/GameModels.kt)
 *   ItemRegistry / SpellRegistry / MonsterRegistry    静态查找表 (object 单例).
 *   SaveRepository           Room 数据库的高层封装. load() / save() / clear().
 *   ExplorationEngine        探险引擎; 协程跑探险算法, 通过 _uiState 推送进度.
 *   L                        我们的 Logger. L.d/i/w/e — Logcat 过滤 "IdleDungeon".
 *   toast(tag, msg)          本类内私有工具, 同步 log + 推送 Snackbar 给 UI.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 */

/**
 * 全 App 唯一的 ViewModel — 装所有游戏状态, 所有 UI 都从这里读数据.
 *
 * ViewModel 的特点: Activity 旋转屏幕重建时, ViewModel 实例不会被销毁, 状态保留.
 * AndroidViewModel = ViewModel + 持有 Application 引用 (可拿 Context, 用来开 DB).
 *
 * 持久化:
 *   - init 块异步从 Room 读出存档, 没有就保留默认 (空白) 状态
 *   - uiState 任何变化触发一次去抖动 (500ms) 后写库, 避免每次操作都 IO
 *   - 新游戏启动前先清库, 防止旧数据混入
 */
@OptIn(FlowPreview::class)                                                  // debounce 还是 preview API, 必须显式同意
class GameViewModel(application: Application) : AndroidViewModel(application) {

    // _uiState 私有可写, uiState 公开只读. 这是 Kotlin 里"封装"StateFlow 的标准写法.
    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    // SharedFlow: 一次性事件流 (Snackbar 消息). 不像 StateFlow 保留"当前值".
    // extraBufferCapacity=8 + DROP_OLDEST: emit 永不阻塞, 队列满了挤掉最旧的.
    private val _userMessages = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val userMessages: SharedFlow<String> = _userMessages.asSharedFlow()

    // 内部统一入口: 同步 log + 推送给 UI. tag 用于 Logcat 分类.
    private fun toast(tag: String, msg: String) {
        L.i(tag, "user-msg: $msg")
        _userMessages.tryEmit(msg)                                          // tryEmit 不阻塞, 适合在普通方法里调
    }

    private val explorationEngine = ExplorationEngine()                     // 探险引擎单例, 装算法逻辑
    private var pendingNewGame = false                                      // 标记"等下要开新游戏", 真重置在 TOWN 显示后

    // SaveRepository 是给 Room 数据库套的高层封装. 这里只在 ViewModel 创建时取一次.
    private val saveRepo = SaveRepository(GameDatabase.getInstance(application))

    // init 块: ViewModel 被 new 出来时立刻执行 (构造函数后).
    init {
        // viewModelScope 是 ViewModel 自带的协程作用域, ViewModel 销毁时它自动取消所有子协程.
        viewModelScope.launch {
            val loadedState = saveRepo.load()                               // 异步读 Room
            // 守护: 用户可能在 load 完成前就点了"新游戏", _uiState 已被改成新存档. 这里别覆盖回去.
            if (loadedState != null && !_uiState.value.gameState.hasStarted) {
                _uiState.value = loadedState
                // 商店列表已从 snapshot 还原, 不再无条件 refreshShops().
                // 跨日的刷新由 App.ON_START → checkDailyRefresh() 自然触发.
                checkDailyRefresh()
            }
        }
        // 自动保存: state 任何变化 → 去抖动 500ms → 写库.
        // debounce 把 500ms 内连续变化合并成一次保存; hasStarted 守卫保证初始空 state
        // (hasStarted=false) 不会被误存 —— 这一条同时也挡掉了 StateFlow 订阅时重放的初始值,
        // 所以不需要再额外 .drop(1).
        viewModelScope.launch {
            _uiState
                .debounce(500L)
                .collect { state ->
                    if (state.gameState.hasStarted) {
                        saveRepo.save(state)
                    }
                }
        }
    }

    // companion object = 类的"静态成员". 通过 GameViewModel.MAIN_QUESTS 访问, 不需要实例.
    companion object {
        // 5 个主线任务, 顺序触发. 完成一个自动激活下一个.
        val MAIN_QUESTS: List<Quest> = listOf(
            Quest(
                id = "q1_explore15", title = "Into the Dungeon",
                description = "Explore 15 rooms",
                type = QuestType.EXPLORE_ROOMS, targetCount = 15
            ),
            Quest(
                id = "q2_boss1", title = "Slay the Rat King",
                description = "Defeat the Floor 1 Boss",
                type = QuestType.DEFEAT_BOSS, targetFloor = 1
            ),
            Quest(
                id = "q3_find_sword", title = "Legend of the Mossy Blade",
                description = "Find the Mossy Blade (Epic) in a guarded chest on Floor 2",
                type = QuestType.FIND_ITEM, targetItemId = QUEST_EPIC_SWORD_ID
            ),
            Quest(
                id = "q4_boss2", title = "Slay the Chieftain",
                description = "Defeat the Floor 2 Boss",
                type = QuestType.DEFEAT_BOSS, targetFloor = 2
            ),
            Quest(
                id = "q5_boss3", title = "Into the Abyss",
                description = "Defeat the Floor 3 Boss",
                type = QuestType.DEFEAT_BOSS, targetFloor = 3
            )
        )
    }

    // ─── 游戏存档 ─────────────────────────────────────────

    // 标记"等下要新游戏". 真正的重置延迟到 Town 屏幕加载后, 否则 StartScreen 退出动画里会闪现"继续"按钮.
    fun requestNewGame() { pendingNewGame = true }

    // 在 TOWN 的 LaunchedEffect 里调; 标志位还在就触发重置, 否则什么都不做.
    fun executePendingNewGame() {
        if (pendingNewGame) {
            pendingNewGame = false
            startNewGame()
        }
    }

    fun startNewGame() {
        L.i("VM", "startNewGame: reset state to a fresh save")
        // 不需要显式 clear DB: 后续自动保存会经 heroDao.replaceAll + snapshot upsert REPLACE 整体覆盖.
        // 显式 clear 反而会和 debounced save 形成 clear→save→clear 的竞态.
        // _uiState.update { 旧 -> 新 }: 原子地把 state 替换成 lambda 返回的新值.
        _uiState.update {
            GameUiState(
                gameState = GameState(hasStarted = true, playerName = "Guild Master"),
                heroes = emptyList(),
                inventory = emptyList(),
                quests = MAIN_QUESTS
            )
        }
        refreshShops()
    }

    // 玩家点开公会界面时调用, 清掉红感叹号.
    fun markQuestUpdateRead() {
        _uiState.update { state ->
            if (!state.gameState.hasUnreadQuestUpdate) return@update state          // 没有变化就别 emit, 省一次保存
            state.copy(gameState = state.gameState.copy(hasUnreadQuestUpdate = false))   // copy() 是 data class 的"改某字段返回新对象"
        }
    }

    // ─── 城镇经营 ─────────────────────────────────────────

    //常规升级
    fun invest(building: String) {
        val guild = _uiState.value.gameState.guild                          // .value 是 StateFlow 当前快照
        val cost = getInvestCost(building)
        // 把所有失败原因前置判断 — 失败时弹 toast, 不进入 update 块.
        if (guild.gold < cost) {
            toast("VM", "Not enough gold: upgrading $building costs $cost, you have ${guild.gold}")
            return
        }
        // when 是 Kotlin 的 switch 升级版: 可以写复合分支, 各分支独立判定+提示.
        when (building) {
            "guild" -> { toast("VM", "Upgrade guild? How did you even get here???"); return }
            "tavern" -> if (guild.tavernLevel >= 10) {
                toast("VM", "Tavern is at max level (10)"); return
            }
            "item_shop" -> if (guild.itemShopLevel >= 5) {
                toast("VM", "Item shop is at max level (5)"); return
            }
            "blacksmith" -> if (guild.blacksmithLevel >= 5) {
                toast("VM", "Blacksmith is at max level (5)"); return
            }
            else -> { L.w("VM", "invest: unknown building '$building'"); return }
        }

        _uiState.update { state ->
            val g = state.gameState.guild                                   // 重新取一次, 因为 .update {} 是原子的, lambda 内必须用最新值
            val newGuild = when (building) {
                "tavern" -> g.copy(tavernLevel = g.tavernLevel + 1, gold = g.gold - cost)
                "blacksmith" -> g.copy(blacksmithLevel = g.blacksmithLevel + 1, gold = g.gold - cost)
                "item_shop" -> g.copy(itemShopLevel = g.itemShopLevel + 1, gold = g.gold - cost)
                else -> g
            }
            state.copy(gameState = state.gameState.copy(guild = newGuild))
        }
        L.i("VM", "invest success: $building upgraded, cost $cost")
        refreshShops()                                                      // 升级后商品池可能变, 立即刷一次
    }

    fun getInvestCost(building: String): Int {
        val level = when (building) {
            "guild" -> _uiState.value.gameState.guild.guildRank
            "tavern" -> _uiState.value.gameState.guild.tavernLevel
            "blacksmith" -> _uiState.value.gameState.guild.blacksmithLevel
            "item_shop" -> _uiState.value.gameState.guild.itemShopLevel
            else -> 1
        }
        return level * 500                                                  // 简单线性公式
    }

    // ─── 商店刷新 ─────────────────────────────────────────

    // 检查是否进入新的一天 (现实时间). 是的话把铁匠铺/道具店/酒馆全部刷新.
    // 应在 App 进入前台 (ON_START) 时调用一次.
    fun checkDailyRefresh() {
        val today = currentDayKey()
        if (today > _uiState.value.gameState.lastShopRefreshDayKey) {
            refreshShops()
        }
    }

    // YYYYMMDD 格式日期键, 不依赖 java.time, 兼容老 Android 版本.
    private fun currentDayKey(): Int {                                      //获取当前时间的函数
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.YEAR) * 10000 +
            (cal.get(java.util.Calendar.MONTH) + 1) * 100 +                 //666
            cal.get(java.util.Calendar.DAY_OF_MONTH)
    }

    // 刷新酒馆招募 + 铁匠铺武器/护甲 + 道具店消耗品, 同时写入今日的 dayKey + 递增招募编号.
    private fun refreshShops() {
        _uiState.update { state ->
            val guild = state.gameState.guild
            val startNum = state.gameState.nextRecruitNumber
            val recruits = generateRecruitableHeroes(guild.tavernLevel, startNum)
            state.copy(
                recruitableHeroes = recruits,
                shopEquipments = generateShopItems(guild.blacksmithLevel, ItemType.EQUIPMENT),
                shopConsumables = generateShopItems(guild.itemShopLevel, ItemType.CONSUMABLE),
                gameState = state.gameState.copy(
                    lastShopRefreshDayKey = currentDayKey(),
                    nextRecruitNumber = startNum + recruits.size
                )
            )
        }
    }

    /**
     * 生成 N 名候选冒险者. 编号从 [startNumber] 开始累加, 跨刷新保证名字唯一.
     * 调用方负责把 startNumber + count 写回 GameState.nextRecruitNumber.
     */
    private fun generateRecruitableHeroes(tavernLevel: Int, startNumber: Int): List<Hero> {
        val count = 3 + tavernLevel                                         // 酒馆每升一级多 1 个候选
        // List(n) { i -> ... }: 创建长度为 n 的列表, lambda 给每个下标 i 生成元素.
        return List(count) { i ->
            val recruitNum = startNumber + i                                // 全局递增编号 (用于 id + 名字)
            val heroClass = HeroClass.entries.random()                      // .entries 是枚举所有值的 List, 随机抽一个
            val level = (1..tavernLevel).random()                       // 等级范围跟酒馆等级挂钩

            // 主属性 (5~11+level*3), 副属性 (2~5+level). 用嵌套函数让下面 mapOf 调用清爽.
            fun primary() = (5..11 + level*3).random()
            fun secondary() = (2..5 + level).random()

            // 按职业决定主/副属性是哪一项. mapOf 创建只读 Map.
            val stats = when (heroClass) {
                HeroClass.WARRIOR -> mapOf(
                    StatType.STR to primary(), StatType.DEX to secondary(),
                    StatType.INT to secondary(), StatType.FTH to secondary()
                )
                HeroClass.RANGER -> mapOf(
                    StatType.STR to secondary(), StatType.DEX to primary(),
                    StatType.INT to secondary(), StatType.FTH to secondary()
                )
                HeroClass.MAGE -> mapOf(
                    StatType.STR to secondary(), StatType.DEX to secondary(),
                    StatType.INT to primary(), StatType.FTH to secondary()
                )
                HeroClass.CLERIC -> mapOf(
                    StatType.STR to secondary(), StatType.DEX to secondary(),
                    StatType.INT to secondary(), StatType.FTH to primary()
                )
            }

            val baseHp = 50 + level * 10 + stats[StatType.STR]!! * 5
            val spells = SpellRegistry.computeSpells(stats, level, heroClass)
            Hero(
                id = "recruit_$recruitNum",
                name = "Adventurer $recruitNum",
                portraitUri = null,
                heroClass = heroClass, level = level,
                currentHp = baseHp, maxHp = baseHp,
                currentMp = 20 + level * 3, maxMp = 20 + level * 3,
                baseStats = stats, spells = spells
            )
        }
    }

    private fun generateShopItems(shopLevel: Int, type: ItemType): List<Item> {
        val pool = ItemRegistry.forShop(type, shopLevel)                    // 已过滤 purchasable + equipLevel <= shopLevel
        val count = (3 + shopLevel).coerceAtMost(pool.size)                 // coerceAtMost = 不超过. shuffled().take(n) = 随机抽 n 个
        return pool.shuffled().take(count)
    }

    // ─── 英雄管理 ─────────────────────────────────────────

    // 重命名英雄. 空白名字忽略, 自动 trim.
    fun renameHero(heroId: String, newName: String) {
        if (newName.isBlank()) return
        _uiState.update { state ->
            // .map { } 把列表的每个元素映射成新元素. List 是不可变的, 这里返回的是新 List.
            state.copy(heroes = state.heroes.map {
                if (it.id == heroId) it.copy(name = newName.trim()) else it
            })
        }
    }

    // 设置英雄头像 (传 null 清空回到默认图标).
    fun setHeroPortrait(heroId: String, uri: String?) {
        _uiState.update { state ->
            state.copy(heroes = state.heroes.map {
                if (it.id == heroId) it.copy(portraitUri = uri) else it
            })
        }
    }

    fun recruitHero(heroId: String) {
        val hero = _uiState.value.recruitableHeroes.find { it.id == heroId }
        if (hero == null) {
            L.w("VM", "recruitHero: id=$heroId not in recruit list (already recruited or refreshed)")
            toast("VM", "That adventurer is no longer available")
            return
        }
        _uiState.update { state ->
            // 招募后给一个新的 ID, 跟酒馆模板 ID 区分开 (防止下次刷新还以为是同一人).
            val recruited = hero.copy(id = "hero_${System.currentTimeMillis()}")
            state.copy(
                heroes = state.heroes + recruited,                          // List + 元素 = 新 List
                recruitableHeroes = state.recruitableHeroes - hero          // List - 元素 = 移除该元素的新 List
            )
        }
        toast("VM", "Recruited ${hero.name} (Lv.${hero.level} ${hero.heroClass})")
    }

    fun buyItem(itemId: String) {
        val state = _uiState.value
        // ?: 是 Elvis 操作符: 左边为 null 用右边. 这里依次找两个商店列表.
        val item = state.shopEquipments.find { it.id == itemId }
            ?: state.shopConsumables.find { it.id == itemId }
        if (item == null) {
            L.w("VM", "buyItem: id=$itemId not found in shop")
            toast("VM", "That item is no longer available")
            return
        }
        val cost = item.goldCost
        val guild = state.gameState.guild
        if (guild.gold < cost) {
            toast("VM", "Not enough gold: ${item.name} costs $cost, you have ${guild.gold}")
            return
        }
        _uiState.update { st ->
            val g = st.gameState.guild
            st.copy(
                inventory = st.inventory + item,
                shopEquipments = st.shopEquipments.filterNot { it.id == itemId },   // filterNot = 反过滤
                shopConsumables = st.shopConsumables.filterNot { it.id == itemId },
                gameState = st.gameState.copy(guild = g.copy(gold = g.gold - cost))
            )
        }
        L.i("VM", "buyItem: purchased ${item.name} for $cost")
        toast("VM", "Purchased ${item.name}")
    }

    // ─── 装备管理 ─────────────────────────────────────────

    // 检查英雄是否满足物品的属性需求 + 职业限制.
    fun meetsRequirements(hero: Hero, item: Item): Boolean {
        // .all { 谓词 }: 列表所有元素都满足谓词时返回 true. 这里是检查每条属性需求都达到.
        val statsOk = item.statRequirements.all { (stat, req) ->
            (hero.baseStats[stat] ?: 0) >= req                              // Map[key] 取不到时返回 null, ?: 0 兜底
        }
        // ?.let {}: 链式调用. weaponType 为 null 整个表达式 null; 否则把它传入 let 的 lambda.
        // 最后 ?: true 表示"没有职业限制就视为通过".
        val classOk = item.weaponType?.requiredClass()?.let { it == hero.heroClass } ?: true
        return statsOk && classOk
    }

    // 装备物品: 从仓库取出 → 装到英雄身上 (旧装备退回仓库).
    fun equipItem(heroId: String, item: Item) {
        val state = _uiState.value
        val hero = state.heroes.find { it.id == heroId }
        if (hero == null) { L.w("VM", "equipItem: hero id=$heroId not found"); return }
        val slot = item.equipSlot
        if (slot == null) { L.w("VM", "equipItem: item ${item.name} has no equip slot"); return }
        if (!meetsRequirements(hero, item)) {
            val classReq = item.weaponType?.requiredClass()
            val reason = if (classReq != null && classReq != hero.heroClass) "${hero.name} is not a $classReq and cannot equip ${item.name}"
                         else "${hero.name} does not meet the stat requirements for ${item.name}"
            toast("VM", reason)
            return
        }
        _uiState.update { st ->
            val h = st.heroes.find { it.id == heroId } ?: return@update st
            val oldItem = h.equipment[slot]                                 // 该槽位原来装的(可能 null)
            val newHero = h.copy(equipment = h.equipment + (slot to item))  // Map + Pair = 新 Map (key 重复就覆盖)
            val newInventory = st.inventory.toMutableList()                 // 先转 mutable 才能 add/removeAt
            val idx = newInventory.indexOfFirst { it.id == item.id }
            if (idx >= 0) newInventory.removeAt(idx)                        // 仓库少一件该物品
            if (oldItem != null) newInventory.add(oldItem)                  // 旧装备退回仓库
            st.copy(
                heroes = st.heroes.map { if (it.id == heroId) newHero else it },
                inventory = newInventory
            )
        }
        L.i("VM", "equipItem: ${hero.name} equipped ${item.name} (slot $slot)")
    }

    // 卸下装备: 从英雄身上取下 → 退回仓库.
    fun unequipItem(heroId: String, slot: EquipSlot) {
        _uiState.update { state ->
            val hero = state.heroes.find { it.id == heroId } ?: return@update state
            val item = hero.equipment[slot] ?: return@update state          // 槽位空着没必要做事
            L.i("VM", "unequipItem: ${hero.name} unequipped ${item.name} (slot $slot)")
            val newHero = hero.copy(equipment = hero.equipment - slot)      // Map - key = 移除该 key 的新 Map
            state.copy(
                heroes = state.heroes.map { if (it.id == heroId) newHero else it },
                inventory = state.inventory + item
            )
        }
    }

    // ─── 属性点分配 ───────────────────────────────────────

    // 消耗 1 属性点, 加 1 点指定属性.
    fun allocateStatPoint(heroId: String, stat: StatType) {
        _uiState.update { state ->
            val hero = state.heroes.find { it.id == heroId } ?: return@update state
            if (hero.unspentStatPoints <= 0) return@update state            // 没点直接 return 不变
            val newStats = hero.baseStats.toMutableMap()                    // 转 mutable 才能改
            newStats[stat] = (newStats[stat] ?: 0) + 1
            val newHero = hero.copy(
                baseStats = newStats,
                unspentStatPoints = hero.unspentStatPoints - 1
            )
            state.copy(heroes = state.heroes.map { if (it.id == heroId) newHero else it })
        }
    }

    // 消耗 1 属性点, 加 50 HP. 当前 HP 也跟着加 (避免上限提了实战还是亏).
    fun allocateHpPoint(heroId: String) {
        _uiState.update { state ->
            val hero = state.heroes.find { it.id == heroId } ?: return@update state
            if (hero.unspentStatPoints <= 0) return@update state
            val newHero = hero.copy(
                maxHp = hero.maxHp + 50,
                currentHp = hero.currentHp + 50,
                unspentStatPoints = hero.unspentStatPoints - 1
            )
            state.copy(heroes = state.heroes.map { if (it.id == heroId) newHero else it })
        }
    }

    // ─── 技能树 ───────────────────────────────────────────

    // 升级已有法术阶级. 升到目标 tier 需要英雄等级 ≥ 3*tier - 2 (T2=4, T3=7, T4=10). 消耗 1 技能点.
    fun upgradeSpellTier(heroId: String, templateId: String) {
        val hero = _uiState.value.heroes.find { it.id == heroId } ?: return
        val spell = hero.spells.find { it.templateId == templateId }
        if (spell == null) { L.w("VM", "upgradeSpellTier: ${hero.name} has not learned $templateId"); return }
        if (hero.unspentSkillPoints < 1) { toast("VM", "Not enough skill points"); return }
        if (spell.currentTier >= 4) { toast("VM", "This spell is already at max tier (★★★★)"); return }
        val nextTier = spell.currentTier + 1
        val reqLv = HeroSpell.requiredLevel(nextTier)
        if (hero.level < reqLv) { toast("VM", "Next tier requires level $reqLv (current ${hero.level})"); return }

        _uiState.update { state ->
            val h = state.heroes.find { it.id == heroId } ?: return@update state
            val newSpells = h.spells.map {
                if (it.templateId == templateId) it.copy(currentTier = nextTier) else it
            }
            val newHero = h.copy(spells = newSpells, unspentSkillPoints = h.unspentSkillPoints - 1)
            state.copy(heroes = state.heroes.map { if (it.id == heroId) newHero else it })
        }
        L.i("VM", "upgradeSpellTier: ${hero.name}'s $templateId raised to T$nextTier")
    }

    // 学习新法术 (需要法师/牧师职业, 跟法术类型匹配, 1 技能点).
    fun learnSpell(heroId: String, templateId: String) {
        val hero = _uiState.value.heroes.find { it.id == heroId } ?: return
        if (hero.unspentSkillPoints < 1) { toast("VM", "Not enough skill points"); return }
        if (hero.spells.any { it.templateId == templateId }) {              // .any { 谓词 }: 至少一个满足就 true
            toast("VM", "${hero.name} already knows this spell"); return
        }
        // try/catch: SpellRegistry.get 找不到 ID 会抛异常, 包起来防止 ViewModel 崩溃.
        val template = try { SpellRegistry.get(templateId) } catch (e: Exception) {
            L.e("VM", "learnSpell: SpellRegistry has no $templateId", e); return
        }
        val classOk = when (hero.heroClass) {
            HeroClass.MAGE -> template.type == SpellType.MAGIC
            HeroClass.CLERIC -> template.type == SpellType.MIRACLE
            else -> false                                                    // 战士/游侠不能学法术
        }
        if (!classOk) {
            toast("VM", "${hero.heroClass} cannot learn this spell (type mismatch)"); return
        }
        _uiState.update { state ->
            val h = state.heroes.find { it.id == heroId } ?: return@update state
            val newHero = h.copy(
                spells = h.spells + HeroSpell(templateId = templateId, currentTier = 1),
                unspentSkillPoints = h.unspentSkillPoints - 1
            )
            state.copy(heroes = state.heroes.map { if (it.id == heroId) newHero else it })
        }
        L.i("VM", "learnSpell: ${hero.name} learned $templateId")
    }

    // 消耗 1 属性点, 加 20 MP.
    fun allocateMpPoint(heroId: String) {
        _uiState.update { state ->
            val hero = state.heroes.find { it.id == heroId } ?: return@update state
            if (hero.unspentStatPoints <= 0) return@update state
            val newHero = hero.copy(
                maxMp = hero.maxMp + 20,
                currentMp = hero.currentMp + 20,
                unspentStatPoints = hero.unspentStatPoints - 1
            )
            state.copy(heroes = state.heroes.map { if (it.id == heroId) newHero else it })
        }
    }

    // ─── 卖物品 ───────────────────────────────────────────

    // 卖出该 ID 的一件物品 (UI 推荐使用).
    fun sellOne(itemId: String) {
        _uiState.update { state ->
            val idx = state.inventory.indexOfFirst { it.id == itemId }
            if (idx < 0) return@update state
            val item = state.inventory[idx]
            // .toMutableList().apply { ... }: 拷贝成 mutable + apply 块里跑代码 + 返回该 mutable.
            val newInventory = state.inventory.toMutableList().apply { removeAt(idx) }
            val guild = state.gameState.guild
            state.copy(
                inventory = newInventory,
                gameState = state.gameState.copy(
                    guild = guild.copy(gold = guild.gold + item.sellPrice)
                )
            )
        }
    }

    // 卖出该 ID 的全部物品.
    fun sellAll(itemId: String) {
        _uiState.update { state ->
            val matching = state.inventory.filter { it.id == itemId }
            if (matching.isEmpty()) return@update state
            val totalGold = matching.first().sellPrice * matching.size      // 假设同 ID 物品卖价一样
            val newInventory = state.inventory.filter { it.id != itemId }
            val guild = state.gameState.guild
            state.copy(
                inventory = newInventory,
                gameState = state.gameState.copy(
                    guild = guild.copy(gold = guild.gold + totalGold)
                )
            )
        }
    }

    // 兼容旧调用.
    fun sellItem(item: Item) = sellOne(item.id)

    // ─── 探险编队 ─────────────────────────────────────────

    fun addToParty(heroId: String, position: Position) {
        val state = _uiState.value
        if (state.partySlots.size >= 4) { toast("VM", "Party is full (4 max)"); return }
        if (state.partySlots.any { it.heroId == heroId }) { toast("VM", "This hero is already in the party"); return }
        _uiState.update { it.copy(partySlots = it.partySlots + PartySlot(heroId, position)) }
        L.i("VM", "addToParty: $heroId joined party (position $position, now ${state.partySlots.size + 1}/4)")
    }

    fun removeFromParty(heroId: String) {
        _uiState.update { state ->
            state.copy(partySlots = state.partySlots.filter { it.heroId != heroId })
        }
    }

    fun setStrategy(strategy: StrategyType) {
        _uiState.update { it.copy(selectedStrategy = strategy) }
    }

    fun setSecondaryStrategy(strategy: SecondaryStrategyType) {
        _uiState.update { it.copy(selectedSecondaryStrategy = strategy) }
    }

    // 携带消耗品: +1 (按 itemId 增加, 可重复携带同种物品).
    fun addCarryItem(itemId: String) {
        val state = _uiState.value
        val current = state.carriedItemIds
        val max = state.gameState.guild.maxCarryItems
        if (current.size >= max) {
            toast("VM", "Carry slots full ($max), upgrade the tavern to expand")
            return
        }
        // 不能超过仓库中该物品的数量 (否则出战时取不出来).
        val ownedCount = state.inventory.count { it.id == itemId }
        val carriedCount = current.count { it == itemId }
        if (carriedCount >= ownedCount) {
            toast("VM", "All of this item in storage is already carried ($ownedCount)")
            return
        }
        _uiState.update { it.copy(carriedItemIds = it.carriedItemIds + itemId) }
    }

    // ─── 出发前补给购买 ───────────────────────────────────

    private val SUPPLY_PER_GOLD = 5                                         // 1 金币 = 5 补给

    // 花 1 金币加 5 补给到本次出战.
    fun buyExtraSupply() {
        if (_uiState.value.gameState.guild.gold < 1) {
            toast("VM", "Not enough gold to buy supplies")
            return
        }
        _uiState.update { state ->
            val guild = state.gameState.guild
            state.copy(
                gameState = state.gameState.copy(guild = guild.copy(gold = guild.gold - 1)),
                purchasedExtraSupply = state.purchasedExtraSupply + SUPPLY_PER_GOLD
            )
        }
    }

    // 退还 5 补给, 退 1 金币.
    fun refundExtraSupply() {
        _uiState.update { state ->
            if (state.purchasedExtraSupply < SUPPLY_PER_GOLD) return@update state
            val guild = state.gameState.guild
            state.copy(
                gameState = state.gameState.copy(guild = guild.copy(gold = guild.gold + 1)),
                purchasedExtraSupply = state.purchasedExtraSupply - SUPPLY_PER_GOLD
            )
        }
    }

    fun removeCarryItem(itemId: String) {
        _uiState.update { state ->
            val current = state.carriedItemIds.toMutableList()
            val idx = current.indexOf(itemId)
            if (idx >= 0) current.removeAt(idx)                             // 只移除一个, 不是全部
            state.copy(carriedItemIds = current)
        }
    }

    // ─── 探险运行 ─────────────────────────────────────────

    fun startExpedition() {
        val state = _uiState.value
        if (state.partySlots.isEmpty()) {
            toast("VM", "Add at least 1 hero to the party before departing")
            return
        }
        if (state.expedition?.isExploring == true) {                        // ?.: 链式调用, 中间为 null 整个表达式 null
            L.w("VM", "startExpedition: an expedition is already running, ignoring duplicate tap")
            return
        }

        // 从仓库中取出携带的消耗品 (防止仓库里没货, 比如玩家临走删除了).
        val toCarry = state.carriedItemIds.toMutableList()
        val newInventory = state.inventory.toMutableList()
        val actualCarried = mutableListOf<String>()
        for (itemId in toCarry) {
            val idx = newInventory.indexOfFirst { it.id == itemId }
            if (idx >= 0) {
                newInventory.removeAt(idx)
                actualCarried.add(itemId)
            }
        }

        // 实际出战补给 = 酒馆默认 + 战术界面买的额外.
        val totalSupply = state.gameState.guild.defaultExpeditionSupply + state.purchasedExtraSupply
        // 复用上次冒险的楼层 (撤退保留进度); 若无则从 1 层开始.
        val savedLayer = state.currentFloor?.layer ?: 1
        val session = ExpeditionSession(
            dungeonId = "main_dungeon",
            party = state.partySlots,
            strategy = state.selectedStrategy,
            secondaryStrategy = state.selectedSecondaryStrategy,
            currentLayer = savedLayer,
            currentSupply = totalSupply,
            maxSupply = totalSupply,
            carriedItems = actualCarried,
            isExploring = true
        )

        // 出征队员: 状态置 DEPLOYED, 同时满血满蓝 (上一轮撤退保留下来的伤害此刻才修复).
        // currentFloor 不清空 — 让 ExplorationEngine 复用上次冒险的地图 (清空格子/visited 标记/位置 全部保留).
        val partyIds = state.partySlots.map { it.heroId }.toSet()           // toSet() 让后面 in 查询 O(1)
        _uiState.update {
            it.copy(
                expedition = session,
                inventory = newInventory,
                purchasedExtraSupply = 0,                                   // 用完即清, 下次出战重新攒
                heroes = it.heroes.map { h ->
                    if (h.id in partyIds) h.copy(
                        status = HeroStatus.DEPLOYED,
                        currentHp = h.maxHp,
                        currentMp = h.maxMp
                    ) else h
                }
            )
        }

        L.i("VM", "startExpedition: party=${state.partySlots.size}, supply=$totalSupply, layer=$savedLayer, " +
            "strategy=${state.selectedStrategy}/${state.selectedSecondaryStrategy}")
        // 启动协程驱动探索 — 包 try/catch 防止引擎崩了让 UI 卡死.
        viewModelScope.launch {
            try {
                explorationEngine.run(session, _uiState)                    // 阻塞到探险结束
            } catch (e: Exception) {
                L.e("VM", "ExplorationEngine threw — force-ending expedition", e)
                toast("VM", "Expedition error, forced to return (see logs)")
                // 让 UI 跳出探险界面 — 把 expedition 标记为已结束, 把 hero 状态从 DEPLOYED 还原.
                _uiState.update { st ->
                    val ended = st.expedition?.copy(isExploring = false)
                    val partyIds = session.party.map { it.heroId }.toSet()
                    val resetHeroes = st.heroes.map { h ->
                        if (h.id in partyIds) h.copy(status = HeroStatus.IDLE) else h
                    }
                    st.copy(expedition = ended, heroes = resetHeroes)
                }
            }
        }
    }
}

/**
 * UI 状态聚合体 — 所有 13 个屏幕从这里读数据.
 * data class 自动生成 equals / hashCode / copy / toString, 适合做不可变状态容器.
 * 默认值都给空, 这样 new 一个 GameUiState() 就是"空白存档".
 */
data class GameUiState(
    val gameState: GameState = GameState(),                                 //数据

    // 英雄
    val heroes: List<Hero> = emptyList(),                                   // 已招募的
    val recruitableHeroes: List<Hero> = emptyList(),                        // 酒馆里待招募的

    // 物品
    val inventory: List<Item> = emptyList(),                                // 仓库
    val shopEquipments: List<Item> = emptyList(),                           // 铁匠铺货架
    val shopConsumables: List<Item> = emptyList(),                          // 道具店货架

    // 任务
    val quests: List<Quest> = emptyList(),

    // 地下城
    val currentFloor: DungeonFloor? = null,                                 // 当前楼层 (探险中才有)
    val expedition: ExpeditionSession? = null,                              // 当前探险会话

    // 编队临时状态 (玩家在战术界面调好的, 出发前不入存档)
    val partySlots: List<PartySlot> = emptyList(),
    val selectedStrategy: StrategyType = StrategyType.BALANCED,
    val selectedSecondaryStrategy: SecondaryStrategyType = SecondaryStrategyType.EXPLORE,
    val carriedItemIds: List<String> = emptyList(),
    // 出发前在战术界面买的额外补给 (5 补给/1 金币的倍数). 出发后清零.
    val purchasedExtraSupply: Int = 0
)
