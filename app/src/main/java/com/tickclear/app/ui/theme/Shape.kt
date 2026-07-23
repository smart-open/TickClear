package com.tickclear.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// 圆角（D.7）：sm8 / md12 / lg16 / xl20 / 胶囊999
val SmallShape = RoundedCornerShape(8.dp)
val MediumShape = RoundedCornerShape(12.dp)
val LargeShape = RoundedCornerShape(16.dp)
val ExtraLargeShape = RoundedCornerShape(20.dp)
val PillShape = RoundedCornerShape(999.dp)

val TickClearShapes = Shapes(
    extraSmall = SmallShape,
    small = SmallShape,
    medium = MediumShape,
    large = LargeShape,
    extraLarge = ExtraLargeShape,
)
