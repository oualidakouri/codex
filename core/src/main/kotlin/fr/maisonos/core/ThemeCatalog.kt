package fr.maisonos.core

data class HomeTheme(val id: String, val name: String, val dark: Boolean, val background: Long, val surface: Long,
    val text: Long, val muted: Long, val accent: Long, val onAccent: Long)

object ThemeCatalog {
    val themes = listOf(
        HomeTheme("ocean", "Océan nocturne", true, 0xFF091521, 0xFF142C40, 0xFFF0F7FF, 0xFFB1CADD, 0xFF87D8FF, 0xFF062435),
        HomeTheme("forest", "Forêt", true, 0xFF0C1915, 0xFF19362A, 0xFFF0F8EF, 0xFFB4D1BB, 0xFFA1E4AE, 0xFF10351B),
        HomeTheme("plum", "Prune et cuivre", true, 0xFF211422, 0xFF3A263D, 0xFFFFF1F5, 0xFFDBC0D6, 0xFFFFC19F, 0xFF402418),
        HomeTheme("graphite", "Graphite", true, 0xFF111315, 0xFF25292E, 0xFFF5F6F8, 0xFFC1C8D0, 0xFFDAC7FF, 0xFF302149),
        HomeTheme("sand", "Sable et olive", false, 0xFFF5F0E5, 0xFFFFFCF4, 0xFF302D24, 0xFF666044, 0xFF4C602C, 0xFFFFFFFF),
        HomeTheme("glacier", "Glacier", false, 0xFFEAF3F8, 0xFFF8FCFF, 0xFF183345, 0xFF496679, 0xFF1B607F, 0xFFFFFFFF),
    )
    fun forHome(home: HomeConfig): HomeTheme = themes.firstOrNull { it.id == home.theme } ?: if (home.dark)
        HomeTheme("classic", "Classique", true, 0xFF0B121B, 0xFF141F2B, 0xFFE6EDF5, 0xFFAFBFD4,
            when (home.accent) { "blue" -> 0xFF8FBAFF; "amber" -> 0xFFF1C983; else -> 0xFF6AE0C2 }, 0xFF10251F)
    else HomeTheme("classic", "Classique", false, 0xFFF3F5F5, 0xFFFFFFFF, 0xFF17272D, 0xFF506474,
        when (home.accent) { "blue" -> 0xFF2B5B9C; "amber" -> 0xFF8F5A17; else -> 0xFF246A60 }, 0xFFFFFFFF)
}
