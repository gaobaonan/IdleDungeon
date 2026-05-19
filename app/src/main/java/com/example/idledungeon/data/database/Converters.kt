package com.example.idledungeon.data.database

import androidx.room.TypeConverter
import com.example.idledungeon.model.DungeonFloor
import com.example.idledungeon.model.EquipSlot
import com.example.idledungeon.model.ExpeditionSession
import com.example.idledungeon.model.Hero
import com.example.idledungeon.model.HeroSpell
import com.example.idledungeon.model.PartySlot
import com.example.idledungeon.model.PlayerGuild
import com.example.idledungeon.model.StatType
import com.example.idledungeon.model.WeaponType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Room TypeConverter — 把 Map / List / 嵌套数据类 ↔ String (JSON).
 * Room 默认只能存 Int/Long/String/Float/Double/Boolean/ByteArray, 其他类型必须经此转换.
 *
 * 用 Gson 反射序列化, 不需要给数据类加任何注解, 改动量最小.
 *
 * 注意: 复杂泛型 (Map<EnumA, V>) 必须用 TypeToken 明确告诉 Gson 类型, 否则反射会丢失泛型参数.
 */
object Converters {

    private val gson = Gson()

    // ── baseStats: Map<StatType, Int> ──
    @TypeConverter
    @JvmStatic
    fun statMapToJson(map: Map<StatType, Int>?): String =
        gson.toJson(map ?: emptyMap<StatType, Int>())

    @TypeConverter
    @JvmStatic
    fun jsonToStatMap(json: String?): Map<StatType, Int> {
        if (json.isNullOrBlank()) return emptyMap()
        val type = object : TypeToken<Map<StatType, Int>>() {}.type
        return gson.fromJson(json, type) ?: emptyMap()
    }

    // ── proficiencyXp: Map<WeaponType, Int> ──
    @TypeConverter
    @JvmStatic
    fun weaponXpMapToJson(map: Map<WeaponType, Int>?): String =
        gson.toJson(map ?: emptyMap<WeaponType, Int>())

    @TypeConverter
    @JvmStatic
    fun jsonToWeaponXpMap(json: String?): Map<WeaponType, Int> {
        if (json.isNullOrBlank()) return emptyMap()
        val type = object : TypeToken<Map<WeaponType, Int>>() {}.type
        return gson.fromJson(json, type) ?: emptyMap()
    }

    // ── equipment: Map<EquipSlot, String> (item id only — 真正的 Item 通过 ItemRegistry 反查) ──
    @TypeConverter
    @JvmStatic
    fun equipMapToJson(map: Map<EquipSlot, String>?): String =
        gson.toJson(map ?: emptyMap<EquipSlot, String>())

    @TypeConverter
    @JvmStatic
    fun jsonToEquipMap(json: String?): Map<EquipSlot, String> {
        if (json.isNullOrBlank()) return emptyMap()
        val type = object : TypeToken<Map<EquipSlot, String>>() {}.type
        return gson.fromJson(json, type) ?: emptyMap()
    }

    // ── inventory ids / carried ids: List<String> ──
    @TypeConverter
    @JvmStatic
    fun stringListToJson(list: List<String>?): String =
        gson.toJson(list ?: emptyList<String>())

    @TypeConverter
    @JvmStatic
    fun jsonToStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    // ── spells: List<HeroSpell> ──
    @TypeConverter
    @JvmStatic
    fun spellListToJson(list: List<HeroSpell>?): String =
        gson.toJson(list ?: emptyList<HeroSpell>())

    @TypeConverter
    @JvmStatic
    fun jsonToSpellList(json: String?): List<HeroSpell> {
        if (json.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<HeroSpell>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    // ── partySlots: List<PartySlot> ──
    @TypeConverter
    @JvmStatic
    fun partySlotListToJson(list: List<PartySlot>?): String =
        gson.toJson(list ?: emptyList<PartySlot>())

    @TypeConverter
    @JvmStatic
    fun jsonToPartySlotList(json: String?): List<PartySlot> {
        if (json.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<PartySlot>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    // ── bossesDefeated: Set<Int> ──
    @TypeConverter
    @JvmStatic
    fun intSetToJson(set: Set<Int>?): String =
        gson.toJson(set ?: emptySet<Int>())

    @TypeConverter
    @JvmStatic
    fun jsonToIntSet(json: String?): Set<Int> {
        if (json.isNullOrBlank()) return emptySet()
        val type = object : TypeToken<Set<Int>>() {}.type
        return gson.fromJson(json, type) ?: emptySet()
    }

    // ── foundItemIds: Set<String> ──
    @TypeConverter
    @JvmStatic
    fun stringSetToJson(set: Set<String>?): String =
        gson.toJson(set ?: emptySet<String>())

    @TypeConverter
    @JvmStatic
    fun jsonToStringSet(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        val type = object : TypeToken<Set<String>>() {}.type
        return gson.fromJson(json, type) ?: emptySet()
    }

    // ── monsterKills: Map<String, Int> ──
    @TypeConverter
    @JvmStatic
    fun stringIntMapToJson(map: Map<String, Int>?): String =
        gson.toJson(map ?: emptyMap<String, Int>())

    @TypeConverter
    @JvmStatic
    fun jsonToStringIntMap(json: String?): Map<String, Int> {
        if (json.isNullOrBlank()) return emptyMap()
        val type = object : TypeToken<Map<String, Int>>() {}.type
        return gson.fromJson(json, type) ?: emptyMap()
    }

    // ── PlayerGuild (整体作为单字段) ──
    @TypeConverter
    @JvmStatic
    fun guildToJson(g: PlayerGuild?): String = gson.toJson(g ?: PlayerGuild())

    @TypeConverter
    @JvmStatic
    fun jsonToGuild(json: String?): PlayerGuild {
        if (json.isNullOrBlank()) return PlayerGuild()
        return gson.fromJson(json, PlayerGuild::class.java) ?: PlayerGuild()
    }

    // ── DungeonFloor? (可空: 没在远征中时为 null) ──
    @TypeConverter
    @JvmStatic
    fun floorToJson(f: DungeonFloor?): String? = f?.let { gson.toJson(it) }

    @TypeConverter
    @JvmStatic
    fun jsonToFloor(json: String?): DungeonFloor? {
        if (json.isNullOrBlank()) return null
        return gson.fromJson(json, DungeonFloor::class.java)
    }

    // ── ExpeditionSession? ──
    @TypeConverter
    @JvmStatic
    fun expeditionToJson(e: ExpeditionSession?): String? = e?.let { gson.toJson(it) }

    @TypeConverter
    @JvmStatic
    fun jsonToExpedition(json: String?): ExpeditionSession? {
        if (json.isNullOrBlank()) return null
        return gson.fromJson(json, ExpeditionSession::class.java)
    }

    // ── recruitableHeroes: List<Hero> (酒馆候选, 每日刷新一次, 同一天重启要保留) ──
    // 注: Hero.equipment 是 Map<EquipSlot, Item>; recruitable 默认 equipment=emptyMap, 序列化轻量.
    @TypeConverter
    @JvmStatic
    fun heroListToJson(list: List<Hero>?): String =
        gson.toJson(list ?: emptyList<Hero>())

    @TypeConverter
    @JvmStatic
    fun jsonToHeroList(json: String?): List<Hero> {
        if (json.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<Hero>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
}
