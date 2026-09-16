package com.pokedex.app.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Anime-Pokédex "device" palette — the chunky red handheld with the blue lens.
// ---------------------------------------------------------------------------
val DexRed = Color(0xFFE3350D)
val DexRedBright = Color(0xFFEE4B2B)
val DexRedDark = Color(0xFFB5280A)
val DexRedShadow = Color(0xFF8A1E06)
val DexHinge = Color(0xFF6E1704)

val DexLensRim = Color(0xFFF4F4F4)
val DexLensCyan = Color(0xFF57C1F5)
val DexLensGlass = Color(0xFF1C6FB6)
val DexLensDeep = Color(0xFF0E3F73)

val DexLedRed = Color(0xFFF0463B)
val DexLedYellow = Color(0xFFF7D02C)
val DexLedGreen = Color(0xFF4FD268)

// The Pokédex "screen" — the pale blue LCD the anime device shows Pokémon on.
val DexScreen = Color(0xFFD3EBF7)
val DexScreenTop = Color(0xFFE4F3FB)
val DexScreenBottom = Color(0xFFC1E1F0)
val DexScreenBezel = Color(0xFF0B1622)
val DexScreenEdge = Color(0xFF9CC6DA)
val DexReadout = Color(0xFF123642)
val DexReadoutDim = Color(0xFF4C7686)

val DexPanel = Color(0xFFFFFFFF)
val DexPanelInk = Color(0xFF15323C)
val DexPanelBorder = Color(0xFFBBDCEA)

val DexCard = Color(0xFFFFFFFF)
val DexCardInk = Color(0xFF15323C)
val DexCardWell = Color(0xFFE9F3F7)
val DexCardBorder = Color(0xFFBBDCEA)

// Semantic feedback accents — a nature/EV raising a stat, or a stat sitting in
// the top tier, is always this green; lowering a stat, or the bottom tier, is
// always this red. Shared by DetailScreen, MemberEditor, Pickers and StatHexagon
// so the meaning stays consistent everywhere a stat's direction is shown.
val DexStatUp = Color(0xFF2E7D32)
val DexStatDown = Color(0xFFC62828)

// The pale circular "well" a team-slot or team-row avatar sits in.
val DexAvatarWell = Color(0xFFEDF2F4)

// Legacy Material tokens (kept so any old references still compile).
val PokedexRed = DexRed
val PokedexRedDark = DexRedDark
val PokedexBlue = DexLensGlass
val PokedexYellow = DexLedYellow
