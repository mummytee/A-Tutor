package com.example.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Part3DParams(
    val radius: Double? = null,
    val radiusTop: Double? = null,
    val radiusBottom: Double? = null,
    val width: Double? = null,
    val height: Double? = null,
    val depth: Double? = null,
    val tube: Double? = null,
    val turns: Double? = null,
    val phase: Double? = null,
    val detail: Int? = null
)

@Serializable
data class Part3DSpec(
    val type: String = "sphere", // box, bevel_box, cylinder, cone, sphere, torus, ring, icosahedron, lathe, tube, helix
    val label: String = "Part",
    val description: String = "",
    val colorHex: String = "#5CE0D2",
    val roughness: Double = 0.42,
    val metalness: Double = 0.15,
    val opacity: Double = 1.0,
    val emissiveHex: String? = null,
    val emissiveIntensity: Double = 0.0,
    val position: List<Double> = listOf(0.0, 0.0, 0.0),
    val rotation: List<Double> = listOf(0.0, 0.0, 0.0),
    val scale: List<Double> = listOf(1.0, 1.0, 1.0),
    val animation: String = "none", // none, spinY, orbitY, bob, pulse
    val orbitSpeed: Double = 0.55,
    val params: Part3DParams = Part3DParams(),
    val points: List<List<Double>>? = null,
    val profile: List<List<Double>>? = null
)

@Serializable
data class Scene3DSpec(
    val title: String = "Interactive 3D Model",
    val subtitle: String = "Drag to orbit • Pinch to zoom • Tap any part",
    val backgroundHex: String = "#0E221F",
    val floating: Boolean = false,
    val parts: List<Part3DSpec> = emptyList()
)

object ThreeDPresets {
    val jsonFormat = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun toJson(spec: Scene3DSpec): String = jsonFormat.encodeToString(spec)

    fun parseOrNull(raw: String): Scene3DSpec? =
        if (raw.isBlank()) null
        else runCatching { jsonFormat.decodeFromString<Scene3DSpec>(raw) }.getOrNull()

    data class PresetOption(
        val id: String,
        val subject: String,
        val label: String,
        val spec: Scene3DSpec
    )

