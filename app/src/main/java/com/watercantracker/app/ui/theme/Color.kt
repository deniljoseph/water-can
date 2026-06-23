package com.watercantracker.app.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------------------------
// Water Can Tracker design tokens
// Identity: "fresh water" — deep teal as the structural color (trust, water, calm),
// a warm amber/coral accent reserved ONLY for "who pays next" call-outs so it reads as urgent
// and distinct from the rest of the UI. Avoids generic Material purple defaults.
// ---------------------------------------------------------------------------------------------

// Core brand
val TealDeep = Color(0xFF0B5D6E)      // primary - deep teal, like still water
val TealMid = Color(0xFF1B8A9E)       // primary variant
val AquaLight = Color(0xFF7FD8DE)     // light accents, containers
val AquaPale = Color(0xFFE3F7F6)      // surfaces / light containers

// Accent reserved for "next payer" urgency
val AmberAccent = Color(0xFFE8893B)   // warm coral-amber, used sparingly
val AmberAccentLight = Color(0xFFFCE0C2)

// Status
val SuccessGreen = Color(0xFF2E8B57)
val ErrorRed = Color(0xFFBA1A1A)
val WarningAmber = Color(0xFFB8860B)

// Neutrals
val InkDark = Color(0xFF111B1C)
val SlateGray = Color(0xFF44525A)
val MistGray = Color(0xFFEFF3F3)
val CloudWhite = Color(0xFFFAFDFD)

// Dark theme surfaces
val NightTeal = Color(0xFF0A2A30)
val NightSurface = Color(0xFF0F1E20)
val NightSurfaceVariant = Color(0xFF18302F)
val NightOnSurface = Color(0xFFD8EDED)
