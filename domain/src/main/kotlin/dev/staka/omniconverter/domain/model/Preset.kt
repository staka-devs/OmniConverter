package dev.staka.omniconverter.domain.model

/** 上級モードで保存したコマンド */
data class Preset(val id: String, val name: String, val command: RawCommand)