    val presets: List<PresetOption> = listOf(
        PresetOption(
            id = "bohr_carbon",
            subject = "Chemistry",
            label = "Carbon-12 Atom",
            spec = Scene3DSpec(
                title = "Carbon-12 Atom (6p⁺ 6n⁰ 6e⁻)",
                subtitle = "1s² 2s² 2p² electron shells orbiting the nucleus",
                backgroundHex = "#0D1E22",
                floating = true,
                parts = listOf(
                    Part3DSpec(type = "sphere", label = "Proton Cluster (Z=6)", description = "Positively charged protons bound by the strong nuclear force", colorHex = "#FF6B5B", roughness = 0.28, metalness = 0.25, emissiveHex = "#FF6B5B", emissiveIntensity = 0.22, position = listOf(0.14, 0.08, 0.0), params = Part3DParams(radius = 0.28)),
                    Part3DSpec(type = "sphere", label = "Neutron Core (N=6)", description = "Neutral nucleons stabilizing proton-proton repulsion", colorHex = "#95B2B8", roughness = 0.45, metalness = 0.35, position = listOf(-0.15, -0.06, 0.1), params = Part3DParams(radius = 0.27)),
                    Part3DSpec(type = "ring", label = "K-Shell (n=1)", description = "Inner orbital holding 2 electrons", colorHex = "#5CE0D2", roughness = 0.2, metalness = 0.85, opacity = 0.65, position = listOf(0.0, 0.0, 0.0), rotation = listOf(0.45, 0.2, 0.0), params = Part3DParams(radius = 1.05, tube = 0.024)),
                    Part3DSpec(type = "ring", label = "L-Shell (n=2)", description = "Valence shell holding 4 bonding electrons", colorHex = "#F4B860", roughness = 0.2, metalness = 0.85, opacity = 0.72, position = listOf(0.0, 0.0, 0.0), rotation = listOf(-0.55, 0.35, 0.25), params = Part3DParams(radius = 1.78, tube = 0.028)),
                    Part3DSpec(type = "sphere", label = "1s² Electron", description = "Inner-shell electron (-1e)", colorHex = "#5CE0D2", roughness = 0.15, emissiveHex = "#5CE0D2", emissiveIntensity = 0.75, position = listOf(1.05, 0.0, 0.0), animation = "orbitY", orbitSpeed = 1.4, params = Part3DParams(radius = 0.11)),
                    Part3DSpec(type = "sphere", label = "1s² Electron (Spin ↓)", description = "Paired inner-shell electron", colorHex = "#5CE0D2", roughness = 0.15, emissiveHex = "#5CE0D2", emissiveIntensity = 0.75, position = listOf(-1.05, 0.0, 0.0), animation = "orbitY", orbitSpeed = 1.4, params = Part3DParams(radius = 0.11)),
                    Part3DSpec(type = "sphere", label = "Valence Electron #1", description = "Forms covalent sp³ / sp² bonds", colorHex = "#F4B860", roughness = 0.15, emissiveHex = "#F4B860", emissiveIntensity = 0.8, position = listOf(1.78, 0.0, 0.0), animation = "orbitY", orbitSpeed = 0.85, params = Part3DParams(radius = 0.12)),
                    Part3DSpec(type = "sphere", label = "Valence Electron #2", description = "Forms covalent bonds with H, O, N, C", colorHex = "#F4B860", roughness = 0.15, emissiveHex = "#F4B860", emissiveIntensity = 0.8, position = listOf(-1.78, 0.0, 0.0), animation = "orbitY", orbitSpeed = 0.85, params = Part3DParams(radius = 0.12)),
                    Part3DSpec(type = "sphere", label = "Valence Electron #3", description = "2p orbital valence electron", colorHex = "#F4B860", roughness = 0.15, emissiveHex = "#F4B860", emissiveIntensity = 0.8, position = listOf(0.0, 0.0, 1.78), animation = "orbitY", orbitSpeed = 0.85, params = Part3DParams(radius = 0.12))
                )
            )
        ),
        PresetOption(
            id = "dna_helix",
            subject = "Biology",
            label = "DNA Double Helix",
            spec = Scene3DSpec(
                title = "DNA Double Helix (B-DNA)",
                subtitle = "Sugar-phosphate backbones + A-T & G-C complementary base pairs",
                backgroundHex = "#0E221F",
                floating = false,
                parts = listOf(
                    Part3DSpec(type = "helix", label = "5'→3' Backbone Strand A", description = "Alternating deoxyribose sugar & phosphate groups", colorHex = "#5CE0D2", roughness = 0.25, metalness = 0.4, position = listOf(0.0, 0.0, 0.0), params = Part3DParams(radius = 0.72, height = 3.2, turns = 1.6, phase = 0.0, tube = 0.075)),
                    Part3DSpec(type = "helix", label = "3'→5' Backbone Strand B", description = "Antiparallel complementary sugar-phosphate strand", colorHex = "#F4B860", roughness = 0.25, metalness = 0.4, position = listOf(0.0, 0.0, 0.0), params = Part3DParams(radius = 0.72, height = 3.2, turns = 1.6, phase = 3.1416, tube = 0.075)),
                    Part3DSpec(type = "cylinder", label = "Base Pair A=T #1", description = "Adenine paired with Thymine via 2 hydrogen bonds", colorHex = "#FF8A75", roughness = 0.35, position = listOf(0.0, -1.1, 0.0), rotation = listOf(0.0, 0.5, 1.5708), params = Part3DParams(radius = 0.06, height = 1.38)),
                    Part3DSpec(type = "cylinder", label = "Base Pair G≡C #2", description = "Guanine paired with Cytosine via 3 hydrogen bonds", colorHex = "#C5B4FF", roughness = 0.35, position = listOf(0.0, -0.55, 0.0), rotation = listOf(0.0, 1.75, 1.5708), params = Part3DParams(radius = 0.06, height = 1.38)),
                    Part3DSpec(type = "cylinder", label = "Base Pair A=T #3", description = "Purine-pyrimidine complementary rung", colorHex = "#FF8A75", roughness = 0.35, position = listOf(0.0, 0.0, 0.0), rotation = listOf(0.0, 3.0, 1.5708), params = Part3DParams(radius = 0.06, height = 1.38)),
                    Part3DSpec(type = "cylinder", label = "Base Pair G≡C #4", description = "Triple hydrogen bond stabilizes helix", colorHex = "#C5B4FF", roughness = 0.35, position = listOf(0.0, 0.55, 0.0), rotation = listOf(0.0, 4.25, 1.5708), params = Part3DParams(radius = 0.06, height = 1.38)),
                    Part3DSpec(type = "cylinder", label = "Base Pair A=T #5", description = "3.4 nm full helical pitch (~10.5 bp/turn)", colorHex = "#FF8A75", roughness = 0.35, position = listOf(0.0, 1.1, 0.0), rotation = listOf(0.0, 5.5, 1.5708), params = Part3DParams(radius = 0.06, height = 1.38)),
                    Part3DSpec(type = "cylinder", label = "Pedestal Mount", description = "360° structural display base", colorHex = "#243B37", roughness = 0.75, metalness = 0.25, position = listOf(0.0, -1.75, 0.0), params = Part3DParams(radiusTop = 0.9, radiusBottom = 1.05, height = 0.18))
                )
            )
        ),
        PresetOption(
            id = "eukaryotic_cell",
            subject = "Biology",
            label = "Eukaryotic Cell",
            spec = Scene3DSpec(
                title = "Eukaryotic Animal Cell Cross-Section",
                subtitle = "Plasma membrane, central nucleus, mitochondria & Golgi body",
                backgroundHex = "#102124",
                floating = false,
                parts = listOf(
                    Part3DSpec(type = "sphere", label = "Plasma Membrane", description = "Semi-permeable phospholipid bilayer enclosing cytoplasm", colorHex = "#5CE0D2", roughness = 0.18, opacity = 0.30, position = listOf(0.0, 0.0, 0.0), scale = listOf(1.25, 0.9, 1.15), params = Part3DParams(radius = 1.4)),
                    Part3DSpec(type = "sphere", label = "Nucleus", description = "Double-membrane organelle housing chromosomal DNA", colorHex = "#7B61FF", roughness = 0.35, position = listOf(0.0, 0.15, 0.0), params = Part3DParams(radius = 0.52)),
                    Part3DSpec(type = "sphere", label = "Nucleolus", description = "Site of ribosomal RNA (rRNA) transcription", colorHex = "#F4B860", roughness = 0.25, emissiveHex = "#F4B860", emissiveIntensity = 0.25, position = listOf(0.12, 0.22, 0.1), params = Part3DParams(radius = 0.19)),
                    Part3DSpec(type = "bevel_box", label = "Mitochondrion #1", description = "ATP powerhouse via oxidative phosphorylation", colorHex = "#FF6B5B", roughness = 0.4, position = listOf(0.9, -0.1, 0.45), rotation = listOf(0.2, 0.6, 0.3), params = Part3DParams(width = 0.55, height = 0.26, depth = 0.26)),
                    Part3DSpec(type = "bevel_box", label = "Mitochondrion #2", description = "Citric acid cycle & electron transport chain", colorHex = "#FF6B5B", roughness = 0.4, position = listOf(-0.85, 0.05, -0.55), rotation = listOf(-0.2, -0.5, 0.1), params = Part3DParams(width = 0.52, height = 0.24, depth = 0.25)),
                    Part3DSpec(type = "torus", label = "Rough Endoplasmic Reticulum", description = "Ribosome-studded protein folding network", colorHex = "#50C8B8", roughness = 0.5, position = listOf(-0.15, -0.12, 0.35), rotation = listOf(1.2, 0.3, 0.0), params = Part3DParams(radius = 0.72, tube = 0.085)),
                    Part3DSpec(type = "tube", label = "Golgi Apparatus", description = "Modifies, sorts & packages proteins into vesicles", colorHex = "#F4B860", roughness = 0.38, position = listOf(0.55, -0.35, -0.55), params = Part3DParams(tube = 0.075), points = listOf(listOf(-0.35, 0.0, 0.1), listOf(-0.1, 0.12, -0.1), listOf(0.2, 0.05, 0.05), listOf(0.42, -0.05, -0.1))),
                    Part3DSpec(type = "sphere", label = "Lysosome", description = "Hydrolytic enzymes digest cellular waste", colorHex = "#FFD166", roughness = 0.3, animation = "bob", position = listOf(-0.65, 0.35, 0.5), params = Part3DParams(radius = 0.16))
                )
            )
        ),
        PresetOption(
            id = "solar_orrery",
            subject = "Physics",
            label = "Solar System Orrery",
            spec = Scene3DSpec(
                title = "Inner & Outer Planetary Orrery",
                subtitle = "Keplerian orbits around the Sun (not to linear scale)",
                backgroundHex = "#081218",
                floating = true,
                parts = listOf(
                    Part3DSpec(type = "sphere", label = "Sun (G2V Star)", description = "99.86% of Solar System mass; core H→He fusion", colorHex = "#FFB74D", roughness = 0.3, emissiveHex = "#FF9800", emissiveIntensity = 0.9, animation = "spinY", position = listOf(0.0, 0.0, 0.0), params = Part3DParams(radius = 0.52)),
                    Part3DSpec(type = "ring", label = "Mercury Orbit (0.39 AU)", description = "88-day orbital period", colorHex = "#4F6D7A", opacity = 0.5, params = Part3DParams(radius = 0.95, tube = 0.015)),
                    Part3DSpec(type = "sphere", label = "Mercury", description = "Closest & smallest rocky planet", colorHex = "#B0BEC5", roughness = 0.85, animation = "orbitY", orbitSpeed = 1.4, position = listOf(0.95, 0.0, 0.0), params = Part3DParams(radius = 0.1)),
                    Part3DSpec(type = "ring", label = "Earth Orbit (1.00 AU)", description = "Habitable zone; 365.25-day orbit", colorHex = "#4FC3F7", opacity = 0.55, params = Part3DParams(radius = 1.55, tube = 0.018)),
                    Part3DSpec(type = "sphere", label = "Earth", description = "Liquid surface water & magnetosphere", colorHex = "#29B6F6", roughness = 0.4, animation = "orbitY", orbitSpeed = 0.78, position = listOf(0.0, 0.0, 1.55), params = Part3DParams(radius = 0.17)),
                    Part3DSpec(type = "ring", label = "Saturn Orbit", description = "Gas giant with icy ring system", colorHex = "#F4B860", opacity = 0.45, params = Part3DParams(radius = 2.35, tube = 0.018)),
                    Part3DSpec(type = "sphere", label = "Saturn", description = "Low-density gas giant (0.687 g/cm³)", colorHex = "#F4C572", roughness = 0.55, animation = "orbitY", orbitSpeed = 0.36, position = listOf(-2.35, 0.0, 0.0), params = Part3DParams(radius = 0.25)),
                    Part3DSpec(type = "ring", label = "Saturn's Rings", description = "Ice & silicate particles in Roche zone", colorHex = "#FFE0A3", roughness = 0.4, animation = "orbitY", orbitSpeed = 0.36, position = listOf(-2.35, 0.0, 0.0), rotation = listOf(0.32, 0.0, 0.2), params = Part3DParams(radius = 0.42, tube = 0.045))
                )
            )
        ),
        PresetOption(
            id = "rocket_nozzle",
            subject = "Mathematics",
            label = "Paraboloid & Cone",
            spec = Scene3DSpec(
                title = "Lathe Surface of Revolution & Cone",
                subtitle = "3D calculus volume of revolution y = f(x) around the Y-axis",
                backgroundHex = "#11221E",
                floating = false,
                parts = listOf(
                    Part3DSpec(type = "cylinder", label = "Coordinate Base Plane", description = "XZ reference plane (y = 0)", colorHex = "#1E3B35", roughness = 0.85, position = listOf(0.0, 0.05, 0.0), params = Part3DParams(radius = 1.75, height = 0.1)),
                    Part3DSpec(type = "cylinder", label = "Axis of Revolution (Y)", description = "Rotational symmetry axis x = 0, z = 0", colorHex = "#F4F3EE", roughness = 0.3, metalness = 0.7, position = listOf(0.0, 1.25, 0.0), params = Part3DParams(radius = 0.03, height = 2.5)),
                    Part3DSpec(type = "lathe", label = "Hourglass Hyperboloid", description = "Lathe geometry formed by revolving r(y) = 0.3 + 0.45(y-1)²", colorHex = "#5CE0D2", roughness = 0.25, metalness = 0.25, opacity = 0.86, position = listOf(0.0, 0.1, 0.0), profile = listOf(listOf(0.95, 0.0), listOf(0.65, 0.45), listOf(0.38, 1.05), listOf(0.55, 1.6), listOf(0.9, 2.1))),
                    Part3DSpec(type = "ring", label = "Washer Cross-Section dV", description = "Disk/washer slice of thickness dy with area πr²", colorHex = "#F4B860", roughness = 0.2, metalness = 0.6, position = listOf(0.0, 1.15, 0.0), params = Part3DParams(radius = 0.41, tube = 0.045)),
                    Part3DSpec(type = "ring", label = "Upper Riemann Rim", description = "Upper integration boundary y = b", colorHex = "#FF8A75", roughness = 0.3, position = listOf(0.0, 2.2, 0.0), params = Part3DParams(radius = 0.9, tube = 0.035))
                )
            )
        )
    )
}
