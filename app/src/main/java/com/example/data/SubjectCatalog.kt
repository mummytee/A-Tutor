package com.example.data

import androidx.compose.ui.graphics.Color

data class CurriculumLesson(
    val id: String,
    val title: String,
    val category: String,
    val durationMinutes: Int,
    val summary: String,
    val recommendedBoard: BoardType
)

data class CurriculumSubject(
    val id: String,
    val name: String,
    val tagline: String,
    val accentColorHex: Long,
    val defaultBoardType: BoardType,
    val categories: List<String>,
    val lessons: List<CurriculumLesson>
)

object SubjectCatalog {
    val subjects: List<CurriculumSubject> = listOf(
        CurriculumSubject(
            id = "math",
            name = "Mathematics",
            tagline = "Algebra, Calculus, Geometry & Linear Algebra",
            accentColorHex = 0xFF0F4C4C,
            defaultBoardType = BoardType.MATH,
            categories = listOf("Algebra", "Calculus", "Geometry", "Statistics"),
            lessons = listOf(
                CurriculumLesson(
                    id = "math_quadratics",
                    title = "Quadratic Functions & Vertex Form",
                    category = "Algebra",
                    durationMinutes = 20,
                    summary = "Explore parabolas, completing the square, discriminant, and real-world projectile trajectories.",
                    recommendedBoard = BoardType.GRAPH
                ),
                CurriculumLesson(
                    id = "math_derivatives",
                    title = "Derivatives & Instantaneous Rate of Change",
                    category = "Calculus",
                    durationMinutes = 25,
                    summary = "Understand secant limits, tangent slopes, power rule, and chain rule visually.",
                    recommendedBoard = BoardType.GRAPH
                ),
                CurriculumLesson(
                    id = "math_trig",
                    title = "Unit Circle & Trigonometric Identities",
                    category = "Geometry",
                    durationMinutes = 20,
                    summary = "Connect right triangles to periodic sine and cosine waves on the coordinate plane.",
                    recommendedBoard = BoardType.MATH
                ),
                CurriculumLesson(
                    id = "math_probability",
                    title = "Bayes' Theorem & Conditional Probability",
                    category = "Statistics",
                    durationMinutes = 20,
                    summary = "Reason with prior beliefs, likelihoods, and tree diagrams.",
                    recommendedBoard = BoardType.MINDMAP
                )
            )
        ),
        CurriculumSubject(
            id = "physics",
            name = "Physics",
            tagline = "Mechanics, Electromagnetism, Waves & Optics",
            accentColorHex = 0xFFB56E15,
            defaultBoardType = BoardType.SCIENCE,
            categories = listOf("Mechanics", "Thermodynamics", "Electromagnetism", "Optics"),
            lessons = listOf(
                CurriculumLesson(
                    id = "phys_kinematics",
                    title = "Projectile Motion & Vector Decomposition",
                    category = "Mechanics",
                    durationMinutes = 25,
                    summary = "Separate horizontal inertia from vertical gravitational acceleration.",
                    recommendedBoard = BoardType.GRAPH
                ),
                CurriculumLesson(
                    id = "phys_circuits",
                    title = "Ohm's Law & Kirchhoff Circuit Loops",
                    category = "Electromagnetism",
                    durationMinutes = 25,
                    summary = "Analyze voltage drops, current division, and series-parallel resistor networks.",
                    recommendedBoard = BoardType.SCIENCE
                ),
                CurriculumLesson(
                    id = "phys_thermo",
                    title = "Laws of Thermodynamics & Entropy",
                    category = "Thermodynamics",
                    durationMinutes = 20,
                    summary = "Energy conservation, heat engines, and statistical disorder.",
                    recommendedBoard = BoardType.SCIENCE
                )
            )
        ),
        CurriculumSubject(
            id = "chemistry",
            name = "Chemistry",
            tagline = "Atomic Structure, Bonding, Reactions & Equilibrium",
            accentColorHex = 0xFFB94A34,
            defaultBoardType = BoardType.SCIENCE,
            categories = listOf("Atomic Theory", "Bonding", "Stoichiometry", "Equilibrium"),
            lessons = listOf(
                CurriculumLesson(
                    id = "chem_bonding",
                    title = "Covalent vs Ionic Bonding & Lewis Structures",
                    category = "Bonding",
                    durationMinutes = 20,
                    summary = "Valence electrons, electronegativity, and molecular geometry.",
                    recommendedBoard = BoardType.SCIENCE
                ),
                CurriculumLesson(
                    id = "chem_acids",
                    title = "pH Scale, Buffers & Acid-Base Equilibrium",
                    category = "Equilibrium",
                    durationMinutes = 25,
                    summary = "Brønsted-Lowry proton transfer and Henderson-Hasselbalch calculations.",
                    recommendedBoard = BoardType.MATH
                )
            )
        ),
        CurriculumSubject(
            id = "biology",
            name = "Biology",
            tagline = "Cellular Energetics, Genetics & Evolution",
            accentColorHex = 0xFF2D6A4F,
            defaultBoardType = BoardType.SCIENCE,
            categories = listOf("Cell Biology", "Genetics", "Ecology", "Physiology"),
            lessons = listOf(
                CurriculumLesson(
                    id = "bio_respiration",
                    title = "Cellular Respiration & ATP Synthesis",
                    category = "Cell Biology",
                    durationMinutes = 25,
                    summary = "Trace glycolysis, the Krebs cycle, and oxidative phosphorylation.",
                    recommendedBoard = BoardType.MINDMAP
                ),
                CurriculumLesson(
                    id = "bio_dna",
                    title = "DNA Replication, Transcription & Translation",
                    category = "Genetics",
                    durationMinutes = 25,
                    summary = "From double helix unzipping to ribosome polypeptide assembly.",
                    recommendedBoard = BoardType.SCIENCE
                )
            )
        ),
        CurriculumSubject(
            id = "cs",
            name = "Computer Science",
            tagline = "Algorithms, Data Structures, Systems & AI",
            accentColorHex = 0xFF1D4E89,
            defaultBoardType = BoardType.MINDMAP,
            categories = listOf("Algorithms", "Data Structures", "Systems", "Machine Learning"),
            lessons = listOf(
                CurriculumLesson(
                    id = "cs_bigo",
                    title = "Big-O Complexity & Recurrence Relations",
                    category = "Algorithms",
                    durationMinutes = 20,
                    summary = "Compare O(1), O(log n), O(n log n), and O(2^n) scaling visually.",
                    recommendedBoard = BoardType.GRAPH
                ),
                CurriculumLesson(
                    id = "cs_graphs",
                    title = "BFS, DFS & Dijkstra Shortest Path",
                    category = "Data Structures",
                    durationMinutes = 25,
                    summary = "Traverse weighted and unweighted graphs step-by-step.",
                    recommendedBoard = BoardType.MINDMAP
                )
            )
        ),
        CurriculumSubject(
            id = "history",
            name = "History",
            tagline = "World Civilizations, Revolutions & Geopolitics",
            accentColorHex = 0xFF7F4F24,
            defaultBoardType = BoardType.MINDMAP,
            categories = listOf("Ancient World", "Modern Era", "Revolutions", "Global Trade"),
            lessons = listOf(
                CurriculumLesson(
                    id = "hist_silkroad",
                    title = "The Silk Road & Cultural Exchange",
                    category = "Global Trade",
                    durationMinutes = 20,
                    summary = "Map trade hubs, technologies, and empires linking East Asia and the Mediterranean.",
                    recommendedBoard = BoardType.MINDMAP
                ),
                CurriculumLesson(
                    id = "hist_industrial",
                    title = "The Industrial Revolution & Urbanization",
                    category = "Modern Era",
                    durationMinutes = 20,
                    summary = "Steam power, factory labor, and global economic transformation.",
                    recommendedBoard = BoardType.MINDMAP
                )
            )
        ),
        CurriculumSubject(
            id = "economics",
            name = "Economics",
            tagline = "Microeconomics, Macroeconomics & Game Theory",
            accentColorHex = 0xFF5A4E7C,
            defaultBoardType = BoardType.GRAPH,
            categories = listOf("Microeconomics", "Macroeconomics", "Game Theory"),
            lessons = listOf(
                CurriculumLesson(
                    id = "econ_supply_demand",
                    title = "Supply, Demand & Market Equilibrium",
                    category = "Microeconomics",
                    durationMinutes = 20,
                    summary = "Shifts in curves, price elasticity, and consumer/producer surplus.",
                    recommendedBoard = BoardType.GRAPH
                ),
                CurriculumLesson(
                    id = "econ_policy",
                    title = "Monetary vs Fiscal Policy",
                    category = "Macroeconomics",
                    durationMinutes = 20,
                    summary = "Interest rates, inflation control, and aggregate demand.",
                    recommendedBoard = BoardType.MINDMAP
                )
            )
        ),
        CurriculumSubject(
            id = "english",
            name = "English",
            tagline = "Rhetoric, Literary Analysis & Essay Craft",
            accentColorHex = 0xFF8D3B55,
            defaultBoardType = BoardType.MINDMAP,
            categories = listOf("Rhetoric", "Literature", "Academic Writing"),
            lessons = listOf(
                CurriculumLesson(
                    id = "eng_rhetoric",
                    title = "Ethos, Pathos, Logos & Argument Structure",
                    category = "Rhetoric",
                    durationMinutes = 20,
                    summary = "Analyze persuasive appeals and craft evidence-driven thesis statements.",
                    recommendedBoard = BoardType.MINDMAP
                ),
                CurriculumLesson(
                    id = "eng_syntax",
                    title = "Literary Devices, Tone & Close Reading",
                    category = "Literature",
                    durationMinutes = 20,
                    summary = "Unpack metaphor, irony, and structural pacing in classic texts.",
                    recommendedBoard = BoardType.GENERAL
                )
            )
        )
    )

    fun findSubject(idOrName: String): CurriculumSubject =
        subjects.firstOrNull {
            it.id.equals(idOrName, ignoreCase = true) ||
                it.name.equals(idOrName, ignoreCase = true)
        } ?: subjects.first()

    fun findLesson(lessonId: String): Pair<CurriculumSubject, CurriculumLesson>? {
        for (subject in subjects) {
            val lesson = subject.lessons.firstOrNull { it.id == lessonId }
            if (lesson != null) return subject to lesson
        }
        return null
    }
}
