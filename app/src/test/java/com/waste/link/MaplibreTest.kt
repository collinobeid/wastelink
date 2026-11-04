package com.waste.link

import org.junit.Test
import org.maplibre.compose.map.MaplibreMap

class MaplibreTest {
    @Test
    fun printMaplibreMapArgs() {
        val clazz = Class.forName("org.maplibre.compose.map.MaplibreMapKt")
        clazz.declaredMethods.forEach { method ->
            if (method.name == "MaplibreMap") {
                println("Method: ${method.name}")
                method.parameters.forEach { param ->
                    println("Param: ${param.name} type: ${param.type.name}")
                }
            }
        }
    }
}
