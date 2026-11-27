package dev.staka.omniconverter.infrastructure.engine

import dev.staka.omniconverter.domain.engine.ConversionEngine
import dev.staka.omniconverter.domain.port.EngineRegistry

/** 合成ルートで並べた順が、そのままエンジンの優先順になる */
class ListEngineRegistry(override val engines: List<ConversionEngine>) : EngineRegistry
